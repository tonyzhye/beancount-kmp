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
 * No unused accounts plugin.
 * Based on beancount.plugins.nounused.
 *
 * Validates that all accounts declared with Open directives are actually used
 * in at least one other directive. Accounts that are open but never used
 * generate warnings.
 */
object NoUnusedPlugin {

    /**
     * Check that all accounts declared open are actually used.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val openMap = mutableMapOf<Account, Open>()
        val referencedAccounts = mutableSetOf<Account>()

        for (entry in entries) {
            when (entry) {
                is Open -> {
                    openMap[entry.account] = entry
                }
                else -> {
                    referencedAccounts.addAll(getEntryAccounts(entry))
                }
            }
        }

        val errors = openMap
            .filter { (account, _) -> account !in referencedAccounts }
            .map { (account, openEntry) ->
                UnusedAccountError(
                    source = openEntry.meta,
                    message = "Unused account '$account'",
                    entry = openEntry
                )
            }

        return entries to errors
    }
}

/**
 * Error type for unused accounts.
 */
data class UnusedAccountError(
    override val source: Meta,
    override val message: String,
    override val entry: Directive?
) : BeancountError
