package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BeancountDoctorTest {

    @Test
    fun `should find missing open directives`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "Assets:Cash", units = Amount(Decimal("100"), "USD")),
                    Posting(account = "Income:Salary", units = Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val doctor = BeancountDoctor()
        val missing = doctor.missingOpen(entries)

        assertEquals(2, missing.size)
        assertTrue(missing.any { it.account == "Assets:Cash" })
        assertTrue(missing.any { it.account == "Income:Salary" })
    }

    @Test
    fun `should not report opened accounts`() {
        val entries = listOf(
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Cash",
                currencies = listOf("USD")
            ),
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 2),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "Assets:Cash", units = Amount(Decimal("100"), "USD"))
                )
            )
        )

        val doctor = BeancountDoctor()
        val missing = doctor.missingOpen(entries)

        assertTrue(missing.isEmpty(), "Should not report opened accounts")
    }

    @Test
    fun `should display context for entry`() {
        val entries = listOf(
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 42),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Target entry"
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 50),
                date = LocalDate(2024, 1, 2),
                flag = "*",
                narration = "Next entry"
            )
        )

        val doctor = BeancountDoctor()
        val context = doctor.context(entries, "test.beancount", 42, 1)

        assertTrue(context.contains("Target entry"), "Context should contain target entry")
        assertTrue(context.contains("test.beancount:42"), "Context should show location")
    }

    @Test
    fun `should handle missing entry in context`() {
        val entries = emptyList<Directive>()

        val doctor = BeancountDoctor()
        val context = doctor.context(entries, "test.beancount", 999, 3)

        assertTrue(context.contains("No entry found"), "Should report missing entry")
    }

    @Test
    fun `should display formatting context`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "Assets:Cash", units = Amount(Decimal("100.00"), "USD")),
                    Posting(account = "Income:Salary", units = Amount(Decimal("-100.00"), "USD"))
                )
            )
        )

        val doctor = BeancountDoctor()
        val displayContext = doctor.displayContext(entries)

        assertTrue(displayContext.contains("USD"), "Should mention USD currency")
        assertTrue(displayContext.contains("max_precision"), "Should show max precision")
    }

    @Test
    fun `should handle empty ledger in display context`() {
        val entries = emptyList<Directive>()

        val doctor = BeancountDoctor()
        val displayContext = doctor.displayContext(entries)

        assertTrue(displayContext.contains("No amounts found"), "Should report empty ledger")
    }

    @Test
    fun `should perform roundtrip`() {
        val entries = listOf(
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Cash",
                currencies = listOf("USD")
            ),
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 2),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "Assets:Cash", units = Amount(Decimal("100"), "USD")),
                    Posting(account = "Income:Salary", units = Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val doctor = BeancountDoctor()
        val roundtrip = doctor.roundtrip(entries)

        assertEquals(2, roundtrip.entryCount)
        assertTrue(roundtrip.formattedOutput.contains("Assets:Cash"), "Should contain account name")
        assertTrue(roundtrip.formattedOutput.contains("2024-01-02"), "Should contain date")
    }

    @Test
    fun `should list options`() {
        val options = Options(
            filename = "test.beancount",
            title = "Test Ledger",
            operatingCurrencies = listOf("USD", "EUR")
        )

        val doctor = BeancountDoctor()
        val output = doctor.listOptions(options)

        assertTrue(output.contains("Test Ledger"), "Should show title")
        assertTrue(output.contains("USD"), "Should show USD")
        assertTrue(output.contains("EUR"), "Should show EUR")
    }

    @Test
    fun `should find missing open for balance directive`() {
        val entries = listOf(
            Balance(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Cash",
                amount = Amount(Decimal("100"), "USD")
            )
        )

        val doctor = BeancountDoctor()
        val missing = doctor.missingOpen(entries)

        assertEquals(1, missing.size)
        assertEquals("Assets:Cash", missing[0].account)
    }

    @Test
    fun `should find missing open for pad directive`() {
        val entries = listOf(
            Pad(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Cash",
                sourceAccount = "Equity:Opening-Balances"
            )
        )

        val doctor = BeancountDoctor()
        val missing = doctor.missingOpen(entries)

        assertEquals(2, missing.size)
        assertTrue(missing.any { it.account == "Assets:Cash" })
        assertTrue(missing.any { it.account == "Equity:Opening-Balances" })
    }
}
