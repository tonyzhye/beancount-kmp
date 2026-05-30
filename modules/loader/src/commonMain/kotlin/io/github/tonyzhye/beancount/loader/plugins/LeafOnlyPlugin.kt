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
 * Leaf-only plugin.
 * Based on beancount.plugins.leafonly.
 *
 * Issues errors when amounts are posted to non-leaf accounts,
 * that is, accounts with child accounts.
 *
 * This is an extra constraint that you may want to apply optionally.
 * If you install this plugin, it will issue errors for all accounts
 * that have postings to non-leaf accounts.
 */
object LeafOnlyPlugin {

    /**
     * Check for non-leaf accounts that have postings on them.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        // Build realization tree (without computing balances)
        val realRoot = realize(entries, computeBalance = false)

        // Build open/close map for error reporting
        val openCloseMap = mutableMapOf<Account, Open?>()
        for (entry in entries) {
            if (entry is Open) {
                openCloseMap[entry.account] = entry
            }
        }

        val errors = mutableListOf<BeancountError>()
        val defaultMeta = newMetadata("<leafonly>", 0)

        // Iterate over all accounts in the tree
        for (realAccount in realRoot.iterate()) {
            // Skip leaf accounts (no children)
            if (realAccount.isLeaf) continue

            // Skip accounts with no postings
            if (realAccount.txnPostings.isEmpty()) continue

            // Check if all postings are allowed types (Open, Balance)
            val allowedTypes = setOf(Open::class, Balance::class)
            val allAllowed = realAccount.txnPostings.all { posting ->
                when (posting) {
                    is Open -> true
                    is Balance -> true
                    is TxnPosting -> false
                    is Pad -> false
                    else -> false
                }
            }
            if (allAllowed) continue

            // This is a non-leaf account with postings - generate error
            val openEntry = openCloseMap[realAccount.account]
            errors.add(
                LeafOnlyError(
                    source = openEntry?.meta ?: defaultMeta,
                    message = "Non-leaf account '${realAccount.account}' has postings on it",
                    entry = openEntry
                )
            )
        }

        return entries to errors
    }
}

/**
 * Error type for leaf-only validation.
 */
data class LeafOnlyError(
    override val source: Meta,
    override val message: String,
    override val entry: Directive? = null
) : BeancountError
