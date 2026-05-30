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
 * Implicit prices plugin.
 * Based on beancount.plugins.implicit_prices.
 *
 * Synthesizes Price directives for all postings with a price directive
 * or if it is an augmenting posting (not reducing), has a cost directive.
 *
 * This plugin automatically creates Price entries from transactions,
 * making it easier to track commodity prices over time.
 */
object ImplicitPricesPlugin {

    /** Metadata field to mark auto-generated price entries. */
    private const val METADATA_FIELD = "__implicit_prices__"

    /**
     * Insert implicitly defined prices from Transactions.
     *
     * Explicit price entries are maintained in the output list.
     * Prices from postings with costs or with prices from Transaction entries
     * are synthesized as new Price entries.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (updated entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val newEntries = mutableListOf<Directive>()
        val errors = mutableListOf<BeancountError>()

        // Track generated price entries for deduplication
        val priceEntryMap = mutableMapOf<Triple<LocalDate, Currency, Currency>, Price>()

        for (entry in entries) {
            // Always replicate the existing entries
            newEntries.add(entry)

            if (entry is Transaction) {
                // Inspect all postings in the transaction
                for (posting in entry.postings) {
                    val units = posting.units ?: continue

                    val priceEntry: Price? = when {
                        // Explicit price on posting, e.g. 100 USD @ 1.10 CAD
                        posting.price != null -> {
                            val price = posting.price!!
                            val meta = newMetadata(
                                entry.meta["filename"] as? String ?: "<implicit_prices>",
                                entry.meta["lineno"] as? Int ?: 0,
                                mapOf(METADATA_FIELD to "from_price")
                            )
                            Price(
                                meta = meta,
                                date = entry.date,
                                currency = units.currency,
                                amount = price
                            )
                        }
                        // Cost without matching existing position, e.g. 100 HOOL {564.20}
                        posting.cost != null -> {
                            val cost = posting.cost!!
                            val costCurrency = cost.currency
                            val costNumber = cost.numberPer
                            if (costCurrency != null && costNumber != null) {
                                val meta = newMetadata(
                                    entry.meta["filename"] as? String ?: "<implicit_prices>",
                                    entry.meta["lineno"] as? Int ?: 0,
                                    mapOf(METADATA_FIELD to "from_cost")
                                )
                                Price(
                                    meta = meta,
                                    date = entry.date,
                                    currency = units.currency,
                                    amount = Amount(costNumber, costCurrency)
                                )
                            } else null
                        }
                        else -> null
                    }

                    if (priceEntry != null) {
                        val key = Triple(
                            priceEntry.date,
                            priceEntry.currency,
                            priceEntry.amount.currency
                        )
                        // Only add if we haven't seen this exact price before
                        if (key !in priceEntryMap) {
                            priceEntryMap[key] = priceEntry
                            newEntries.add(priceEntry)
                        }
                    }
                }
            }
        }

        return newEntries to errors
    }
}
