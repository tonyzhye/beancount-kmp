package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ported from Python beancount.core.interpolate_test.
 *
 * Tests for interpolation, residual computation, tolerance inference,
 * and balance checking.
 */
class InterpolatePythonTest {

    @Test
    fun `hasNontrivialBalance - entry without cost or price`() {
        val posting = Posting("Assets:Bank:Checking", Amount(Decimal("105.50"), "USD"))
        assertFalse(hasNontrivialBalance(posting))
    }

    @Test
    fun `hasNontrivialBalance - entry without cost with price`() {
        val posting = Posting(
            "Assets:Bank:Checking",
            Amount(Decimal("105.50"), "USD"),
            price = Amount(Decimal("0.90"), "CAD")
        )
        assertTrue(hasNontrivialBalance(posting))
    }

    @Test
    fun `hasNontrivialBalance - entry with cost without price`() {
        val posting = Posting(
            "Assets:Bank:Checking",
            Amount(Decimal("105.50"), "USD"),
            cost = CostSpec(numberPer = Decimal("0.80"), currency = "EUR")
        )
        assertTrue(hasNontrivialBalance(posting))
    }

    @Test
    fun `hasNontrivialBalance - entry with cost and price`() {
        val posting = Posting(
            "Assets:Bank:Checking",
            Amount(Decimal("105.50"), "USD"),
            cost = CostSpec(numberPer = Decimal("0.80"), currency = "EUR"),
            price = Amount(Decimal("2.00"), "CAD")
        )
        assertTrue(hasNontrivialBalance(posting))
    }

    @Test
    fun `computeResidual - two accounts`() {
        val residual = computeResidual(
            listOf(
                Posting("Assets:Bank:Checking", Amount(Decimal("105.50"), "USD")),
                Posting("Assets:Bank:Checking", Amount(Decimal("-194.50"), "USD"))
            )
        )
        assertEquals(
            Inventory.fromString("-89 USD"),
            residual.reduce { it.units }
        )
    }

    @Test
    fun `computeResidual - more accounts`() {
        val residual = computeResidual(
            listOf(
                Posting("Assets:Bank:Checking", Amount(Decimal("105.50"), "USD")),
                Posting("Assets:Bank:Checking", Amount(Decimal("-194.50"), "USD")),
                Posting("Assets:Bank:Investing", Amount(Decimal("5"), "AAPL")),
                Posting("Assets:Bank:Savings", Amount(Decimal("89.00"), "USD"))
            )
        )
        assertEquals(
            Inventory.fromString("5 AAPL"),
            residual.reduce { it.units }
        )
    }

    @Test
    fun `fillResidualPosting - balanced transactions`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2001-01-01 open Assets:Account1
            2001-01-01 open Assets:Other
            2001-01-01 open Equity:Rounding

            2014-01-01 *
              Assets:Account1      100.00 USD
              Assets:Other        -100.00 USD

            2014-01-02 *
              Assets:Account1      100.00 USD
              Assets:Other        -100.00 USD
        """.trimIndent())

        val transactions = result.entries.filterIsInstance<Transaction>()
        val account = "Equity:Rounding"

        for (entry in transactions.take(2)) {
            val filled = fillResidualPosting(entry, account)
            assertEquals(entry.postings.size, filled.postings.size)
            val residual = computeResidual(filled.postings)
            assertTrue(residual.isEmpty())
        }
    }

    @Test
    fun `fillResidualPosting - small imbalance`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2001-01-01 open Assets:Account1
            2001-01-01 open Assets:Other
            2001-01-01 open Equity:Rounding

            2014-01-03 *
              Assets:Account1      100.00 USD
              Assets:Other        -100.0000001 USD
        """.trimIndent())

        val entry = result.entries.filterIsInstance<Transaction>()[0]
        val filled = fillResidualPosting(entry, "Equity:Rounding")

        assertEquals(3, filled.postings.size)
        val roundingPosting = filled.postings.find { it.account == "Equity:Rounding" }
        assertTrue(roundingPosting != null)
        assertEquals(Decimal("0.0000001"), roundingPosting!!.units!!.number)
        assertEquals("USD", roundingPosting.units!!.currency)

        val residual = computeResidual(filled.postings)
        assertFalse(residual.isEmpty())
    }

    @Test
    fun `fillResidualPosting - price conversion residual`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2001-01-01 open Assets:Account1
            2001-01-01 open Assets:Other
            2001-01-01 open Equity:Rounding

            2014-01-04 *
              Assets:Account1      100.00 USD
              Assets:Other        -112.69 CAD @ 0.8875 USD
        """.trimIndent())

        val entry = result.entries.filterIsInstance<Transaction>()[0]
        val filled = fillResidualPosting(entry, "Equity:Rounding")

        assertEquals(3, filled.postings.size)
        val roundingPosting = filled.postings.find { it.account == "Equity:Rounding" }
        assertTrue(roundingPosting != null)

        val residual = computeResidual(filled.postings)
        assertFalse(residual.isEmpty())
    }

    @Test
    fun `computeEntriesBalance - currencies`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2014-01-01 open Assets:Bank:Checking
            2014-01-01 open Assets:Bank:Savings
            2014-01-01 open Assets:Investing
            2014-01-01 open Assets:Other

            2014-06-01 *
              Assets:Bank:Checking  111.23 USD
              Assets:Other

            2014-06-02 *
              Assets:Bank:Savings   222.74 USD
              Assets:Other

            2014-06-03 *
              Assets:Bank:Savings   17.23 CAD
              Assets:Other

            2014-06-04 *
              Assets:Investing      10000 EUR
              Assets:Other
        """.trimIndent())

        // Python loader.load_doc runs booking pipeline; mimic that
        val (bookedEntries, _) = Booking.book(result.entries, result.options)
        val computedBalance = computeEntriesBalance(bookedEntries)
        val expectedBalance = Inventory()
        assertEquals(expectedBalance, computedBalance)
    }

    @Test
    fun `computeEntriesBalance - at cost`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2014-01-01 open Assets:Investing
            2014-01-01 open Assets:Other

            2014-06-05 *
              Assets:Investing      30 HOOL {40 USD}
              Assets:Other

            2014-06-05 *
              Assets:Investing      -20 HOOL {40 USD}
              Assets:Other
        """.trimIndent())

        val (bookedEntries, bookingErrors) = Booking.book(result.entries, result.options)
        assertTrue(bookingErrors.isEmpty(), "Booking errors: ${bookingErrors.map { it.message }}")

        val computedBalance = computeEntriesBalance(bookedEntries)
        val expectedBalance = Inventory()
        expectedBalance.addAmount(Amount(Decimal("-400"), "USD"))
        expectedBalance.addAmount(
            Amount(Decimal("10"), "HOOL"),
            Cost(Decimal("40"), "USD", LocalDate(2014, 6, 5), null)
        )
        assertEquals(expectedBalance, computedBalance)
    }

    @Test
    fun `computeEntriesBalance - conversions`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2014-01-01 open Assets:Investing
            2014-01-01 open Assets:Other

            2014-06-06 *
              Assets:Investing          1000 EUR @ 1.78 GBP
              Assets:Other

            2014-06-07 *
              Assets:Investing          1000 EUR @@ 1780 GBP
              Assets:Other
        """.trimIndent())

        val (bookedEntries, _) = Booking.book(result.entries, result.options)
        val computedBalance = computeEntriesBalance(bookedEntries)
        val expectedBalance = Inventory()
        expectedBalance.addAmount(Amount(Decimal("2000.00"), "EUR"))
        expectedBalance.addAmount(Amount(Decimal("-3560.00"), "GBP"))
        assertEquals(expectedBalance, computedBalance)
    }

    @Test
    fun `inferTolerances - no precision`() {
        val postings = listOf(
            Posting("Assets:Account1", Amount(Decimal("500"), "USD")),
            Posting("Assets:Account2", Amount(Decimal("-120"), "USD")),
            Posting("Assets:Account3", Amount(Decimal("-380"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertEquals(emptyMap<String, Decimal>(), tolerances)
    }

    @Test
    fun `inferTolerances - dubious precision`() {
        val postings = listOf(
            Posting("Assets:Account1", Amount(Decimal("5.0000"), "USD")),
            Posting("Assets:Account2", Amount(Decimal("5.000"), "USD")),
            Posting("Assets:Account3", Amount(Decimal("5.00"), "USD")),
            Posting("Assets:Account4", Amount(Decimal("-5.0"), "USD")),
            Posting("Assets:Account4", Amount(Decimal("-5"), "USD")),
            Posting("Assets:Account4", Amount(Decimal("-5"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertEquals(mapOf("USD" to Decimal("0.05")), tolerances)
    }

    @Test
    fun `inferTolerances - ignore price`() {
        val postings = listOf(
            Posting("Assets:Account3", Amount(Decimal("5"), "VHT"), price = Amount(Decimal("102.2340"), "USD")),
            Posting("Assets:Account4", Amount(Decimal("-511.11"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertEquals(mapOf("USD" to Decimal("0.005")), tolerances)
    }

    @Test
    fun `inferTolerances - ignore cost`() {
        val postings = listOf(
            Posting("Assets:Account3", Amount(Decimal("5"), "VHT"), cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD")),
            Posting("Assets:Account4", Amount(Decimal("-511.11"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertEquals(mapOf("USD" to Decimal("0.005")), tolerances)
    }

    @Test
    fun `inferTolerances - ignore cost and price`() {
        val postings = listOf(
            Posting(
                "Assets:Account3",
                Amount(Decimal("5"), "VHT"),
                cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD"),
                price = Amount(Decimal("103.45237239"), "USD")
            ),
            Posting("Assets:Account4", Amount(Decimal("-511.11"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertEquals(mapOf("USD" to Decimal("0.005")), tolerances)
    }

    @Test
    fun `inferTolerances - cost and number ignored when integer`() {
        val postings = listOf(
            Posting("Assets:Account3", Amount(Decimal("5"), "VHT"), cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD")),
            Posting("Assets:Account4", Amount(Decimal("-511"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertEquals(emptyMap<String, Decimal>(), tolerances)
    }

    @Test
    fun `inferTolerances - number on cost used`() {
        val postings = listOf(
            Posting("Assets:Account3", Amount(Decimal("5.111"), "VHT"), cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD")),
            Posting("Assets:Account4", Amount(Decimal("-511"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertEquals(mapOf("VHT" to Decimal("0.0005")), tolerances)

        val tolerancesWithCost = inferTolerances(postings, useCost = true)
        assertEquals(mapOf("VHT" to Decimal("0.0005"), "USD" to Decimal("0.051117")), tolerancesWithCost)
    }

    @Test
    fun `inferTolerances - number on cost used overrides`() {
        val postings = listOf(
            Posting("Assets:Account3", Amount(Decimal("5.111"), "VHT"), cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD")),
            Posting("Assets:Account4", Amount(Decimal("-511.0"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertEquals(mapOf("VHT" to Decimal("0.0005"), "USD" to Decimal("0.05")), tolerances)

        val tolerancesWithCost = inferTolerances(postings, useCost = true)
        assertEquals(mapOf("VHT" to Decimal("0.0005"), "USD" to Decimal("0.051117")), tolerancesWithCost)
    }

    @Test
    fun `inferTolerances - minimum on costs`() {
        val postings = listOf(
            Posting("Assets:Account3", Amount(Decimal("5.11111"), "VHT"), cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD")),
            Posting("Assets:Account3", Amount(Decimal("5.111111"), "VHT"), cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD")),
            Posting("Assets:Account3", Amount(Decimal("5.1111111"), "VHT"), cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD")),
            Posting("Assets:Account4", Amount(Decimal("-1564.18"), "USD"))
        )
        val tolerances = inferTolerances(postings)
        assertEquals(mapOf("VHT" to Decimal("0.000005"), "USD" to Decimal("0.005")), tolerances)
    }

    @Test
    fun `inferTolerances - modes max and min`() {
        val postings = listOf(
            Posting("Expenses:Test", Amount(Decimal("4.8"), "EUR")),
            Posting("Expenses:Test", Amount(Decimal("2.97"), "EUR")),
            Posting("Assets:Test", null)
        )
        val tolerancesMax = inferTolerances(postings, mode = "max")
        assertEquals(Decimal("0.05"), tolerancesMax["EUR"])

        val tolerancesMin = inferTolerances(postings, mode = "min")
        assertEquals(Decimal("0.005"), tolerancesMin["EUR"])
    }

    @Test
    fun `quantizeWithTolerance - known currency`() {
        val tolerances = mapOf("USD" to Decimal("0.01"))
        assertEquals(
            Decimal("100.12"),
            quantizeWithTolerance(tolerances, "USD", Decimal("100.123123123"))
        )
    }

    @Test
    fun `quantizeWithTolerance - unknown currency returns original`() {
        // Kotlin implementation does not support default tolerance for unknown currencies
        val tolerances = mapOf("USD" to Decimal("0.01"))
        assertEquals(
            Decimal("100.123123123"),
            quantizeWithTolerance(tolerances, "CAD", Decimal("100.123123123"))
        )
    }

    @Test
    fun `quantizeWithTolerance - without default`() {
        val tolerances = mapOf("USD" to Decimal("0.01"))
        assertEquals(
            Decimal("100.12"),
            quantizeWithTolerance(tolerances, "USD", Decimal("100.123123123"))
        )
        assertEquals(
            Decimal("100.123123123"),
            quantizeWithTolerance(tolerances, "CAD", Decimal("100.123123123"))
        )
    }

    @Test
    fun `computeEntryContext - transaction context`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2014-01-01 open Assets:Account1
            2014-01-01 open Assets:Account2
            2014-01-01 open Assets:Account3
            2014-01-01 open Assets:Account4
            2014-01-01 open Assets:Other

            2014-02-10 *
              Assets:Account1      100.00 USD
              Assets:Other

            2014-02-11 *
              Assets:Account2       80.00 USD
              Assets:Other

            2014-02-12 *
              Assets:Account3       60.00 USD
              Assets:Account3       40.00 USD
              Assets:Other

            2014-02-20 * "Context" #context
              Assets:Account1       5.00 USD
              Assets:Account2      -5.00 USD

            2014-02-21 balance Assets:Account1   105.00 USD

            2014-02-25 *
              Assets:Account3       5.00 USD
              Assets:Account4      -5.00 USD
        """.trimIndent())

        val contextEntry = result.entries.find { it is Transaction && it.tags.contains("context") } as Transaction
        val (balanceBefore, balanceAfter) = computeEntryContext(result.entries, contextEntry)

        assertEquals(Inventory.fromString("100.00 USD"), balanceBefore["Assets:Account1"])
        assertEquals(Inventory.fromString("80.00 USD"), balanceBefore["Assets:Account2"])

        assertEquals(Inventory.fromString("105.00 USD"), balanceAfter["Assets:Account1"])
        assertEquals(Inventory.fromString("75.00 USD"), balanceAfter["Assets:Account2"])

        // For non-transaction entries, before and after should be the same
        val balanceEntry = result.entries.find { it is Balance } as Balance
        val (before, after) = computeEntryContext(result.entries, balanceEntry)
        assertEquals(before, after)
    }

    @Test
    fun `inferTolerances - missing units only with price`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2017-01-01 open Assets:Checking USD
            2017-01-01 open Assets:Cash     CAD

            2017-06-23 * "Taking out cash from RBC machine"
              Assets:Checking     USD @ 1.32 CAD
              Assets:Cash     400 CAD
        """.trimIndent())

        // Python's test just verifies parsing succeeds without errors.
        // Integer amounts (400 CAD) do not contribute to tolerance inference.
        assertEquals(0, result.errors.size, "Expected no parse errors: ${result.errors.map { it.message }}")
        val txn = result.entries.filterIsInstance<Transaction>()[0]
        val tolerances = inferTolerances(txn.postings)
        assertEquals(emptyMap<String, Decimal>(), tolerances)
    }

    @Test
    fun `inferTolerances - number on cost fail to succ`() {
        // Test that infer_tolerance_from_cost increases tolerance from cost values.
        val postings = listOf(
            Posting("Assets:Account3", Amount(Decimal("5.111"), "VHT"), cost = CostSpec(numberPer = Decimal("1000.00"), currency = "USD")),
            Posting("Assets:Account4", Amount(Decimal("-5110.80"), "USD"))
        )

        val optionsWithout = Options()
        val tolWithout = inferTolerances(postings, optionsWithout)
        // Without cost inference: only USD from cash leg = 0.005
        assertEquals(Decimal("0.005"), tolWithout["USD"])

        val optionsWith = Options(inferToleranceFromCost = true)
        val tolWith = inferTolerances(postings, optionsWith)
        // With cost inference: tolerance includes cost contribution
        // VHT tolerance = 0.0005, cost tolerance for USD = 0.0005 * 1000.00 = 0.5 (capped)
        // Final USD = max(0.005, 0.5) = 0.5
        assertEquals(Decimal("0.0005"), tolWith["VHT"])
        assertEquals(Decimal("0.5"), tolWith["USD"])
    }

    @Test
    fun `inferTolerances - with inference`() {
        val postings = listOf(
            Posting("Assets:Account3", Amount(Decimal("5.1111"), "VHT"), cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD")),
            Posting("Assets:Account4", null)
        )
        val options = Options()

        val tolerances = inferTolerances(postings, options)
        assertEquals(mapOf("VHT" to Decimal("0.00005")), tolerances)

        val tolerancesWithCost = inferTolerances(postings, options.copy(inferToleranceFromCost = true))
        assertEquals(mapOf("VHT" to Decimal("0.00005"), "USD" to Decimal("0.0051117")), tolerancesWithCost)
    }

    @Test
    fun `inferTolerances - capped inference`() {
        val postings = listOf(
            Posting("Assets:Account3", Amount(Decimal("5.1"), "VHT"), cost = CostSpec(numberPer = Decimal("102.2340"), currency = "USD")),
            Posting("Assets:Account4", null)
        )
        val options = Options()

        val tolerances = inferTolerances(postings, options)
        assertEquals(mapOf("VHT" to Decimal("0.05")), tolerances)

        val tolerancesWithCost = inferTolerances(postings, options.copy(inferToleranceFromCost = true))
        // Cost tolerance is capped at MAXIMUM_TOLERANCE (0.5)
        assertEquals(mapOf("VHT" to Decimal("0.05"), "USD" to Decimal("0.5")), tolerancesWithCost)
    }

    @Test
    fun `inferTolerances - multiplier`() {
        val postings = listOf(
            Posting("Assets:B1", Amount(Decimal("-200.00"), "EUR")),
            Posting("Assets:B2", Amount(Decimal("200.011"), "EUR"))
        )

        // Default multiplier (0.5): tolerance = 0.01 * 0.5 = 0.005
        val tolDefault = inferTolerances(postings, Options())
        assertEquals(Decimal("0.005"), tolDefault["EUR"])

        // Custom multiplier (1.1): tolerance = 0.01 * 1.1 = 0.011
        val tolCustom = inferTolerances(postings, Options(toleranceMultiplier = Decimal("1.1")))
        assertEquals(Decimal("0.011"), tolCustom["EUR"])
    }

    @Test
    fun `inferTolerances - bug53a`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            option "infer_tolerance_from_cost" "TRUE"

            2000-01-01 open Assets:Investments:VWELX
            2000-01-01 open Assets:Investments:Cash

            2006-01-17 * "Plan Contribution"
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:Cash -575.00 USD
        """.trimIndent())

        // Book the entries to compute tolerances (parser alone doesn't book)
        val (bookedEntries, bookingErrors) = Booking.book(result.entries, result.options)
        assertEquals(0, bookingErrors.size, "Expected no booking errors: ${bookingErrors.map { it.message }}")

        val txn = bookedEntries.filterIsInstance<Transaction>()[0]
        @Suppress("UNCHECKED_CAST")
        val tolerances = txn.meta["__tolerances__"] as? Map<String, Decimal>
        assertNotNull(tolerances)
        // VWELX tolerance from units precision
        assertEquals(Decimal("0.0005"), tolerances!!["VWELX"])
        // USD tolerance includes cost inference
        assertNotNull(tolerances["USD"])
    }

    @Test
    fun `inferTolerances - bug53b`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            option "infer_tolerance_from_cost" "TRUE"

            2000-01-01 open Assets:Investments:VWELX
            2000-01-01 open Assets:Investments:Cash

            2006-01-02 * "Plan Contribution"
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:Cash -575.00 USD
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:Cash -575.00 USD

            2006-01-03 * "Plan Contribution"
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:Cash -575.00 USD
              Assets:Investments:Cash -575.00 USD
              Assets:Investments:Cash -575.00 USD

            2006-01-03 * "Plan Contribution"
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:Cash -1725.00 USD

            2006-01-16 * "Plan Contribution"
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:Cash -9200.00 USD
        """.trimIndent())

        // Book the entries to compute tolerances
        val (bookedEntries, bookingErrors) = Booking.book(result.entries, result.options)
        assertEquals(0, bookingErrors.size, "Expected no booking errors: ${bookingErrors.map { it.message }}")

        val transactions = bookedEntries.filterIsInstance<Transaction>()

        @Suppress("UNCHECKED_CAST")
        val tol0 = transactions[0].meta["__tolerances__"] as? Map<String, Decimal>
        assertEquals(Decimal("0.03096"), tol0?.get("USD"))
        assertEquals(Decimal("0.0005"), tol0?.get("VWELX"))

        @Suppress("UNCHECKED_CAST")
        val tol1 = transactions[1].meta["__tolerances__"] as? Map<String, Decimal>
        assertEquals(Decimal("0.04644"), tol1?.get("USD"))
        assertEquals(Decimal("0.0005"), tol1?.get("VWELX"))

        @Suppress("UNCHECKED_CAST")
        val tol2 = transactions[2].meta["__tolerances__"] as? Map<String, Decimal>
        assertEquals(Decimal("0.04644"), tol2?.get("USD"))
        assertEquals(Decimal("0.0005"), tol2?.get("VWELX"))

        @Suppress("UNCHECKED_CAST")
        val tol3 = transactions[3].meta["__tolerances__"] as? Map<String, Decimal>
        assertEquals(Decimal("0.247680"), tol3?.get("USD"))
        assertEquals(Decimal("0.0005"), tol3?.get("VWELX"))
    }

    @Test
    fun `inferTolerances - bug53 price`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            option "infer_tolerance_from_cost" "TRUE"

            2000-01-01 open Assets:Investments:VWELX
            2000-01-01 open Assets:Investments:Cash

            2006-01-02 * "Plan Contribution"
              Assets:Investments:VWELX 18.572 VWELX {30.96 USD}
              Assets:Investments:VWELX 18.572 VWELX @ 20.40 USD
              Assets:Investments:Cash
        """.trimIndent())

        // Book the entries to compute tolerances
        val (bookedEntries, bookingErrors) = Booking.book(result.entries, result.options)
        assertEquals(0, bookingErrors.size, "Expected no booking errors: ${bookingErrors.map { it.message }}")

        val txn = bookedEntries.filterIsInstance<Transaction>()[0]
        @Suppress("UNCHECKED_CAST")
        val tolerances = txn.meta["__tolerances__"] as? Map<String, Decimal>
        assertNotNull(tolerances)
        assertEquals(Decimal("0.02568"), tolerances!!["USD"])
        assertEquals(Decimal("0.0005"), tolerances["VWELX"])
    }

    @Test
    fun `quantizeWithTolerance - with wildcard default`() {
        val tolerances = mapOf("USD" to Decimal("0.01"), "*" to Decimal("0.000005"))
        assertEquals(
            Decimal("100.12"),
            quantizeWithTolerance(tolerances, "USD", Decimal("100.123123123"))
        )
        val cadResult = quantizeWithTolerance(tolerances, "CAD", Decimal("100.123123123"))
        println("DEBUG CAD result: $cadResult")
        assertEquals(
            Decimal("100.12312"),
            cadResult
        )

        val tolerancesNoDefault = mapOf("USD" to Decimal("0.01"))
        assertEquals(
            Decimal("100.12"),
            quantizeWithTolerance(tolerancesNoDefault, "USD", Decimal("100.123123123"))
        )
        assertEquals(
            Decimal("100.123123123"),
            quantizeWithTolerance(tolerancesNoDefault, "CAD", Decimal("100.123123123"))
        )
    }
}
