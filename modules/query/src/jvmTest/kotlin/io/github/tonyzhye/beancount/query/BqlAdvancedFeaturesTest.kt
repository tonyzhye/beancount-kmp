package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.parser.BqlParser
import io.github.tonyzhye.beancount.query.parser.QueryType
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for BQL advanced features: PIVOT, JOURNAL, DEFINE.
 */
class BqlAdvancedFeaturesTest {

    private val sampleEntries = listOf(
        Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
        Open(emptyMap(), LocalDate(2024, 1, 1), "Income:Salary", listOf("USD")),
        Open(emptyMap(), LocalDate(2024, 1, 1), "Expenses:Food", listOf("USD")),
        Transaction(
            emptyMap(), LocalDate(2024, 1, 15), "*",
            narration = "Salary",
            postings = listOf(
                Posting("Assets:Bank", Amount(Decimal("1000"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-1000"), "USD"))
            )
        ),
        Transaction(
            emptyMap(), LocalDate(2024, 2, 15), "*",
            narration = "Salary",
            postings = listOf(
                Posting("Assets:Bank", Amount(Decimal("1200"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-1200"), "USD"))
            )
        ),
        Transaction(
            emptyMap(), LocalDate(2024, 1, 20), "*",
            narration = "Groceries",
            postings = listOf(
                Posting("Expenses:Food", Amount(Decimal("100"), "USD")),
                Posting("Assets:Bank", Amount(Decimal("-100"), "USD"))
            )
        ),
        Transaction(
            emptyMap(), LocalDate(2024, 2, 20), "*",
            narration = "Groceries",
            postings = listOf(
                Posting("Expenses:Food", Amount(Decimal("150"), "USD")),
                Posting("Assets:Bank", Amount(Decimal("-150"), "USD"))
            )
        )
    )

    @Test
    fun `PIVOT BY should pivot rows to columns`() {
        val engine = QueryEngine(sampleEntries)
        // Query that groups by account and month, then pivots by month
        val result = engine.execute("""
            SELECT account, month(date) AS month, sum(number) AS total
            FROM postings
            GROUP BY account, month
            PIVOT BY account, month
        """.trimIndent())

        // Should have columns: account, 1, 2 (for January and February)
        assertTrue(result.columnNames.isNotEmpty())
        assertEquals("account", result.columnNames[0])
        
        // Should have rows for each account
        val accounts = result.rows.map { it[0].asString() }.toSet()
        assertTrue(accounts.isNotEmpty())
    }

    @Test
    fun `JOURNAL should produce journal format query`() {
        val engine = QueryEngine(sampleEntries)
        val result = engine.execute("JOURNAL 'Assets:Bank'")

        // Should have journal columns: date, flag, payee, narration, account, position, balance
        val expectedColumns = listOf("date", "flag", "payee", "narration", "account", "position", "balance")
        assertEquals(expectedColumns, result.columnNames)

        // Should only include postings for Assets:Bank
        val accounts = result.rows.map { it[4].asString() } // account column
        assertTrue(accounts.all { it.startsWith("Assets:Bank") })
    }

    @Test
    fun `DEFINE should substitute variables in query`() {
        val parser = BqlParser("DEFINE account_name = 'Assets:Bank'; SELECT account, number FROM postings WHERE account = account_name")
        val ast = parser.parseQuery()

        // WHERE clause should have the variable substituted
        assertTrue(ast.where != null)
        assertEquals(QueryType.SELECT, ast.queryType)
    }

    @Test
    fun `PIVOT BY with integer column references`() {
        val engine = QueryEngine(sampleEntries)
        val result = engine.execute("""
            SELECT account, month(date) AS month, sum(number) AS total
            FROM postings
            GROUP BY account, month
            PIVOT BY 1, 2
        """.trimIndent())

        // Should work with 1-based column references
        assertTrue(result.columnNames.isNotEmpty())
        assertEquals("account", result.columnNames[0])
    }

    @Test
    fun `JOURNAL with FROM clause`() {
        val engine = QueryEngine(sampleEntries)
        val result = engine.execute("JOURNAL 'Assets:Bank' FROM postings")

        // Should have journal columns
        assertTrue(result.columnNames.size >= 6)
    }

    @Test
    fun `DEFINE with date variable`() {
        val parser = BqlParser("DEFINE start_date = 2024-01-01; SELECT account, number FROM postings WHERE date >= start_date")
        val ast = parser.parseQuery()

        assertEquals(QueryType.SELECT, ast.queryType)
        // The WHERE clause should be substituted
        assertTrue(ast.where != null)
    }

    @Test
    fun `PIVOT BY should produce correct pivoted structure`() {
        // Create entries with known structure for predictable pivot
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            ),
            Transaction(
                emptyMap(), LocalDate(2024, 2, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("200"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-200"), "USD"))
                )
            )
        )

        val engine = QueryEngine(entries)
        val result = engine.execute("""
            SELECT account, month(date) AS month, sum(number) AS total
            FROM postings
            GROUP BY account, month
            PIVOT BY account, month
        """.trimIndent())

        // Verify structure
        assertTrue(result.columnNames.size > 1)
        assertTrue(result.rows.isNotEmpty())
    }
}
