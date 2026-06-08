package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests ported from Python beancount.parser.booking_test.
 * These verify invalid amount/cost handling in Booking.book().
 */
class BookingPythonTest {

    private val parser = BeancountParser()

    // ---- TestInvalidAmountsErrors ----

    @Test
    fun `test zero amount`() {
        val result = parser.parseString("""
            2013-05-18 open Assets:Investments:MSFT
            2013-05-18 open Assets:Investments:Cash

            2013-05-18 * ""
              Assets:Investments:MSFT      0 MSFT
              Assets:Investments:Cash      0 USD
        """.trimIndent())

        val (_, bookingErrors) = Booking.book(result.entries, result.options)
        assertEquals(0, bookingErrors.size, "Expected no errors for zero amount: ${bookingErrors.map { it.message }}")
    }

    @Test
    fun `test zero amount with cost`() {
        val result = parser.parseString("""
            2013-05-18 open Assets:Investments:MSFT
            2013-05-18 open Assets:Investments:Cash

            2013-05-18 * ""
              Assets:Investments:MSFT      0 MSFT {200.00 USD}
              Assets:Investments:Cash    1 USD
        """.trimIndent())

        val (_, bookingErrors) = Booking.book(result.entries, result.options)
        assertEquals(1, bookingErrors.size, "Expected 1 error for zero amount with cost")
        assertTrue(bookingErrors[0].message.contains("Amount is zero", ignoreCase = true),
            "Error should mention 'Amount is zero': ${bookingErrors[0].message}")
    }

    @Test
    fun `test cost zero`() {
        val result = parser.parseString("""
            2013-05-18 open Assets:Investments:MSFT
            2013-05-18 open Assets:Investments:Cash

            2013-05-18 * ""
              Assets:Investments:MSFT      -10 MSFT {0.00 USD}
              Assets:Investments:Cash  2000.00 USD
        """.trimIndent())

        val (_, bookingErrors) = Booking.book(result.entries, result.options)
        assertEquals(0, bookingErrors.size, "Expected no errors for zero cost: ${bookingErrors.map { it.message }}")
    }

    @Test
    fun `test cost negative`() {
        val result = parser.parseString("""
            2013-05-18 open Assets:Investments:MSFT
            2013-05-18 open Assets:Investments:Cash

            2013-05-18 * ""
              Assets:Investments:MSFT      -10 MSFT {-200.00 USD}
              Assets:Investments:Cash  2000.00 USD
        """.trimIndent())

        val (_, bookingErrors) = Booking.book(result.entries, result.options)
        assertEquals(1, bookingErrors.size, "Expected 1 error for negative cost")
        assertTrue(bookingErrors[0].message.contains("Cost is negative", ignoreCase = true),
            "Error should mention 'Cost is negative': ${bookingErrors[0].message}")
    }

    // ---- TestBookingValidation (via validateInventoryBooking) ----

    @Test
    fun `test validate inventory booking normal sequence`() {
        val result = parser.parseString("""
            2014-01-01 open Assets:Investments:Cash
            2014-01-01 open Assets:Investments:Stock

            2014-06-22 * "Add some positive units"
              Assets:Investments:Stock    1 HOOL {500 USD}
              Assets:Investments:Cash  -500 USD

            2014-06-23 * "Down to zero"
              Assets:Investments:Stock   -1 HOOL {500 USD}
              Assets:Investments:Cash   500 USD

            2014-06-24 * "Go negative from zero"
              Assets:Investments:Stock   -1 HOOL {500 USD}
              Assets:Investments:Cash   500 USD

            2014-06-25 * "Go positive much"
              Assets:Investments:Stock    11 HOOL {500 USD}
              Assets:Investments:Cash  -5500 USD

            2014-06-26 * "Cross to negative from above zero"
              Assets:Investments:Stock  -15 HOOL {500 USD}
              Assets:Investments:Cash  7500 USD
        """.trimIndent())

        val validationErrors = validateInventoryBooking(result.entries, result.options)
        assertEquals(0, validationErrors.size, "Expected no errors: ${validationErrors.map { it.message }}")
    }

    @Test
    fun `test validate inventory booking same day`() {
        val result = parser.parseString("""
            2014-01-01 open Assets:Investments:Cash
            2014-01-01 open Assets:Investments:Stock

            2014-06-22 * "Add some positive units"
              Assets:Investments:Stock    1 HOOL {500 USD}
              Assets:Investments:Cash  -500 USD

            2014-06-22 * "Down to zero"
              Assets:Investments:Stock   -1 HOOL {500 USD}
              Assets:Investments:Cash   500 USD

            2014-06-22 * "Go negative from zero"
              Assets:Investments:Stock   -1 HOOL {500 USD}
              Assets:Investments:Cash   500 USD

            2014-06-22 * "Go positive much"
              Assets:Investments:Stock    11 HOOL {500 USD}
              Assets:Investments:Cash  -5500 USD

            2014-06-22 * "Cross to negative from above zero"
              Assets:Investments:Stock  -15 HOOL {500 USD}
              Assets:Investments:Cash  7500 USD
        """.trimIndent())

        val validationErrors = validateInventoryBooking(result.entries, result.options)
        assertEquals(0, validationErrors.size, "Expected no errors: ${validationErrors.map { it.message }}")
    }

    @Test
    fun `test simple negative lots`() {
        val result = parser.parseString("""
            2013-05-01 open Assets:Bank:Investing
            2013-05-01 open Equity:Opening-Balances

            2013-05-02 *
              Assets:Bank:Investing                -1 HOOL {501 USD}
              Equity:Opening-Balances             501 USD
        """.trimIndent())

        val validationErrors = validateInventoryBooking(result.entries, result.options)
        assertEquals(0, validationErrors.size, "Expected no errors: ${validationErrors.map { it.message }}")
    }

    @Test
    fun `test mixed lots in single transaction`() {
        val result = parser.parseString("""
            2013-05-01 open Assets:Bank:Investing
            2013-05-01 open Equity:Opening-Balances

            2013-05-02 *
              Assets:Bank:Investing                 5 HOOL {501 USD}
              Assets:Bank:Investing                -1 HOOL {502 USD}
              Equity:Opening-Balances           -2003 USD
        """.trimIndent())

        val validationErrors = validateInventoryBooking(result.entries, result.options)
        assertEquals(1, validationErrors.size, "Expected 1 mixed lot error")
    }

    @Test
    fun `test mixed lots in multiple transactions augmenting`() {
        val result = parser.parseString("""
            2013-05-01 open Assets:Bank:Investing
            2013-05-01 open Equity:Opening-Balances

            2013-05-02 *
              Assets:Bank:Investing                 5 HOOL {501 USD}
              Equity:Opening-Balances            -501 USD

            2013-05-03 *
              Assets:Bank:Investing                -1 HOOL {502 USD}
              Equity:Opening-Balances             502 USD
        """.trimIndent())

        val validationErrors = validateInventoryBooking(result.entries, result.options)
        assertEquals(1, validationErrors.size, "Expected 1 mixed lot error")
    }

    @Test
    fun `test mixed lots in multiple transactions reducing`() {
        val result = parser.parseString("""
            2013-05-01 open Assets:Bank:Investing
            2013-05-01 open Equity:Opening-Balances

            2013-05-02 *
              Assets:Bank:Investing                 5 HOOL {501 USD}
              Assets:Bank:Investing                 5 HOOL {502 USD}
              Equity:Opening-Balances           -5015 USD

            2013-05-03 *
              Assets:Bank:Investing                -6 HOOL {502 USD}
              Equity:Opening-Balances            3012 USD
        """.trimIndent())

        val validationErrors = validateInventoryBooking(result.entries, result.options)
        assertEquals(1, validationErrors.size, "Expected 1 mixed lot error")
    }
}
