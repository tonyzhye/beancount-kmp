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
 * Tests for sanityCheckTypes function.
 */
class SanityCheckTypesTest {

    @Test
    fun `sanityCheckTypes should pass for valid entries`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank",
                currencies = listOf("USD")
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "Assets:Bank", units = Amount(Decimal("100"), "USD")),
                    Posting(account = "Income:Salary", units = Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val errors = sanityCheckTypes(entries)
        assertTrue(errors.isEmpty(), "Expected no errors but got: ${errors.map { it.message }}")
    }

    @Test
    fun `sanityCheckTypes should detect empty account`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 1),
                account = "",
                currencies = listOf("USD")
            )
        )

        val errors = sanityCheckTypes(entries)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("empty account"))
    }

    @Test
    fun `sanityCheckTypes should detect empty currency`() {
        val entries = listOf(
            Open(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank",
                currencies = listOf("")
            )
        )

        val errors = sanityCheckTypes(entries)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("empty currency"))
    }

    @Test
    fun `sanityCheckTypes should detect transaction with no postings`() {
        val entries = listOf(
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = emptyList()
            )
        )

        val errors = sanityCheckTypes(entries)
        assertTrue(errors.any { it.message.contains("no postings") })
    }

    @Test
    fun `sanityCheckTypes should detect empty posting account`() {
        val entries = listOf(
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "", units = Amount(Decimal("100"), "USD"))
                )
            )
        )

        val errors = sanityCheckTypes(entries)
        assertTrue(errors.any { it.message.contains("empty account") })
    }

    @Test
    fun `sanityCheckTypes should detect empty document filename`() {
        val entries = listOf(
            Document(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 1),
                account = "Assets:Bank",
                filename = ""
            )
        )

        val errors = sanityCheckTypes(entries)
        assertTrue(errors.any { it.message.contains("empty filename") })
    }

    @Test
    fun `sanityCheckTypes should detect empty include filename`() {
        val entries = listOf(
            Include(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 1),
                filename = ""
            )
        )

        val errors = sanityCheckTypes(entries)
        assertTrue(errors.any { it.message.contains("empty filename") })
    }
}
