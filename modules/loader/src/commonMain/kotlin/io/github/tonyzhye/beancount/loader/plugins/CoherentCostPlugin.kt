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
 * Coherent cost plugin.
 * Based on beancount.plugins.coherent_cost.
 *
 * Validates that currencies held at cost aren't ever converted at price
 * and vice-versa. This prevents users from selling a lot without specifying
 * it via its cost basis.
 */
object CoherentCostPlugin {

    /**
     * Check that all currencies are either used at cost or not at all, but never both.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val withCost = mutableMapOf<Currency, Transaction>()
        val withoutCost = mutableMapOf<Currency, Transaction>()

        for (entry in entries) {
            if (entry is Transaction) {
                for (posting in entry.postings) {
                    val units = posting.units ?: continue
                    val currency = units.currency
                    if (posting.cost != null) {
                        if (currency !in withCost) {
                            withCost[currency] = entry
                        }
                    } else {
                        if (currency !in withoutCost) {
                            withoutCost[currency] = entry
                        }
                    }
                }
            }
        }

        val errors = mutableListOf<BeancountError>()
        val conflictingCurrencies = withCost.keys.intersect(withoutCost.keys)

        for (currency in conflictingCurrencies) {
            val withoutCostEntry = withoutCost[currency]!!
            errors.add(
                CoherentCostError(
                    source = withoutCostEntry.meta,
                    message = "Currency '$currency' is used both with and without cost",
                    entry = withCost[currency]
                )
            )
        }

        return entries to errors
    }
}

/**
 * Error type for coherent cost validation.
 */
data class CoherentCostError(
    override val source: Meta,
    override val message: String,
    override val entry: Directive?
) : BeancountError
