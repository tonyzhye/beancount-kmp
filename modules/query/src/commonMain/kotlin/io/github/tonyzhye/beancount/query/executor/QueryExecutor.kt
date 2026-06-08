package io.github.tonyzhye.beancount.query.executor

import io.github.tonyzhye.beancount.core.Decimal
import io.github.tonyzhye.beancount.query.*
import io.github.tonyzhye.beancount.query.compiler.*
import io.github.tonyzhye.beancount.query.tables.Table

/**
 * Query result.
 */
data class QueryResult(
    val columnNames: List<String>,
    val rows: List<List<BqlValue>>
) {
    override fun toString(): String {
        val sb = StringBuilder()
        // Header
        sb.append(columnNames.joinToString(" | "))
        sb.appendLine()
        sb.append(columnNames.joinToString(" | ") { "-".repeat(it.length) })
        sb.appendLine()
        // Rows
        for (row in rows) {
            sb.append(row.joinToString(" | ") { formatValue(it) })
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun formatValue(value: BqlValue): String {
        return when {
            value.isNull() -> ""
            value.type == BqlType.String -> value.asString()
            value.type == BqlType.Decimal -> value.asDecimal().toPlainString()
            value.type == BqlType.Integer -> value.asInteger().toString()
            value.type == BqlType.Date -> value.asDate().toString()
            value.type == BqlType.Boolean -> value.asBoolean().toString()
            value.type == BqlType.Set -> value.asSet().toString()
            value.type == BqlType.Inventory -> value.asInventory().toString()
            value.type == BqlType.Position -> value.asPosition().toString()
            value.type == BqlType.Amount -> value.asAmount().toString()
            else -> value.raw?.toString() ?: ""
        }
    }
}

/**
 * Intermediate result row that retains RowContext for ORDER BY evaluation.
 */
private data class ResultRow(
    val values: List<BqlValue>,
    val context: RowContext?  // Representative context for ORDER BY evaluation
)

/**
 * Query executor.
 * Based on beanquery.query_execute.
 */
class QueryExecutor(
    private val table: Table
) {
    fun execute(query: CompiledQuery): QueryResult {
        // Step 1: Iterate table rows
        var rows = table.iterator().asSequence().toList()

        // Step 2: Apply WHERE filter
        if (query.where != null) {
            rows = rows.filter { row ->
                val result = query.where.evaluate(row)
                !result.isNull() && result.asBoolean()
            }
        }

        // Step 3: GROUP BY
        val groupedRows = if (query.groupBy.isNotEmpty()) {
            groupRows(rows, query.groupBy)
        } else {
            listOf(rows)
        }

        // Step 4: Compute targets and apply HAVING
        val resultRows = mutableListOf<ResultRow>()
        
        // Check if any target is an aggregator
        val hasAggregators = query.targets.any { it.node is EvalAggregator }
        
        if (hasAggregators) {
            // Aggregate query: one result row per group
            for (group in groupedRows) {
                val rowValues = mutableListOf<BqlValue>()
                for (target in query.targets) {
                    val value = if (target.node is EvalAggregator) {
                        val aggregator = target.node
                        val accumulator = aggregator.createAccumulator()
                        for (row in group) {
                            accumulator.update(aggregator.operand.evaluate(row))
                        }
                        accumulator.finalize()
                    } else {
                        target.node.evaluate(group.firstOrNull() ?: continue)
                    }
                    rowValues.add(value)
                }
                if (rowValues.isNotEmpty()) {
                    // Apply HAVING filter
                    if (query.having != null) {
                        val havingResult = evaluateWithAggregates(query.having, group)
                        if (!havingResult.isNull() && !havingResult.asBoolean()) {
                            continue  // Skip groups that don't match HAVING
                        }
                    }
                    resultRows.add(ResultRow(rowValues, group.firstOrNull()))
                }
            }
        } else {
            // Non-aggregate query: one result row per input row
            for (group in groupedRows) {
                for (row in group) {
                    val rowValues = mutableListOf<BqlValue>()
                    for (target in query.targets) {
                        rowValues.add(target.node.evaluate(row))
                    }
                    // Apply HAVING filter (for non-aggregate queries, HAVING behaves like WHERE)
                    if (query.having != null) {
                        val havingResult = query.having.evaluate(row)
                        if (!havingResult.isNull() && !havingResult.asBoolean()) {
                            continue  // Skip rows that don't match HAVING
                        }
                    }
                    resultRows.add(ResultRow(rowValues, row))
                }
            }
        }

        // Step 5: DISTINCT
        var finalRows = if (query.distinct) {
            resultRows.distinctBy { it.values }
        } else {
            resultRows
        }

        // Step 6: ORDER BY (using EvalNode for expression support)
        if (query.orderBy.isNotEmpty()) {
            finalRows = sortRows(finalRows, query.orderBy)
        }

        // Step 7: LIMIT
        if (query.limit != null) {
            finalRows = finalRows.take(query.limit)
        }

        // Step 8: PIVOT BY
        val (finalColumnNames, finalRows2) = if (query.pivotBy != null) {
            applyPivotBy(query, finalRows)
        } else {
            query.targets.map { it.name } to finalRows.map { it.values }
        }

        return QueryResult(
            columnNames = finalColumnNames,
            rows = finalRows2
        )
    }

    /**
     * Apply PIVOT BY transformation.
     *
     * pivotBy contains two column indexes:
     * - index 0: row key column ( GROUP BY column that identifies rows)
     * - index 1: column key column (GROUP BY column whose values become new columns)
     */
    private fun applyPivotBy(
        query: CompiledQuery,
        rows: List<ResultRow>
    ): Pair<List<String>, List<List<BqlValue>>> {
        val pivots = query.pivotBy!!
        val col1 = pivots[0]
        val col2 = pivots[1]
        val otherCols = query.targets.indices.filter { it !in pivots }
        val nother = otherCols.size

        // Collect unique values for the column key
        val keys = rows.map { it.values[col2] }.distinct().sortedWith(::compareValues)

        // Build new column names
        val columnNames = mutableListOf<String>()
        columnNames.add(query.targets[col1].name)
        for (key in keys) {
            if (nother > 1) {
                for (otherCol in otherCols) {
                    columnNames.add("$key/${query.targets[otherCol].name}")
                }
            } else {
                columnNames.add("$key")
            }
        }

        // Build pivoted rows
        val pivotedRows = mutableListOf<List<BqlValue>>()
        val rowsByCol1 = rows.groupBy { it.values[col1] }.toList().sortedWith { a, b ->
            compareValues(a.first, b.first)
        }

        for ((field1, group) in rowsByCol1) {
            val outRow = MutableList<BqlValue>(columnNames.size) { BqlNullValue() }
            outRow[0] = field1

            for (row in group) {
                val keyIndex = keys.indexOf(row.values[col2])
                if (keyIndex >= 0) {
                    for (i in otherCols.indices) {
                        val colIndex = keyIndex * nother + 1 + i
                        if (colIndex < outRow.size) {
                            outRow[colIndex] = row.values[otherCols[i]]
                        }
                    }
                }
            }
            pivotedRows.add(outRow)
        }

        return columnNames to pivotedRows
    }

    private fun groupRows(rows: List<RowContext>, groupBy: List<EvalNode>): List<List<RowContext>> {
        val groups = mutableMapOf<List<BqlValue>, MutableList<RowContext>>()
        for (row in rows) {
            val key = groupBy.map { it.evaluate(row) }
            groups.getOrPut(key) { mutableListOf() }.add(row)
        }
        return groups.values.toList()
    }

    /**
     * Evaluate an expression that may contain aggregate functions using a group of rows.
     * This is used for HAVING clause evaluation.
     */
    private fun evaluateWithAggregates(node: EvalNode, group: List<RowContext>): BqlValue {
        return when (node) {
            is EvalAggregator -> {
                val accumulator = node.createAccumulator()
                for (row in group) {
                    accumulator.update(node.operand.evaluate(row))
                }
                accumulator.finalize()
            }
            is EvalBinaryOp -> {
                val left = evaluateWithAggregates(node.left, group)
                val right = evaluateWithAggregates(node.right, group)
                when (node.operator) {
                    "=" -> BqlBooleanValue(left == right)
                    "!=" -> BqlBooleanValue(left != right)
                    "<" -> BqlBooleanValue(compareMixedValues(left, right) < 0)
                    ">" -> BqlBooleanValue(compareMixedValues(left, right) > 0)
                    "<=" -> BqlBooleanValue(compareMixedValues(left, right) <= 0)
                    ">=" -> BqlBooleanValue(compareMixedValues(left, right) >= 0)
                    "AND" -> BqlBooleanValue(left.asBoolean() && right.asBoolean())
                    "OR" -> BqlBooleanValue(left.asBoolean() || right.asBoolean())
                    else -> throw IllegalArgumentException("Unknown operator in HAVING: ${node.operator}")
                }
            }
            is EvalUnaryOp -> {
                val value = evaluateWithAggregates(node.operand, group)
                when (node.operator) {
                    "NOT" -> BqlBooleanValue(!value.asBoolean())
                    else -> throw IllegalArgumentException("Unknown unary operator in HAVING: ${node.operator}")
                }
            }
            else -> node.evaluate(group.firstOrNull() ?: return BqlNullValue())
        }
    }

    private fun sortRows(
        rows: List<ResultRow>,
        orderBy: List<OrderByNode>
    ): List<ResultRow> {
        return rows.sortedWith { row1, row2 ->
            var result = 0
            for (order in orderBy) {
                // Evaluate ORDER BY expression on the representative context
                val v1 = row1.context?.let { order.node.evaluate(it) }
                val v2 = row2.context?.let { order.node.evaluate(it) }
                result = compareValues(v1, v2)
                if (result != 0) {
                    if (order.descending) result = -result
                    break
                }
            }
            result
        }
    }

    private fun compareValues(v1: BqlValue?, v2: BqlValue?): Int {
        if (v1 == null && v2 == null) return 0
        if (v1 == null) return 1  // NULLs last
        if (v2 == null) return -1

        return when {
            v1.type == BqlType.Decimal && v2.type == BqlType.Decimal ->
                v1.asDecimal().compareTo(v2.asDecimal())
            v1.type == BqlType.Integer && v2.type == BqlType.Integer ->
                v1.asInteger().compareTo(v2.asInteger())
            v1.type == BqlType.Date && v2.type == BqlType.Date ->
                v1.asDate().compareTo(v2.asDate())
            v1.type == BqlType.String && v2.type == BqlType.String ->
                v1.asString().compareTo(v2.asString())
            v1.type == BqlType.Boolean && v2.type == BqlType.Boolean ->
                v1.asBoolean().compareTo(v2.asBoolean())
            else -> 0
        }
    }

    /**
     * Compare values with mixed types (e.g., Decimal vs Integer).
     */
    private fun compareMixedValues(v1: BqlValue, v2: BqlValue): Int {
        if (v1.isNull() && v2.isNull()) return 0
        if (v1.isNull()) return 1
        if (v2.isNull()) return -1

        // Handle numeric comparisons (Decimal vs Integer)
        if ((v1.type == BqlType.Decimal || v1.type == BqlType.Integer) &&
            (v2.type == BqlType.Decimal || v2.type == BqlType.Integer)) {
            val d1 = if (v1.type == BqlType.Integer) Decimal(v1.asInteger().toString()) else v1.asDecimal()
            val d2 = if (v2.type == BqlType.Decimal) v2.asDecimal() else Decimal(v2.asInteger().toString())
            return d1.compareTo(d2)
        }

        return compareValues(v1, v2)
    }
}
