package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.Amount
import io.github.tonyzhye.beancount.core.CostSpec
import io.github.tonyzhye.beancount.core.Decimal
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests ported from Python beancount.parser.booking_full_test.TestComputeCostNumber.
 */
class ComputeCostNumberTest {

    private val date = LocalDate(2016, 1, 1)

    // Note: Python's test_missing_per uses MISSING (a sentinel value) for number_per,
    // which returns None. Kotlin has no MISSING sentinel; null means "not specified".
    // When numberPer is null and numberTotal is present, we compute numberTotal/units.

    @Test
    fun `test missing per computes from total`() {
        val costSpec = CostSpec(numberPer = null, numberTotal = Decimal("1"), currency = "USD")
        val units = Amount(Decimal("12"), "HOOL")
        assertEquals(Decimal("0.08333333333333333333"), Booking.computeCostNumber(costSpec, units))
    }

    @Test
    fun `test missing total returns per`() {
        val costSpec = CostSpec(numberPer = Decimal("1"), numberTotal = null, currency = "USD")
        val units = Amount(Decimal("12"), "HOOL")
        assertEquals(Decimal("1"), Booking.computeCostNumber(costSpec, units))
    }

    @Test
    fun `test both none`() {
        val costSpec = CostSpec(numberPer = null, numberTotal = null, currency = "USD")
        val units = Amount(Decimal("12"), "HOOL")
        assertNull(Booking.computeCostNumber(costSpec, units))
    }

    @Test
    fun `test total only`() {
        val costSpec = CostSpec(numberPer = null, numberTotal = Decimal("48"), currency = "USD")
        val units = Amount(Decimal("12"), "HOOL")
        assertEquals(Decimal("4"), Booking.computeCostNumber(costSpec, units))
    }

    @Test
    fun `test per only`() {
        val costSpec = CostSpec(numberPer = Decimal("4"), numberTotal = null, currency = "USD")
        val units = Amount(Decimal("12"), "HOOL")
        assertEquals(Decimal("4"), Booking.computeCostNumber(costSpec, units))
    }

    @Test
    fun `test both`() {
        val costSpec = CostSpec(numberPer = Decimal("3"), numberTotal = Decimal("6"), currency = "USD", date = date)
        val units = Amount(Decimal("12"), "HOOL")
        assertEquals(Decimal("3.5"), Booking.computeCostNumber(costSpec, units))
    }

    @Test
    fun `test no currency`() {
        val costSpec = CostSpec(numberPer = Decimal("3"), numberTotal = Decimal("6"), currency = null, date = date)
        val units = Amount(Decimal("12"), "HOOL")
        assertEquals(Decimal("3.5"), Booking.computeCostNumber(costSpec, units))
    }

    @Test
    fun `test negative numbers`() {
        val costSpec = CostSpec(numberPer = Decimal("3"), numberTotal = Decimal("6"), currency = null, date = date)
        val units = Amount(Decimal("-12"), "HOOL")
        assertEquals(Decimal("3.5"), Booking.computeCostNumber(costSpec, units))
    }
}
