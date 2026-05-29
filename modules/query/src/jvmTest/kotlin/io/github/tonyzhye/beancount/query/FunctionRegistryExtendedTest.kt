package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FunctionRegistryExtendedTest {

    private val testEntries = listOf(
        Transaction(
            meta = emptyMap(),
            date = LocalDate(2024, 1, 1),
            flag = "*",
            narration = "Test",
            postings = listOf(
                Posting(account = "Assets:Bank:Checking", units = Amount(Decimal("100.00"), "USD")),
                Posting(account = "Expenses:Food", units = Amount(Decimal("-50.00"), "USD"))
            )
        )
    )

    @Test
    fun `should use account column in query`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT account FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val firstAccount = result.rows[0][0]
        assertEquals("Assets:Bank:Checking", firstAccount.asString())
    }

    @Test
    fun `should use parent function in query`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT parent(account) FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val firstParent = result.rows[0][0]
        assertEquals("Assets:Bank", firstParent.asString())
    }

    @Test
    fun `should use root function in query`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT root(account) FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val firstRoot = result.rows[0][0]
        assertEquals("Assets", firstRoot.asString())
    }

    @Test
    fun `should use currency function in query`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT currency(position) FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val firstCurrency = result.rows[0][0]
        assertEquals("USD", firstCurrency.asString())
    }

    @Test
    fun `should use number function in query`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT number(position) FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val firstNumber = result.rows[0][0]
        assertEquals(Decimal("100.00"), firstNumber.asDecimal())
    }
}
