package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RealizationTest {

    @Test
    fun `should realize simple transaction`() {
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
                narration = "Deposit",
                postings = listOf(
                    Posting(account = "Assets:Cash", units = Amount(Decimal("100"), "USD")),
                    Posting(account = "Income:Salary", units = Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val root = realize(entries)

        // Check tree structure
        assertNotNull(root["Assets"])
        assertNotNull(root["Assets"]?.get("Cash"))

        // Check postings
        val cashAccount = root["Assets"]?.get("Cash")
        assertNotNull(cashAccount)
        assertEquals(2, cashAccount!!.txnPostings.size) // Open + TxnPosting

        // Check balance
        val cashUnits = cashAccount.balance.getCurrencyUnits("USD")
        assertEquals(Decimal("100"), cashUnits.number)
    }

    @Test
    fun `should create account tree structure`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "Assets:Bank:Checking", units = Amount(Decimal("100"), "USD")),
                    Posting(account = "Assets:Bank:Savings", units = Amount(Decimal("50"), "USD")),
                    Posting(account = "Liabilities:CreditCard", units = Amount(Decimal("-150"), "USD"))
                )
            )
        )

        val root = realize(entries)

        // Check tree structure
        assertNotNull(root["Assets"])
        assertNotNull(root["Assets"]?.get("Bank"))
        assertNotNull(root["Assets"]?.get("Bank")?.get("Checking"))
        assertNotNull(root["Assets"]?.get("Bank")?.get("Savings"))
        assertNotNull(root["Liabilities"])
        assertNotNull(root["Liabilities"]?.get("CreditCard"))

        // Check full account names
        assertEquals("Assets:Bank:Checking", root["Assets"]?.get("Bank")?.get("Checking")?.account)
        assertEquals("Assets:Bank:Savings", root["Assets"]?.get("Bank")?.get("Savings")?.account)
    }

    @Test
    fun `should compute balances correctly`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "First",
                postings = listOf(
                    Posting(account = "Assets:Cash", units = Amount(Decimal("100"), "USD")),
                    Posting(account = "Income:Salary", units = Amount(Decimal("-100"), "USD"))
                )
            ),
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 2),
                flag = "*",
                narration = "Second",
                postings = listOf(
                    Posting(account = "Assets:Cash", units = Amount(Decimal("50"), "USD")),
                    Posting(account = "Income:Bonus", units = Amount(Decimal("-50"), "USD"))
                )
            )
        )

        val root = realize(entries)

        val cashAccount = root["Assets"]?.get("Cash")
        assertNotNull(cashAccount)
        assertEquals(Decimal("150"), cashAccount!!.balance.getCurrencyUnits("USD").number)
    }

    @Test
    fun `should handle balance directive`() {
        val entries = listOf(
            Balance(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Cash",
                amount = Amount(Decimal("100"), "USD")
            )
        )

        val root = realize(entries)

        val cashAccount = root["Assets"]?.get("Cash")
        assertNotNull(cashAccount)
        assertEquals(1, cashAccount!!.txnPostings.size)
        assertTrue(cashAccount.txnPostings[0] is Balance)
    }

    @Test
    fun `should handle pad directive`() {
        val entries = listOf(
            Pad(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Cash",
                sourceAccount = "Equity:Opening-Balances"
            )
        )

        val root = realize(entries)

        val cashAccount = root["Assets"]?.get("Cash")
        val equityAccount = root["Equity"]?.get("Opening-Balances")

        assertNotNull(cashAccount)
        assertNotNull(equityAccount)

        // Pad should appear in both accounts
        assertEquals(1, cashAccount!!.txnPostings.size)
        assertEquals(1, equityAccount!!.txnPostings.size)
    }

    @Test
    fun `should iterate with balance`() {
        val postings = listOf(
            TxnPosting(
                txn = Transaction(
                    meta = emptyMap(),
                    date = LocalDate(2024, 1, 1),
                    flag = "*",
                    narration = "Deposit"
                ),
                posting = Posting(account = "Assets:Cash", units = Amount(Decimal("100"), "USD"))
            ),
            TxnPosting(
                txn = Transaction(
                    meta = emptyMap(),
                    date = LocalDate(2024, 1, 2),
                    flag = "*",
                    narration = "Withdrawal"
                ),
                posting = Posting(account = "Assets:Cash", units = Amount(Decimal("-30"), "USD"))
            )
        )

        val iterations = iterateWithBalance(postings)

        assertEquals(2, iterations.size)

        // First iteration: balance = 100
        assertEquals(Decimal("100"), iterations[0].balance.getCurrencyUnits("USD").number)

        // Second iteration: balance = 70
        assertEquals(Decimal("70"), iterations[1].balance.getCurrencyUnits("USD").number)
    }

    @Test
    fun `should compute total balance including children`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "Assets:Bank:Checking", units = Amount(Decimal("100"), "USD")),
                    Posting(account = "Assets:Bank:Savings", units = Amount(Decimal("50"), "USD")),
                    Posting(account = "Income:Salary", units = Amount(Decimal("-150"), "USD"))
                )
            )
        )

        val root = realize(entries)

        // Total balance of Assets should include all children
        val assetsBalance = computeBalance(root["Assets"]!!)
        assertEquals(Decimal("150"), assetsBalance.getCurrencyUnits("USD").number)

        // Total balance of Bank should include Checking + Savings
        val bankBalance = computeBalance(root["Assets"]?.get("Bank")!!)
        assertEquals(Decimal("150"), bankBalance.getCurrencyUnits("USD").number)
    }

    @Test
    fun `should handle empty entries`() {
        val root = realize(emptyList())

        assertTrue(root.children.isEmpty())
        assertTrue(root.txnPostings.isEmpty())
        assertTrue(root.balance.isEmpty())
    }

    @Test
    fun `should iterate all accounts`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "Assets:Cash", units = Amount(Decimal("100"), "USD")),
                    Posting(account = "Expenses:Food", units = Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val root = realize(entries)
        val allAccounts = root.iterate().toList()

        // Root + Assets + Cash + Expenses + Food = 5
        assertEquals(5, allAccounts.size)
    }

    @Test
    fun `should iterate leaf accounts`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "Assets:Bank:Checking", units = Amount(Decimal("100"), "USD")),
                    Posting(account = "Assets:Bank:Savings", units = Amount(Decimal("50"), "USD"))
                )
            )
        )

        val root = realize(entries)
        val leafAccounts = root.iterateLeaves().toList()

        // Only Checking and Savings are leaves
        assertEquals(2, leafAccounts.size)
        assertTrue(leafAccounts.any { it.account == "Assets:Bank:Checking" })
        assertTrue(leafAccounts.any { it.account == "Assets:Bank:Savings" })
    }

    @Test
    fun `should get short name`() {
        val account = RealAccount("Assets:Bank:Checking")
        assertEquals("Checking", account.shortName)

        val simpleAccount = RealAccount("Assets")
        assertEquals("Assets", simpleAccount.shortName)
    }
}
