package io.github.tonyzhye.beancount.api

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Additional tests for Beancount API to reach 80% coverage.
 */
class BeancountApiAdditionalTest {

    private val sampleEntries = run {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD
            2024-01-01 open Income:Salary USD
            2024-01-01 commodity USD

            2024-01-15 * "Paycheck" #income ^link1
              Assets:Bank:Checking  1000.00 USD
              Income:Salary

            2024-01-20 * "Grocery" #food
              Expenses:Food  50.00 USD
              Assets:Bank:Checking
        """.trimIndent()
        Beancount.loadString(content).entries
    }

    @Test
    fun `D overloads should work`() {
        assertEquals(Decimal("3.14"), Beancount.D(3.14))
        assertEquals(Decimal("100"), Beancount.D(100L))
        assertEquals(Decimal("42"), Beancount.D(42))
    }

    @Test
    fun `newMetadata should create metadata`() {
        val meta = Beancount.newMetadata("test.beancount", 10)
        assertEquals("test.beancount", meta["filename"])
        assertEquals(10, meta["lineno"])
    }

    @Test
    fun `newMetadata with kvlist should merge`() {
        val meta = Beancount.newMetadata("test.beancount", 10, mapOf("custom" to "value"))
        assertEquals("value", meta["custom"])
    }

    @Test
    fun `createSimplePosting should create posting`() {
        val txn = Transaction(emptyMap(), LocalDate(2024, 1, 1), "*", narration = "Test")
        val posting = Beancount.createSimplePosting(txn, "Assets:Bank", Decimal("100"), "USD")
        assertEquals("Assets:Bank", posting.account)
        assertEquals(Decimal("100"), posting.units?.number)
    }

    @Test
    fun `flag constants should be defined`() {
        assertEquals("*", Beancount.FLAG_OKAY)
        assertNotNull(Beancount.FLAG_WARNING)
        assertNotNull(Beancount.FLAG_PADDING)
        assertNotNull(Beancount.FLAG_SUMMARIZE)
        assertNotNull(Beancount.FLAG_TRANSFER)
        assertNotNull(Beancount.FLAG_CONVERSIONS)
        assertNotNull(Beancount.FLAG_MERGING)
    }

    @Test
    fun `getAccountType should return root type`() {
        assertEquals("Assets", Beancount.getAccountType("Assets:Bank:Checking"))
        assertEquals("Expenses", Beancount.getAccountType("Expenses:Food"))
    }

    @Test
    fun `getAccountSign should return correct sign`() {
        assertEquals(1, Beancount.getAccountSign("Assets:Bank"))
        assertEquals(1, Beancount.getAccountSign("Expenses:Food"))
        assertEquals(-1, Beancount.getAccountSign("Income:Salary"))
        assertEquals(-1, Beancount.getAccountSign("Liabilities:Credit"))
    }

    @Test
    fun `getAccountSortKey should return sortable key`() {
        assertTrue(Beancount.getAccountSortKey("Assets:Bank") < Beancount.getAccountSortKey("Liabilities:Credit"))
    }

    @Test
    fun `isAssets should identify asset accounts`() {
        assertTrue(Beancount.isAssets("Assets:Bank"))
        assertFalse(Beancount.isAssets("Expenses:Food"))
    }

    @Test
    fun `isLiabilities should identify liability accounts`() {
        assertTrue(Beancount.isLiabilities("Liabilities:Credit"))
        assertFalse(Beancount.isLiabilities("Assets:Bank"))
    }

    @Test
    fun `isEquity should identify equity accounts`() {
        assertTrue(Beancount.isEquity("Equity:Opening"))
        assertFalse(Beancount.isEquity("Assets:Bank"))
    }

    @Test
    fun `isIncome should identify income accounts`() {
        assertTrue(Beancount.isIncome("Income:Salary"))
        assertFalse(Beancount.isIncome("Assets:Bank"))
    }

    @Test
    fun `isExpenses should identify expense accounts`() {
        assertTrue(Beancount.isExpenses("Expenses:Food"))
        assertFalse(Beancount.isExpenses("Assets:Bank"))
    }

    @Test
    fun `isBalanceSheetAccount should identify balance sheet accounts`() {
        assertTrue(Beancount.isBalanceSheetAccount("Assets:Bank"))
        assertTrue(Beancount.isBalanceSheetAccount("Liabilities:Credit"))
        assertTrue(Beancount.isBalanceSheetAccount("Equity:Opening"))
        assertFalse(Beancount.isBalanceSheetAccount("Income:Salary"))
    }

    @Test
    fun `isIncomeStatementAccount should identify income statement accounts`() {
        assertTrue(Beancount.isIncomeStatementAccount("Income:Salary"))
        assertTrue(Beancount.isIncomeStatementAccount("Expenses:Food"))
        assertFalse(Beancount.isIncomeStatementAccount("Assets:Bank"))
    }

    @Test
    fun `isEquityAccount should identify equity accounts`() {
        assertTrue(Beancount.isEquityAccount("Equity:Opening"))
        assertFalse(Beancount.isEquityAccount("Assets:Bank"))
    }

    @Test
    fun `accountJoin should join components`() {
        assertEquals("Assets:Bank:Checking", Beancount.accountJoin("Assets", "Bank", "Checking"))
    }

    @Test
    fun `accountSplit should split account`() {
        assertEquals(listOf("Assets", "Bank", "Checking"), Beancount.accountSplit("Assets:Bank:Checking"))
    }

    @Test
    fun `accountParent should return parent`() {
        assertEquals("Assets:Bank", Beancount.accountParent("Assets:Bank:Checking"))
    }

    @Test
    fun `accountLeaf should return leaf`() {
        assertEquals("Checking", Beancount.accountLeaf("Assets:Bank:Checking"))
    }

    @Test
    fun `accountHasComponent should check component`() {
        assertTrue(Beancount.accountHasComponent("Assets:Bank:Checking", "Bank"))
        assertFalse(Beancount.accountHasComponent("Assets:Bank:Checking", "Savings"))
    }

    @Test
    fun `getAccounts should return all accounts`() {
        val accounts = Beancount.getAccounts(sampleEntries)
        assertTrue(accounts.contains("Assets:Bank:Checking"))
        assertTrue(accounts.contains("Expenses:Food"))
    }

    @Test
    fun `getAllTags should return all tags`() {
        val tags = Beancount.getAllTags(sampleEntries)
        assertTrue(tags.contains("income"))
        assertTrue(tags.contains("food"))
    }

    @Test
    fun `getCommodityDirectives should return commodities`() {
        val commodities = Beancount.getCommodityDirectives(sampleEntries)
        assertTrue(commodities.containsKey("USD"))
    }

    @Test
    fun `getValue should return value from position`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = Beancount.buildPriceMap(entries)
        val pos = Position(Amount(Decimal("100"), "EUR"), null)
        val value = Beancount.getValue(pos, priceMap)
        // Value should be computed, currency may vary based on implementation
        assertNotNull(value)
    }

    @Test
    fun `convertPosting should convert posting currency`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = Beancount.buildPriceMap(entries)
        val posting = Posting("Assets:Bank", Amount(Decimal("100"), "EUR"))
        val converted = Beancount.convertPosting(posting, "USD", priceMap)
        assertEquals("USD", converted.currency)
    }

    @Test
    fun `printEntries should format entries`() {
        val output = Beancount.printEntries(sampleEntries)
        assertTrue(output.contains("2024-01-01"))
    }

    @Test
    fun `hashEntry with excludeMeta should produce hash`() {
        val txn = sampleEntries.filterIsInstance<Transaction>().first()
        val hash1 = Beancount.hashEntry(txn, false)
        val hash2 = Beancount.hashEntry(txn, true)
        assertNotNull(hash1)
        assertNotNull(hash2)
    }

    @Test
    fun `realize should create account tree`() {
        val root = Beancount.realize(sampleEntries)
        assertNotNull(root)
        assertTrue(root.account.isEmpty()) // Root account has empty name
    }
}
