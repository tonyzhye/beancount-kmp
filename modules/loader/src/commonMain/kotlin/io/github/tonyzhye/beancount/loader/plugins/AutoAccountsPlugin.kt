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
 * Auto-accounts plugin.
 * Based on beancount.plugins.auto_accounts.
 *
 * Automatically inserts Open directives for accounts that are used in transactions
 * but don't have an explicit Open directive. This is useful for demos and initial
 * setup.
 *
 * The plugin also removes Open directives for accounts that are never used.
 */
object AutoAccountsPlugin {

    /**
     * Insert Open directives for accounts not opened.
     *
     * Open directives are inserted at the date of the first entry that uses the account.
     * Open directives for unused accounts are removed.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (updated entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        // Collect all explicitly opened accounts
        val openedAccounts = entries
            .filterIsInstance<Open>()
            .map { it.account }
            .toSet()

        // Collect all accounts used in transactions and their first use date
        val accountsFirstUsed = mutableMapOf<Account, LocalDate>()
        for (entry in entries) {
            if (entry is Transaction) {
                for (posting in entry.postings) {
                    val account = posting.account
                    val currentDate = accountsFirstUsed[account]
                    if (currentDate == null || entry.date < currentDate) {
                        accountsFirstUsed[account] = entry.date
                    }
                }
            }
        }

        // Create Open directives for accounts that are used but not opened
        val newOpenEntries = mutableListOf<Open>()
        for ((index, account) in accountsFirstUsed.keys.sorted().withIndex()) {
            if (account !in openedAccounts) {
                val meta = newMetadata("<auto_accounts>", index)
                val firstUsedDate = accountsFirstUsed[account]!!
                newOpenEntries.add(
                    Open(
                        meta = meta,
                        date = firstUsedDate,
                        account = account,
                        currencies = emptyList(),
                        booking = null
                    )
                )
            }
        }

        // Merge and sort if there are new entries
        return if (newOpenEntries.isNotEmpty()) {
            val allEntries = entries + newOpenEntries
            allEntries.sorted() to emptyList()
        } else {
            entries to emptyList()
        }
    }
}
