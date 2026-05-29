package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.query.executor.QueryResult
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class QueryFormatterTest {

    private fun date(y: Int, m: Int, d: Int) = BqlDateValue(LocalDate(y, m, d))
    private fun string(s: String) = BqlStringValue(s)
    private fun decimal(s: String) = BqlDecimalValue(io.github.tonyzhye.beancount.core.Decimal(s))
    private fun nullValue() = BqlNullValue()

    @Test
    fun `should format as table`() {
        val result = QueryResult(
            columnNames = listOf("date", "account", "amount"),
            rows = listOf(
                listOf(date(2024, 1, 1), string("Assets:Cash"), decimal("100.00")),
                listOf(date(2024, 1, 2), string("Expenses:Food"), decimal("50.00"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TABLE)

        assertTrue(formatted.contains("date"))
        assertTrue(formatted.contains("account"))
        assertTrue(formatted.contains("amount"))
        assertTrue(formatted.contains("Assets:Cash"))
        assertTrue(formatted.contains("100.00"))
        assertTrue(formatted.contains("(2 rows)"))
    }

    @Test
    fun `should format as csv`() {
        val result = QueryResult(
            columnNames = listOf("date", "account"),
            rows = listOf(
                listOf(date(2024, 1, 1), string("Assets:Cash"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.CSV)

        assertTrue(formatted.contains("date,account"))
        assertTrue(formatted.contains("2024-01-01,Assets:Cash"))
    }

    @Test
    fun `should format as json`() {
        val result = QueryResult(
            columnNames = listOf("date", "account", "amount"),
            rows = listOf(
                listOf(date(2024, 1, 1), string("Assets:Cash"), decimal("100.00"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.JSON)

        assertTrue(formatted.contains("["))
        assertTrue(formatted.contains("]"))
        assertTrue(formatted.contains("\"date\""))
        assertTrue(formatted.contains("\"2024-01-01\""))
        assertTrue(formatted.contains("\"Assets:Cash\""))
        assertTrue(formatted.contains("\"100.00\""))
    }

    @Test
    fun `should format as tsv`() {
        val result = QueryResult(
            columnNames = listOf("date", "account"),
            rows = listOf(
                listOf(date(2024, 1, 1), string("Assets:Cash"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TSV)

        assertTrue(formatted.contains("date\taccount"))
        assertTrue(formatted.contains("2024-01-01\tAssets:Cash"))
    }

    @Test
    fun `should handle empty result`() {
        val result = QueryResult(
            columnNames = listOf("date", "account"),
            rows = emptyList()
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.TABLE)

        assertTrue(formatted.contains("date"))
        assertTrue(formatted.contains("account"))
        assertTrue(formatted.contains("(0 rows)"))
    }

    @Test
    fun `should escape csv values with commas`() {
        val result = QueryResult(
            columnNames = listOf("description"),
            rows = listOf(
                listOf(string("Food, Restaurant"))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.CSV)

        assertTrue(formatted.contains("\"Food, Restaurant\""))
    }

    @Test
    fun `should escape json values with quotes`() {
        val result = QueryResult(
            columnNames = listOf("description"),
            rows = listOf(
                listOf(string("He said \"hello\""))
            )
        )

        val formatted = QueryFormatter.format(result, QueryFormatter.Format.JSON)

        assertTrue(formatted.contains("\\\"hello\\\""))
    }

    @Test
    fun `should format null values`() {
        val result = QueryResult(
            columnNames = listOf("value"),
            rows = listOf(
                listOf(nullValue())
            )
        )

        val tableFormatted = QueryFormatter.format(result, QueryFormatter.Format.TABLE)
        assertTrue(tableFormatted.contains("(1 rows)"))

        val jsonFormatted = QueryFormatter.format(result, QueryFormatter.Format.JSON)
        assertTrue(jsonFormatted.contains("null"))
    }
}
