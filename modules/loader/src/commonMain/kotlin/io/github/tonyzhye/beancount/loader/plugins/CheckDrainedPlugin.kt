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

package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Check drained plugin.
 * Based on beancount.plugins.check_drained.
 *
 * For balance sheet accounts (Assets, Liabilities, Equity) with a Close directive,
 * inserts Balance directives just after the closing date for all commodities
 * that have appeared in that account. This ensures closed accounts have zero balance.
 *
 * Example transformation:
 *   2020-02-01 close Assets:Project:Cash
 * becomes:
 *   2020-02-01 close Assets:Project:Cash
 *   2020-02-02 balance Assets:Project:Cash  0 USD
 *   2020-02-02 balance Assets:Project:Cash  0 CAD
 */
object CheckDrainedPlugin {

    /**
     * Check that closed balance sheet accounts are empty.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries with inserted balances, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val config = options.accountTypes
        val newEntries = mutableListOf<Directive>()
        val currencies = mutableMapOf<Account, MutableSet<Currency>>()
        val balances = mutableMapOf<Account, MutableSet<Pair<LocalDate, Currency>>>()

        for (entry in entries) {
            when (entry) {
                is Transaction -> {
                    // Accumulate all currencies seen in each account
                    for (posting in entry.postings) {
                        if (AccountTypes.isBalanceSheetAccount(posting.account, config)) {
                            currencies.getOrPut(posting.account) { mutableSetOf() }
                                .add(posting.units!!.currency)
                        }
                    }
                }
                is Open -> {
                    // Accumulate all currencies declared in the account opening
                    if (AccountTypes.isBalanceSheetAccount(entry.account, config) && entry.currencies.isNotEmpty()) {
                        currencies.getOrPut(entry.account) { mutableSetOf() }
                            .addAll(entry.currencies)
                    }
                }
                is Balance -> {
                    // Track existing balance directives to avoid duplicates
                    if (AccountTypes.isBalanceSheetAccount(entry.account, config)) {
                        balances.getOrPut(entry.account) { mutableSetOf() }
                            .add(Pair(entry.date, entry.amount.currency))
                    }
                }
                else -> {}
            }

            if (entry is Close && AccountTypes.isBalanceSheetAccount(entry.account, config)) {
                // Insert balance checks for each currency seen in this account
                val accountCurrencies = currencies[entry.account] ?: emptySet()
                val accountBalances = balances[entry.account] ?: emptySet()

                for (currency in accountCurrencies) {
                    // Skip if there's already a balance directive for this date/currency
                    if (Pair(entry.date, currency) in accountBalances) {
                        continue
                    }

                    // Insert balance directive the day after close
                    val balanceEntry = Balance(
                        meta = entry.meta,
                        date = entry.date.plus(1, DateTimeUnit.DAY),
                        account = entry.account,
                        amount = Amount(Decimal.ZERO, currency)
                    )
                    newEntries.add(balanceEntry)
                }
            }

            newEntries.add(entry)
        }

        return newEntries to emptyList()
    }
}
