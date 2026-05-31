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
 * Close tree plugin.
 * Based on beancount.plugins.close_tree.
 *
 * When a parent account is closed, automatically insert Close directives
 * for all unclosed subaccounts.
 *
 * Example:
 *   2017-11-10 open Assets:Brokerage:AAPL
 *   2017-11-10 open Assets:Brokerage:ORNG
 *   2018-11-10 close Assets:Brokerage
 *
 * becomes:
 *   2018-11-10 close Assets:Brokerage:AAPL
 *   2018-11-10 close Assets:Brokerage:ORNG
 */
object CloseTreePlugin {

    /**
     * Insert close entries for all subaccounts of a closed account.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries with inserted closes, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val newEntries = mutableListOf<Directive>()
        val errors = mutableListOf<BeancountError>()

        val opens = entries.filterIsInstance<Open>().map { it.account }.toMutableSet()
        val closes = entries.filterIsInstance<Close>().map { it.account }.toMutableSet()

        for (entry in entries) {
            if (entry is Close) {
                // Find all subaccounts that are open but not yet closed
                val subaccounts = opens.filter { account ->
                    account.startsWith("${entry.account}:") && account !in closes
                }

                for (subacc in subaccounts) {
                    val closeEntry = Close(
                        meta = newMetadata("<beancount.plugins.close_tree>", 0),
                        date = entry.date,
                        account = subacc
                    )
                    newEntries.add(closeEntry)
                    // Mark as closed so we don't re-close grandchildren
                    closes.add(subacc)
                }

                // Only add the original close if the account was explicitly opened
                if (entry.account in opens) {
                    newEntries.add(entry)
                }
            } else {
                newEntries.add(entry)
            }
        }

        return newEntries to errors
    }
}
