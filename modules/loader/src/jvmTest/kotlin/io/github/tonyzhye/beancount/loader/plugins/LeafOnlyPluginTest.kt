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

class LeafOnlyPluginTest {

    @Test
    fun `should detect postings on non-leaf accounts`() {
        val entries = listOf(
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank"
            ),
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank:Checking"
            ),
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Income:Salary"
            ),
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Paycheck",
                postings = listOf(
                    // Posting to parent account (non-leaf because it has child)
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val (result, errors) = LeafOnlyPlugin.transform(entries, Options())

        assertEquals(entries.size, result.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Non-leaf account 'Assets:Bank'"))
    }

    @Test
    fun `should allow Open and Balance on non-leaf accounts`() {
        val entries = listOf(
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank"
            ),
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank:Checking"
            ),
            Balance(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 31),
                account = "Assets:Bank",
                amount = Amount(Decimal("0"), "USD")
            )
        )

        val (result, errors) = LeafOnlyPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `should not error on leaf accounts`() {
        val entries = listOf(
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank:Checking"
            ),
            Open(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                account = "Income:Salary"
            ),
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Paycheck",
                postings = listOf(
                    Posting("Assets:Bank:Checking", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val (result, errors) = LeafOnlyPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
    }
}
