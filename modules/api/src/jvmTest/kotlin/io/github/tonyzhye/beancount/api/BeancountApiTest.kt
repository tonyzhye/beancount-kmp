/*
 * Beancount JVM - A JVM implementation of Beancount
 * Copyright (C) 2026  Beancount JVM Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Beancount by Martin Blais
 * Original project: https://github.com/beancount/beancount
 */

package io.github.tonyzhye.beancount.api

import io.github.tonyzhye.beancount.core.Decimal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for the Beancount API facade.
 */
class BeancountApiTest {

    @Test
    fun `D should create Decimal from string`() {
        val d = Beancount.D("100.50")
        assertEquals(Decimal("100.50"), d)
    }

    @Test
    fun `D should create Decimal from double`() {
        val d = Beancount.D(100.5)
        assertTrue(d.compareTo(Decimal("100.5")) == 0)
    }

    @Test
    fun `D should create Decimal from long`() {
        val d = Beancount.D(100L)
        assertEquals(Decimal("100"), d)
    }

    @Test
    fun `D should create Decimal from int`() {
        val d = Beancount.D(42)
        assertEquals(Decimal("42"), d)
    }

    @Test
    fun `loadString should parse simple ledger`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent()

        val result = Beancount.loadString(content)
        assertTrue(result.entries.isNotEmpty(), "Should have entries")
        assertTrue(result.errors.isEmpty(), "Should have no errors for valid ledger")
    }

    @Test
    fun `loadString should report errors for invalid ledger`() {
        val content = """
            2024-01-15 * "Transaction without open"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent()

        val result = Beancount.loadString(content)
        assertTrue(result.errors.isNotEmpty(), "Should have errors for missing open")
    }

    @Test
    fun `flag constants should match expected values`() {
        assertEquals("*", Beancount.FLAG_OKAY)
        assertEquals("!", Beancount.FLAG_WARNING)
        assertEquals("P", Beancount.FLAG_PADDING)
        assertEquals("S", Beancount.FLAG_SUMMARIZE)
        assertEquals("T", Beancount.FLAG_TRANSFER)
        assertEquals("C", Beancount.FLAG_CONVERSIONS)
        assertEquals("M", Beancount.FLAG_MERGING)
    }

    @Test
    fun `getAccountType should return root type`() {
        assertEquals("Assets", Beancount.getAccountType("Assets:Bank:Checking"))
        assertEquals("Expenses", Beancount.getAccountType("Expenses:Food"))
        assertEquals("Income", Beancount.getAccountType("Income:Salary"))
        assertEquals("Liabilities", Beancount.getAccountType("Liabilities:CreditCard"))
        assertEquals("Equity", Beancount.getAccountType("Equity:Opening-Balances"))
    }

    @Test
    fun `isAssets should identify asset accounts`() {
        assertTrue(Beancount.isAssets("Assets:Bank:Checking"))
        assertFalse(Beancount.isAssets("Expenses:Food"))
        assertFalse(Beancount.isAssets("Income:Salary"))
    }

    @Test
    fun `isLiabilities should identify liability accounts`() {
        assertTrue(Beancount.isLiabilities("Liabilities:CreditCard"))
        assertFalse(Beancount.isLiabilities("Assets:Bank"))
        assertFalse(Beancount.isLiabilities("Equity:Opening-Balances"))
    }

    @Test
    fun `isEquity should identify equity accounts`() {
        assertTrue(Beancount.isEquity("Equity:Opening-Balances"))
        assertFalse(Beancount.isEquity("Assets:Bank"))
        assertFalse(Beancount.isEquity("Income:Salary"))
    }

    @Test
    fun `isIncome should identify income accounts`() {
        assertTrue(Beancount.isIncome("Income:Salary"))
        assertFalse(Beancount.isIncome("Assets:Bank"))
        assertFalse(Beancount.isIncome("Expenses:Food"))
    }

    @Test
    fun `isExpenses should identify expense accounts`() {
        assertTrue(Beancount.isExpenses("Expenses:Food"))
        assertFalse(Beancount.isExpenses("Assets:Bank"))
        assertFalse(Beancount.isExpenses("Income:Salary"))
    }

    @Test
    fun `getAccountSign should return correct signs`() {
        assertEquals(1, Beancount.getAccountSign("Assets:Bank"))
        assertEquals(1, Beancount.getAccountSign("Expenses:Food"))
        assertEquals(-1, Beancount.getAccountSign("Income:Salary"))
        assertEquals(-1, Beancount.getAccountSign("Liabilities:CreditCard"))
        assertEquals(-1, Beancount.getAccountSign("Equity:Opening-Balances"))
    }

    @Test
    fun `getAccountSortKey should start with account type prefix`() {
        assertTrue(Beancount.getAccountSortKey("Assets:Bank").startsWith("0"))
        assertTrue(Beancount.getAccountSortKey("Liabilities:CreditCard").startsWith("1"))
        assertTrue(Beancount.getAccountSortKey("Equity:Opening").startsWith("2"))
        assertTrue(Beancount.getAccountSortKey("Income:Salary").startsWith("3"))
        assertTrue(Beancount.getAccountSortKey("Expenses:Food").startsWith("4"))
    }

    @Test
    fun `newMetadata should create metadata map`() {
        val meta = Beancount.newMetadata("test.beancount", 42)
        assertEquals("test.beancount", meta["filename"])
        assertEquals(42, meta["lineno"])
    }

    @Test
    fun `newMetadata should include additional kv pairs`() {
        val extra = mapOf("source" to "employer", "verified" to true)
        val meta = Beancount.newMetadata("test.beancount", 1, extra)
        assertEquals("employer", meta["source"])
        assertEquals(true, meta["verified"])
    }

    @Test
    fun `createSimplePosting should create valid posting`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent()

        val result = Beancount.loadString(content)
        val txn = result.entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>().first()
        val posting = Beancount.createSimplePosting(
            txn,
            "Assets:Savings",
            Decimal("50.00"),
            "USD"
        )

        assertEquals("Assets:Savings", posting.account)
        assertEquals(Decimal("50.00"), posting.units?.number)
        assertEquals("USD", posting.units?.currency)
    }

    @Test
    fun `getAccounts should list all accounts`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD
            2024-01-01 open Income:Salary USD
        """.trimIndent()

        val result = Beancount.loadString(content)
        val accounts = Beancount.getAccounts(result.entries)

        assertEquals(3, accounts.size)
        assertTrue(accounts.contains("Assets:Bank:Checking"))
        assertTrue(accounts.contains("Expenses:Food"))
        assertTrue(accounts.contains("Income:Salary"))
    }

    @Test
    fun `getCommodityDirectives should list all commodities`() {
        val content = """
            2024-01-01 commodity USD
            2024-01-01 commodity EUR
            2024-01-01 commodity HOOL
        """.trimIndent()

        val result = Beancount.loadString(content)
        val commodities = Beancount.getCommodityDirectives(result.entries)

        assertEquals(3, commodities.size)
        assertTrue(commodities.containsKey("USD"))
        assertTrue(commodities.containsKey("EUR"))
        assertTrue(commodities.containsKey("HOOL"))
    }

    @Test
    fun `getAllTags should collect all tags`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            2024-01-15 * "Grocery" #shopping
              Expenses:Food  50.00 USD
              Assets:Bank:Checking

            2024-01-20 * "Restaurant" #dining
              Expenses:Food  30.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val result = Beancount.loadString(content)
        val tags = Beancount.getAllTags(result.entries)

        assertTrue(tags.contains("shopping"))
        assertTrue(tags.contains("dining"))
    }

    @Test
    fun `accountJoin should join components`() {
        assertEquals("Assets:Bank:Checking", Beancount.accountJoin("Assets", "Bank", "Checking"))
    }

    @Test
    fun `accountSplit should split account name`() {
        val parts = Beancount.accountSplit("Assets:Bank:Checking")
        assertEquals(listOf("Assets", "Bank", "Checking"), parts)
    }

    @Test
    fun `accountParent should return parent account`() {
        assertEquals("Assets:Bank", Beancount.accountParent("Assets:Bank:Checking"))
        assertEquals("Assets", Beancount.accountParent("Assets:Bank"))
        // Root account returns empty string or null depending on implementation
        val rootParent = Beancount.accountParent("Assets")
        assertTrue(rootParent == null || rootParent == "")
    }

    @Test
    fun `accountLeaf should return leaf component`() {
        assertEquals("Checking", Beancount.accountLeaf("Assets:Bank:Checking"))
        assertEquals("Bank", Beancount.accountLeaf("Assets:Bank"))
    }

    @Test
    fun `accountHasComponent should check components`() {
        assertTrue(Beancount.accountHasComponent("Assets:Bank:Checking", "Bank"))
        assertFalse(Beancount.accountHasComponent("Assets:Bank:Checking", "Savings"))
    }
}
