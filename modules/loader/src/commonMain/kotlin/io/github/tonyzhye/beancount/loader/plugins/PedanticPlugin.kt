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
 * Pedantic plugin.
 * Based on beancount.plugins.pedantic.
 *
 * A plugin that triggers all pedantic validation plugins at once.
 * This is useful for strict validation mode.
 *
 * Plugins included:
 * - CheckCommodityPlugin: Validates all commodities have Commodity directives
 * - CheckDrainedPlugin: Inserts balance=0 checks before closing accounts
 * - CoherentCostPlugin: Validates cost consistency
 * - LeafOnlyPlugin: Ensures only leaf accounts have postings
 * - NoDuplicatesPlugin: Ensures no duplicate transactions
 * - NoUnusedPlugin: Ensures all open accounts are used
 * - OneCommodityPlugin: Ensures accounts use only one commodity
 * - SellGainsPlugin: Validates sell gains against prices on lot sales
 * - UniquePricesPlugin: Ensures unique prices per day
 */
object PedanticPlugin {

    /**
     * Run all pedantic validations.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val allErrors = mutableListOf<BeancountError>()
        var currentEntries = entries

        // Run check commodity validation
        val (entries0, errors0) = CheckCommodityPlugin.transform(currentEntries, options)
        currentEntries = entries0
        allErrors.addAll(errors0)

        // Run check drained validation
        val (entries0b, errors0b) = CheckDrainedPlugin.transform(currentEntries, options)
        currentEntries = entries0b
        allErrors.addAll(errors0b)

        // Run coherent cost validation
        val (entries1, errors1) = CoherentCostPlugin.transform(currentEntries, options)
        currentEntries = entries1
        allErrors.addAll(errors1)

        // Run leaf-only validation
        val (entries2, errors2) = LeafOnlyPlugin.transform(currentEntries, options)
        currentEntries = entries2
        allErrors.addAll(errors2)

        // Run no duplicates validation
        val (entries3, errors3) = NoDuplicatesPlugin.transform(currentEntries, options)
        currentEntries = entries3
        allErrors.addAll(errors3)

        // Run no unused accounts validation
        val (entries4, errors4) = NoUnusedPlugin.transform(currentEntries, options)
        currentEntries = entries4
        allErrors.addAll(errors4)

        // Run one commodity validation
        val (entries5, errors5) = OneCommodityPlugin.transform(currentEntries, options)
        currentEntries = entries5
        allErrors.addAll(errors5)

        // Run sell gains validation
        val (entries5b, errors5b) = SellGainsPlugin.transform(currentEntries, options)
        currentEntries = entries5b
        allErrors.addAll(errors5b)

        // Run unique prices validation
        val (entries6, errors6) = UniquePricesPlugin.transform(currentEntries, options)
        currentEntries = entries6
        allErrors.addAll(errors6)

        return currentEntries to allErrors
    }
}
