package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Booking tests ported from Python beancount.parser.booking_full_test.
 * These verify that our Kotlin implementation produces the same results
 * as Python beancount for identical scenarios.
 */
class PythonCompatibilityBookingTest {

    private fun createOpen(account: String, booking: io.github.tonyzhye.beancount.core.Booking, date: LocalDate = LocalDate(2015, 1, 1)) =
        Open(
            meta = mapOf("filename" to "test.beancount", "lineno" to 1),
            date = date,
            account = account,
            currencies = listOf("HOOL"),
            booking = booking
        )

    private fun createBuy(account: String, units: String, cost: String, currency: String = "USD", date: LocalDate) =
        Transaction(
            meta = mapOf("filename" to "test.beancount", "lineno" to 1),
            date = date,
            flag = "*",
            narration = "Buy",
            postings = listOf(
                Posting(account, Amount(Decimal(units), "HOOL"), CostSpec(numberPer = Decimal(cost), currency = currency, date = date)),
                Posting("Assets:Cash", Amount(Decimal("-${Decimal(units).times(Decimal(cost)).toPlainString()}"), currency))
            )
        )

    private fun createSell(account: String, units: String, date: LocalDate, costSpec: CostSpec = CostSpec(currency = "USD")) =
        Transaction(
            meta = mapOf("filename" to "test.beancount", "lineno" to 1),
            date = date,
            flag = "*",
            narration = "Sell",
            postings = listOf(
                Posting(account, Amount(Decimal("-$units"), "HOOL"), costSpec),
                Posting("Assets:Cash", Amount(Decimal("1000"), "USD"))  // dummy cash amount
            )
        )

    // ---- Python: TestAmbiguousFIFO ----

    @Test
    fun `FIFO no match against any lots - sell 0`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.FIFO),
            createBuy(account, "5", "111.11", date = LocalDate(2015, 10, 2)),
            createBuy(account, "4", "100.00", date = LocalDate(2015, 10, 1)),
            createBuy(account, "6", "122.22", date = LocalDate(2015, 10, 3)),
            createSell(account, "0", date = LocalDate(2015, 2, 22))
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        // Check final inventory still has all 3 lots
        val finalTxn = result[4] as Transaction
        assertEquals(2, finalTxn.postings.size)
    }

    @Test
    fun `FIFO match against partial first lot - sell 2`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.FIFO),
            createBuy(account, "5", "111.11", date = LocalDate(2015, 10, 2)),
            createBuy(account, "4", "100.00", date = LocalDate(2015, 10, 1)),
            createBuy(account, "6", "122.22", date = LocalDate(2015, 10, 3)),
            createSell(account, "2", date = LocalDate(2015, 2, 22))
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        // FIFO: consume 2 from oldest lot (100.00, 2015-10-01)
        assertEquals(1, sellPostings.size)
        assertEquals(Decimal("-2"), sellPostings[0].units?.number)
        assertEquals(Decimal("100.00"), sellPostings[0].cost?.numberPer)
    }

    @Test
    fun `FIFO match against complete first lot - sell 4`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.FIFO),
            createBuy(account, "5", "111.11", date = LocalDate(2015, 10, 2)),
            createBuy(account, "4", "100.00", date = LocalDate(2015, 10, 1)),
            createBuy(account, "6", "122.22", date = LocalDate(2015, 10, 3)),
            createSell(account, "4", date = LocalDate(2015, 2, 22))
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        // FIFO: consume all 4 from oldest lot (100.00, 2015-10-01)
        assertEquals(1, sellPostings.size)
        assertEquals(Decimal("-4"), sellPostings[0].units?.number)
        assertEquals(Decimal("100.00"), sellPostings[0].cost?.numberPer)
    }

    @Test
    fun `FIFO partial match against first two lots - sell 7`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.FIFO),
            createBuy(account, "5", "111.11", date = LocalDate(2015, 10, 2)),
            createBuy(account, "4", "100.00", date = LocalDate(2015, 10, 1)),
            createBuy(account, "6", "122.22", date = LocalDate(2015, 10, 3)),
            createSell(account, "7", date = LocalDate(2015, 2, 22))
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        // FIFO: consume 4 from Lot 1 (100.00), then 3 from Lot 2 (111.11)
        assertEquals(2, sellPostings.size)
        assertEquals(Decimal("-4"), sellPostings[0].units?.number)
        assertEquals(Decimal("100.00"), sellPostings[0].cost?.numberPer)
        assertEquals(Decimal("-3"), sellPostings[1].units?.number)
        assertEquals(Decimal("111.11"), sellPostings[1].cost?.numberPer)
    }

    @Test
    fun `FIFO complete match against first two lots - sell 9`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.FIFO),
            createBuy(account, "5", "111.11", date = LocalDate(2015, 10, 2)),
            createBuy(account, "4", "100.00", date = LocalDate(2015, 10, 1)),
            createBuy(account, "6", "122.22", date = LocalDate(2015, 10, 3)),
            createSell(account, "9", date = LocalDate(2015, 2, 22))
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        // FIFO: consume 4 from Lot 1, then 5 from Lot 2
        assertEquals(2, sellPostings.size)
        assertEquals(Decimal("-4"), sellPostings[0].units?.number)
        assertEquals(Decimal("100.00"), sellPostings[0].cost?.numberPer)
        assertEquals(Decimal("-5"), sellPostings[1].units?.number)
        assertEquals(Decimal("111.11"), sellPostings[1].cost?.numberPer)
    }

    @Test
    fun `FIFO partial match against first three lots - sell 12`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.FIFO),
            createBuy(account, "5", "111.11", date = LocalDate(2015, 10, 2)),
            createBuy(account, "4", "100.00", date = LocalDate(2015, 10, 1)),
            createBuy(account, "6", "122.22", date = LocalDate(2015, 10, 3)),
            createSell(account, "12", date = LocalDate(2015, 2, 22))
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        // FIFO: consume 4 from Lot 1, 5 from Lot 2, 3 from Lot 3
        assertEquals(3, sellPostings.size)
        assertEquals(Decimal("-4"), sellPostings[0].units?.number)
        assertEquals(Decimal("100.00"), sellPostings[0].cost?.numberPer)
        assertEquals(Decimal("-5"), sellPostings[1].units?.number)
        assertEquals(Decimal("111.11"), sellPostings[1].cost?.numberPer)
        assertEquals(Decimal("-3"), sellPostings[2].units?.number)
        assertEquals(Decimal("122.22"), sellPostings[2].cost?.numberPer)
    }

    // ---- Python: TestAmbiguousLIFO ----

    @Test
    fun `LIFO match against partial first lot - sell 2`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.LIFO),
            createBuy(account, "5", "111.11", date = LocalDate(2015, 10, 2)),
            createBuy(account, "4", "100.00", date = LocalDate(2015, 10, 1)),
            createBuy(account, "6", "122.22", date = LocalDate(2015, 10, 3)),
            createSell(account, "2", date = LocalDate(2015, 2, 22))
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        // LIFO: consume 2 from newest lot (122.22, 2015-10-03)
        assertEquals(1, sellPostings.size)
        assertEquals(Decimal("-2"), sellPostings[0].units?.number)
        assertEquals(Decimal("122.22"), sellPostings[0].cost?.numberPer)
    }

    @Test
    fun `LIFO complete match against last lot - sell 6`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.LIFO),
            createBuy(account, "5", "111.11", date = LocalDate(2015, 10, 2)),
            createBuy(account, "4", "100.00", date = LocalDate(2015, 10, 1)),
            createBuy(account, "6", "122.22", date = LocalDate(2015, 10, 3)),
            createSell(account, "6", date = LocalDate(2015, 2, 22))
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        // LIFO: consume all 6 from newest lot (122.22)
        assertEquals(1, sellPostings.size)
        assertEquals(Decimal("-6"), sellPostings[0].units?.number)
        assertEquals(Decimal("122.22"), sellPostings[0].cost?.numberPer)
    }

    @Test
    fun `LIFO partial match against last two lots - sell 8`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.LIFO),
            createBuy(account, "5", "111.11", date = LocalDate(2015, 10, 2)),
            createBuy(account, "4", "100.00", date = LocalDate(2015, 10, 1)),
            createBuy(account, "6", "122.22", date = LocalDate(2015, 10, 3)),
            createSell(account, "8", date = LocalDate(2015, 2, 22))
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        // LIFO: consume 6 from Lot 3, then 2 from Lot 2
        assertEquals(2, sellPostings.size)
        assertEquals(Decimal("-6"), sellPostings[0].units?.number)
        assertEquals(Decimal("122.22"), sellPostings[0].cost?.numberPer)
        assertEquals(Decimal("-2"), sellPostings[1].units?.number)
        assertEquals(Decimal("111.11"), sellPostings[1].cost?.numberPer)
    }

    // ---- Python: TestReduceMultiple (HIFO) ----

    @Test
    fun `HIFO multiple reductions`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.HIFO),
            // Ante: 3 lots
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2016, 1, 1),
                flag = "*",
                narration = "Ante",
                postings = listOf(
                    Posting(account, Amount(Decimal("50"), "HOOL"), CostSpec(numberPer = Decimal("115.00"), currency = "USD", date = LocalDate(2016, 1, 15))),
                    Posting(account, Amount(Decimal("50"), "HOOL"), CostSpec(numberPer = Decimal("116.00"), currency = "USD", date = LocalDate(2016, 1, 16))),
                    Posting(account, Amount(Decimal("50"), "HOOL"), CostSpec(numberPer = Decimal("114.00"), currency = "USD", date = LocalDate(2016, 1, 17))),
                    Posting("Assets:Cash", Amount(Decimal("-17250.00"), "USD"))
                )
            ),
            // Apply: sell in 3 chunks
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2016, 5, 2),
                flag = "*",
                narration = "Sell",
                postings = listOf(
                    Posting(account, Amount(Decimal("-40"), "HOOL"), CostSpec(currency = "USD")),
                    Posting(account, Amount(Decimal("-35"), "HOOL"), CostSpec(currency = "USD")),
                    Posting(account, Amount(Decimal("-30"), "HOOL"), CostSpec(currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("10000"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val sellTxn = result[2] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        // HIFO: highest cost first
        // Lot 2: 50 @ 116.00 (highest)
        // Lot 1: 50 @ 115.00
        // Lot 3: 50 @ 114.00 (lowest)
        // Sell 40: all from Lot 2 (116.00)
        // Sell 35: 10 from Lot 2 (116.00) + 25 from Lot 1 (115.00)
        // Sell 30: 25 from Lot 1 (115.00) + 5 from Lot 3 (114.00)
        assertEquals(5, sellPostings.size)
        assertEquals(Decimal("-40"), sellPostings[0].units?.number)
        assertEquals(Decimal("116.00"), sellPostings[0].cost?.numberPer)
        assertEquals(Decimal("-10"), sellPostings[1].units?.number)
        assertEquals(Decimal("116.00"), sellPostings[1].cost?.numberPer)
        assertEquals(Decimal("-25"), sellPostings[2].units?.number)
        assertEquals(Decimal("115.00"), sellPostings[2].cost?.numberPer)
        assertEquals(Decimal("-25"), sellPostings[3].units?.number)
        assertEquals(Decimal("115.00"), sellPostings[3].cost?.numberPer)
        assertEquals(Decimal("-5"), sellPostings[4].units?.number)
        assertEquals(Decimal("114.00"), sellPostings[4].cost?.numberPer)
    }

    // ---- Python: TestStrict ----

    @Test
    fun `STRICT should allow exact total match of ambiguous lots`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.STRICT),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2016, 1, 1),
                flag = "*",
                narration = "Ante",
                postings = listOf(
                    Posting(account, Amount(Decimal("7"), "HOOL"), CostSpec(numberPer = Decimal("115.00"), currency = "USD", date = LocalDate(2016, 1, 15))),
                    Posting(account, Amount(Decimal("4"), "HOOL"), CostSpec(numberPer = Decimal("115.00"), currency = "USD", date = LocalDate(2016, 1, 16))),
                    Posting(account, Amount(Decimal("3"), "HOOL"), CostSpec(numberPer = Decimal("117.00"), currency = "USD", date = LocalDate(2016, 1, 15))),
                    Posting("Assets:Cash", Amount(Decimal("-2006"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2016, 5, 2),
                flag = "*",
                narration = "Sell",
                postings = listOf(
                    Posting(account, Amount(Decimal("-11"), "HOOL"), CostSpec(numberPer = Decimal("115.00"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("1000"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        // STRICT: 11 matches the total of 7+4=11 at 115.00, should succeed
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val sellTxn = result[2] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == account }

        assertEquals(2, sellPostings.size)
    }

    @Test
    fun `STRICT should report error when not enough lots`() {
        val account = "Assets:Account"
        val entries = listOf(
            createOpen(account, io.github.tonyzhye.beancount.core.Booking.STRICT),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2016, 1, 1),
                flag = "*",
                narration = "Ante",
                postings = listOf(
                    Posting(account, Amount(Decimal("5"), "HOOL"), CostSpec(numberPer = Decimal("115.00"), currency = "USD", date = LocalDate(2016, 1, 15))),
                    Posting("Assets:Cash", Amount(Decimal("-575"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2016, 5, 2),
                flag = "*",
                narration = "Sell",
                postings = listOf(
                    Posting(account, Amount(Decimal("-4"), "HOOL"), CostSpec(numberPer = Decimal("115.00"), currency = "USD")),
                    Posting(account, Amount(Decimal("-4"), "HOOL"), CostSpec(numberPer = Decimal("115.00"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("1000"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        // STRICT: only 5 available, trying to sell 8
        assertTrue(errors.isNotEmpty(), "Expected insufficient lots error")
        assertTrue(errors.any { it.message.contains("Insufficient") || it.message.contains("Not enough") })
    }
}
