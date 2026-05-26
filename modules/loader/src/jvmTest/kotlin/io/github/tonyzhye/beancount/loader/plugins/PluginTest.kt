package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.loader.runTransformations
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for built-in plugins.
 */
class PluginTest {

    private fun createMeta(filename: String = "test.bean", lineno: Int = 1) = mapOf(
        "filename" to filename,
        "lineno" to lineno
    )

    @Test
    fun `PadPlugin should generate pad transaction`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Open(createMeta(), LocalDate(2023, 1, 1), "Equity:OpeningBalances", listOf("USD")),
            Pad(createMeta(), LocalDate(2023, 1, 5), "Assets:Cash", "Equity:OpeningBalances"),
            Transaction(
                createMeta(), LocalDate(2023, 1, 10), "*",
                narration = "Expense",
                postings = listOf(
                    Posting("Expenses:Food", Amount(Decimal("50"), "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-50"), "USD"))
                )
            ),
            Balance(createMeta(), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("100"), "USD"))
        )

        val options = Options()
        val (result, errors) = PadPlugin.transform(entries, options)

        // Should have no errors
        assertEquals(0, errors.size, "Expected no errors but got: $errors")

        // Should have added one transaction (the pad)
        assertEquals(6, result.size, "Expected 6 entries but got ${result.size}")

        // Check that a pad transaction was added
        val padTransactions = result.filterIsInstance<Transaction>()
            .filter { it.flag == "P" }
        assertEquals(1, padTransactions.size, "Expected one pad transaction")

        val padTx = padTransactions.first()
        assertEquals("Padding for balance assertion", padTx.narration)

        // The pad should bring the balance from -50 to 100, so +150
        val cashPosting = padTx.postings.find { it.account == "Assets:Cash" }
        assertEquals(Decimal("150"), cashPosting?.units?.number)
    }

    @Test
    fun `PadPlugin should not generate pad if balance already matches`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Open(createMeta(), LocalDate(2023, 1, 1), "Equity:OpeningBalances", listOf("USD")),
            Pad(createMeta(), LocalDate(2023, 1, 5), "Assets:Cash", "Equity:OpeningBalances"),
            Transaction(
                createMeta(), LocalDate(2023, 1, 10), "*",
                narration = "Initial deposit",
                postings = listOf(
                    Posting("Assets:Cash", Amount(Decimal("100"), "USD")),
                    Posting("Equity:OpeningBalances", Amount(Decimal("-100"), "USD"))
                )
            ),
            Balance(createMeta(), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("100"), "USD"))
        )

        val options = Options()
        val (result, errors) = PadPlugin.transform(entries, options)

        assertEquals(0, errors.size)
        // Should not add any pad transaction since balance is already 100
        assertEquals(5, result.size)
    }

    @Test
    fun `PadPlugin should report error if no balance assertion found`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Pad(createMeta(), LocalDate(2023, 1, 5), "Assets:Cash", "Equity:OpeningBalances")
        )

        val options = Options()
        val (result, errors) = PadPlugin.transform(entries, options)

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("No balance assertion found"))
    }

    @Test
    fun `BalancePlugin should pass when balance matches`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Transaction(
                createMeta(), LocalDate(2023, 1, 10), "*",
                narration = "Deposit",
                postings = listOf(
                    Posting("Assets:Cash", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            ),
            Balance(createMeta(), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("100"), "USD"))
        )

        val options = Options()
        val (result, errors) = BalancePlugin.transform(entries, options)

        assertEquals(0, errors.size)
        assertEquals(3, result.size)
    }

    @Test
    fun `BalancePlugin should report error when balance does not match`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Transaction(
                createMeta(), LocalDate(2023, 1, 10), "*",
                narration = "Deposit",
                postings = listOf(
                    Posting("Assets:Cash", Amount(Decimal("50"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-50"), "USD"))
                )
            ),
            Balance(createMeta(), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("100"), "USD"))
        )

        val options = Options()
        val (result, errors) = BalancePlugin.transform(entries, options)

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Balance failed"))
        assertTrue(errors[0].message.contains("expected 100"))
        assertTrue(errors[0].message.contains("but got 50"))
    }

    @Test
    fun `BalancePlugin should use tolerance`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Transaction(
                createMeta(), LocalDate(2023, 1, 10), "*",
                narration = "Deposit",
                postings = listOf(
                    Posting("Assets:Cash", Amount(Decimal("100.003"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100.003"), "USD"))
                )
            ),
            Balance(
                createMeta(), LocalDate(2023, 1, 15), "Assets:Cash",
                Amount(Decimal("100"), "USD"),
                tolerance = Decimal("0.01")
            )
        )

        val options = Options()
        val (result, errors) = BalancePlugin.transform(entries, options)

        // 0.003 difference within 0.01 tolerance should pass
        assertEquals(0, errors.size)
    }

    @Test
    fun `DocumentsPlugin should pass for valid document`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Document(createMeta(), LocalDate(2023, 1, 15), "Assets:Cash", "/path/to/statement.pdf")
        )

        val options = Options()
        val (result, errors) = DocumentsPlugin.transform(entries, options)

        assertEquals(0, errors.size)
        assertEquals(2, result.size)
    }

    @Test
    fun `DocumentsPlugin should report error for unopened account`() {
        val entries = listOf(
            Document(createMeta(), LocalDate(2023, 1, 15), "Assets:Unknown", "/path/to/statement.pdf")
        )

        val options = Options()
        val (result, errors) = DocumentsPlugin.transform(entries, options)

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("unopened account"))
    }

    @Test
    fun `DocumentsPlugin should report error for empty filename`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Document(createMeta(), LocalDate(2023, 1, 15), "Assets:Cash", "")
        )

        val options = Options()
        val (result, errors) = DocumentsPlugin.transform(entries, options)

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("empty filename"))
    }

    @Test
    fun `runTransformations should execute plugin chain`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Open(createMeta(), LocalDate(2023, 1, 1), "Equity:OpeningBalances", listOf("USD")),
            Pad(createMeta(), LocalDate(2023, 1, 5), "Assets:Cash", "Equity:OpeningBalances"),
            Transaction(
                createMeta(), LocalDate(2023, 1, 10), "*",
                narration = "Expense",
                postings = listOf(
                    Posting("Expenses:Food", Amount(Decimal("50"), "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-50"), "USD"))
                )
            ),
            Balance(createMeta(), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("100"), "USD"))
        )

        val options = Options(pluginProcessingMode = PluginProcessingMode.DEFAULT)
        val (result, errors) = runTransformations(entries, options)

        // Should have no errors (pad generates +150, balance is satisfied)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")

        // Should have original entries + pad transaction
        assertEquals(6, result.size)
    }

    @Test
    fun `runTransformations should respect RAW mode`() {
        val entries = listOf(
            Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD")),
            Pad(createMeta(), LocalDate(2023, 1, 5), "Assets:Cash", "Equity:OpeningBalances"),
            Transaction(
                createMeta(), LocalDate(2023, 1, 10), "*",
                narration = "Expense",
                postings = listOf(
                    Posting("Expenses:Food", Amount(Decimal("50"), "USD")),
                    Posting("Assets:Cash", Amount(Decimal("-50"), "USD"))
                )
            ),
            Balance(createMeta(), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("100"), "USD"))
        )

        // RAW mode - no built-in plugins run
        val options = Options(pluginProcessingMode = PluginProcessingMode.RAW)
        val (result, errors) = runTransformations(entries, options)

        // In RAW mode without user plugins, no transformations run
        assertEquals(0, errors.size)
        assertEquals(4, result.size) // No pad transaction generated
    }
}
