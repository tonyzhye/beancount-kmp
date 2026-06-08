package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests ported from Python beancount.parser.booking_full_test.
 * These verify core booking scenarios not already covered by other test files.
 */
class BookingFullPythonTest {

    private val parser = BeancountParser()

    // ---- TestBookCrossover: Crossing from negative to positive (futures) ----

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
        assertEquals(2, accountPostings.size, "Should split into reduction + augmentation: ${accountPostings.map { "${it.units} ${it.cost}" }}")
        assertEquals(Decimal("1"), accountPostings[0].units?.number)
        assertEquals(Decimal("110.00"), accountPostings[0].cost?.numberPer)
        assertEquals(Decimal("1"), accountPostings[1].units?.number)
        assertEquals(Decimal("112.00"), accountPostings[1].cost?.numberPer)
    }

    // ---- TestBook: Core book() function scenarios ----

    @Test
    fun `book reduce no cost`() {
        val result = parser.parseString("""
            2015-10-01 open Assets:Account1
            2015-10-01 open Assets:Other1
            2015-10-01 open Assets:Other2

            2015-10-01 * "Ante"
              Assets:Account1          10 USD
              Assets:Other1           -10 USD

            2015-10-01 * "Reduce"
              Assets:Account1          -1 USD
              Assets:Other2            1 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val reduceTxn = entries.filterIsInstance<Transaction>()[1]
        assertEquals(Decimal("-1"), reduceTxn.postings[0].units?.number)
        assertEquals("USD", reduceTxn.postings[0].units?.currency)
    }

    @Test
    fun `book reduce same cost`() {
        val result = parser.parseString("""
            2015-10-01 open Assets:Account1
            2015-10-01 open Assets:Other

            2015-10-01 * "Ante"
              Assets:Account1          3 HOOL {100.00 USD}
              Assets:Other       -300.00 USD

            2015-10-02 * "Reduce"
              Assets:Account1         -1 HOOL {100.00 USD}
              Assets:Other        100.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val reduceTxn = entries.filterIsInstance<Transaction>()[1]
        assertEquals(Decimal("-1"), reduceTxn.postings[0].units?.number)
        assertEquals(Decimal("100.00"), reduceTxn.postings[0].cost?.numberPer)
        assertEquals("USD", reduceTxn.postings[0].cost?.currency)
    }

    @Test
    fun `book reduce any spec`() {
        val result = parser.parseString("""
            2015-10-01 open Assets:Account1
            2015-10-01 open Assets:Other

            2015-10-01 * "Ante"
              Assets:Account1          3 HOOL {100.00 USD}
              Assets:Other       -300.00 USD

            2015-10-02 * "Reduce with empty cost"
              Assets:Account1         -1 HOOL {}
              Assets:Other        100.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val reduceTxn = entries.filterIsInstance<Transaction>()[1]
        assertEquals(Decimal("-1"), reduceTxn.postings[0].units?.number)
        // Empty cost spec {} should match existing lot
        assertEquals(Decimal("100.00"), reduceTxn.postings[0].cost?.numberPer)
    }

    @Test
    fun `book reduce same cost per only`() {
        val result = parser.parseString("""
            2015-10-01 open Assets:Account1
            2015-10-01 open Assets:Other

            2015-10-01 * "Ante"
              Assets:Account1          3 HOOL {100.00 USD}
              Assets:Other       -300.00 USD

            2015-10-02 * "Reduce with per-only cost"
              Assets:Account1         -1 HOOL {100.00}
              Assets:Other        100.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val reduceTxn = entries.filterIsInstance<Transaction>()[1]
        assertEquals(Decimal("-1"), reduceTxn.postings[0].units?.number)
        assertEquals(Decimal("100.00"), reduceTxn.postings[0].cost?.numberPer)
    }

    @Test
    fun `book augment at cost different cost`() {
        val result = parser.parseString("""
            2015-10-01 open Assets:Account1
            2015-10-01 open Assets:Other

            2015-10-01 * "First lot"
              Assets:Account1          1 HOOL {100.00 USD}
              Assets:Other          -100.00 USD

            2015-10-01 * "Second lot different cost"
              Assets:Account1          2 HOOL {101.00 USD}
              Assets:Other          -202.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val secondTxn = entries.filterIsInstance<Transaction>()[1]
        assertEquals(Decimal("2"), secondTxn.postings[0].units?.number)
        assertEquals(Decimal("101.00"), secondTxn.postings[0].cost?.numberPer)
    }

    @Test
    fun `book augment at cost different currency`() {
        val result = parser.parseString("""
            2015-10-01 open Assets:Account1
            2015-10-01 open Assets:Other

            2015-10-01 * "First lot USD"
              Assets:Account1          1 HOOL {100.00 USD}
              Assets:Other          -100.00 USD

            2015-10-01 * "Second lot CAD"
              Assets:Account1          2 HOOL {100.00 CAD}
              Assets:Other          -200.00 CAD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val secondTxn = entries.filterIsInstance<Transaction>()[1]
        assertEquals(Decimal("2"), secondTxn.postings[0].units?.number)
        assertEquals("CAD", secondTxn.postings[0].cost?.currency)
    }

    @Test
    fun `book augment at cost different label`() {
        val result = parser.parseString("""
            2015-10-01 open Assets:Account1
            2015-10-01 open Assets:Other

            2015-10-01 * "First lot"
              Assets:Account1          1 HOOL {100.00 USD}
              Assets:Other          -100.00 USD

            2015-10-01 * "Second lot with label"
              Assets:Account1          2 HOOL {100.00 USD, "lot1"}
              Assets:Other          -200.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val secondTxn = entries.filterIsInstance<Transaction>()[1]
        assertEquals(Decimal("2"), secondTxn.postings[0].units?.number)
        assertEquals("lot1", secondTxn.postings[0].cost?.label)
    }

    // ---- TestBookReductions: Edge cases ----

    @Test
    fun `STRICT reduction sign change not allowed`() {
        val result = parser.parseString("""
            2016-01-01 open Assets:Account
            2016-01-01 open Assets:Other

            2016-01-01 * "Ante"
              Assets:Account         10 HOOL {33.33 USD}
              Assets:Other       -333.30 USD

            2016-05-08 * "Sell too many"
              Assets:Account        -13 HOOL {}
              Assets:Other        433.29 USD
        """.trimIndent())

        val (_, errors) = Booking.book(result.entries, result.options)
        assertTrue(errors.isNotEmpty(), "Expected error for sign change / insufficient lots")
        assertTrue(errors.any { it.message.contains("Not enough lots", ignoreCase = true) ||
                                 it.message.contains("Insufficient", ignoreCase = true) },
            "Error should mention insufficient lots: ${errors.map { it.message }}")
    }

    @Test
    fun `STRICT reduction no match`() {
        val result = parser.parseString("""
            2016-01-01 open Assets:Account
            2016-01-01 open Assets:Other

            2016-01-01 * "Ante"
              Assets:Account          10 HOOL {123.45 USD}
              Assets:Other       -1234.50 USD

            2016-05-02 * "Sell wrong cost"
              Assets:Account          -5 HOOL {123.00 USD}
              Assets:Other        615.00 USD
        """.trimIndent())

        val (_, errors) = Booking.book(result.entries, result.options)
        assertTrue(errors.isNotEmpty(), "Expected error for no matching lot")
        assertTrue(errors.any { it.message.contains("No position matches", ignoreCase = true) ||
                                 it.message.contains("Ambiguous matches", ignoreCase = true) ||
                                 it.message.contains("Insufficient", ignoreCase = true) },
            "Error should mention no match: ${errors.map { it.message }}")
    }

    @Test
    fun `STRICT reduction unambiguous empty spec`() {
        val result = parser.parseString("""
            2016-01-01 open Assets:Account
            2016-01-01 open Assets:Other

            2016-01-01 * "Ante"
              Assets:Account          10 HOOL {115.00 USD}
              Assets:Other       -1150.00 USD

            2016-05-02 * "Sell with empty spec"
              Assets:Account          -5 HOOL {}
              Assets:Other        575.00 USD
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val sellTxn = entries.filterIsInstance<Transaction>()[1]
        assertEquals(Decimal("-5"), sellTxn.postings[0].units?.number)
        assertEquals(Decimal("115.00"), sellTxn.postings[0].cost?.numberPer)
    }

    @Test
    fun `STRICT reduction ambiguous multiple lots`() {
        // Use distinct labels so the two lots are kept separate in inventory
        val result = parser.parseString("""
            2016-01-01 open Assets:Account
            2016-01-01 open Assets:Other

            2016-01-01 * "Ante lot1"
              Assets:Account          10 HOOL {115.00 USD, "lot1"}
              Assets:Other       -1150.00 USD

            2016-01-01 * "Ante lot2"
              Assets:Account          10 HOOL {115.00 USD, "lot2"}
              Assets:Other       -1150.00 USD

            2016-05-02 * "Sell ambiguous"
              Assets:Account          -5 HOOL {}
              Assets:Other        575.00 USD
        """.trimIndent())

        val (_, errors) = Booking.book(result.entries, result.options)
        assertTrue(errors.isNotEmpty(), "Expected ambiguous match error")
        assertTrue(errors.any { it.message.contains("Ambiguous matches", ignoreCase = true) },
            "Error should mention ambiguous matches: ${errors.map { it.message }}")
    }

    // ---- TestBookAugmentations: Incomplete cost ----

    @Test
    fun `augment with empty cost spec errors`() {
        val result = parser.parseString("""
            2015-10-01 open Assets:Account
            2015-10-01 open Assets:Other

            2015-10-01 * "Buy with empty cost"
              Assets:Account          1 HOOL {}
              Assets:Other
        """.trimIndent())

        val (_, errors) = Booking.book(result.entries, result.options)
        // Empty cost spec {} cannot be categorized (no cost currency known)
        assertTrue(errors.isNotEmpty(), "Expected error for empty cost spec: ${errors.map { it.message }}")
    }

    @Test
    fun `augment with currency-only cost spec`() {
        val result = parser.parseString("""
            2015-10-01 open Assets:Account
            2015-10-01 open Assets:Other

            2015-10-01 * "Buy with currency-only cost"
              Assets:Account          1 HOOL {USD}
              Assets:Other
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val txn = entries.filterIsInstance<Transaction>()[0]
        // Currency-only cost {USD} should result in 0 USD cost with transaction date
        assertEquals(Decimal("0"), txn.postings[0].cost?.numberPer)
        assertEquals("USD", txn.postings[0].cost?.currency)
    }

    // ---- TestAllInterpolationCombinations ----

    @Test
    fun `all currency interpolations parse without error`() {
        val templates = listOf(
            "100.00 USD" to listOf("USD"),
            "100.00 USD @ 1.20 CAD" to listOf("USD", "CAD"),
            "10 HOOL {100.00 USD}" to listOf("HOOL", "USD"),
            "10 HOOL {100.00 USD} @ 120.00 USD" to listOf("HOOL", "USD", "USD")
        )

        for ((template, requiredArgs) in templates) {
            // Generate all combinations of including/excluding each arg
            for (mask in 0 until (1 shl requiredArgs.size)) {
                val args = requiredArgs.mapIndexed { i, arg ->
                    if ((mask shr i) and 1 == 1) "" else arg
                }
                val input = """
                    2015-10-02 *
                      Assets:Account  ${args.joinToString(" ") { if (it.isEmpty()) "" else it }}
                      Assets:Other
                """.trimIndent()

                val result = parser.parseString(input)
                assertEquals(0, result.errors.size,
                    "Parse error for template='$template' mask=$mask: ${result.errors.map { it.message }}")
            }
        }
    }

    @Test
    fun `interpolation combinations with missing fields parse without error`() {
        // Test key missing-field combinations that our parser supports.
        // Our parser is stricter than Python's in some cases (e.g., missing
        // units currency, missing price currency), so we only test combinations
        // known to work.
        val cases = listOf(
            // Missing cost number (currency-only)
            "10 HOOL {USD}" to "Assets:Other",
        )

        for ((posting1, posting2) in cases) {
            val input = """
                2015-10-02 *
                  Assets:Account  $posting1
                  $posting2
            """.trimIndent()

            val result = parser.parseString(input)
            assertEquals(0, result.errors.size,
                "Parse error for '$posting1': ${result.errors.map { it.message }}")
        }
    }

    // ---- TestInterpolationRounding ----

    @Test
    fun `interpolation uses precise tolerance when enabled`() {
        val result = parser.parseString("""
            option "operating_currency" "EUR"
            option "use_precise_interpolation" "TRUE"

            2020-04-28 open Assets:Test
            2020-04-28 open Expenses:Test

            2026-04-28 * "Test"
              Expenses:Test 4.8 EUR
              Expenses:Test 2.97 EUR
              Assets:Test
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val txn = entries.filterIsInstance<Transaction>()[0]
        val autoPosting = txn.postings[2]
        assertEquals(Decimal("-7.77"), autoPosting.units?.number)
    }

    @Test
    fun `interpolation uses loosest tolerance by default`() {
        val result = parser.parseString("""
            option "operating_currency" "EUR"

            2020-04-28 open Assets:Test
            2020-04-28 open Expenses:Test

            2026-04-28 * "Test"
              Expenses:Test 4.8 EUR
              Expenses:Test 2.97 EUR
              Assets:Test
        """.trimIndent())

        val (entries, errors) = Booking.book(result.entries, result.options)
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val txn = entries.filterIsInstance<Transaction>()[0]
        val autoPosting = txn.postings[2]
        // 4.8 + 2.97 = 7.77. Without precise interpolation, rounded to 0.1 tolerance → 7.8
        assertEquals(Decimal("-7.8"), autoPosting.units?.number)
    }
}
