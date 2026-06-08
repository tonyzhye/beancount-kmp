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
import kotlinx.datetime.LocalDate

/**
 * Currency accounts plugin.
 * Based on beancount.plugins.currency_accounts.
 *
 * Implements automatic currency trading accounts. When enabled,
 * transactions involving multiple currencies with price conversions
 * automatically get additional postings to balance currency trading.
 *
 * Usage:
 * ```
 * plugin "beancount.plugins.currency_accounts" "Equity:CurrencyAccounts"
 * ```
 *
 * Accounts will be automatically created under the given base account,
 * with the currency name appended, e.g., Equity:CurrencyAccounts:CAD.
 */
object CurrencyAccountsPlugin {

    /** Metadata field to mark processed entries. */
    private const val META_PROCESSED = "currency_accounts_processed"

    /** Default base account for currency trading. */
    private const val DEFAULT_BASE_ACCOUNT = "Equity:CurrencyAccounts"

    /**
     * Insert currency trading postings.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @param config The base account name for currency trading accounts.
     * @return A pair of (updated entries, errors).
     */
    fun transform(
        entries: List<Directive>,
        options: Options,
        config: String = DEFAULT_BASE_ACCOUNT
    ): Pair<List<Directive>, List<BeancountError>> {
        val baseAccount = config.trim().ifEmpty { DEFAULT_BASE_ACCOUNT }

        val newEntries = mutableListOf<Directive>()
        val newAccounts = mutableSetOf<Account>()
        val errors = mutableListOf<BeancountError>()

        for (entry in entries) {
            if (entry is Transaction) {
                val (curmap, hasPrice) = groupPostingsByCurrency(entry)
                if (hasPrice && curmap.size > 1) {
                    // This transaction needs currency trading adjustments
                    val newPostings = getNeutralizingPostings(curmap, baseAccount, newAccounts)
                    val newMeta = entry.meta.toMutableMap()
                    newMeta[META_PROCESSED] = true
                    newEntries.add(
                        entry.copy(
                            meta = newMeta,
                            postings = newPostings
                        )
                    )
                } else {
                    newEntries.add(entry)
                }
            } else {
                newEntries.add(entry)
            }
        }

        // Create Open directives for new currency accounts
        val earliestDate = entries.firstOrNull()?.date ?: LocalDate(2024, 1, 1)
        val openEntries = newAccounts.sorted().mapIndexed { index, account ->
            Open(
                meta = newMetadata("<currency_accounts>", index),
                date = earliestDate,
                account = account,
                currencies = emptyList(),
                booking = null
            )
        }

        return (openEntries + newEntries) to errors
    }

    /**
     * Group postings by their weight currency.
     *
     * @param entry A Transaction entry.
     * @return A pair of (currency to postings map, hasPrice flag).
     */
    private fun groupPostingsByCurrency(entry: Transaction): Pair<Map<Currency, List<Posting>>, Boolean> {
        val curmap = mutableMapOf<Currency, MutableList<Posting>>()
        var hasPrice = false

        for (posting in entry.postings) {
            var currency = posting.units?.currency ?: continue

            val cost = posting.cost
            if (cost != null) {
                // Use cost currency if available
                cost.currency?.let { currency = it }
            }
            if (posting.price != null) {
                hasPrice = true
            }

            curmap.getOrPut(currency) { mutableListOf() }.add(posting)
        }

        return curmap to hasPrice
    }

    /**
     * Generate neutralizing postings for currency trading accounts.
     *
     * @param curmap Map of currency to postings.
     * @param baseAccount Base account for currency trading.
     * @param newAccounts Mutable set to collect new account names.
     * @return Updated list of postings.
     */
    private fun getNeutralizingPostings(
        curmap: Map<Currency, List<Posting>>,
        baseAccount: Account,
        newAccounts: MutableSet<Account>
    ): List<Posting> {
        val newPostings = mutableListOf<Posting>()

        for ((currency, postings) in curmap) {
            // Compute the per-currency balance using weights
            var totalNumber = Decimal.ZERO
            for (posting in postings) {
                val weight = getWeight(posting)
                if (weight.currency == currency) {
                    totalNumber += weight.number
                }
            }

            // If balance is zero, just keep original postings
            if (totalNumber == Decimal.ZERO) {
                newPostings.addAll(postings)
                continue
            }

            // Re-insert original postings without price conversions
            for (posting in postings) {
                if (posting.price != null) {
                    newPostings.add(posting.copy(price = null))
                } else {
                    newPostings.add(posting)
                }
            }

            // Insert the currency trading account posting
            val account = "$baseAccount:$currency"
            newAccounts.add(account)
            newPostings.add(
                Posting(
                    account = account,
                    units = Amount(-totalNumber, currency)
                )
            )
        }

        return newPostings
    }
}
