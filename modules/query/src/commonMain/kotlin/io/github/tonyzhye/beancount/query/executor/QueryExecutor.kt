package io.github.tonyzhye.beancount.query.executor

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

        // Step 4: Compute targets
        val resultRows = mutableListOf<List<BqlValue>>()
        
        // Check if any target is an aggregator
        val hasAggregators = query.targets.any { it.node is EvalAggregator }
        
        if (hasAggregators) {
            // Aggregate query: one result row per group
            for (group in groupedRows) {
                val rowValues = mutableListOf<BqlValue>()
                for (target in query.targets) {
                    val value = if (target.node is EvalAggregator) {
                        val aggregator = target.node as EvalAggregator
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
                    resultRows.add(rowValues)
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
                    resultRows.add(rowValues)
                }
            }
        }

        // Step 5: DISTINCT
        var finalRows = if (query.distinct) {
            resultRows.distinct()
        } else {
            resultRows
        }

        // Step 6: ORDER BY
        if (query.orderBy.isNotEmpty()) {
            finalRows = sortRows(finalRows, query.orderBy, query.targets)
        }

        // Step 7: LIMIT
        if (query.limit != null) {
            finalRows = finalRows.take(query.limit)
        }

        return QueryResult(
            columnNames = query.targets.map { it.name },
            rows = finalRows
        )
    }

    private fun groupRows(rows: List<RowContext>, groupBy: List<EvalNode>): List<List<RowContext>> {
        val groups = mutableMapOf<List<BqlValue>, MutableList<RowContext>>()
        for (row in rows) {
            val key = groupBy.map { it.evaluate(row) }
            groups.getOrPut(key) { mutableListOf() }.add(row)
        }
        return groups.values.toList()
    }

    private fun sortRows(
        rows: List<List<BqlValue>>,
        orderBy: List<OrderByNode>,
        targets: List<TargetNode>
    ): List<List<BqlValue>> {
        return rows.sortedWith { row1, row2 ->
            var result = 0
            for (order in orderBy) {
                // Find the column index for the order expression
                val idx = targets.indexOfFirst { it.name == (order.node as? EvalColumn)?.name }
                if (idx >= 0) {
                    val v1 = row1.getOrNull(idx)
                    val v2 = row2.getOrNull(idx)
                    result = compareValues(v1, v2)
                    if (result != 0) {
                        if (order.descending) result = -result
                        break
                    }
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
}
