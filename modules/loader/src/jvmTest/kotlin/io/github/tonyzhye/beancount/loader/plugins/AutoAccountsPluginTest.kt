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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AutoAccountsPluginTest {

    @Test
    fun `should insert Open directives for unopened accounts`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val (result, errors) = AutoAccountsPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        val openEntries = result.filterIsInstance<Open>()
        assertEquals(2, openEntries.size)
        assertTrue(openEntries.any { it.account == "Assets:Bank:Checking" })
        assertTrue(openEntries.any { it.account == "Income:Salary" })
    }

    @Test
    fun `should not insert Open for already opened accounts`() {
        val entries = listOf(
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank:Checking"
            ),
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val (result, errors) = AutoAccountsPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        val openEntries = result.filterIsInstance<Open>()
        assertEquals(2, openEntries.size) // 1 existing + 1 new for Income:Salary
        assertTrue(openEntries.any { it.account == "Assets:Bank:Checking" && it.date == LocalDate(2024, 1, 1) })
        assertTrue(openEntries.any { it.account == "Income:Salary" && it.date == LocalDate(2024, 1, 15) })
    }

    @Test
    fun `should use first use date for Open directive`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 3, 1),
                flag = "*",
                narration = "First",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100"), "USD"))
                )
            ),
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Earlier",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("50"), "USD"))
                )
            )
        )

        val (result, errors) = AutoAccountsPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        val openEntry = result.filterIsInstance<Open>().first()
        assertEquals(LocalDate(2024, 1, 1), openEntry.date)
    }

    @Test
    fun `should return entries unchanged if all accounts are opened`() {
        val entries = listOf(
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank:Checking"
            ),
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100"), "USD"))
                )
            )
        )

        val (result, errors) = AutoAccountsPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(entries.size, result.size)
    }
}
