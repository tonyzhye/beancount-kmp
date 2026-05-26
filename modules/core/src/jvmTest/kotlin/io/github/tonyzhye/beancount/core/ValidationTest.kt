package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Validation functions.
 */
class ValidationTest {

    private fun createMeta(filename: String = "example.beancount", lineno: Int = 1) = mapOf(
        "filename" to filename,
        "lineno" to lineno
    )

    @Test
    fun `validateOpenClose should detect duplicate opens`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Open(createMeta(lineno = 2), LocalDate(2023, 1, 2), "Assets:Cash", listOf("EUR"))
        )
        
        val errors = validateOpenClose(entries, Options())
        
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Duplicate open"))
    }

    @Test
    fun `validateOpenClose should detect close before open`() {
        val entries = listOf(
            Close(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash")
        )
        
        val errors = validateOpenClose(entries, Options())
        
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Unopened account"))
    }

    @Test
    fun `validateActiveAccounts should detect unknown account`() {
        val entries = listOf(
            Transaction(
                createMeta(), LocalDate(2023, 1, 1), "*",
                postings = listOf(
                    Posting("Assets:Unknown", Amount(Decimal("100"), "USD"))
                )
            )
        )
        
        val errors = validateActiveAccounts(entries, Options())
        
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("unknown account"))
    }

    @Test
    fun `validateCurrencyConstraints should detect invalid currency`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Transaction(
                createMeta(lineno = 2), LocalDate(2023, 1, 2), "*",
                postings = listOf(
                    Posting("Assets:Cash", Amount(Decimal("100"), "EUR"))
                )
            )
        )
        
        val errors = validateCurrencyConstraints(entries, Options())
        
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Invalid currency"))
    }

    @Test
    fun `validateDuplicateBalances should detect duplicate balances`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Balance(createMeta(lineno = 2), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("100"), "USD")),
            Balance(createMeta(lineno = 3), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("200"), "USD"))
        )
        
        val errors = validateDuplicateBalances(entries, Options())
        
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Duplicate balance"))
    }

    @Test
    fun `validateDuplicateCommodities should detect duplicates`() {
        val entries = listOf(
            Commodity(createMeta(), LocalDate(2023, 1, 1), "USD"),
            Commodity(createMeta(lineno = 2), LocalDate(2023, 1, 2), "USD")
        )
        
        val errors = validateDuplicateCommodities(entries, Options())
        
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Duplicate commodity"))
    }

    @Test
    fun `validateCheckTransactionBalances should detect imbalance`() {
        val entries = listOf(
            Transaction(
                createMeta(), LocalDate(2023, 1, 1), "*",
                postings = listOf(
                    Posting("Expenses:Food", Amount(Decimal("100"), "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-50"), "USD"))
                )
            )
        )
        
        val errors = validateCheckTransactionBalances(entries, Options())
        
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("does not balance"))
    }

    @Test
    fun `validateCheckTransactionBalances should pass for balanced transaction`() {
        val entries = listOf(
            Transaction(
                createMeta(), LocalDate(2023, 1, 1), "*",
                postings = listOf(
                    Posting("Expenses:Food", Amount(Decimal("100"), "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-100"), "USD"))
                )
            )
        )
        
        val errors = validateCheckTransactionBalances(entries, Options())
        
        assertEquals(0, errors.size)
    }

    @Test
    fun `validateDataTypes should detect missing metadata`() {
        val entries = listOf(
            Open(mapOf(), LocalDate(2023, 1, 1), "Assets:Cash")
        )
        
        val errors = validateDataTypes(entries, Options())
        
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `validateDataTypes should pass for valid entry`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash")
        )
        
        val errors = validateDataTypes(entries, Options())
        
        assertEquals(0, errors.size)
    }
}
