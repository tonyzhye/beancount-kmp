package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for interpolate functions.
 */
class InterpolateTest {

    @Test
    fun `computeResidual should compute zero for balanced transaction`() {
        val postings = listOf(
            Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
            Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
        )
        val residual = computeResidual(postings)
        assertTrue(residual.isSmall(mapOf("*" to Decimal("0.01"))))
    }

    @Test
    fun `computeResidual should skip residual postings`() {
        val postings = listOf(
            Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
            Posting(
                "Income:Salary", Amount(Decimal("-100"), "USD"),
                meta = mapOf(AUTOMATIC_RESIDUAL to true)
            )
        )
        val residual = computeResidual(postings)
        assertEquals(1, residual.size())
        assertEquals(Decimal("100"), residual.getCurrencyUnits("USD")?.number)
    }

    @Test
    fun `inferTolerances should infer from postings`() {
        val postings = listOf(
            Posting("Assets:Bank", Amount(Decimal("100.00"), "USD")),
            Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertTrue(tolerances.containsKey("USD"))
        assertEquals(Decimal("0.005"), tolerances["USD"])
    }

    @Test
    fun `inferTolerances with min mode should use min`() {
        val postings = listOf(
            Posting("Assets:Bank", Amount(Decimal("100.00"), "USD")),
            Posting("Income:Salary", Amount(Decimal("-100.0"), "USD"))
        )
        val tolerances = inferTolerances(postings, mode = "min")
        assertTrue(tolerances.containsKey("USD"))
    }

    @Test
    fun `hasNontrivialBalance should detect cost or price`() {
        val plain = Posting("Assets:Bank", Amount(Decimal("100"), "USD"))
        val withCost = Posting(
            "Assets:Invest", Amount(Decimal("10"), "AAPL"),
            cost = CostSpec(Decimal("150"), null, "USD", null, null)
        )
        val withPrice = Posting(
            "Assets:Invest", Amount(Decimal("10"), "AAPL"),
            price = Amount(Decimal("160"), "USD")
        )

        assertFalse(hasNontrivialBalance(plain))
        assertTrue(hasNontrivialBalance(withCost))
        assertTrue(hasNontrivialBalance(withPrice))
    }

    @Test
    fun `isToleranceUserSpecified should check digit count`() {
        assertTrue(isToleranceUserSpecified(Decimal("0.01")))
        // Count significant digits (like Python's tolerance.as_tuple().digits)
        // 0.00001 has 1 significant digit, which is <= 5
        assertTrue(isToleranceUserSpecified(Decimal("0.00001")))
        // 0.12345 has 5 significant digits, which is <= 5
        assertTrue(isToleranceUserSpecified(Decimal("0.12345")))
        // 0.123456 has 6 significant digits, which is > 5
        assertFalse(isToleranceUserSpecified(Decimal("0.123456")))
    }

    @Test
    fun `getResidualPostings should return empty for zero residual`() {
        val residual = Inventory()
        val postings = getResidualPostings(residual, "Equity:Residual")
        assertTrue(postings.isEmpty())
    }

    @Test
    fun `getResidualPostings should create postings for non-zero residual`() {
        val residual = Inventory()
        residual.addAmount(Amount(Decimal("0.01"), "USD"))
        val postings = getResidualPostings(residual, "Equity:Residual")
        assertEquals(1, postings.size)
        assertEquals("Equity:Residual", postings[0].account)
        assertEquals(Decimal("-0.01"), postings[0].units?.number)
    }
}
