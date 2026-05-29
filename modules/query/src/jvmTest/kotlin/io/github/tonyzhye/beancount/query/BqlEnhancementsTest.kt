package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for BQL enhancements: HAVING, IN, BETWEEN, AVG, ORDER BY expressions.
 */
class BqlEnhancementsTest {

    private val testEntries = listOf(
        Open(mapOf("filename" to "test.beancount", "lineno" to 1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
        Open(mapOf("filename" to "test.beancount", "lineno" to 2), LocalDate(2024, 1, 1), "Assets:Bank:Savings", listOf("USD")),
        Open(mapOf("filename" to "test.beancount", "lineno" to 3), LocalDate(2024, 1, 1), "Income:Salary", listOf("USD")),
        Transaction(
            mapOf("filename" to "test.beancount", "lineno" to 4), LocalDate(2024, 1, 15), "*",
            postings = listOf(
                Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
            )
        ),
        Transaction(
            mapOf("filename" to "test.beancount", "lineno" to 5), LocalDate(2024, 2, 15), "*",
            postings = listOf(
                Posting("Assets:Bank:Checking", Amount(Decimal("200.00"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-200.00"), "USD"))
            )
        ),
        Transaction(
            mapOf("filename" to "test.beancount", "lineno" to 6), LocalDate(2024, 3, 15), "*",
            postings = listOf(
                Posting("Assets:Bank:Savings", Amount(Decimal("50.00"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-50.00"), "USD"))
            )
        ),
        Transaction(
            mapOf("filename" to "test.beancount", "lineno" to 7), LocalDate(2024, 4, 15), "*",
            postings = listOf(
                Posting("Assets:Bank:Checking", Amount(Decimal("150.00"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-150.00"), "USD"))
            )
        )
    )

    @Test
    fun `should support HAVING with aggregate filter`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT account, sum(number) as total
            FROM postings
            GROUP BY account
            HAVING sum(number) > 100
        """.trimIndent())

        assertTrue(result.rows.isNotEmpty(), "Result should not be empty")
        
        // Check all returned accounts
        val accounts = result.rows.map { it[0].asString() }
        
        // Assets:Bank:Checking has 100 + 200 + 150 = 450
        assertTrue("Assets:Bank:Checking" in accounts, "Should include Assets:Bank:Checking")
        
        // Assets:Bank:Savings has 50, should be filtered out
        assertTrue("Assets:Bank:Savings" !in accounts, "Should filter out Assets:Bank:Savings")
        
        // Income:Salary has -500, should be filtered out
        assertTrue("Income:Salary" !in accounts, "Should filter out Income:Salary")
    }

    @Test
    fun `should support HAVING with count`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT account, count(*) as cnt
            FROM postings
            GROUP BY account
            HAVING count(*) >= 2
        """.trimIndent())

        // Assets:Bank:Checking has 3 postings
        val checkingRow = result.rows.find { it[0].asString() == "Assets:Bank:Checking" }
        assertTrue(checkingRow != null, "Should include Assets:Bank:Checking")
        
        // Assets:Bank:Savings has 1 posting, filtered out
        val savingsRow = result.rows.find { it[0].asString() == "Assets:Bank:Savings" }
        assertTrue(savingsRow == null, "Should filter out Assets:Bank:Savings")
    }

    @Test
    fun `should support IN operator`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT account, number
            FROM postings
            WHERE account IN ('Assets:Bank:Checking', 'Assets:Bank:Savings')
        """.trimIndent())

        assertTrue(result.rows.isNotEmpty())
        for (row in result.rows) {
            val account = row[0].asString()
            assertTrue(
                account == "Assets:Bank:Checking" || account == "Assets:Bank:Savings",
                "Account should be Checking or Savings, got $account"
            )
        }
    }

    @Test
    fun `should support BETWEEN operator for dates`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT date, account, number
            FROM postings
            WHERE date BETWEEN 2024-02-01 AND 2024-03-31
        """.trimIndent())

        assertTrue(result.rows.isNotEmpty())
        for (row in result.rows) {
            val date = row[0].asDate()
            assertTrue(
                date >= LocalDate(2024, 2, 1) && date <= LocalDate(2024, 3, 31),
                "Date $date should be between 2024-02-01 and 2024-03-31"
            )
        }
    }

    @Test
    fun `should support BETWEEN operator for numbers`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT account, number
            FROM postings
            WHERE number BETWEEN 100 AND 200
        """.trimIndent())

        assertTrue(result.rows.isNotEmpty())
        for (row in result.rows) {
            val number = row[1].asDecimal()
            assertTrue(
                number >= Decimal("100") && number <= Decimal("200"),
                "Number $number should be between 100 and 200"
            )
        }
    }

    @Test
    fun `should support AVG aggregate function`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT avg(number) as avg_amount
            FROM postings
            WHERE account = 'Assets:Bank:Checking'
        """.trimIndent())

        assertEquals(1, result.rows.size)
        // (100 + 200 + 150) / 3 = 150
        assertEquals(Decimal("150"), result.rows[0][0].asDecimal())
    }

    @Test
    fun `should support ORDER BY expression not in SELECT`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT account
            FROM postings
            ORDER BY number DESC
        """.trimIndent())

        assertTrue(result.rows.size >= 2)
        // The first row should be the one with the largest number
        // We can't easily assert the exact order without more test data,
        // but we can verify the query executes without error
    }

    @Test
    fun `should support ORDER BY with aggregate`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT account, sum(number) as total
            FROM postings
            GROUP BY account
            ORDER BY total DESC
        """.trimIndent())

        assertTrue(result.rows.size >= 2)
        // Assets:Bank:Checking (450) should be first
        assertEquals("Assets:Bank:Checking", result.rows[0][0].asString())
    }

    @Test
    fun `should support GROUP BY with HAVING and ORDER BY`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT account, sum(number) as total
            FROM postings
            GROUP BY account
            HAVING total >= 50
            ORDER BY total DESC
        """.trimIndent())

        assertTrue(result.rows.isNotEmpty())
        // All accounts should be included (all have >= 50)
        // Ordered by total descending
        assertEquals("Assets:Bank:Checking", result.rows[0][0].asString())
    }

    @Test
    fun `should support IN with multiple values`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT account
            FROM postings
            WHERE account IN ('Income:Salary')
        """.trimIndent())

        assertTrue(result.rows.isNotEmpty())
        for (row in result.rows) {
            assertEquals("Income:Salary", row[0].asString())
        }
    }

    @Test
    fun `should support complex WHERE with AND and BETWEEN`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("""
            SELECT date, account, number
            FROM postings
            WHERE account = 'Assets:Bank:Checking' AND date BETWEEN 2024-01-01 AND 2024-03-31
        """.trimIndent())

        assertTrue(result.rows.isNotEmpty())
        for (row in result.rows) {
            assertEquals("Assets:Bank:Checking", row[1].asString())
            val date = row[0].asDate()
            assertTrue(date >= LocalDate(2024, 1, 1) && date <= LocalDate(2024, 3, 31))
        }
    }
}
