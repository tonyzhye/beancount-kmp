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

/**
 * Check commodity plugin.
 * Based on beancount.plugins.check_commodity.
 *
 * Verifies that all seen commodities have a Commodity directive.
 * This is useful for ensuring that commodity attributes are declared
 * for each commodity used in the ledger.
 */
object CheckCommodityPlugin {

    /** Context placeholder for commodities appearing in Price directives. */
    private const val PRICE_CONTEXT = "Price Directive Context"

    /** Set of anonymous contexts (not tied to a specific account). */
    private val ANONYMOUS = setOf(PRICE_CONTEXT)

    /**
     * Find all commodities used and ensure they have a corresponding Commodity directive.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val occurrences = mutableSetOf<Pair<String, Currency>>()
        val commodityMap = mutableMapOf<Currency, Commodity>()

        for (entry in entries) {
            when (entry) {
                is Commodity -> {
                    commodityMap[entry.currency] = entry
                }
                is Open -> {
                    for (currency in entry.currencies) {
                        occurrences.add(entry.account to currency)
                    }
                }
                is Transaction -> {
                    for (posting in entry.postings) {
                        val units = posting.units
                        if (units != null) {
                            occurrences.add(posting.account to units.currency)
                        }
                        val cost = posting.cost
                        val costCurrency = cost?.currency
                        if (costCurrency != null) {
                            occurrences.add(posting.account to costCurrency)
                        }
                        val price = posting.price
                        if (price != null) {
                            occurrences.add(posting.account to price.currency)
                        }
                    }
                }
                is Balance -> {
                    occurrences.add(entry.account to entry.amount.currency)
                }
                is Price -> {
                    occurrences.add(PRICE_CONTEXT to entry.currency)
                    occurrences.add(PRICE_CONTEXT to entry.amount.currency)
                }
                else -> {}
            }
        }

        val errors = mutableListOf<BeancountError>()
        val issued = mutableSetOf<Currency>()

        // Process all currencies with account context
        for ((context, currency) in occurrences.sortedBy { it.first }) {
            if (context in ANONYMOUS) continue

            val commodityEntry = commodityMap[currency]
            if (commodityEntry != null || currency in issued) continue

            errors.add(
                CheckCommodityError(
                    source = newMetadata("<check_commodity>", 0),
                    message = "Missing Commodity directive for '$currency' in '$context'",
                    currency = currency
                )
            )
            issued.add(currency)
        }

        // Process anonymous contexts (Price directives)
        for ((context, currency) in occurrences.sortedBy { it.first }) {
            if (context !in ANONYMOUS) continue

            val commodityEntry = commodityMap[currency]
            if (commodityEntry != null || currency in issued) continue

            errors.add(
                CheckCommodityError(
                    source = newMetadata("<check_commodity>", 0),
                    message = "Missing Commodity directive for '$currency' in '$context'",
                    currency = currency
                )
            )
            issued.add(currency)
        }

        return entries to errors
    }
}

/**
 * Error type for missing commodity directives.
 */
data class CheckCommodityError(
    override val source: Meta,
    override val message: String,
    val currency: Currency
) : BeancountError {
    override val entry: Directive? = null
}
