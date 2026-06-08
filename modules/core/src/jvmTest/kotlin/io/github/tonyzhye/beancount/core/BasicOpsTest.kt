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

package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for BasicOps functions.
 */
class BasicOpsTest {

    private fun createTransaction(
        tags: Set<String> = emptySet(),
        links: Set<String> = emptySet(),
        narration: String = "Test"
    ): Transaction {
        return Transaction(
            meta = mapOf("filename" to "test.beancount", "lineno" to 1),
            date = LocalDate(2024, 1, 1),
            flag = "*",
            narration = narration,
            tags = tags,
            links = links,
            postings = listOf(
                Posting(
                    account = "Assets:Bank",
                    units = Amount(Decimal("100"), "USD")
                ),
                Posting(
                    account = "Income:Salary",
                    units = Amount(Decimal("-100"), "USD")
                )
            )
        )
    }

    private fun createNote(account: String = "Assets:Bank", tags: Set<String>? = null, links: Set<String>? = null): Note {
        return Note(
            meta = mapOf("filename" to "test.beancount", "lineno" to 1),
            date = LocalDate(2024, 1, 1),
            account = account,
            comment = "Test note",
            tags = tags,
            links = links
        )
    }

    @Test
    fun `filterByTag should return entries with matching tags`() {
        val tx1 = createTransaction(tags = setOf("food", "monthly"))
        val tx2 = createTransaction(tags = setOf("travel"))
        val tx3 = createTransaction(tags = emptySet())

        val entries = listOf(tx1, tx2, tx3)
        val filtered = filterByTag(entries, setOf("food"))

        assertEquals(1, filtered.size)
        assertEquals(tx1, filtered[0])
    }

    @Test
    fun `filterByTag should return entries with any matching tag`() {
        val tx1 = createTransaction(tags = setOf("food"))
        val tx2 = createTransaction(tags = setOf("travel"))
        val tx3 = createTransaction(tags = setOf("food", "travel"))

        val entries = listOf(tx1, tx2, tx3)
        val filtered = filterByTag(entries, setOf("food", "travel"))

        assertEquals(3, filtered.size)
    }

    @Test
    fun `filterByTag should include notes with tags`() {
        val tx = createTransaction(tags = setOf("food"))
        val note = createNote(tags = setOf("important"))

        val entries = listOf(tx, note)
        val filtered = filterByTag(entries, setOf("important"))

        assertEquals(1, filtered.size)
        assertTrue(filtered[0] is Note)
    }

    @Test
    fun `filterByLink should return entries with matching links`() {
        val tx1 = createTransaction(links = setOf("invoice-2024-001"))
        val tx2 = createTransaction(links = setOf("invoice-2024-002"))

        val entries = listOf(tx1, tx2)
        val filtered = filterByLink(entries, setOf("invoice-2024-001"))

        assertEquals(1, filtered.size)
        assertEquals(tx1, filtered[0])
    }

    @Test
    fun `groupEntriesByLink should group entries by link`() {
        val tx1 = createTransaction(links = setOf("project-a"))
        val tx2 = createTransaction(links = setOf("project-a", "project-b"))
        val tx3 = createTransaction(links = setOf("project-b"))

        val entries = listOf(tx1, tx2, tx3)
        val grouped = groupEntriesByLink(entries)

        assertEquals(2, grouped["project-a"]?.size)
        assertEquals(2, grouped["project-b"]?.size)
    }

    @Test
    fun `getCommonAccounts should find common accounts between entries`() {
        val tx1 = createTransaction()
        val tx2 = Transaction(
            meta = mapOf("filename" to "test.beancount", "lineno" to 2),
            date = LocalDate(2024, 1, 2),
            flag = "*",
            narration = "Another",
            postings = listOf(
                Posting(account = "Assets:Bank", units = Amount(Decimal("50"), "USD")),
                Posting(account = "Expenses:Food", units = Amount(Decimal("-50"), "USD"))
            )
        )

        val common = getCommonAccounts(tx1, tx2)
        assertEquals(setOf("Assets:Bank"), common)
    }

    @Test
    fun `getAllTags should return all unique tags`() {
        val tx1 = createTransaction(tags = setOf("food", "monthly"))
        val tx2 = createTransaction(tags = setOf("travel"))
        val note = createNote(tags = setOf("important"))

        val tags = getAllTags(listOf(tx1, tx2, note))
        assertEquals(listOf("food", "important", "monthly", "travel"), tags)
    }

    @Test
    fun `getAllLinks should return all unique links`() {
        val tx1 = createTransaction(links = setOf("doc-1"))
        val tx2 = createTransaction(links = setOf("doc-2"))

        val links = getAllLinks(listOf(tx1, tx2))
        assertEquals(listOf("doc-1", "doc-2"), links)
    }

    @Test
    fun `findClosest should find closest entry to date`() {
        val tx1 = createTransaction().copy(date = LocalDate(2024, 1, 1))
        val tx2 = createTransaction().copy(date = LocalDate(2024, 6, 1))
        val tx3 = createTransaction().copy(date = LocalDate(2024, 12, 1))

        val closest = findClosest(listOf(tx1, tx2, tx3), LocalDate(2024, 5, 15))
        assertEquals(tx2, closest)
    }

    @Test
    fun `filterByDateWindow should return entries in range`() {
        val tx1 = createTransaction().copy(date = LocalDate(2024, 1, 1))
        val tx2 = createTransaction().copy(date = LocalDate(2024, 6, 1))
        val tx3 = createTransaction().copy(date = LocalDate(2024, 12, 1))

        val filtered = filterByDateWindow(
            listOf(tx1, tx2, tx3),
            LocalDate(2024, 3, 1),
            LocalDate(2024, 9, 1)
        )
        assertEquals(1, filtered.size)
        assertEquals(tx2, filtered[0])
    }

    @Test
    fun `removeAccountPostings should remove postings for account`() {
        val tx = createTransaction()
        val entries = listOf(tx)
        val filtered = removeAccountPostings(entries, "Income:Salary")

        val result = filtered[0] as Transaction
        assertEquals(1, result.postings.size)
        assertEquals("Assets:Bank", result.postings[0].account)
    }

    @Test
    fun `getAllPayees should return all payees`() {
        val tx1 = createTransaction().copy(payee = "Store A")
        val tx2 = createTransaction().copy(payee = "Store B")
        val tx3 = createTransaction().copy(payee = null)

        val payees = getAllPayees(listOf(tx1, tx2, tx3))
        assertEquals(listOf("Store A", "Store B"), payees)
    }

}
