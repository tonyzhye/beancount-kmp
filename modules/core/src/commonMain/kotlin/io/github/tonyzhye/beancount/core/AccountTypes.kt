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
}
