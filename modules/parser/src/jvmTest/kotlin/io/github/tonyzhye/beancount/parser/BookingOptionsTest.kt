package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.Booking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests ported from Python beancount.parser.booking_full_test.TestParseBookingOptions.
 */
class BookingOptionsTest {

    private val parser = BeancountParser()

    @Test
    fun `test booking method strict`() {
        val result = parser.parseString("""
            option "booking_method" "STRICT"
        """.trimIndent())

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(Booking.STRICT, result.options.bookingMethod)
    }

    @Test
    fun `test booking method average`() {
        val result = parser.parseString("""
            option "booking_method" "AVERAGE"
        """.trimIndent())

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(Booking.AVERAGE, result.options.bookingMethod)
    }

    @Test
    fun `test booking method invalid`() {
        val result = parser.parseString("""
            option "booking_method" "XXX"
        """.trimIndent())

        assertEquals(1, result.errors.size, "Expected 1 error for invalid booking method")
        assertTrue(result.errors[0].message.contains("Invalid booking method", ignoreCase = true),
            "Error should mention 'Invalid booking method': ${result.errors[0].message}")
        // Default falls back to STRICT
        assertEquals(Booking.STRICT, result.options.bookingMethod)
    }
}
