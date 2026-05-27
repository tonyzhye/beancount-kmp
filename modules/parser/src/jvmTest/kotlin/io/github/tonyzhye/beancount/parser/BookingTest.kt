package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Booking logic.
 */
class BookingTest {

    @Test
    fun `should complete single missing posting`() {
        val transaction = Transaction(
            meta = mapOf("filename" to "example.beancount", "lineno" to 1),
            date = LocalDate(2023, 1, 15),
            flag = "*",
            narration = "Grocery shopping",
            postings = listOf(
                Posting("Expenses:Food", Amount(Decimal("50.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        val (completed, errors) = Booking.book(listOf(transaction), Options())

        assertEquals(0, errors.size, "Expected no errors")
        assertEquals(1, completed.size)

        val completedTransaction = completed[0] as Transaction
        assertEquals(2, completedTransaction.postings.size)

        val cashPosting = completedTransaction.postings[1]
        assertNotNull(cashPosting.units)
        assertEquals(Decimal("-50.00"), cashPosting.units?.number)
        assertEquals("USD", cashPosting.units?.currency)
    }

    @Test
    fun `should not modify complete transaction`() {
        val transaction = Transaction(
            meta = mapOf("filename" to "example.beancount", "lineno" to 1),
            date = LocalDate(2023, 1, 15),
            flag = "*",
            narration = "Grocery shopping",
            postings = listOf(
                Posting("Expenses:Food", Amount(Decimal("50.00"), "USD")),
                Posting("Assets:Cash", Amount(Decimal("-50.00"), "USD"))
            )
        )

        val (completed, errors) = Booking.book(listOf(transaction), Options())

        assertEquals(0, errors.size)
        assertEquals(1, completed.size)

        val completedTransaction = completed[0] as Transaction
        assertEquals(Decimal("-50.00"), completedTransaction.postings[1].units?.number)
    }

    @Test
    fun `should report error for multiple missing postings`() {
        val transaction = Transaction(
            meta = mapOf("filename" to "example.beancount", "lineno" to 1),
            date = LocalDate(2023, 1, 15),
            flag = "*",
            narration = "Grocery shopping",
            postings = listOf(
                Posting("Expenses:Food", null),
                Posting("Assets:Cash", null)
            )
        )

        val (completed, errors) = Booking.book(listOf(transaction), Options())

        assertEquals(1, errors.size, "Expected error for multiple missing postings")
        assertTrue(errors[0].message.contains("2 postings with missing amounts"))
    }

    @Test
    fun `should handle transaction with payee and missing posting`() {
        val transaction = Transaction(
            meta = mapOf("filename" to "example.beancount", "lineno" to 1),
            date = LocalDate(2023, 1, 31),
            flag = "*",
            payee = "Employer",
            narration = "Salary",
            postings = listOf(
                Posting("Assets:Cash", Amount(Decimal("1000.00"), "USD")),
                Posting("Income:Salary", null)
            )
        )

        val (completed, errors) = Booking.book(listOf(transaction), Options())

        assertEquals(0, errors.size)

        val completedTransaction = completed[0] as Transaction
        val salaryPosting = completedTransaction.postings[1]
        assertNotNull(salaryPosting.units)
        assertEquals(Decimal("-1000.00"), salaryPosting.units?.number)
    }

    @Test
    fun `should handle three postings with one missing`() {
        val transaction = Transaction(
            meta = mapOf("filename" to "example.beancount", "lineno" to 1),
            date = LocalDate(2023, 1, 15),
            flag = "*",
            narration = "Split expense",
            postings = listOf(
                Posting("Expenses:Food", Amount(Decimal("30.00"), "USD")),
                Posting("Expenses:Drinks", Amount(Decimal("20.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        val (completed, errors) = Booking.book(listOf(transaction), Options())

        assertEquals(0, errors.size)

        val completedTransaction = completed[0] as Transaction
        val cashPosting = completedTransaction.postings[2]
        assertNotNull(cashPosting.units)
        assertEquals(Decimal("-50.00"), cashPosting.units?.number)
    }

    @Test
    fun `should leave non-transaction entries unchanged`() {
        val entries = listOf(
            Open(mapOf("filename" to "example.beancount", "lineno" to 1), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Balance(mapOf("filename" to "example.beancount", "lineno" to 2), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("100"), "USD"))
        )

        val (completed, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)
        assertEquals(2, completed.size)
        assertTrue(completed[0] is Open)
        assertTrue(completed[1] is Balance)
    }

    // ---- STRICT Booking Method Tests ----

    @Test
    fun `STRICT should match single lot exactly`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.STRICT
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 1, 15),
                flag = "*",
                narration = "Buy AAPL",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 3),
                date = LocalDate(2023, 2, 1),
                flag = "*",
                narration = "Sell AAPL",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("-5"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("550"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size, "Expected no errors but got: ${errors.map { it.message }}")

        // Check that the sell transaction was processed
        val sellTxn = result[2] as Transaction
        assertEquals(2, sellTxn.postings.size)

        val sellPosting = sellTxn.postings[0]
        assertEquals(Decimal("-5"), sellPosting.units?.number)
        assertNotNull(sellPosting.cost)
        assertEquals(Decimal("100"), sellPosting.cost?.numberPer)
        assertEquals("USD", sellPosting.cost?.currency)
    }

    @Test
    fun `STRICT should report error on ambiguous match`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.STRICT
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 1, 15),
                flag = "*",
                narration = "Buy Lot 1",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 3),
                date = LocalDate(2023, 2, 1),
                flag = "*",
                narration = "Buy Lot 2",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("5"), "AAPL"), CostSpec(numberPer = Decimal("120"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-600"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 4),
                date = LocalDate(2023, 3, 1),
                flag = "*",
                narration = "Sell AAPL ambiguous",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("-3"), "AAPL"), CostSpec(currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("360"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(1, errors.size, "Expected ambiguous match error")
        assertTrue(errors[0].message.contains("Ambiguous matches"), "Error should mention ambiguous matches: ${errors[0].message}")
    }

    @Test
    fun `STRICT should handle insufficient lots`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.STRICT
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 1, 15),
                flag = "*",
                narration = "Buy AAPL",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("5"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-500"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 3),
                date = LocalDate(2023, 2, 1),
                flag = "*",
                narration = "Sell too many",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("-10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("1100"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(1, errors.size, "Expected insufficient lots error")
        assertTrue(errors[0].message.contains("Insufficient"), "Error should mention insufficient: ${errors[0].message}")
    }

    // ---- NONE Booking Method Tests ----

    @Test
    fun `NONE should allow negative positions without matching`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Options",
                currencies = listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.NONE
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 1, 15),
                flag = "*",
                narration = "Short AAPL",
                postings = listOf(
                    Posting("Assets:Options", Amount(Decimal("-5"), "AAPL"), CostSpec(currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("500"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size, "Expected no errors with NONE booking: ${errors.map { it.message }}")

        val txn = result[1] as Transaction
        assertEquals(Decimal("-5"), txn.postings[0].units?.number)
    }

    // ---- CostSpec Interpolation Tests ----

    @Test
    fun `should interpolate missing cost date from transaction date`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL")
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 6, 15),
                flag = "*",
                narration = "Buy AAPL",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val txn = result[1] as Transaction
        val cost = txn.postings[0].cost
        assertNotNull(cost)
        assertEquals(LocalDate(2023, 6, 15), cost?.date)
    }

    @Test
    fun `should not modify explicitly specified cost date`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL")
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 6, 15),
                flag = "*",
                narration = "Buy AAPL",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD", date = LocalDate(2023, 1, 10))),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size)

        val txn = result[1] as Transaction
        val cost = txn.postings[0].cost
        assertNotNull(cost)
        assertEquals(LocalDate(2023, 1, 10), cost?.date)
    }
}
