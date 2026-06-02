package io.github.tonyzhye.beancount.core

/**
 * Table data structure for rendering reports.
 *
 * Based on beancount.utils.table.Table
 *
 * @param columns Column names/identifiers
 * @param header Header row display text
 * @param body Table body rows (each row is a list of cell strings)
 */
data class Table(
    val columns: List<String>,
    val header: List<String>,
    val body: List<List<String>>
)

/**
 * Column specification for table creation.
 *
 * @param name Column name or index
 * @param header Header text (null = auto-generated from name)
 * @param formatter Optional formatter function
 */
data class ColumnSpec(
    val name: String,
    val header: String? = null,
    val formatter: ((Any?) -> String)? = null
)

/**
 * Convert snake_case to Title Case.
 *
 * Based on beancount.utils.table.attribute_to_title
 */
fun attributeToTitle(fieldName: String): String {
    return fieldName.replace("_", " ").replaceFirstChar { it.uppercase() }
}

/**
 * Create a Table from a list of row data.
 *
 * Based on beancount.utils.table.create_table
 *
 * @param headers Column headers
 * @param rows List of row data (each row is a list of cell values)
 * @param formatters Optional formatters for each column
 * @return A Table instance ready for rendering
 */
fun createTable(
    headers: List<String>,
    rows: List<List<Any?>>,
    formatters: List<((Any?) -> String)?>? = null
): Table {
    if (rows.isEmpty()) {
        return Table(emptyList(), emptyList(), emptyList())
    }

    val body = rows.map { row ->
        row.mapIndexed { index, value ->
            val formatter = formatters?.getOrNull(index)
            when {
                value == null -> ""
                formatter != null -> formatter(value)
                else -> value.toString()
            }
        }
    }

    val columns = headers.mapIndexed { index, header ->
        "col$index"
    }

    return Table(columns, headers, body)
}

/**
 * Compute the maximum width needed for each column.
 *
 * Based on beancount.utils.table.compute_table_widths
 */
fun computeTableWidths(rows: List<List<String>>): List<Int> {
    if (rows.isEmpty()) return emptyList()

    val numColumns = rows.first().size
    val widths = MutableList(numColumns) { 0 }

    for (row in rows) {
        require(row.size == numColumns) { "All rows must have the same number of columns" }
        for (i in row.indices) {
            widths[i] = maxOf(widths[i], row[i].length)
        }
    }

    return widths
}

/**
 * Render a Table to ASCII text.
 *
 * Based on beancount.utils.table.table_to_text
 *
 * @param table The table to render
 * @param columnInterspace String to place between columns
 * @param formats Optional column format overrides (column name to alignment char: '<', '>', '^')
 * @return Formatted text table
 */
fun tableToText(
    table: Table,
    columnInterspace: String = "  ",
    formats: Map<String, Char> = emptyMap()
): String {
    if (table.body.isEmpty()) {
        return if (table.header.isNotEmpty()) table.header.joinToString(columnInterspace) + "\n" else ""
    }

    val allRows = if (table.header.isNotEmpty()) listOf(table.header) + table.body else table.body
    val widths = computeTableWidths(allRows)

    val aligns = table.columns.mapIndexed { index, colName ->
        formats[colName] ?: formats["*"] ?: '<'
    }

    fun formatRow(row: List<String>): String {
        return row.mapIndexed { index, cell ->
            val width = widths.getOrElse(index) { 0 }
            when (aligns.getOrElse(index) { '<' }) {
                '>' -> cell.padStart(width)
                '^' -> {
                    val padding = width - cell.length
                    val left = padding / 2
                    val right = padding - left
                    " ".repeat(left) + cell + " ".repeat(right)
                }
                else -> cell.padEnd(width)
            }
        }.joinToString(columnInterspace) + "\n"
    }

    val separator = widths.map { width ->
        "-".repeat(width)
    }.joinToString(columnInterspace) + "\n"

    val output = StringBuilder()

    if (table.header.isNotEmpty()) {
        output.append(formatRow(table.header))
    }

    output.append(separator)

    for (row in table.body) {
        output.append(formatRow(row))
    }

    output.append(separator)

    return output.toString()
}

/**
 * Render a Table to CSV format.
 *
 * Based on beancount.utils.table.table_to_csv
 *
 * @param table The table to render
 * @return CSV formatted string
 */
fun tableToCsv(table: Table): String {
    val output = StringBuilder()

    if (table.header.isNotEmpty()) {
        output.append(table.header.joinToString(",") { escapeCsvCell(it) })
        output.append("\n")
    }

    for (row in table.body) {
        output.append(row.joinToString(",") { escapeCsvCell(it) })
        output.append("\n")
    }

    return output.toString()
}

/**
 * Escape a cell for CSV output.
 */
private fun escapeCsvCell(cell: String): String {
    return when {
        cell.contains(",") || cell.contains("\"") || cell.contains("\n") -> {
            val escaped = cell.replace("\"", "\"\"")
            "\"$escaped\""
        }
        else -> cell
    }
}

/**
 * Render a Table to HTML.
 *
 * Based on beancount.utils.table.table_to_html
 *
 * @param table The table to render
 * @param classes Optional CSS classes
 * @return HTML table string
 */
fun tableToHtml(table: Table, classes: List<String> = emptyList()): String {
    val output = StringBuilder()

    val classAttr = if (classes.isNotEmpty()) " class=\"${classes.joinToString(" ")}\"" else ""
    output.append("<table$classAttr>\n")

    if (table.header.isNotEmpty()) {
        output.append("  \u003cthead\u003e\n")
        output.append("    \u003ctr\u003e\n")
        for (header in table.header) {
            output.append("      \u003cth\u003e${escapeHtml(header)}\u003c/th\u003e\n")
        }
        output.append("    \u003c/tr\u003e\n")
        output.append("  \u003c/thead\u003e\n")
    }

    output.append("  \u003ctbody\u003e\n")
    for (row in table.body) {
        output.append("    \u003ctr\u003e\n")
        for (cell in row) {
            output.append("      \u003ctd\u003e${escapeHtml(cell)}\u003c/td\u003e\n")
        }
        output.append("    \u003c/tr\u003e\n")
    }
    output.append("  \u003c/tbody\u003e\n")
    output.append("\u003c/table\u003e\n")

    return output.toString()
}

/**
 * Escape HTML special characters.
 */
private fun escapeHtml(text: String): String {
    return text
        .replace("\u0026", "\u0026amp;")
        .replace("\u003c", "\u0026lt;")
        .replace("\u003e", "\u0026gt;")
        .replace("\"", "\u0026quot;")
}

/**
 * Render a Table to the requested format.
 *
 * Based on beancount.utils.table.render_table
 *
 * @param table The table to render
 * @param outputFormat One of: "txt", "text", "csv", "html", "htmldiv"
 * @return Rendered output string
 */
fun renderTable(table: Table, outputFormat: String): String {
    return when (outputFormat.lowercase()) {
        "txt", "text" -> tableToText(table, "  ", mapOf("*" to '>', "account" to '<'))
        "csv" -> tableToCsv(table)
        "html" -> "\u003chtml\u003e\n\u003cbody\u003e\n${tableToHtml(table)}\u003c/body\u003e\n\u003c/html\u003e\n"
        "htmldiv" -> "\u003cdiv\u003e\n${tableToHtml(table)}\u003c/div\u003e\n"
        else -> throw IllegalArgumentException("Unsupported format: $outputFormat")
    }
}
