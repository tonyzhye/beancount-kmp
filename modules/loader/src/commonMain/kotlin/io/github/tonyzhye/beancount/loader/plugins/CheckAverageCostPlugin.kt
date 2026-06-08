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
import io.github.tonyzhye.beancount.core.Booking as BookingMethod

/**
 * Check average cost plugin.
 * Based on beancount.plugins.check_average_cost.
 *
 * Ensures cost basis is preserved in unbooked (NONE) transactions.
 * For accounts using the NONE booking method, this plugin checks that
 * reducing legs match the average cost basis within a tolerance.
 *
 * This is a partial step toward implementing the AVERAGE booking method.
 *
 * @param tolerance Fractional tolerance from average cost (default 0.01 = 1%)
 */
class CheckAverageCostPlugin(internal val tolerance: Decimal = Decimal("0.01")) {

    /**
     * Validate that reducing postings on NONE-booked accounts match average cost.
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()

        // Get open/close map to check booking methods
        val ocMap = getAccountOpenClose(entries)

        // Track balances per account + currency + cost_currency
        val balances = mutableMapOf<Triple<Account, Currency, Currency?>, Inventory>()

        val minTolerance = Decimal.ONE - tolerance
        val maxTolerance = Decimal.ONE + tolerance

        for (entry in entries) {
            if (entry !is Transaction) continue

            for (posting in entry.postings) {
                val units = posting.units ?: continue
                val costSpec = posting.cost ?: continue
                val costNumber = costSpec.numberPer ?: continue
                val costCurrency = costSpec.currency ?: continue

                // Only process accounts with NONE booking
                val openInfo = ocMap[posting.account]?.first
                if (openInfo?.booking != BookingMethod.NONE) continue

                val key = Triple(posting.account, units.currency, costCurrency)
                val balance = balances.getOrPut(key) { Inventory() }

                // For reducing postings, check cost against average
                if (units.number.isNegative()) {
                    val avgCost = computeAverageCost(balance)
                    if (avgCost != null) {
                        val minValid = avgCost * minTolerance
                        val maxValid = avgCost * maxTolerance

                        if (costNumber < minValid || costNumber > maxValid) {
                            errors.add(
                                LoadError(
                                    source = entry.meta,
                                    message = "Cost basis on reducing posting is too far from " +
                                        "the average cost ($costNumber vs. $avgCost)",
                                    entry = entry
                                )
                            )
                        }
                    }
                }

                // Convert posting to position for inventory tracking
                val cost = Cost(costNumber, costCurrency, entry.date)
                balance.addPosition(Position(units, cost))
            }
        }

        return entries to errors
    }

    /**
     * Compute the average cost of positions in an inventory.
     * Average cost = sum(units * cost) / sum(units)
     */
    private fun computeAverageCost(inventory: Inventory): Decimal? {
        var totalUnits = Decimal.ZERO
        var totalCostValue = Decimal.ZERO

        for (position in inventory) {
            val cost = position.cost ?: continue
            totalUnits += position.units.number
            totalCostValue += position.units.number * cost.number
        }

        return if (!totalUnits.isZero()) {
            totalCostValue / totalUnits
        } else null
    }

    companion object {
        /** Default tolerance: 1% */
        val DEFAULT_TOLERANCE = Decimal("0.01")

        /**
         * Create plugin with parsed config string.
         * Config should be a float string, e.g. "0.02" for 2% tolerance.
         */
        fun withConfig(config: String): CheckAverageCostPlugin {
            val trimmed = config.trim()
            val tolerance = if (trimmed.isNotEmpty()) {
                try {
                    Decimal(trimmed)
                } catch (e: Exception) {
                    DEFAULT_TOLERANCE
                }
            } else {
                DEFAULT_TOLERANCE
            }
            return CheckAverageCostPlugin(tolerance)
        }
    }
}
