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
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for CmpTest functionality.
 */
class CmpTestTest {

    private fun createTransaction(
        narration: String = "Test",
        tags: Set<String> = emptySet()
    ): Transaction {
        return Transaction(
            meta = mapOf("filename" to "test.beancount", "lineno" to 1),
            date = LocalDate(2024, 1, 1),
            flag = "*",
            narration = narration,
            tags = tags,
            postings = listOf(
                Posting(account = "Assets:Bank", units = Amount(Decimal("100"), "USD")),
                Posting(account = "Income:Salary", units = Amount(Decimal("-100"), "USD"))
            )
        )
    }

    @Test
    fun `compareEntries should return true for identical entries`() {
        val entries1 = listOf(createTransaction(), createTransaction())
        val entries2 = listOf(createTransaction(), createTransaction())

        assertTrue(CmpTest.compareEntries(entries1, entries2))
    }

    @Test
    fun `compareEntries should return false for different counts`() {
        val entries1 = listOf(createTransaction())
        val entries2 = listOf(createTransaction(), createTransaction())

        assertFalse(CmpTest.compareEntries(entries1, entries2))
    }

    @Test
    fun `compareEntries should return false for different content`() {
        val entries1 = listOf(createTransaction(narration = "A"))
        val entries2 = listOf(createTransaction(narration = "B"))

        assertFalse(CmpTest.compareEntries(entries1, entries2))
    }

    @Test
    fun `compareEntries should ignore meta differences by default`() {
        val entry1 = createTransaction().copy(
            meta = mapOf("filename" to "file1.beancount", "lineno" to 1)
        )
        val entry2 = createTransaction().copy(
            meta = mapOf("filename" to "file2.beancount", "lineno" to 2)
        )

        assertTrue(CmpTest.compareEntries(listOf(entry1), listOf(entry2)))
    }

    @Test
    fun `compareEntries should detect meta differences when requested`() {
        val entry1 = createTransaction().copy(
            meta = mapOf("filename" to "file1.beancount", "lineno" to 1)
        )
        val entry2 = createTransaction().copy(
            meta = mapOf("filename" to "file2.beancount", "lineno" to 2)
        )

        assertFalse(CmpTest.compareEntries(listOf(entry1), listOf(entry2), ignoreMeta = false))
    }

    @Test
    fun `findDifferences should report count differences`() {
        val entries1 = listOf(createTransaction())
        val entries2 = listOf(createTransaction(), createTransaction())

        val diffs = CmpTest.findDifferences(entries1, entries2)
        assertTrue(diffs.any { it.contains("count differs") })
    }

    @Test
    fun `findDifferences should report content differences`() {
        val entries1 = listOf(createTransaction(narration = "A"))
        val entries2 = listOf(createTransaction(narration = "B"))

        val diffs = CmpTest.findDifferences(entries1, entries2)
        assertTrue(diffs.isNotEmpty())
    }

    @Test
    fun `assertEntriesEqual should pass for identical entries`() {
        val entries = listOf(createTransaction())
        assertDoesNotThrow {
            CmpTest.assertEntriesEqual(entries, entries)
        }
    }

    @Test
    fun `assertEntriesEqual should throw for different entries`() {
        val entries1 = listOf(createTransaction(narration = "A"))
        val entries2 = listOf(createTransaction(narration = "B"))

        assertThrows(AssertionError::class.java) {
            CmpTest.assertEntriesEqual(entries1, entries2)
        }
    }

    @Test
    fun `compareEntry should detect different types`() {
        val open = Open(
            meta = mapOf("filename" to "test.beancount", "lineno" to 1),
            date = LocalDate(2024, 1, 1),
            account = "Assets:Bank",
            currencies = listOf("USD")
        )
        val close = Close(
            meta = mapOf("filename" to "test.beancount", "lineno" to 2),
            date = LocalDate(2024, 1, 1),
            account = "Assets:Bank"
        )

        assertFalse(CmpTest.compareEntry(open, close))
    }

    @Test
    fun `compareEntry should detect different dates`() {
        val entry1 = createTransaction().copy(date = LocalDate(2024, 1, 1))
        val entry2 = createTransaction().copy(date = LocalDate(2024, 2, 1))

        assertFalse(CmpTest.compareEntry(entry1, entry2))
    }
}
