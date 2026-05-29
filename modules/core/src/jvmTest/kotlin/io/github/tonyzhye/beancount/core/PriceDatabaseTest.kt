package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for PriceDatabase.
 */
class PriceDatabaseTest {

    private val testEntries = listOf(
        Price(mapOf("filename" to "test.beancount", "lineno" to 1), LocalDate(2024, 1, 1), "AAPL", Amount(Decimal("150.00"), "USD")),
        Price(mapOf("filename" to "test.beancount", "lineno" to 2), LocalDate(2024, 2, 1), "AAPL", Amount(Decimal("160.00"), "USD")),
        Price(mapOf("filename" to "test.beancount", "lineno" to 3), LocalDate(2024, 3, 1), "AAPL", Amount(Decimal("170.00"), "USD")),
        Price(mapOf("filename" to "test.beancount", "lineno" to 4), LocalDate(2024, 1, 1), "GOOGL", Amount(Decimal("2800.00"), "USD")),
        Price(mapOf("filename" to "test.beancount", "lineno" to 5), LocalDate(2024, 2, 1), "GOOGL", Amount(Decimal("2900.00"), "USD")),
        Price(mapOf("filename" to "test.beancount", "lineno" to 6), LocalDate(2024, 1, 1), "USD", Amount(Decimal("0.92"), "EUR")),
        Price(mapOf("filename" to "test.beancount", "lineno" to 7), LocalDate(2024, 2, 1), "USD", Amount(Decimal("0.93"), "EUR")),
        Price(mapOf("filename" to "test.beancount", "lineno" to 8), LocalDate(2024, 3, 1), "USD", Amount(Decimal("0.91"), "EUR"))
    )

    @Test
    fun `should build price database from entries`() {
        val db = PriceDatabase.build(testEntries)

        // Check forward pairs
        assertTrue(db.forwardPairs.contains(Pair("AAPL", "USD")))
        assertTrue(db.forwardPairs.contains(Pair("GOOGL", "USD")))
        assertTrue(db.forwardPairs.contains(Pair("USD", "EUR")))

        // Check inverse pairs were generated
        val usdAapl = db.getAllPrices("USD", "AAPL")
        assertTrue(usdAapl.isNotEmpty(), "Inverse rate USD/AAPL should exist")
    }

    @Test
    fun `should get all prices for a pair`() {
        val db = PriceDatabase.build(testEntries)

        val prices = db.getAllPrices("AAPL", "USD")
        assertEquals(3, prices.size)
        assertEquals(LocalDate(2024, 1, 1), prices[0].first)
        assertEquals(Decimal("150.00"), prices[0].second)
        assertEquals(LocalDate(2024, 3, 1), prices[2].first)
        assertEquals(Decimal("170.00"), prices[2].second)
    }

    @Test
    fun `should get latest price`() {
        val db = PriceDatabase.build(testEntries)

        val (date, rate) = db.getLatestPrice("AAPL", "USD")
        assertEquals(LocalDate(2024, 3, 1), date)
        assertEquals(Decimal("170.00"), rate)
    }

    @Test
    fun `should get price as of specific date`() {
        val db = PriceDatabase.build(testEntries)

        // Exact match
        val (date1, rate1) = db.getPrice("AAPL", "USD", LocalDate(2024, 2, 1))
        assertEquals(LocalDate(2024, 2, 1), date1)
        assertEquals(Decimal("160.00"), rate1)

        // Between dates - should return most recent before
        val (date2, rate2) = db.getPrice("AAPL", "USD", LocalDate(2024, 2, 15))
        assertEquals(LocalDate(2024, 2, 1), date2)
        assertEquals(Decimal("160.00"), rate2)

        // Before first price
        val (date3, rate3) = db.getPrice("AAPL", "USD", LocalDate(2023, 12, 1))
        assertNull(date3)
        assertNull(rate3)

        // After last price
        val (date4, rate4) = db.getPrice("AAPL", "USD", LocalDate(2024, 5, 1))
        assertEquals(LocalDate(2024, 3, 1), date4)
        assertEquals(Decimal("170.00"), rate4)
    }

    @Test
    fun `should return one for same currency`() {
        val db = PriceDatabase.build(testEntries)

        val (date, rate) = db.getPrice("USD", "USD", LocalDate(2024, 1, 15))
        assertNull(date)
        assertEquals(Decimal.ONE, rate)
    }

    @Test
    fun `should interpolate price`() {
        val db = PriceDatabase.build(testEntries)

        // Midpoint between Jan 1 (150) and Feb 1 (160) = ~155
        val price = db.getInterpolatedPrice("AAPL", "USD", LocalDate(2024, 1, 16))
        assertNotNull(price)
        // Should be roughly halfway
        assertTrue(price > Decimal("150"))
        assertTrue(price < Decimal("160"))
    }

    @Test
    fun `should handle inverse rates`() {
        val db = PriceDatabase.build(testEntries)

        // USD/EUR rate should exist as inverse of EUR/USD (or vice versa)
        val (date, rate) = db.getPrice("EUR", "USD", LocalDate(2024, 2, 1))
        assertNotNull(rate)
        // Inverse of 0.93 should be approximately 1.075
        val expectedInverse = Decimal.ONE / Decimal("0.93")
        assertEquals(expectedInverse, rate)
    }

    @Test
    fun `should convert amount between currencies`() {
        val db = PriceDatabase.build(testEntries)

        val converted = db.convert(Amount(Decimal("100"), "USD"), "EUR", LocalDate(2024, 2, 1))
        assertNotNull(converted)
        assertEquals("EUR", converted.currency)
        // 100 USD * 0.93 = 93 EUR
        assertEquals(Decimal("93.00"), converted.number)
    }

    @Test
    fun `should project prices to another currency`() {
        val db = PriceDatabase.build(testEntries)

        // Project AAPL/USD prices to AAPL/EUR using USD/EUR rates
        val projected = db.project("USD", "EUR")

        val (date, rate) = projected.getPrice("AAPL", "EUR", LocalDate(2024, 2, 1))
        assertNotNull(rate)
        // AAPL/USD on Feb 1 = 160, USD/EUR on Feb 1 = 0.93
        // AAPL/EUR = 160 * 0.93 = 148.8
        val expected = Decimal("160.00") * Decimal("0.93")
        assertEquals(expected, rate)
    }

    @Test
    fun `should return null for unknown pair`() {
        val db = PriceDatabase.build(testEntries)

        val (date, rate) = db.getPrice("UNKNOWN", "USD", LocalDate(2024, 1, 1))
        assertNull(date)
        assertNull(rate)
    }

    @Test
    fun `should deduplicate same-date prices`() {
        val entriesWithDup = listOf(
            Price(mapOf("filename" to "test.beancount", "lineno" to 1), LocalDate(2024, 1, 1), "AAPL", Amount(Decimal("150.00"), "USD")),
            Price(mapOf("filename" to "test.beancount", "lineno" to 2), LocalDate(2024, 1, 1), "AAPL", Amount(Decimal("155.00"), "USD")),
            Price(mapOf("filename" to "test.beancount", "lineno" to 3), LocalDate(2024, 1, 1), "AAPL", Amount(Decimal("152.00"), "USD"))
        )

        val db = PriceDatabase.build(entriesWithDup)
        val prices = db.getAllPrices("AAPL", "USD")

        assertEquals(1, prices.size, "Should deduplicate same-date prices")
        assertEquals(Decimal("152.00"), prices[0].second, "Should keep the last entry for the date")
    }

    @Test
    fun `should handle inverse pair merge`() {
        // Create entries with both forward and inverse rates
        val entries = listOf(
            Price(mapOf("filename" to "test.beancount", "lineno" to 1), LocalDate(2024, 1, 1), "USD", Amount(Decimal("0.92"), "EUR")),
            Price(mapOf("filename" to "test.beancount", "lineno" to 2), LocalDate(2024, 2, 1), "USD", Amount(Decimal("0.93"), "EUR")),
            Price(mapOf("filename" to "test.beancount", "lineno" to 3), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.08"), "USD"))
        )

        val db = PriceDatabase.build(entries)

        // Should have merged into a single pair (the one with more data points: USD/EUR with 2 entries)
        val usdEur = db.getAllPrices("USD", "EUR")
        assertTrue(usdEur.size >= 2, "Should merge inverse pairs")

        // EUR/USD should also exist (as inverse)
        val eurUsd = db.getAllPrices("EUR", "USD")
        assertTrue(eurUsd.isNotEmpty(), "Inverse rate should exist")
    }

    @Test
    fun `should get last price entries before date`() {
        val entries = listOf(
            Price(mapOf("filename" to "test.beancount", "lineno" to 1), LocalDate(2024, 1, 1), "AAPL", Amount(Decimal("150.00"), "USD")),
            Price(mapOf("filename" to "test.beancount", "lineno" to 2), LocalDate(2024, 2, 1), "AAPL", Amount(Decimal("160.00"), "USD")),
            Price(mapOf("filename" to "test.beancount", "lineno" to 3), LocalDate(2024, 1, 1), "GOOGL", Amount(Decimal("2800.00"), "USD"))
        )

        val lastPrices = getLastPriceEntries(entries, LocalDate(2024, 2, 15))

        assertEquals(2, lastPrices.size)
        assertTrue(lastPrices.any { it.currency == "AAPL" })
        assertTrue(lastPrices.any { it.currency == "GOOGL" })
    }
}
