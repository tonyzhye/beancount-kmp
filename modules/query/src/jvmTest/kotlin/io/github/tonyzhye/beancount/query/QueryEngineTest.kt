package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class QueryEngineTest {

    private val testEntries = listOf<Directive>(
        Transaction(
            meta = mapOf("filename" to "test.beancount", "lineno" to 1),
            date = LocalDate(2023, 1, 15),
            flag = "*",
            payee = "Coffee Shop",
            narration = "Morning coffee",
            tags = setOf("food", "daily"),
            links = emptySet(),
            postings = listOf(
                Posting(
                    account = "Expenses:Food:Coffee",
                    units = Amount(Decimal("4.50"), "USD"),
                    flag = null,
                    meta = mapOf("filename" to "test.beancount", "lineno" to 2)
                ),
                Posting(
                    account = "Assets:Bank:Checking",
                    units = Amount(Decimal("-4.50"), "USD"),
                    flag = null,
                    meta = mapOf("filename" to "test.beancount", "lineno" to 3)
                )
            )
        ),
        Transaction(
            meta = mapOf("filename" to "test.beancount", "lineno" to 4),
            date = LocalDate(2023, 1, 20),
            flag = "*",
            payee = "Grocery Store",
            narration = "Weekly groceries",
            tags = setOf("food", "groceries"),
            links = emptySet(),
            postings = listOf(
                Posting(
                    account = "Expenses:Food:Groceries",
                    units = Amount(Decimal("56.78"), "USD"),
                    flag = null,
                    meta = mapOf("filename" to "test.beancount", "lineno" to 5)
                ),
                Posting(
                    account = "Assets:Bank:Checking",
                    units = Amount(Decimal("-56.78"), "USD"),
                    flag = null,
                    meta = mapOf("filename" to "test.beancount", "lineno" to 6)
                )
            )
        ),
        Transaction(
            meta = mapOf("filename" to "test.beancount", "lineno" to 7),
            date = LocalDate(2023, 2, 1),
            flag = "*",
            payee = "Salary",
            narration = "Monthly salary",
            tags = setOf("income"),
            links = emptySet(),
            postings = listOf(
                Posting(
                    account = "Assets:Bank:Checking",
                    units = Amount(Decimal("5000.00"), "USD"),
                    flag = null,
                    meta = mapOf("filename" to "test.beancount", "lineno" to 8)
                ),
                Posting(
                    account = "Income:Salary",
                    units = Amount(Decimal("-5000.00"), "USD"),
                    flag = null,
                    meta = mapOf("filename" to "test.beancount", "lineno" to 9)
                )
            )
        )
    )

    private val engine = QueryEngine(testEntries)

    @Test
    fun `should execute basic SELECT query`() {
        val result = engine.execute("SELECT date, account, narration FROM postings")

        assertEquals(listOf("date", "account", "narration"), result.columnNames)
        assertEquals(6, result.rows.size) // 3 transactions * 2 postings each

        // First row
        val firstRow = result.rows[0]
        assertEquals(LocalDate(2023, 1, 15), firstRow[0].asDate())
        assertEquals("Expenses:Food:Coffee", firstRow[1].asString())
        assertEquals("Morning coffee", firstRow[2].asString())
    }

    @Test
    fun `should execute SELECT with WHERE clause`() {
        val result = engine.execute(
            "SELECT date, account, number FROM postings WHERE account = 'Expenses:Food:Coffee'"
        )

        assertEquals(1, result.rows.size)
        assertEquals("Expenses:Food:Coffee", result.rows[0][1].asString())
        assertEquals(Decimal("4.50"), result.rows[0][2].asDecimal())
    }

    @Test
    fun `should execute SELECT with wildcard columns`() {
        val result = engine.execute("SELECT * FROM postings LIMIT 2")

        assertTrue(result.columnNames.isNotEmpty())
        assertEquals(2, result.rows.size)
    }

    @Test
    fun `should execute SELECT with ORDER BY`() {
        val result = engine.execute(
            "SELECT date, account, number FROM postings ORDER BY number DESC"
        )

        val numbers = result.rows.map { it[2].asDecimal() }
        assertEquals(listOf(
            Decimal("5000.00"),
            Decimal("56.78"),
            Decimal("4.50"),
            Decimal("-4.50"),
            Decimal("-56.78"),
            Decimal("-5000.00")
        ), numbers)
    }

    @Test
    fun `should execute SELECT with LIMIT`() {
        val result = engine.execute("SELECT date, account FROM postings LIMIT 3")

        assertEquals(3, result.rows.size)
    }

    @Test
    fun `should execute SELECT with DISTINCT`() {
        val result = engine.execute("SELECT DISTINCT account FROM postings")

        val accounts = result.rows.map { it[0].asString() }.toSet()
        assertEquals(4, accounts.size)
        assertTrue(accounts.contains("Expenses:Food:Coffee"))
        assertTrue(accounts.contains("Assets:Bank:Checking"))
        assertTrue(accounts.contains("Expenses:Food:Groceries"))
        assertTrue(accounts.contains("Income:Salary"))
    }

    @Test
    fun `should execute SELECT with function call`() {
        val result = engine.execute("SELECT year(date) AS year FROM postings")

        assertEquals("year", result.columnNames[0])
        assertTrue(result.rows.all { it[0].asInteger() == 2023 })
    }

    @Test
    fun `should execute SELECT with aggregate function`() {
        val result = engine.execute("SELECT sum(number) AS total FROM postings")

        assertEquals(1, result.rows.size)
        // 4.50 + (-4.50) + 56.78 + (-56.78) + 5000.00 + (-5000.00) = 0
        assertEquals(Decimal("0"), result.rows[0][0].asDecimal())
    }

    @Test
    fun `should execute SELECT with GROUP BY`() {
        val result = engine.execute(
            "SELECT account, sum(number) AS total FROM postings GROUP BY account"
        )

        assertEquals(2, result.columnNames.size)
        assertEquals(4, result.rows.size) // 4 unique accounts

        // Find the row for Expenses:Food:Coffee
        val coffeeRow = result.rows.find { it[0].asString() == "Expenses:Food:Coffee" }
        assertNotNull(coffeeRow)
        assertEquals(Decimal("4.50"), coffeeRow!![1].asDecimal())
    }

    @Test
    fun `should execute SELECT with regex match`() {
        val result = engine.execute(
            "SELECT account FROM postings WHERE account ~ 'Expenses'"
        )

        assertTrue(result.rows.all { it[0].asString().startsWith("Expenses") })
        assertEquals(2, result.rows.size)
    }

    @Test
    fun `should execute SELECT from entries table`() {
        val result = engine.execute("SELECT date, type FROM entries")

        assertEquals(3, result.rows.size)
        assertTrue(result.rows.all { it[1].asString() == "transaction" })
    }

    @Test
    fun `should execute SELECT with AND condition`() {
        val result = engine.execute(
            "SELECT account FROM postings WHERE account = 'Assets:Bank:Checking' AND number > 0"
        )

        assertEquals(1, result.rows.size) // One positive posting to checking (salary)
        assertTrue(result.rows.all { it[0].asString() == "Assets:Bank:Checking" })
    }

    @Test
    fun `should handle empty result`() {
        val result = engine.execute(
            "SELECT account FROM postings WHERE account = 'NonExistent'"
        )

        assertEquals(0, result.rows.size)
    }

    @Test
    fun `should get table names`() {
        val names = engine.getTableNames()
        assertTrue(names.contains("postings"))
        assertTrue(names.contains("entries"))
        assertTrue(names.contains("transactions"))
    }

    @Test
    fun `should get column names for postings table`() {
        val columns = engine.getColumnNames("postings")
        assertTrue(columns.contains("date"))
        assertTrue(columns.contains("account"))
        assertTrue(columns.contains("number"))
        assertTrue(columns.contains("balance"))
    }
}
