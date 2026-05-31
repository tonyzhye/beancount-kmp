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
 * Unique prices plugin.
 * Based on beancount.plugins.unique_prices.
 *
 * Validates that there is only a single price per day for a particular
 * base/quote currency pair. If multiple conflicting price values are
 * declared, an error is generated.
 *
 * Note: Multiple price entries with the same number do not generate an error.
 */
object UniquePricesPlugin {

    /**
     * Check that there is only a single price per day for a particular base/quote.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val prices = mutableMapOf<Triple<LocalDate, Currency, Currency>, MutableList<Price>>()

        for (entry in entries) {
            if (entry is Price) {
                val key = Triple(entry.date, entry.currency, entry.amount.currency)
                prices.getOrPut(key) { mutableListOf() }.add(entry)
            }
        }

        val errors = mutableListOf<BeancountError>()
        for ((_, priceEntries) in prices) {
            if (priceEntries.size > 1) {
                val numberMap = priceEntries.associateBy { it.amount.number }
                if (numberMap.size > 1) {
                    val errorEntry = numberMap.values.first()
                    errors.add(
                        UniquePricesError(
                            source = errorEntry.meta,
                            message = "Disagreeing price entries for ${errorEntry.currency}/${errorEntry.amount.currency} on ${errorEntry.date}",
                            entries = priceEntries
                        )
                    )
                }
            }
        }

        return entries to errors
    }
}

/**
 * Error type for unique prices validation.
 */
data class UniquePricesError(
    override val source: Meta,
    override val message: String,
    val entries: List<Price>
) : BeancountError {
    override val entry: Directive? = entries.firstOrNull()
}
