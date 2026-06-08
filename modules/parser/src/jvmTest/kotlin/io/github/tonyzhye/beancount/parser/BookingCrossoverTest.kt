package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests ported from Python beancount.parser.booking_full_test.TestBookCrossover.
 * These verify crossing from negative to positive inventory (futures/short-selling scenarios).
 */
class BookingCrossoverTest {

    private val parser = BeancountParser()

    @Test
    fun `FIFO crossover from negative to positive`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-01-01 * "Short"
              Assets:Account          -1 HOOL {110.00 USD}
              Assets:Other            110.00 USD

            2015-02-22 * "Cover and go long"
              Assets:Account           2 HOOL {112.00 USD}
              Assets:Other           -224.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val coverTxn = entries.filterIsInstance<Transaction>()[1]
        val accountPostings = coverTxn.postings.filter { it.account == "Assets:Account" }

        // Crossover: reduce 1 at old cost (covering short), augment 1 at new cost
        assertEquals(2, accountPostings.size)
        assertEquals(Decimal("1"), accountPostings[0].units?.number)
        assertEquals(Decimal("110.00"), accountPostings[0].cost?.numberPer)
        assertEquals(Decimal("1"), accountPostings[1].units?.number)
        assertEquals(Decimal("112.00"), accountPostings[1].cost?.numberPer)
    }

    @Test
    fun `crossover from negative to zero`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-01-01 * "Short"
              Assets:Account          -1 HOOL {110.00 USD}
              Assets:Other            110.00 USD

            2015-02-22 * "Cover short exactly"
              Assets:Account           1 HOOL {112.00 USD}
              Assets:Other           -112.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val coverTxn = entries.filterIsInstance<Transaction>()[1]
        val accountPostings = coverTxn.postings.filter { it.account == "Assets:Account" }

        // Exact cover: just reduces the negative lot to zero, no augmentation
        assertEquals(1, accountPostings.size)
        assertEquals(Decimal("1"), accountPostings[0].units?.number)
        assertEquals(Decimal("110.00"), accountPostings[0].cost?.numberPer)
    }

    @Test
    fun `crossover multiple short lots`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-01-01 * "Short 1"
              Assets:Account          -1 HOOL {110.00 USD}
              Assets:Other            110.00 USD

            2015-01-15 * "Short 2"
              Assets:Account          -1 HOOL {115.00 USD}
              Assets:Other            115.00 USD

            2015-02-22 * "Cover all and go long"
              Assets:Account           3 HOOL {112.00 USD}
              Assets:Other           -336.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val coverTxn = entries.filterIsInstance<Transaction>()[2]
        val accountPostings = coverTxn.postings.filter { it.account == "Assets:Account" }

        // Should cover 2 short lots + augment 1 new lot = 3 postings
        assertEquals(3, accountPostings.size)
        val totalUnits = accountPostings.sumOf { it.units!!.number.toDouble() }
        assertEquals(3.0, totalUnits, 0.001)
    }

    @Test
    fun `crossover with LIFO ordering`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-01-01 * "Short 1"
              Assets:Account          -1 HOOL {110.00 USD}
              Assets:Other            110.00 USD

            2015-01-15 * "Short 2"
              Assets:Account          -1 HOOL {115.00 USD}
              Assets:Other            115.00 USD

            2015-02-22 * "Cover with LIFO"
              Assets:Account           1 HOOL {112.00 USD}
              Assets:Other           -112.00 USD
        """.trimIndent())

        // Set LIFO booking method on the account
        val openEntry = result.entries.filterIsInstance<Open>().find { it.account == "Assets:Account" }!!
        val modifiedOpen = openEntry.copy(booking = io.github.tonyzhye.beancount.core.Booking.LIFO)
        val modifiedEntries = result.entries.map { if (it === openEntry) modifiedOpen else it }

        val (entries, errors) = Booking.book(modifiedEntries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val coverTxn = entries.filterIsInstance<Transaction>()[2]
        val accountPostings = coverTxn.postings.filter { it.account == "Assets:Account" }

        // LIFO should cover the most recent short first (115.00)
        assertEquals(1, accountPostings.size)
        assertEquals(Decimal("115.00"), accountPostings[0].cost?.numberPer)
    }

    @Test
    fun `crossover from zero to positive no crossover`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-02-22 * "Go long"
              Assets:Account           2 HOOL {112.00 USD}
              Assets:Other           -224.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val txn = entries.filterIsInstance<Transaction>()[0]
        val accountPostings = txn.postings.filter { it.account == "Assets:Account" }

        // Simple augmentation, no split
        assertEquals(1, accountPostings.size)
        assertEquals(Decimal("2"), accountPostings[0].units?.number)
        assertEquals(Decimal("112.00"), accountPostings[0].cost?.numberPer)
    }

    @Test
    fun `crossover from positive to zero no crossover`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-01-01 * "Buy"
              Assets:Account           2 HOOL {112.00 USD}
              Assets:Other           -224.00 USD

            2015-02-22 * "Sell all"
              Assets:Account          -2 HOOL {112.00 USD}
              Assets:Other            224.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val sellTxn = entries.filterIsInstance<Transaction>()[1]
        val accountPostings = sellTxn.postings.filter { it.account == "Assets:Account" }

        // Normal reduction, no crossover
        assertEquals(1, accountPostings.size)
        assertEquals(Decimal("-2"), accountPostings[0].units?.number)
    }

    @Test
    fun `crossover insufficient without negative inventory`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-01-01 * "Buy"
              Assets:Account           1 HOOL {112.00 USD}
              Assets:Other           -112.00 USD

            2015-02-22 * "Sell too many"
              Assets:Account          -2 HOOL {112.00 USD}
              Assets:Other            224.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        // Selling more than owned should produce an error
        assertTrue(errors.isNotEmpty(), "Expected insufficient lots error")
        assertTrue(errors.any { it.message.contains("Insufficient lots", ignoreCase = true) },
            "Error should mention insufficient lots: ${errors.map { it.message }}")
    }

    @Test
    fun `crossover with NONE booking allows negative`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-02-22 * "Go short with NONE"
              Assets:Account          -2 HOOL {112.00 USD}
              Assets:Other            224.00 USD
        """.trimIndent())

        val openEntry = result.entries.filterIsInstance<Open>().find { it.account == "Assets:Account" }!!
        val modifiedOpen = openEntry.copy(booking = io.github.tonyzhye.beancount.core.Booking.NONE)
        val modifiedEntries = result.entries.map { if (it === openEntry) modifiedOpen else it }

        val (entries, errors) = Booking.book(modifiedEntries, result.options)
        assertEquals(0, errors.size, "Expected no errors with NONE booking: ${errors.map { it.message }}")

        val txn = entries.filterIsInstance<Transaction>()[0]
        val accountPostings = txn.postings.filter { it.account == "Assets:Account" }

        // NONE booking allows negative positions directly
        assertEquals(1, accountPostings.size)
        assertEquals(Decimal("-2"), accountPostings[0].units?.number)
    }

    @Test
    fun `crossover then reduce positive`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-01-01 * "Short"
              Assets:Account          -1 HOOL {110.00 USD}
              Assets:Other            110.00 USD

            2015-02-22 * "Crossover to long"
              Assets:Account           3 HOOL {112.00 USD}
              Assets:Other           -336.00 USD

            2015-03-15 * "Reduce long"
              Assets:Account          -1 HOOL {112.00 USD}
              Assets:Other            112.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val reduceTxn = entries.filterIsInstance<Transaction>()[2]
        val accountPostings = reduceTxn.postings.filter { it.account == "Assets:Account" }

        // After crossover, should have 2 HOOL at 112. Reducing 1 should match the new lot.
        assertEquals(1, accountPostings.size)
        assertEquals(Decimal("-1"), accountPostings[0].units?.number)
        assertEquals(Decimal("112.00"), accountPostings[0].cost?.numberPer)
    }

    @Test
    fun `crossover partial cover multiple lots`() {
        val result = parser.parseString("""
            2015-01-01 open Assets:Account
            2015-01-01 open Assets:Other

            2015-01-01 * "Short 1"
              Assets:Account          -1 HOOL {110.00 USD}
              Assets:Other            110.00 USD

            2015-01-15 * "Short 2"
              Assets:Account          -1 HOOL {115.00 USD}
              Assets:Other            115.00 USD

            2015-02-22 * "Partial cover"
              Assets:Account           1 HOOL {112.00 USD}
              Assets:Other           -112.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val coverTxn = entries.filterIsInstance<Transaction>()[2]
        val accountPostings = coverTxn.postings.filter { it.account == "Assets:Account" }

        // Partial cover of 1 unit against 2 short lots
        assertEquals(1, accountPostings.size)
        assertEquals(Decimal("1"), accountPostings[0].units?.number)
    }
}
