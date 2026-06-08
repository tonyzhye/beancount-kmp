package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for internal Booking API functions.
 *
 * These test the core internal functions of the Booking object:
 * - categorizeByCurrency
 * - interpolateGroup
 * - detectSelfReduction
 * - interpolateCostSpec
 *
 * Many of these scenarios are simplified from Python beancount's booking_full_test.py.
 * Full Python compatibility for inventory-based currency inference and cost/price
 * interpolation is not yet implemented.
 */
class BookingInternalApiTest {

    // ---- categorizeByCurrency tests ----

    @Test
    fun `categorizeByCurrency simple USD group`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("100"), "USD")),
            Posting("Assets:Other", Amount(Decimal("-100"), "USD"))
        )
        val groups = Booking.categorizeByCurrency(postings)
        assertEquals(1, groups.size)
        assertEquals("USD", groups[0].first)
        assertEquals(2, groups[0].second.size)
    }

    @Test
    fun `categorizeByCurrency cost basis determines weight currency`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("10"), "HOOL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
            Posting("Assets:Other", Amount(Decimal("-1000"), "USD"))
        )
        val groups = Booking.categorizeByCurrency(postings)
        assertEquals(1, groups.size)
        assertEquals("USD", groups[0].first)
    }

    @Test
    fun `categorizeByCurrency price determines weight currency`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("100"), "USD"), price = Amount(Decimal("1.2"), "CAD")),
            Posting("Assets:Other", Amount(Decimal("-120"), "CAD"))
        )
        val groups = Booking.categorizeByCurrency(postings)
        assertEquals(1, groups.size)
        assertEquals("CAD", groups[0].first)
    }

    @Test
    fun `categorizeByCurrency multiple groups`() {
        val postings = listOf(
            Posting("Assets:Account1", Amount(Decimal("100"), "USD")),
            Posting("Assets:Account2", Amount(Decimal("-80"), "USD")),
            Posting("Assets:Account3", Amount(Decimal("200"), "CAD")),
            Posting("Assets:Account4", Amount(Decimal("-200"), "CAD"))
        )
        val groups = Booking.categorizeByCurrency(postings)
        assertEquals(2, groups.size)
        val currencies = groups.map { it.first }.toSet()
        assertTrue(currencies.contains("USD"))
        assertTrue(currencies.contains("CAD"))
    }

    @Test
    fun `categorizeByCurrency assigns unknown to single group`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("100"), "USD")),
            Posting("Assets:Other", null) // missing units
        )
        val groups = Booking.categorizeByCurrency(postings)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].second.size)
    }

    @Test
    fun `categorizeByCurrency empty postings`() {
        val groups = Booking.categorizeByCurrency(emptyList())
        assertEquals(0, groups.size)
    }

    // ---- interpolateGroup tests ----

    @Test
    fun `interpolateGroup single missing posting`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("100"), "USD")),
            Posting("Assets:Other", null)
        )
        val (result, errors) = Booking.interpolateGroup(postings, groupCurrency = "USD")
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")
        assertEquals(2, result.size)
        assertEquals(Decimal("-100"), result[1].units?.number)
        assertEquals("USD", result[1].units?.currency)
    }

    @Test
    fun `interpolateGroup no missing postings`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("100"), "USD")),
            Posting("Assets:Other", Amount(Decimal("-100"), "USD"))
        )
        val (result, errors) = Booking.interpolateGroup(postings, groupCurrency = "USD")
        assertEquals(0, errors.size)
        assertEquals(2, result.size)
    }

    @Test
    fun `interpolateGroup too many missing postings`() {
        val postings = listOf(
            Posting("Assets:Account", null),
            Posting("Assets:Other", null)
        )
        val (result, errors) = Booking.interpolateGroup(postings, groupCurrency = "USD")
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Too many missing numbers"))
    }

    @Test
    fun `interpolateGroup zero residual`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("100"), "USD")),
            Posting("Assets:Other", Amount(Decimal("-100"), "USD")),
            Posting("Assets:Third", null)
        )
        val (result, errors) = Booking.interpolateGroup(postings, groupCurrency = "USD")
        assertEquals(0, errors.size)
        assertEquals(Decimal.ZERO, result[2].units?.number)
    }

    @Test
    fun `interpolateGroup with tolerance`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("4.8"), "EUR")),
            Posting("Assets:Account", Amount(Decimal("2.97"), "EUR")),
            Posting("Assets:Other", null)
        )
        val tolerances = mapOf("EUR" to Decimal("0.01"))
        val (result, errors) = Booking.interpolateGroup(postings, tolerances, groupCurrency = "EUR")
        assertEquals(0, errors.size)
        assertEquals(Decimal("-7.77"), result[2].units?.number)
    }

    // ---- detectSelfReduction tests ----

    @Test
    fun `detectSelfReduction no self reduction`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("10"), "HOOL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
            Posting("Assets:Other", Amount(Decimal("-10"), "HOOL"), CostSpec(numberPer = Decimal("100"), currency = "USD"))
        )
        val result = Booking.detectSelfReduction(postings)
        assertEquals(0, result.size)
    }

    @Test
    fun `detectSelfReduction detects same account`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("10"), "HOOL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
            Posting("Assets:Account", Amount(Decimal("-5"), "HOOL"), CostSpec(numberPer = Decimal("100"), currency = "USD"))
        )
        val result = Booking.detectSelfReduction(postings)
        assertEquals(1, result.size)
        assertEquals("Assets:Account", result[0])
    }

    @Test
    fun `detectSelfReduction ignores postings without cost`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("100"), "USD")),
            Posting("Assets:Account", Amount(Decimal("-50"), "USD"))
        )
        val result = Booking.detectSelfReduction(postings)
        assertEquals(0, result.size)
    }

    @Test
    fun `detectSelfReduction multiple accounts`() {
        val postings = listOf(
            Posting("Assets:A", Amount(Decimal("10"), "HOOL"), CostSpec(currency = "USD")),
            Posting("Assets:A", Amount(Decimal("-5"), "HOOL"), CostSpec(currency = "USD")),
            Posting("Assets:B", Amount(Decimal("20"), "AAPL"), CostSpec(currency = "USD")),
            Posting("Assets:B", Amount(Decimal("-10"), "AAPL"), CostSpec(currency = "USD"))
        )
        val result = Booking.detectSelfReduction(postings)
        assertEquals(2, result.size)
        assertTrue(result.contains("Assets:A"))
        assertTrue(result.contains("Assets:B"))
    }

    // ---- interpolateCostSpec tests ----

    @Test
    fun `interpolateCostSpec fills date from transaction date`() {
        val spec = CostSpec(currency = "USD")
        val units = Amount(Decimal("10"), "HOOL")
        val txnDate = LocalDate(2024, 6, 1)
        val result = Booking.interpolateCostSpec(spec, units, txnDate)
        assertEquals(txnDate, result.date)
        assertEquals(Decimal.ZERO, result.numberPer)
    }

    @Test
    fun `interpolateCostSpec computes numberPer from numberTotal`() {
        val spec = CostSpec(numberTotal = Decimal("100"), currency = "USD")
        val units = Amount(Decimal("10"), "HOOL")
        val txnDate = LocalDate(2024, 6, 1)
        val result = Booking.interpolateCostSpec(spec, units, txnDate)
        assertEquals(Decimal("10"), result.numberPer)
        assertEquals(null, result.numberTotal) // consumed
    }

    @Test
    fun `interpolateCostSpec preserves existing numberPer`() {
        val spec = CostSpec(numberPer = Decimal("50"), currency = "USD", date = LocalDate(2024, 1, 1))
        val units = Amount(Decimal("10"), "HOOL")
        val txnDate = LocalDate(2024, 6, 1)
        val result = Booking.interpolateCostSpec(spec, units, txnDate)
        assertEquals(Decimal("50"), result.numberPer)
        assertEquals(LocalDate(2024, 1, 1), result.date) // preserves existing date
    }

    @Test
    fun `interpolateCostSpec currency only defaults to zero`() {
        val spec = CostSpec(currency = "USD")
        val units = Amount(Decimal("10"), "HOOL")
        val result = Booking.interpolateCostSpec(spec, units, LocalDate(2024, 6, 1))
        assertEquals(Decimal.ZERO, result.numberPer)
        assertEquals("USD", result.currency)
    }
}
