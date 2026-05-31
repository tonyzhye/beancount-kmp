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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/**
 * Check closing plugin.
 * Based on beancount.plugins.check_closing.
 *
 * Automatically inserts a balance check on postings tagged with
 * 'closing: TRUE' metadata. The balance check verifies that the
 * position is zero the day after the closing transaction.
 *
 * Example:
 * ```
 * 2024-01-15 * "Sell stock"
 *   Assets:Investments:Stocks     -10 HOOL {150.00 USD}
 *     closing: TRUE
 *   Assets:Bank:Checking          1500.00 USD
 * ```
 *
 * Expands to include:
 * ```
 * 2024-01-16 balance Assets:Investments:Stocks  0 HOOL
 * ```
 */
object CheckClosingPlugin {

    /**
     * Expand 'closing' metadata to a zero balance check.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (updated entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val newEntries = mutableListOf<Directive>()
        val errors = mutableListOf<BeancountError>()

        for (entry in entries) {
            if (entry is Transaction) {
                val updatedPostings = mutableListOf<Posting>()
                val newBalances = mutableListOf<Balance>()

                for (posting in entry.postings) {
                    val postingMeta = posting.meta
                    if (postingMeta != null && postingMeta["closing"] == true) {
                        // Remove the closing metadata
                        val newMeta = postingMeta.toMutableMap()
                        newMeta.remove("closing")
                        updatedPostings.add(posting.copy(meta = newMeta))

                        // Insert a zero balance check for the next day
                        val units = posting.units
                        if (units != null) {
                            newBalances.add(
                                Balance(
                                    meta = newMetadata("<check_closing>", 0),
                                    date = entry.date.plus(DatePeriod(days = 1)),
                                    account = posting.account,
                                    amount = Amount(Decimal.ZERO, units.currency)
                                )
                            )
                        }
                    } else {
                        updatedPostings.add(posting)
                    }
                }

                newEntries.add(entry.copy(postings = updatedPostings))
                newEntries.addAll(newBalances)
            } else {
                newEntries.add(entry)
            }
        }

        return newEntries.sorted() to errors
    }
}
