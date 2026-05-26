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
}
