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
 * Sell gains plugin.
 * Based on beancount.plugins.sellgains.
 *
 * Validates that for transactions with postings that have both a cost and a price,
 * the sum of non-Income posting weights matches the expected proceeds from the sale.
 *
 * This provides an extra level of verification and allows eliding income amounts
 * when the price is present.
 */
object SellGainsPlugin {

    /** Multiplier for tolerance - provides extra space for satisfying two constraint sets. */
    private const val EXTRA_TOLERANCE_MULTIPLIER = 2

    /**
     * Validate sell gains for all transactions.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val config = options.accountTypes
        val proceedTypes = setOf(config.assets, config.liabilities, config.equity, config.expenses)

        for (entry in entries) {
            if (entry !is Transaction) continue

            // Find postings at cost (must have both cost and price)
            val postingsAtCost = entry.postings.filter { it.cost != null }
            if (postingsAtCost.isEmpty() || !postingsAtCost.all { it.price != null }) {
                continue
            }

            // Accumulate total expected proceeds and sum of asset/expense legs
            val totalPrice = Inventory()
            val totalProceeds = Inventory()

            for (posting in entry.postings) {
                if (posting.cost != null) {
                    // Posting held at cost - add priced value to total_price
                    val price = posting.price!!
                    val pricedValue = Amount(price.number * (-posting.units!!.number), price.currency)
                    totalPrice.addAmount(pricedValue)
                } else {
                    // Otherwise use weight, ignore postings to Income accounts
                    val accountType = AccountTypes.getAccountType(posting.account)
                    if (accountType in proceedTypes) {
                        totalProceeds.addAmount(getWeight(posting))
                    }
                }
            }

            // Compare inventories currency by currency
            val dictPrice = totalPrice.associate { it.units.currency to it.units.number }
            val dictProceeds = totalProceeds.associate { it.units.currency to it.units.number }.toMutableMap()

            val tolerances = inferTolerances(entry.postings, options)
            var invalid = false

            for ((currency, priceNumber) in dictPrice) {
                val tolerance = tolerances[currency]?.let {
                    it * Decimal(EXTRA_TOLERANCE_MULTIPLIER.toString())
                } ?: Decimal("0.01")

                val proceedsNumber = dictProceeds.remove(currency) ?: Decimal.ZERO
                val diff = (priceNumber - proceedsNumber).abs()
                if (diff > tolerance) {
                    invalid = true
                    break
                }
            }

            if (invalid || dictProceeds.isNotEmpty()) {
                errors.add(
                    SellGainsError(
                        source = entry.meta,
                        message = "Invalid price vs. proceeds/gains: $totalPrice vs. $totalProceeds; " +
                                "difference: ${totalPrice.copy().addInventory(totalProceeds.copy().negate())}",
                        entry = entry
                    )
                )
            }
        }

        return entries to errors
    }

    /**
     * Infer tolerances from posting metadata or use options tolerance map.
     */
    private fun inferTolerances(postings: List<Posting>, options: Options): Map<Currency, Decimal> {
        val result = mutableMapOf<Currency, Decimal>()
        // Use options tolerance map as base
        result.putAll(options.toleranceMap)
        // Add defaults for currencies not in tolerance map
        for (posting in postings) {
            val currency = posting.units?.currency ?: continue
            if (currency !in result) {
                result[currency] = Decimal("0.005")
            }
        }
        return result
    }
}

/**
 * Error for invalid sell gains.
 */
data class SellGainsError(
    override val source: Meta,
    override val message: String,
    override val entry: Directive
) : BeancountError
