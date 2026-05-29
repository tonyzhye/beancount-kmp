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
        TABLE,
        CSV,
        JSON,
        TSV
    }

    /**
     * Format query result.
     */
    fun format(result: QueryResult, format: Format = Format.TABLE): String {
        return when (format) {
            Format.TABLE -> formatTable(result)
            Format.CSV -> formatCsv(result)
            Format.JSON -> formatJson(result)
            Format.TSV -> formatTsv(result)
        }
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
