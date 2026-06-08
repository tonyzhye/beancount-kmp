package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.query.executor.QueryResult
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Extended tests for QueryFormatter.
 */
class QueryFormatterExtendedTest {

    private fun date(y: Int, m: Int, d: Int) = BqlDateValue(LocalDate(y, m, d))
    private fun string(s: String) = BqlStringValue(s)
    private fun decimal(s: String) = BqlDecimalValue(io.github.tonyzhye.beancount.core.Decimal(s))
    private fun integer(i: Int) = BqlIntegerValue(i)
    private fun bool(b: Boolean) = BqlBooleanValue(b)
    private fun amount(n: String, c: String) = BqlAmountValue(io.github.tonyzhye.beancount.core.Amount(io.github.tonyzhye.beancount.core.Decimal(n), c))
    private fun set(vararg items: String) = BqlSetValue(items.toSet())
    private fun nullValue() = BqlNullValue()

    @Test
    fun `should format as beancount`() {
        val result = QueryResult(
            columnNames = listOf("date", "flag", "narration", "account", "position"),
            rows = listOf(
                listOf(date(2024, 1, 1), string("*"), string("Test"), string("Assets:Bank"), amount("100", "USD"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.BEANCOUNT)

        assertTrue(formatted.contains("2024-01-01"))
        assertTrue(formatted.contains("Assets:Bank"))
        assertTrue(formatted.contains("100 USD"))
    }

    @Test
    fun `should format beancount fallback for non-transaction rows`() {
        val result = QueryResult(
            columnNames = listOf("account", "total"),
            rows = listOf(
                listOf(string("Assets:Bank"), decimal("100.00"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.BEANCOUNT)
        assertTrue(formatted.contains("Assets:Bank"))
    }

    @Test
    fun `numberify should extract only numeric columns`() {
        val result = QueryResult(
            columnNames = listOf("date", "account", "number"),
            rows = listOf(
                listOf(date(2024, 1, 1), string("Assets:Cash"), decimal("100.00"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TEXT, numberify = true)
        assertFalse(formatted.contains("date"))
        assertFalse(formatted.contains("account"))
        assertTrue(formatted.contains("number"))
    }

    @Test
    fun `numberify with no numeric columns should return empty`() {
        val result = QueryResult(
            columnNames = listOf("date", "account"),
            rows = listOf(
                listOf(date(2024, 1, 1), string("Assets:Cash"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TEXT, numberify = true)
        assertTrue(formatted.contains("(0 rows)"))
    }

    @Test
    fun `should format boolean values`() {
        val result = QueryResult(
            columnNames = listOf("flag"),
            rows = listOf(listOf(bool(true)), listOf(bool(false)))
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TEXT)
        assertTrue(formatted.contains("true"))
        assertTrue(formatted.contains("false"))
    }

    @Test
    fun `should format set values`() {
        val result = QueryResult(
            columnNames = listOf("tags"),
            rows = listOf(listOf(set("food", "daily")))
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TEXT)
        assertTrue(formatted.contains("food"))
    }

    @Test
    fun `should format amount values`() {
        val result = QueryResult(
            columnNames = listOf("position"),
            rows = listOf(listOf(amount("100.00", "USD")))
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TEXT)
        assertTrue(formatted.contains("100.00"))
    }

    @Test
    fun `should handle null in csv`() {
        val result = QueryResult(
            columnNames = listOf("col1", "col2"),
            rows = listOf(listOf(nullValue(), string("test")))
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.CSV)
        // Null value produces empty field: col1,col2 -> ,test
        assertTrue(formatted.contains(",test"))
    }

    @Test
    fun `should handle null in tsv`() {
        val result = QueryResult(
            columnNames = listOf("col1", "col2"),
            rows = listOf(listOf(nullValue(), string("test")))
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TSV)
        // Null value produces empty field
        assertTrue(formatted.contains("\ttest"))
    }

    @Test
    fun `should escape json special characters`() {
        val result = QueryResult(
            columnNames = listOf("text"),
            rows = listOf(
                listOf(string("line1\nline2")),
                listOf(string("tab\there")),
                listOf(string("backslash\\path"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.JSON)
        assertTrue(formatted.contains("\\n"))
        assertTrue(formatted.contains("\\t"))
        assertTrue(formatted.contains("\\\\"))
    }

    @Test
    fun `should escape csv with quotes`() {
        val result = QueryResult(
            columnNames = listOf("desc"),
            rows = listOf(listOf(string("He said \"hello\"")))
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.CSV)
        assertTrue(formatted.contains("\"\"hello\"\""))
    }

    @Test
    fun `should escape csv with newlines`() {
        val result = QueryResult(
            columnNames = listOf("desc"),
            rows = listOf(listOf(string("Line1\nLine2")))
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.CSV)
        // The row value should be quoted because it contains a newline
        assertTrue(formatted.contains("\"Line1"))
    }

    @Test
    fun `should format multiple rows`() {
        val result = QueryResult(
            columnNames = listOf("id"),
            rows = listOf(
                listOf(integer(1)),
                listOf(integer(2)),
                listOf(integer(3))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TEXT)
        assertTrue(formatted.contains("(3 rows)"))
    }

    @Test
    fun `json format with multiple rows should have commas`() {
        val result = QueryResult(
            columnNames = listOf("id"),
            rows = listOf(
                listOf(integer(1)),
                listOf(integer(2))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.JSON)
        assertTrue(formatted.contains("},"))
    }

    @Test
    fun `json format with single row should not have trailing comma`() {
        val result = QueryResult(
            columnNames = listOf("id"),
            rows = listOf(listOf(integer(1)))
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.JSON)
        assertFalse(formatted.contains("},\n]"))
    }
}
