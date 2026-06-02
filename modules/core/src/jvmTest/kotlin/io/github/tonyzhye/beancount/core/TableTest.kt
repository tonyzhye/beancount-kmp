package io.github.tonyzhye.beancount.core

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for table rendering utilities.
 */
class TableTest {

    @Test
    fun `createTable creates table from data`() {
        val headers = listOf("Account", "Balance", "Currency")
        val rows = listOf(
            listOf("Assets:Cash", "100.00", "USD"),
            listOf("Assets:Bank", "500.00", "USD"),
            listOf("Liabilities:Credit", "-200.00", "USD")
        )

        val table = createTable(headers, rows)

        assertEquals(listOf("col0", "col1", "col2"), table.columns)
        assertEquals(headers, table.header)
        assertEquals(3, table.body.size)
        assertEquals(listOf("Assets:Cash", "100.00", "USD"), table.body[0])
    }

    @Test
    fun `createTable with custom formatters`() {
        val headers = listOf("Account", "Amount")
        val rows = listOf(
            listOf("Assets:Cash", 100.0),
            listOf("Assets:Bank", 500.0)
        )

        val table = createTable(headers, rows, formatters = listOf(
            null,
            { value -> String.format("%.2f", value as Double) }
        ))

        assertEquals(listOf("Assets:Cash", "100.00"), table.body[0])
        assertEquals(listOf("Assets:Bank", "500.00"), table.body[1])
    }

    @Test
    fun `tableToText renders ASCII table`() {
        val table = Table(
            columns = listOf("account", "balance"),
            header = listOf("Account", "Balance"),
            body = listOf(
                listOf("Assets:Cash", "100.00"),
                listOf("Assets:Bank", "500.00")
            )
        )

        val text = tableToText(table)

        assertContains(text, "Account")
        assertContains(text, "Balance")
        assertContains(text, "Assets:Cash")
        assertContains(text, "100.00")
    }

    @Test
    fun `tableToCsv renders CSV format`() {
        val table = Table(
            columns = listOf("account", "balance"),
            header = listOf("Account", "Balance"),
            body = listOf(
                listOf("Assets:Cash", "100.00"),
                listOf("Liabilities:Credit", "-200.00")
            )
        )

        val csv = tableToCsv(table)

        assertContains(csv, "Account,Balance")
        assertContains(csv, "Assets:Cash,100.00")
        assertContains(csv, "Liabilities:Credit,-200.00")
    }

    @Test
    fun `tableToHtml renders HTML table`() {
        val table = Table(
            columns = listOf("account"),
            header = listOf("Account"),
            body = listOf(listOf("Assets:Cash"))
        )

        val html = tableToHtml(table)

        assertContains(html, "\u003ctable\u003e")
        assertContains(html, "\u003cth\u003eAccount\u003c/th\u003e")
        assertContains(html, "\u003ctd\u003eAssets:Cash\u003c/td\u003e")
        assertContains(html, "\u003c/table\u003e")
    }

    @Test
    fun `renderTable dispatches to correct format`() {
        val table = Table(
            columns = listOf("a"),
            header = listOf("A"),
            body = listOf(listOf("1"))
        )

        val text = renderTable(table, "txt")
        assertTrue(text.contains("A"))

        val csv = renderTable(table, "csv")
        assertTrue(csv.contains("A\n1"))

        val html = renderTable(table, "html")
        assertTrue(html.contains("\u003chtml\u003e"))
    }

    @Test
    fun `computeTableWidths calculates correct widths`() {
        val rows = listOf(
            listOf("Short", "Very Long String"),
            listOf("Medium", "Short")
        )

        val widths = computeTableWidths(rows)

        assertEquals(2, widths.size)
        assertEquals("Very Long String".length, widths[1])
    }

    @Test
    fun `empty table returns empty string`() {
        val table = Table(emptyList(), emptyList(), emptyList())
        assertEquals("", tableToText(table))
    }

    @Test
    fun `csv escapes special characters`() {
        val table = Table(
            columns = listOf("desc"),
            header = listOf("Description"),
            body = listOf(listOf("Has, comma"), listOf("Has \"quotes\""))
        )

        val csv = tableToCsv(table)

        assertContains(csv, "\"Has, comma\"")
        assertContains(csv, "\"Has \"\"quotes\"\"\"")
    }

    @Test
    fun `html escapes special characters`() {
        val table = Table(
            columns = listOf("content"),
            header = listOf("Content"),
            body = listOf(listOf("\u003ca href=\"test\"\u003eLink\u003c/a\u003e"))
        )

        val html = tableToHtml(table)

        assertTrue(html.contains("\u0026lt;a href=\u0026quot;test\u0026quot;\u0026gt;"))
    }
}
