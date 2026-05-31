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
 * One commodity per account plugin.
 * Based on beancount.plugins.onecommodity.
 *
 * Issues errors when more than one commodity is used in an account.
 *
 * Notes:
 * - Accounts that have explicitly declared multiple commodities in their
 *   Open directive are automatically skipped.
 * - You can set the metadata "onecommodity: FALSE" on an account's Open
 *   directive to skip the checks for that account.
 * - If provided, the configuration should be a regular expression restricting
 *   the set of accounts to check.
 */
object OneCommodityPlugin {

    /**
     * Check that each account has units in only a single commodity.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @param config Optional regex pattern to restrict accounts to check.
     * @return A pair of (entries, errors).
     */
    fun transform(
        entries: List<Directive>,
        options: Options,
        config: String? = null
    ): Pair<List<Directive>, List<BeancountError>> {
        val skipAccounts = mutableSetOf<Account>()
        val unitsMap = mutableMapOf<Account, MutableSet<Currency>>()
        val costMap = mutableMapOf<Account, MutableSet<Currency>>()
        val unitsSourceMap = mutableMapOf<Account, Directive>()
        val costSourceMap = mutableMapOf<Account, Directive>()

        // Gather accounts to skip from Open directives
        for (entry in entries) {
            if (entry is Open) {
                val shouldSkip = !entry.meta.getOrDefault("onecommodity", true).let {
                    it == true || it == "TRUE" || it == "true"
                } || entry.currencies.size > 1
                if (shouldSkip) {
                    skipAccounts.add(entry.account)
                }
            }
        }

        // Accumulate all the commodities used
        for (entry in entries) {
            when (entry) {
                is Transaction -> {
                    for (posting in entry.postings) {
                        if (posting.account in skipAccounts) continue

                        val units = posting.units
                        if (units != null) {
                            unitsMap.getOrPut(posting.account) { mutableSetOf() }.add(units.currency)
                            if (unitsMap[posting.account]!!.size > 1) {
                                unitsSourceMap[posting.account] = entry
                            }
                        }

                        val cost = posting.cost
                        val costCurrency = cost?.currency
                        if (costCurrency != null) {
                            costMap.getOrPut(posting.account) { mutableSetOf() }.add(costCurrency)
                            if (costMap[posting.account]!!.size > 1) {
                                costSourceMap[posting.account] = entry
                            }
                        }
                    }
                }
                is Balance -> {
                    if (entry.account !in skipAccounts) {
                        unitsMap.getOrPut(entry.account) { mutableSetOf() }.add(entry.amount.currency)
                        if (unitsMap[entry.account]!!.size > 1) {
                            unitsSourceMap[entry.account] = entry
                        }
                    }
                }
                else -> {}
            }
        }

        val errors = mutableListOf<BeancountError>()

        // Check units
        for ((account, currencies) in unitsMap) {
            if (account in skipAccounts) continue
            if (currencies.size > 1) {
                errors.add(
                    OneCommodityError(
                        source = unitsSourceMap[account]?.meta ?: emptyMap(),
                        message = "More than one currency in account '$account': ${currencies.joinToString(",")}",
                        account = account
                    )
                )
            }
        }

        // Check costs
        for ((account, currencies) in costMap) {
            if (account in skipAccounts) continue
            if (currencies.size > 1) {
                errors.add(
                    OneCommodityError(
                        source = costSourceMap[account]?.meta ?: emptyMap(),
                        message = "More than one cost currency in account '$account': ${currencies.joinToString(",")}",
                        account = account
                    )
                )
            }
        }

        return entries to errors
    }
}

/**
 * Error type for one commodity validation.
 */
data class OneCommodityError(
    override val source: Meta,
    override val message: String,
    val account: Account
) : BeancountError {
    override val entry: Directive? = null
}
