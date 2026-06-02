package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.query.executor.QueryResult

/**
 * Formats query results for display.
 *
 * Supports multiple output formats:
 * - TABLE: Human-readable table with aligned columns
 * - CSV: Comma-separated values
 * - JSON: JSON array of objects
 * - TSV: Tab-separated values
 */
object QueryFormatter {

    enum class Format {
        TEXT,
        CSV,
        JSON,
        TSV,
        BEANCOUNT
    }

    /**
     * Format query result.
     */
    fun format(result: QueryResult, format: Format = Format.TEXT, numberify: Boolean = false): String {
        val numericResult = if (numberify) extractNumericColumns(result) else result
        return when (format) {
            Format.TEXT -> formatTable(numericResult)
            Format.CSV -> formatCsv(numericResult)
            Format.JSON -> formatJson(numericResult)
            Format.TSV -> formatTsv(numericResult)
            Format.BEANCOUNT -> formatBeancount(numericResult)
        }
    }

    /**
     * Extract only numeric columns (Decimal, Integer, Amount, Position, Inventory).
     */
    private fun extractNumericColumns(result: QueryResult): QueryResult {
        val numericIndices = result.columnNames.mapIndexedNotNull { index, _ ->
            val hasNumeric = result.rows.any { row ->
                val value = row.getOrNull(index)
                value != null && isNumeric(value)
            }
            if (hasNumeric) index else null
        }

        if (numericIndices.isEmpty()) return QueryResult(emptyList(), emptyList())

        val newColumnNames = numericIndices.map { result.columnNames[it] }
        val newRows = result.rows.map { row ->
            numericIndices.map { row[it] }
        }
        return QueryResult(newColumnNames, newRows)
    }

    private fun isNumeric(value: BqlValue): Boolean {
        return value.type in setOf(
            BqlType.Decimal, BqlType.Integer,
            BqlType.Amount, BqlType.Position, BqlType.Inventory
        )
    }

    /**
     * Format as human-readable table.
     */
    private fun formatTable(result: QueryResult): String {
        if (result.rows.isEmpty()) {
            return result.columnNames.joinToString(" | ") + "\n" +
                "-".repeat(result.columnNames.joinToString(" | ").length) + "\n" +
                "(0 rows)\n"
        }

        val sb = StringBuilder()

        // Calculate column widths
        val widths = result.columnNames.mapIndexed { index, name ->
            val maxDataWidth = result.rows.maxOfOrNull { row ->
                formatValue(row.getOrNull(index)).length
            } ?: 0
            maxOf(name.length, maxDataWidth)
        }

        // Header
        sb.appendLine(
            result.columnNames.mapIndexed { i, name ->
                name.padEnd(widths[i])
            }.joinToString(" | ")
        )

        // Separator
        sb.appendLine(
            widths.map { "-".repeat(it) }.joinToString(" | ")
        )

        // Rows
        for (row in result.rows) {
            sb.appendLine(
                row.mapIndexed { i, value ->
                    formatValue(value).padEnd(widths[i])
                }.joinToString(" | ")
            )
        }

        // Row count
        sb.appendLine("(${result.rows.size} rows)")

        return sb.toString()
    }

    /**
     * Format as CSV.
     */
    private fun formatCsv(result: QueryResult): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine(result.columnNames.joinToString(",") { escapeCsv(it) })

        // Rows
        for (row in result.rows) {
            sb.appendLine(
                row.map { escapeCsv(formatValue(it)) }.joinToString(",")
            )
        }

        return sb.toString()
    }

    /**
     * Format as TSV.
     */
    private fun formatTsv(result: QueryResult): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine(result.columnNames.joinToString("\t"))

        // Rows
        for (row in result.rows) {
            sb.appendLine(row.map { formatValue(it) }.joinToString("\t"))
        }

        return sb.toString()
    }

    /**
     * Format as Beancount syntax.
     * Simplified implementation: outputs results as beancount-compatible text.
     */
    private fun formatBeancount(result: QueryResult): String {
        val sb = StringBuilder()
        sb.appendLine("; Query results in beancount format")
        sb.appendLine()

        for (row in result.rows) {
            val date = row.getOrNull(result.columnNames.indexOf("date"))?.let { formatValue(it) } ?: ""
            val account = row.getOrNull(result.columnNames.indexOf("account"))?.let { formatValue(it) } ?: ""
            val narration = row.getOrNull(result.columnNames.indexOf("narration"))?.let { formatValue(it) } ?: ""
            val flag = row.getOrNull(result.columnNames.indexOf("flag"))?.let { formatValue(it) } ?: "*"
            val position = row.getOrNull(result.columnNames.indexOf("position"))?.let { formatValue(it) } ?: ""
            val amount = row.getOrNull(result.columnNames.indexOf("amount"))?.let { formatValue(it) } ?: ""

            if (date.isNotEmpty() && account.isNotEmpty()) {
                sb.appendLine("$date $flag \"$narration\"")
                val pos = position.ifEmpty { amount }
                if (pos.isNotEmpty()) {
                    sb.appendLine("  $account  $pos")
                } else {
                    sb.appendLine("  $account")
                }
                sb.appendLine()
            } else {
                // Fallback to text format for non-transaction rows
                sb.appendLine(row.map { formatValue(it) }.joinToString(" | "))
            }
        }

        return sb.toString()
    }

    /**
     * Format as JSON.
     */
    private fun formatJson(result: QueryResult): String {
        val sb = StringBuilder()
        sb.appendLine("[")

        result.rows.forEachIndexed { index, row ->
            sb.append("  {")
            val pairs = row.mapIndexed { i, value ->
                "\"${result.columnNames[i]}\": ${jsonValue(value)}"
            }
            sb.append(pairs.joinToString(", "))
            sb.append("}")
            if (index < result.rows.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("]")
        return sb.toString()
    }

    /**
     * Format a single BqlValue for display.
     */
    private fun formatValue(value: BqlValue?): String {
        if (value == null) return ""
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

    /**
     * Format value for JSON output.
     */
    private fun jsonValue(value: BqlValue?): String {
        if (value == null) return "null"
        return when {
            value.isNull() -> "null"
            value.type == BqlType.String -> "\"${escapeJson(value.asString())}\""
            value.type == BqlType.Decimal -> "\"${value.asDecimal().toPlainString()}\""
            value.type == BqlType.Integer -> value.asInteger().toString()
            value.type == BqlType.Date -> "\"${value.asDate()}\""
            value.type == BqlType.Boolean -> value.asBoolean().toString()
            else -> "\"${formatValue(value)}\""
        }
    }

    /**
     * Escape string for CSV.
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Escape string for JSON.
     */
    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
