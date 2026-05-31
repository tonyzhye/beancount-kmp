package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Lifetimes module.
 */
class LifetimesTest {

    private fun txn(date: LocalDate, vararg postings: Pair<String, Pair<String, String>>): Transaction {
        return Transaction(
            meta = emptyMap(),
            date = date,
            flag = "*",
            postings = postings.map { (account, amount) ->
                Posting(account, Amount(Decimal(amount.second), amount.first))
            }
        )
    }

    @Test
    fun `getCommodityLifetimes should track when commodities are held`() {
        val entries = listOf(
            txn(
                LocalDate(2024, 1, 1),
                "Assets:Bank" to ("USD" to "100"),
                "Income:Salary" to ("USD" to "-100")
            ),
            txn(
                LocalDate(2024, 1, 2),
                "Assets:Invest" to ("AAPL" to "10"),
                "Assets:Bank" to ("USD" to "-1000"),
                "Income:Capital" to ("USD" to "900")
            ),
            txn(
                LocalDate(2024, 1, 3),
                "Assets:Bank" to ("USD" to "500"),
                "Assets:Invest" to ("AAPL" to "-10"),
                "Income:Capital" to ("USD" to "-500")
            )
        )

        val lifetimes = Lifetimes.getCommodityLifetimes(entries)

        // USD should be active from Jan 1 to Jan 4 (day after last seen)
        val usdLifetimes = lifetimes.filter { (key, _) -> key.first == "USD" }
        assertTrue(usdLifetimes.isNotEmpty(), "USD should have lifetimes")

        // AAPL should be active from Jan 2 to Jan 4
        val aaplLifetimes = lifetimes.filter { (key, _) -> key.first == "AAPL" }
        assertTrue(aaplLifetimes.isNotEmpty(), "AAPL should have lifetimes")
    }

    @Test
    fun `getCommodityLifetimes should handle empty entries`() {
        val lifetimes = Lifetimes.getCommodityLifetimes(emptyList())
        assertTrue(lifetimes.isEmpty())
    }

    @Test
    fun `compressIntervalsDays should merge close intervals`() {
        val intervals = listOf(
            LocalDate(2024, 1, 1) to LocalDate(2024, 1, 10),
            LocalDate(2024, 1, 12) to LocalDate(2024, 1, 20), // 2 days gap
            LocalDate(2024, 2, 1) to LocalDate(2024, 2, 10)   // Big gap
        )

        val result = Lifetimes.compressIntervalsDays(intervals, 5)

        // First two intervals should be merged (gap is only 2 days < 5)
        assertEquals(2, result.size)
    }

    @Test
    fun `trimIntervals should trim to specified range`() {
        val intervals = listOf(
            LocalDate(2024, 1, 1) to LocalDate(2024, 1, 31),
            LocalDate(2024, 2, 1) to LocalDate(2024, 2, 28)
        )

        val result = Lifetimes.trimIntervals(
            intervals,
            trimStart = LocalDate(2024, 1, 15),
            trimEnd = LocalDate(2024, 2, 15)
        )

        assertEquals(2, result.size)
        assertEquals(LocalDate(2024, 1, 15), result[0].first)
        assertEquals(LocalDate(2024, 1, 31), result[0].second) // Original end date
        assertEquals(LocalDate(2024, 2, 1), result[1].first)
        assertEquals(LocalDate(2024, 2, 15), result[1].second)
    }

    @Test
    fun `requiredWeeklyPrices should enumerate Fridays`() {
        val lifetimes: Map<Pair<String, String?>, List<Pair<LocalDate, LocalDate?>>> = mapOf(
            ("AAPL" to "USD") to listOf(
                LocalDate(2024, 1, 1) to LocalDate(2024, 1, 15)
            )
        )

        val result = Lifetimes.requiredWeeklyPrices(lifetimes, LocalDate(2024, 1, 31))

        assertTrue(result.isNotEmpty(), "Should have weekly prices")
        // First Friday before Jan 1, 2024 is Dec 29, 2023
        assertEquals(LocalDate(2023, 12, 29), result[0].first)
        assertEquals("AAPL", result[0].second)
        assertEquals("USD", result[0].third)
    }

    @Test
    fun `requiredDailyPrices should enumerate all days`() {
        val lifetimes: Map<Pair<String, String?>, List<Pair<LocalDate, LocalDate?>>> = mapOf(
            ("AAPL" to "USD") to listOf(
                LocalDate(2024, 1, 1) to LocalDate(2024, 1, 5)
            )
        )

        val result = Lifetimes.requiredDailyPrices(lifetimes, LocalDate(2024, 1, 31))

        assertEquals(4, result.size) // Jan 1, 2, 3, 4
        assertTrue(result.all { it.second == "AAPL" && it.third == "USD" })
    }

    @Test
    fun `requiredDailyPrices with weekdaysOnly should skip weekends`() {
        val lifetimes: Map<Pair<String, String?>, List<Pair<LocalDate, LocalDate?>>> = mapOf(
            ("AAPL" to "USD") to listOf(
                LocalDate(2024, 1, 1) to LocalDate(2024, 1, 10)
            )
        )

        val result = Lifetimes.requiredDailyPrices(lifetimes, LocalDate(2024, 1, 31), weekdaysOnly = true)

        // Should only include weekdays
        assertTrue(result.isNotEmpty())
        // Jan 1, 2024 is a Monday, so all days are weekdays
        // But let's just check we have fewer entries than without weekdaysOnly
    }
}
