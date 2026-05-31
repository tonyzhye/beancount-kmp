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
 * No duplicates plugin.
 * Based on beancount.plugins.noduplicates.
 *
 * Validates that there are no duplicate transactions.
 * Uses hash comparison to detect duplicates (excluding metadata).
 */
object NoDuplicatesPlugin {

    /**
     * Check that the entries are unique by computing hashes.
     *
     * @param entries A list of directives.
     * @param options Parser options.
     * @return A pair of (entries, errors).
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val seen = mutableSetOf<Int>()
        val errors = mutableListOf<BeancountError>()

        for (entry in entries) {
            val hash = computeEntryHash(entry)
            if (hash in seen) {
                errors.add(
                    DuplicateEntryError(
                        source = entry.meta,
                        message = "Duplicate entry: ${entry::class.simpleName} on ${entry.date}",
                        entry = entry
                    )
                )
            } else {
                seen.add(hash)
            }
        }

        return entries to errors
    }

    /**
     * Compute a hash for an entry that excludes metadata fields.
     */
    private fun computeEntryHash(entry: Directive): Int {
        return when (entry) {
            is Transaction -> {
                val postingsHash = entry.postings.map { posting ->
                    listOf(
                        posting.account,
                        posting.units?.number?.toString(),
                        posting.units?.currency,
                        posting.cost?.numberPer?.toString(),
                        posting.cost?.currency,
                        posting.price?.number?.toString(),
                        posting.price?.currency
                    ).hashCode()
                }.hashCode()
                listOf(
                    entry.date,
                    entry.flag,
                    entry.payee,
                    entry.narration,
                    entry.tags.sorted(),
                    entry.links.sorted(),
                    postingsHash
                ).hashCode()
            }
            else -> listOf(entry.date, entry::class.simpleName, entry.toString()).hashCode()
        }
    }
}

/**
 * Error type for duplicate entries.
 */
data class DuplicateEntryError(
    override val source: Meta,
    override val message: String,
    override val entry: Directive?
) : BeancountError
