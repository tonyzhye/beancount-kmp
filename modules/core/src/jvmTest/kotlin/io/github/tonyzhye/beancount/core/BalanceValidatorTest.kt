package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for balance assertion validation.
 */
class BalanceValidatorTest {

    private fun makeMeta(line: Int = 1) = mapOf(
        "filename" to "test.beancount",
        "lineno" to line
    )

    @Test
    fun `should pass when balance matches exactly`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Balance(makeMeta(3), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("100.00"), "USD"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(0, errors.size, "Should have no errors for matching balance")
        assertEquals(3, newEntries.size)
        val balanceEntry = newEntries[2] as Balance
        assertNull(balanceEntry.diffAmount, "diffAmount should be null for passing balance")
    }

    @Test
    fun `should fail when balance does not match`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Balance(makeMeta(3), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("200.00"), "USD"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(1, errors.size, "Should have one error for mismatched balance")
        assertTrue(errors[0].message.contains("Balance failed"))
        assertTrue(errors[0].message.contains("expected 200.00 USD"))
        assertTrue(errors[0].message.contains("accumulated 100.00 USD"))

        val balanceEntry = newEntries[2] as Balance
        assertNotNull(balanceEntry.diffAmount, "diffAmount should be set for failing balance")
        assertEquals(Decimal("-100.00"), balanceEntry.diffAmount!!.number)
        assertEquals("USD", balanceEntry.diffAmount!!.currency)
    }

    @Test
    fun `should use tolerance when provided`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Balance(
                makeMeta(3), LocalDate(2024, 1, 31), "Assets:Bank:Checking",
                Amount(Decimal("100.01"), "USD"),
                tolerance = Decimal("0.05")
            )
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(0, errors.size, "Should pass when within tolerance")
    }

    @Test
    fun `should fail when outside tolerance`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Balance(
                makeMeta(3), LocalDate(2024, 1, 31), "Assets:Bank:Checking",
                Amount(Decimal("100.10"), "USD"),
                tolerance = Decimal("0.05")
            )
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(1, errors.size, "Should fail when outside tolerance")
    }

    @Test
    fun `should infer tolerance from decimal precision`() {
        // For amount 100.005, plain string is "100.005" -> 3 fractional digits
        // Tolerance = 10^-3 = 0.001
        // Diff = 100.005 - 100.00 = 0.005 > 0.001 -> FAILS
        // So we need amount with 2 fractional digits, e.g., 100.01 -> tolerance = 0.01
        // Diff = 100.01 - 100.00 = 0.01 -> equal to tolerance, should pass
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Balance(makeMeta(3), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("100.01"), "USD"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(0, errors.size, "Should pass when within inferred tolerance of 0.01")
    }

    @Test
    fun `should fail when outside inferred tolerance`() {
        // Amount with 2 decimal places gets tolerance of 0.01 (10^-2)
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Balance(makeMeta(3), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("100.02"), "USD"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(1, errors.size, "Should fail when outside inferred tolerance of 0.01")
    }

    @Test
    fun `should fail for unknown account`() {
        val entries = listOf(
            Balance(makeMeta(1), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("100.00"), "USD"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("unknown account"))
    }

    @Test
    fun `should fail for invalid currency`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Balance(makeMeta(2), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("100.00"), "EUR"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Invalid currency"))
    }

    @Test
    fun `should include subaccount balances`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Open(makeMeta(2), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Open(makeMeta(3), LocalDate(2024, 1, 1), "Assets:Bank:Savings", listOf("USD")),
            Transaction(
                makeMeta(4), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Transaction(
                makeMeta(5), LocalDate(2024, 1, 20), "*",
                postings = listOf(
                    Posting("Assets:Bank:Savings", Amount(Decimal("50.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-50.00"), "USD"))
                )
            ),
            Balance(makeMeta(6), LocalDate(2024, 1, 31), "Assets:Bank", Amount(Decimal("150.00"), "USD"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(0, errors.size, "Should pass when parent account balance includes children")
    }

    @Test
    fun `should track multiple currencies separately`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD", "EUR")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Transaction(
                makeMeta(3), LocalDate(2024, 1, 20), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("50.00"), "EUR")),
                    Posting("Income:Salary", Amount(Decimal("-50.00"), "EUR"))
                )
            ),
            Balance(makeMeta(4), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
            Balance(makeMeta(5), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("50.00"), "EUR"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(0, errors.size, "Should track multiple currencies correctly")
    }

    @Test
    fun `should handle multiple transactions before balance`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 10), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Transaction(
                makeMeta(3), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("-30.00"), "USD")),
                    Posting("Expenses:Food", Amount(Decimal("30.00"), "USD"))
                )
            ),
            Transaction(
                makeMeta(4), LocalDate(2024, 1, 20), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("50.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-50.00"), "USD"))
                )
            ),
            Balance(makeMeta(5), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("120.00"), "USD"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(0, errors.size, "Should compute running balance correctly")
    }

    @Test
    fun `should show too much when actual exceeds expected`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Balance(makeMeta(3), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("50.00"), "USD"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("too much"), "Should indicate too much")
    }

    @Test
    fun `should show too little when actual is less than expected`() {
        val entries = listOf(
            Open(makeMeta(1), LocalDate(2024, 1, 1), "Assets:Bank:Checking", listOf("USD")),
            Transaction(
                makeMeta(2), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
                )
            ),
            Balance(makeMeta(3), LocalDate(2024, 1, 31), "Assets:Bank:Checking", Amount(Decimal("200.00"), "USD"))
        )

        val (newEntries, errors) = validateBalances(entries, Options())

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("too little"), "Should indicate too little")
    }
}
