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

package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*

/**
 * Compare two parsed outputs for equality.
 * Based on beancount.parser.cmptest.
 *
 * This is useful for testing that different parsers or parser versions
 * produce equivalent output.
 */
object CmpTest {

    /**
     * Compare two lists of entries for structural equality.
     * Ignores metadata differences (filename, lineno) by default.
     *
     * @param entries1 First list of entries.
     * @param entries2 Second list of entries.
     * @param ignoreMeta Whether to ignore metadata differences.
     * @return True if the entries are structurally equal.
     */
    fun compareEntries(
        entries1: List<Directive>,
        entries2: List<Directive>,
        ignoreMeta: Boolean = true
    ): Boolean {
        if (entries1.size != entries2.size) return false

        for (i in entries1.indices) {
            if (!compareEntry(entries1[i], entries2[i], ignoreMeta)) {
                return false
            }
        }
        return true
    }

    /**
     * Compare two entries for structural equality.
     *
     * @param entry1 First entry.
     * @param entry2 Second entry.
     * @param ignoreMeta Whether to ignore metadata differences.
     * @return True if the entries are structurally equal.
     */
    fun compareEntry(entry1: Directive, entry2: Directive, ignoreMeta: Boolean = true): Boolean {
        if (entry1::class != entry2::class) return false
        if (entry1.date != entry2.date) return false

        if (!ignoreMeta) {
            if (entry1.meta != entry2.meta) return false
        }

        return when {
            entry1 is Transaction && entry2 is Transaction -> {
                entry1.flag == entry2.flag &&
                entry1.payee == entry2.payee &&
                entry1.narration == entry2.narration &&
                entry1.tags == entry2.tags &&
                entry1.links == entry2.links &&
                comparePostings(entry1.postings, entry2.postings, ignoreMeta)
            }
            entry1 is Open && entry2 is Open -> {
                entry1.account == entry2.account &&
                entry1.currencies == entry2.currencies &&
                entry1.booking == entry2.booking
            }
            entry1 is Close && entry2 is Close -> {
                entry1.account == entry2.account
            }
            entry1 is Balance && entry2 is Balance -> {
                entry1.account == entry2.account &&
                entry1.amount == entry2.amount &&
                entry1.tolerance == entry2.tolerance
            }
            entry1 is Pad && entry2 is Pad -> {
                entry1.account == entry2.account &&
                entry1.sourceAccount == entry2.sourceAccount
            }
            entry1 is Commodity && entry2 is Commodity -> {
                entry1.currency == entry2.currency
            }
            entry1 is Price && entry2 is Price -> {
                entry1.currency == entry2.currency &&
                entry1.amount == entry2.amount
            }
            entry1 is Event && entry2 is Event -> {
                entry1.type == entry2.type &&
                entry1.description == entry2.description
            }
            entry1 is Query && entry2 is Query -> {
                entry1.name == entry2.name &&
                entry1.queryString == entry2.queryString
            }
            entry1 is Note && entry2 is Note -> {
                entry1.account == entry2.account &&
                entry1.comment == entry2.comment &&
                entry1.tags == entry2.tags &&
                entry1.links == entry2.links
            }
            entry1 is Document && entry2 is Document -> {
                entry1.account == entry2.account &&
                entry1.filename == entry2.filename &&
                entry1.tags == entry2.tags &&
                entry1.links == entry2.links
            }
            entry1 is Custom && entry2 is Custom -> {
                entry1.type == entry2.type &&
                entry1.values == entry2.values
            }
            entry1 is Include && entry2 is Include -> {
                entry1.filename == entry2.filename
            }
            else -> true
        }
    }

    /**
     * Compare two lists of postings for equality.
     */
    private fun comparePostings(
        postings1: List<Posting>,
        postings2: List<Posting>,
        ignoreMeta: Boolean
    ): Boolean {
        if (postings1.size != postings2.size) return false

        for (i in postings1.indices) {
            val p1 = postings1[i]
            val p2 = postings2[i]

            if (p1.account != p2.account) return false
            if (p1.units != p2.units) return false
            if (p1.cost != p2.cost) return false
            if (p1.price != p2.price) return false
            if (p1.flag != p2.flag) return false

            if (!ignoreMeta && p1.meta != p2.meta) return false
        }
        return true
    }

    /**
     * Find differences between two lists of entries.
     * Returns a list of human-readable difference descriptions.
     *
     * @param entries1 First list of entries.
     * @param entries2 Second list of entries.
     * @param ignoreMeta Whether to ignore metadata differences.
     * @return A list of difference descriptions.
     */
    fun findDifferences(
        entries1: List<Directive>,
        entries2: List<Directive>,
        ignoreMeta: Boolean = true
    ): List<String> {
        val differences = mutableListOf<String>()

        if (entries1.size != entries2.size) {
            differences.add("Entry count differs: ${entries1.size} vs ${entries2.size}")
        }

        val minSize = minOf(entries1.size, entries2.size)
        for (i in 0 until minSize) {
            val diff = findEntryDifference(entries1[i], entries2[i], ignoreMeta, i)
            if (diff != null) {
                differences.add(diff)
            }
        }

        return differences
    }

    /**
     * Find the difference between two entries.
     * Returns a human-readable description or null if they are equal.
     */
    private fun findEntryDifference(
        entry1: Directive,
        entry2: Directive,
        ignoreMeta: Boolean,
        index: Int
    ): String? {
        if (entry1::class != entry2::class) {
            return "Entry $index: type differs (${entry1::class.simpleName} vs ${entry2::class.simpleName})"
        }

        if (entry1.date != entry2.date) {
            return "Entry $index: date differs (${entry1.date} vs ${entry2.date})"
        }

        if (!compareEntry(entry1, entry2, ignoreMeta)) {
            return "Entry $index (${entry1::class.simpleName} ${entry1.date}): content differs"
        }

        return null
    }

    /**
     * Assert that two lists of entries are equal.
     * Throws AssertionError with detailed message if they differ.
     *
     * @param entries1 First list of entries.
     * @param entries2 Second list of entries.
     * @param ignoreMeta Whether to ignore metadata differences.
     */
    fun assertEntriesEqual(
        entries1: List<Directive>,
        entries2: List<Directive>,
        ignoreMeta: Boolean = true
    ) {
        val differences = findDifferences(entries1, entries2, ignoreMeta)
        if (differences.isNotEmpty()) {
            throw AssertionError(
                "Entries differ:\n" +
                differences.joinToString("\n") { "  - $it" }
            )
        }
    }
}
