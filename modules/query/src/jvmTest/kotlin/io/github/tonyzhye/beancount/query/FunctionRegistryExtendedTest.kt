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

    private val entriesWithPrice = listOf(
        Price(
            meta = emptyMap(),
            date = LocalDate(2024, 1, 1),
            currency = "EUR",
            amount = Amount(Decimal("1.10"), "USD")
        ),
        Transaction(
            meta = emptyMap(),
            date = LocalDate(2024, 1, 15),
            flag = "*",
            narration = "Test with EUR",
            postings = listOf(
                Posting(account = "Assets:Bank:Checking", units = Amount(Decimal("100.00"), "EUR")),
                Posting(account = "Income:Salary", units = Amount(Decimal("-100.00"), "EUR"))
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

    @Test
    fun `should use date_add function`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT date_add(date, 5) FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val addedDate = result.rows[0][0].asDate()
        assertEquals(LocalDate(2024, 1, 6), addedDate)
    }

    @Test
    fun `should use date_diff function`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT date_diff(date, 2024-01-11) FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val diff = result.rows[0][0].asInteger()
        assertEquals(10, diff)
    }

    @Test
    fun `should use date_trunc to month`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT date_trunc(date, 'month') FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val truncated = result.rows[0][0].asDate()
        assertEquals(LocalDate(2024, 1, 1), truncated)
    }

    @Test
    fun `should use date_trunc to year`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT date_trunc(date, 'year') FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val truncated = result.rows[0][0].asDate()
        assertEquals(LocalDate(2024, 1, 1), truncated)
    }

    @Test
    fun `should use days_between function`() {
        val engine = QueryEngine(testEntries)
        val result = engine.execute("SELECT days_between(date, 2024-01-11) FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val days = result.rows[0][0].asInteger()
        assertEquals(10, days)
    }

    @Test
    fun `should use getprice function with price map`() {
        val engine = QueryEngine(entriesWithPrice)
        val result = engine.execute("SELECT getprice('EUR', 'USD') FROM prices")

        assertTrue(result.rows.isNotEmpty())
        val price = result.rows[0][0]
        assertFalse(price.isNull())
        val amount = price.asAmount()
        assertEquals(Decimal("1.10"), amount.number)
        assertEquals("USD", amount.currency)
    }

    @Test
    fun `should use convert function with price map`() {
        val engine = QueryEngine(entriesWithPrice)
        val result = engine.execute("SELECT convert(position, 'USD') FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val converted = result.rows[0][0]
        assertFalse(converted.isNull())
        val amount = converted.asAmount()
        assertEquals("USD", amount.currency)
    }

    @Test
    fun `should use getvalue function with price map`() {
        val engine = QueryEngine(entriesWithPrice)
        val result = engine.execute("SELECT getvalue(position, 'USD') FROM postings")

        assertTrue(result.rows.isNotEmpty())
        val value = result.rows[0][0]
        assertFalse(value.isNull())
        val amount = value.asAmount()
        assertEquals("USD", amount.currency)
    }
}
