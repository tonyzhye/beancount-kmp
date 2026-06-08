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

        assertEquals(0, errors.size, "Expected no errors but got: ${errors.map { it.message }}")
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
                    Posting("Assets:Cash", Amount(Decimal("500"), "USD"))
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
                    Posting("Assets:Cash", Amount(Decimal("0"), "USD"))
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
                    Posting("Assets:Cash", Amount(Decimal("500"), "USD"))
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
                    Posting("Assets:Options", Amount(Decimal("-5"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
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

    // ---- FIFO Booking Method Tests ----

    @Test
    fun `FIFO should consume oldest lots first`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.FIFO
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 1, 15),
                flag = "*",
                narration = "Buy Lot 1",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD", date = LocalDate(2023, 1, 15))),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 3),
                date = LocalDate(2023, 3, 1),
                flag = "*",
                narration = "Buy Lot 2",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("5"), "AAPL"), CostSpec(numberPer = Decimal("120"), currency = "USD", date = LocalDate(2023, 3, 1))),
                    Posting("Assets:Cash", Amount(Decimal("-600"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 4),
                date = LocalDate(2023, 6, 1),
                flag = "*",
                narration = "Buy Lot 3",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("3"), "AAPL"), CostSpec(numberPer = Decimal("110"), currency = "USD", date = LocalDate(2023, 6, 1))),
                    Posting("Assets:Cash", Amount(Decimal("-330"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 5),
                date = LocalDate(2023, 12, 1),
                flag = "*",
                narration = "Sell AAPL",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("-12"), "AAPL"), CostSpec(currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("1240"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == "Assets:Invest" }

        // FIFO: consume 10 from Lot 1 (oldest), then 2 from Lot 2
        assertEquals(2, sellPostings.size)
        assertEquals(Decimal("-10"), sellPostings[0].units?.number)
        assertEquals(Decimal("100"), sellPostings[0].cost?.numberPer)
        assertEquals(Decimal("-2"), sellPostings[1].units?.number)
        assertEquals(Decimal("120"), sellPostings[1].cost?.numberPer)
    }

    // ---- LIFO Booking Method Tests ----

    @Test
    fun `LIFO should consume newest lots first`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.LIFO
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 1, 15),
                flag = "*",
                narration = "Buy Lot 1",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD", date = LocalDate(2023, 1, 15))),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 3),
                date = LocalDate(2023, 3, 1),
                flag = "*",
                narration = "Buy Lot 2",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("5"), "AAPL"), CostSpec(numberPer = Decimal("120"), currency = "USD", date = LocalDate(2023, 3, 1))),
                    Posting("Assets:Cash", Amount(Decimal("-600"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 4),
                date = LocalDate(2023, 6, 1),
                flag = "*",
                narration = "Buy Lot 3",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("3"), "AAPL"), CostSpec(numberPer = Decimal("110"), currency = "USD", date = LocalDate(2023, 6, 1))),
                    Posting("Assets:Cash", Amount(Decimal("-330"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 5),
                date = LocalDate(2023, 12, 1),
                flag = "*",
                narration = "Sell AAPL",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("-12"), "AAPL"), CostSpec(currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("1330"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == "Assets:Invest" }

        // LIFO: consume 3 from Lot 3 (newest), then 5 from Lot 2, then 4 from Lot 1
        assertEquals(3, sellPostings.size)
        assertEquals(Decimal("-3"), sellPostings[0].units?.number)
        assertEquals(Decimal("110"), sellPostings[0].cost?.numberPer)
        assertEquals(Decimal("-5"), sellPostings[1].units?.number)
        assertEquals(Decimal("120"), sellPostings[1].cost?.numberPer)
        assertEquals(Decimal("-4"), sellPostings[2].units?.number)
        assertEquals(Decimal("100"), sellPostings[2].cost?.numberPer)
    }

    // ---- HIFO Booking Method Tests ----

    @Test
    fun `HIFO should consume highest cost lots first`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.HIFO
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 1, 15),
                flag = "*",
                narration = "Buy Lot 1",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD", date = LocalDate(2023, 1, 15))),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 3),
                date = LocalDate(2023, 3, 1),
                flag = "*",
                narration = "Buy Lot 2",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("5"), "AAPL"), CostSpec(numberPer = Decimal("120"), currency = "USD", date = LocalDate(2023, 3, 1))),
                    Posting("Assets:Cash", Amount(Decimal("-600"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 4),
                date = LocalDate(2023, 6, 1),
                flag = "*",
                narration = "Buy Lot 3",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("3"), "AAPL"), CostSpec(numberPer = Decimal("110"), currency = "USD", date = LocalDate(2023, 6, 1))),
                    Posting("Assets:Cash", Amount(Decimal("-330"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 5),
                date = LocalDate(2023, 12, 1),
                flag = "*",
                narration = "Sell AAPL",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("-12"), "AAPL"), CostSpec(currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("1330"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val sellTxn = result[4] as Transaction
        val sellPostings = sellTxn.postings.filter { it.account == "Assets:Invest" }

        // HIFO: consume 5 from Lot 2 (highest cost $120), then 3 from Lot 3 ($110), then 4 from Lot 1 ($100)
        assertEquals(3, sellPostings.size)
        assertEquals(Decimal("-5"), sellPostings[0].units?.number)
        assertEquals(Decimal("120"), sellPostings[0].cost?.numberPer)
        assertEquals(Decimal("-3"), sellPostings[1].units?.number)
        assertEquals(Decimal("110"), sellPostings[1].cost?.numberPer)
        assertEquals(Decimal("-4"), sellPostings[2].units?.number)
        assertEquals(Decimal("100"), sellPostings[2].cost?.numberPer)
    }

    // ---- STRICT_WITH_SIZE Booking Method Tests ----

    @Test
    fun `STRICT_WITH_SIZE should auto resolve by exact lot size match`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.STRICT_WITH_SIZE
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 1, 15),
                flag = "*",
                narration = "Buy Lot 1",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD", date = LocalDate(2023, 1, 15))),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 3),
                date = LocalDate(2023, 3, 1),
                flag = "*",
                narration = "Buy Lot 2",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("5"), "AAPL"), CostSpec(numberPer = Decimal("120"), currency = "USD", date = LocalDate(2023, 3, 1))),
                    Posting("Assets:Cash", Amount(Decimal("-600"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 4),
                date = LocalDate(2023, 12, 1),
                flag = "*",
                narration = "Sell AAPL",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("-5"), "AAPL"), CostSpec(currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("600"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        val sellTxn = result[3] as Transaction
        val sellPosting = sellTxn.postings[0]

        // STRICT_WITH_SIZE: Lot 2 has exactly 5 shares, select it
        assertEquals(Decimal("-5"), sellPosting.units?.number)
        assertEquals(Decimal("120"), sellPosting.cost?.numberPer)
    }

    @Test
    fun `STRICT_WITH_SIZE should fallback to STRICT when no exact size match`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Invest",
                currencies = listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.STRICT_WITH_SIZE
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2023, 1, 15),
                flag = "*",
                narration = "Buy Lot 1",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD", date = LocalDate(2023, 1, 15))),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 3),
                date = LocalDate(2023, 3, 1),
                flag = "*",
                narration = "Buy Lot 2",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("5"), "AAPL"), CostSpec(numberPer = Decimal("120"), currency = "USD", date = LocalDate(2023, 3, 1))),
                    Posting("Assets:Cash", Amount(Decimal("-600"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 4),
                date = LocalDate(2023, 12, 1),
                flag = "*",
                narration = "Sell AAPL ambiguous",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("-3"), "AAPL"), CostSpec(currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("0"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        // No lot has exactly 3 shares, so should report ambiguous match error
        assertEquals(1, errors.size, "Expected ambiguous match error")
        assertTrue(errors[0].message.contains("Ambiguous matches"), "Error should mention ambiguous matches: ${errors[0].message}")
    }

    // ---- AVERAGE Booking Method Tests ----

    @Test
    fun `AVERAGE booking method should return error`() {
        val account = "Assets:Invest"
        val entries = listOf(
            Open(
                mapOf("filename" to "test.beancount", "lineno" to 1),
                LocalDate(2015, 1, 1), account, listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.AVERAGE
            ),
            Transaction(
                mapOf("filename" to "test.beancount", "lineno" to 2),
                LocalDate(2015, 1, 1), "*",
                postings = listOf(
                    Posting(account, Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                mapOf("filename" to "test.beancount", "lineno" to 3),
                LocalDate(2015, 2, 1), "*",
                postings = listOf(
                    Posting(account, Amount(Decimal("-5"), "AAPL"), CostSpec(currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("0"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        // AVERAGE should return an error, same as Python
        assertTrue(errors.isNotEmpty(), "AVERAGE should produce errors")
        assertTrue(errors.any { it.message.contains("AVERAGE method is not supported") },
            "Error should mention 'AVERAGE method is not supported'")
    }

    // ---- Self-Reduction Detection Tests ----

    @Test
    fun `should detect self-reduction in transaction`() {
        val account = "Assets:Invest"
        val entries = listOf(
            Open(
                mapOf("filename" to "test.beancount", "lineno" to 1),
                LocalDate(2015, 1, 1), account, listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.STRICT
            ),
            Transaction(
                mapOf("filename" to "test.beancount", "lineno" to 2),
                LocalDate(2015, 1, 1), "*",
                postings = listOf(
                    Posting(account, Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting(account, Amount(Decimal("-5"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-500"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        // Should detect self-reduction
        assertTrue(errors.any { it.message.contains("Self-reduction") },
            "Should detect self-reduction: ${errors.map { it.message }}")
    }

    @Test
    fun `local balance isolation should prevent double matching`() {
        val account = "Assets:Invest"
        val entries = listOf(
            Open(
                mapOf("filename" to "test.beancount", "lineno" to 1),
                LocalDate(2015, 1, 1), account, listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.STRICT
            ),
            // First transaction: buy 10 AAPL
            Transaction(
                mapOf("filename" to "test.beancount", "lineno" to 2),
                LocalDate(2015, 1, 1), "*",
                postings = listOf(
                    Posting(account, Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            // Second transaction: sell 6 AAPL twice in same transaction (total 12)
            // First sell reduces local balance to 4, second sell should fail
            Transaction(
                mapOf("filename" to "test.beancount", "lineno" to 3),
                LocalDate(2015, 2, 1), "*",
                postings = listOf(
                    Posting(account, Amount(Decimal("-6"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting(account, Amount(Decimal("-6"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("1200"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        // Second transaction should report insufficient lots for second sell
        // (first sell takes 6, leaving 4; second sell wants 6 but only 4 available)
        val txn = result[2] as Transaction
        assertEquals(3, txn.postings.size, "Should have 3 postings")

        // First sell should succeed
        assertEquals(Decimal("-6"), txn.postings[0].units?.number)

        // Second sell should report insufficient lots
        assertTrue(errors.any { it.message.contains("Insufficient lots") },
            "Should report insufficient lots for second sell: ${errors.map { it.message }}")
    }

    @Test
    fun `local balance isolation should not affect other transactions`() {
        val account = "Assets:Invest"
        val entries = listOf(
            Open(
                mapOf("filename" to "test.beancount", "lineno" to 1),
                LocalDate(2015, 1, 1), account, listOf("AAPL"),
                booking = io.github.tonyzhye.beancount.core.Booking.STRICT
            ),
            // Transaction 1: buy 10 AAPL
            Transaction(
                mapOf("filename" to "test.beancount", "lineno" to 2),
                LocalDate(2015, 1, 1), "*",
                postings = listOf(
                    Posting(account, Amount(Decimal("10"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            // Transaction 2: sell 5 AAPL
            Transaction(
                mapOf("filename" to "test.beancount", "lineno" to 3),
                LocalDate(2015, 2, 1), "*",
                postings = listOf(
                    Posting(account, Amount(Decimal("-5"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("500"), "USD"))
                )
            ),
            // Transaction 3: sell 3 AAPL
            Transaction(
                mapOf("filename" to "test.beancount", "lineno" to 4),
                LocalDate(2015, 3, 1), "*",
                postings = listOf(
                    Posting(account, Amount(Decimal("-3"), "AAPL"), CostSpec(numberPer = Decimal("100"), currency = "USD")),
                    Posting("Assets:Cash", Amount(Decimal("300"), "USD"))
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        // All transactions should succeed
        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")

        // Final inventory should have 2 AAPL remaining
        val finalTxn = result[3] as Transaction
        assertEquals(Decimal("-3"), finalTxn.postings[0].units?.number)
    }
}
