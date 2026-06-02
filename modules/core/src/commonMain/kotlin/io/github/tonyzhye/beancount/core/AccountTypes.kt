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

/**
 * Account type utilities.
 * Based on beancount.core.account_types.
 */
object AccountTypes {

    /**
     * Get the root account type from a full account name.
     */
    fun getAccountType(account: Account): String {
        val firstColon = account.indexOf(':')
        return if (firstColon > 0) account.substring(0, firstColon) else account
    }

    /**
     * Check if an account is an Assets account.
     */
    fun isAssets(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return getAccountType(account) == config.assets
    }

    /**
     * Check if an account is a Liabilities account.
     */
    fun isLiabilities(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return getAccountType(account) == config.liabilities
    }

    /**
     * Check if an account is an Equity account.
     */
    fun isEquity(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return getAccountType(account) == config.equity
    }

    /**
     * Check if an account is an Income account.
     */
    fun isIncome(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return getAccountType(account) == config.income
    }

    /**
     * Check if an account is an Expenses account.
     */
    fun isExpenses(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return getAccountType(account) == config.expenses
    }

    /**
     * Check if an account is a balance sheet account (Assets, Liabilities, or Equity).
     */
    fun isBalanceSheetAccount(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return isAssets(account, config) || isLiabilities(account, config) || isEquity(account, config)
    }

    /**
     * Check if an account is an income statement account (Income or Expenses).
     */
    fun isIncomeStatementAccount(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return isIncome(account, config) || isExpenses(account, config)
    }

    /**
     * Get the sign (+1 or -1) for an account based on its type.
     *
     * Assets and Expenses have positive sign (debit increases).
     * Liabilities, Income, and Equity have negative sign (credit increases).
     */
    fun getAccountSign(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Int {
        return when {
            isAssets(account, config) || isExpenses(account, config) -> 1
            isLiabilities(account, config) || isIncome(account, config) || isEquity(account, config) -> -1
            else -> 1
        }
    }

    /**
     * Check if an account is an equity account.
     */
    fun isEquityAccount(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return isEquity(account, config)
    }

    /**
     * Check if an account has inverted sign (Liabilities, Income, or Equity).
     */
    fun isInvertedAccount(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return isLiabilities(account, config) || isIncome(account, config) || isEquity(account, config)
    }

    /**
     * Get a sort key for ordering accounts.
     * Accounts are sorted by type (Assets, Liabilities, Equity, Income, Expenses) then alphabetically.
     */
    fun getAccountSortKey(account: Account, config: AccountTypesConfig = AccountTypesConfig()): String {
        val typeOrder = when {
            isAssets(account, config) -> "0"
            isLiabilities(account, config) -> "1"
            isEquity(account, config) -> "2"
            isIncome(account, config) -> "3"
            isExpenses(account, config) -> "4"
            else -> "5"
        }
        return "$typeOrder:$account"
    }
}
