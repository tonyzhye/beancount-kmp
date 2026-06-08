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

package io.github.tonyzhye.beancount.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for AccountTypes to improve coverage.
 */
class AccountTypesTest {

    @Test
    fun `getAccountType should return root type`() {
        assertEquals("Assets", AccountTypes.getAccountType("Assets:Bank:Checking"))
        assertEquals("Liabilities", AccountTypes.getAccountType("Liabilities:Credit"))
        assertEquals("Assets", AccountTypes.getAccountType("Assets"))
    }

    @Test
    fun `isAssets should identify asset accounts`() {
        assertTrue(AccountTypes.isAssets("Assets:Bank"))
        assertFalse(AccountTypes.isAssets("Liabilities:Credit"))
        assertFalse(AccountTypes.isAssets("Expenses:Food"))
    }

    @Test
    fun `isLiabilities should identify liability accounts`() {
        assertTrue(AccountTypes.isLiabilities("Liabilities:Credit"))
        assertFalse(AccountTypes.isLiabilities("Assets:Bank"))
    }

    @Test
    fun `isEquity should identify equity accounts`() {
        assertTrue(AccountTypes.isEquity("Equity:OpeningBalances"))
        assertFalse(AccountTypes.isEquity("Assets:Bank"))
    }

    @Test
    fun `isIncome should identify income accounts`() {
        assertTrue(AccountTypes.isIncome("Income:Salary"))
        assertFalse(AccountTypes.isIncome("Assets:Bank"))
    }

    @Test
    fun `isExpenses should identify expense accounts`() {
        assertTrue(AccountTypes.isExpenses("Expenses:Food"))
        assertFalse(AccountTypes.isExpenses("Assets:Bank"))
    }

    @Test
    fun `isBalanceSheetAccount should identify balance sheet accounts`() {
        assertTrue(AccountTypes.isBalanceSheetAccount("Assets:Bank"))
        assertTrue(AccountTypes.isBalanceSheetAccount("Liabilities:Credit"))
        assertTrue(AccountTypes.isBalanceSheetAccount("Equity:OpeningBalances"))
        assertFalse(AccountTypes.isBalanceSheetAccount("Income:Salary"))
        assertFalse(AccountTypes.isBalanceSheetAccount("Expenses:Food"))
    }

    @Test
    fun `isIncomeStatementAccount should identify income statement accounts`() {
        assertTrue(AccountTypes.isIncomeStatementAccount("Income:Salary"))
        assertTrue(AccountTypes.isIncomeStatementAccount("Expenses:Food"))
        assertFalse(AccountTypes.isIncomeStatementAccount("Assets:Bank"))
    }

    @Test
    fun `getAccountSign should return correct sign`() {
        assertEquals(1, AccountTypes.getAccountSign("Assets:Bank"))
        assertEquals(1, AccountTypes.getAccountSign("Expenses:Food"))
        assertEquals(-1, AccountTypes.getAccountSign("Liabilities:Credit"))
        assertEquals(-1, AccountTypes.getAccountSign("Income:Salary"))
        assertEquals(-1, AccountTypes.getAccountSign("Equity:OpeningBalances"))
    }

    @Test
    fun `isEquityAccount should identify equity accounts`() {
        assertTrue(AccountTypes.isEquityAccount("Equity:OpeningBalances"))
        assertFalse(AccountTypes.isEquityAccount("Assets:Bank"))
    }

    @Test
    fun `isInvertedAccount should identify inverted accounts`() {
        assertTrue(AccountTypes.isInvertedAccount("Liabilities:Credit"))
        assertTrue(AccountTypes.isInvertedAccount("Income:Salary"))
        assertTrue(AccountTypes.isInvertedAccount("Equity:OpeningBalances"))
        assertFalse(AccountTypes.isInvertedAccount("Assets:Bank"))
        assertFalse(AccountTypes.isInvertedAccount("Expenses:Food"))
    }

    @Test
    fun `getAccountSortKey should return sortable key`() {
        assertTrue(AccountTypes.getAccountSortKey("Assets:Bank") < AccountTypes.getAccountSortKey("Liabilities:Credit"))
        assertTrue(AccountTypes.getAccountSortKey("Liabilities:Credit") < AccountTypes.getAccountSortKey("Equity:Opening"))
        assertTrue(AccountTypes.getAccountSortKey("Equity:Opening") < AccountTypes.getAccountSortKey("Income:Salary"))
        assertTrue(AccountTypes.getAccountSortKey("Income:Salary") < AccountTypes.getAccountSortKey("Expenses:Food"))
    }

    @Test
    fun `AccountTypes should work with custom config`() {
        val customConfig = AccountTypesConfig(
            assets = "Aktiva",
            liabilities = "Passiva",
            equity = "Eigenkapital",
            income = "Einnahmen",
            expenses = "Ausgaben"
        )

        assertTrue(AccountTypes.isAssets("Aktiva:Bank", customConfig))
        assertTrue(AccountTypes.isLiabilities("Passiva:Kredit", customConfig))
        assertTrue(AccountTypes.isEquity("Eigenkapital:Anfang", customConfig))
        assertTrue(AccountTypes.isIncome("Einnahmen:Gehalt", customConfig))
        assertTrue(AccountTypes.isExpenses("Ausgaben:Lebensmittel", customConfig))
    }
}
