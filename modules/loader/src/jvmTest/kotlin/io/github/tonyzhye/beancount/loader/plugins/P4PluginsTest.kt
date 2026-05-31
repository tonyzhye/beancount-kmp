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

class P4PluginsTest {

    // ===== SellGainsPlugin Tests =====

    @Test
    fun `sellgains should pass for valid sale with matching proceeds`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Brokerage:AAPL", Amount(Decimal("-10"), "AAPL"),
                        cost = CostSpec(numberPer = Decimal("150"), currency = "USD"),
                        price = Amount(Decimal("160"), "USD")),
                    Posting("Assets:Bank", Amount(Decimal("1600"), "USD"))
                )
            )
        )

        val (result, errors) = SellGainsPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(entries, result)
    }

    @Test
    fun `sellgains should fail when proceeds dont match price`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Brokerage:AAPL", Amount(Decimal("-10"), "AAPL"),
                        cost = CostSpec(numberPer = Decimal("150"), currency = "USD"),
                        price = Amount(Decimal("160"), "USD")),
                    Posting("Assets:Bank", Amount(Decimal("1500"), "USD")) // Wrong: should be 1600
                )
            )
        )

        val (result, errors) = SellGainsPlugin.transform(entries, Options())

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Invalid price vs. proceeds"))
    }

    @Test
    fun `sellgains should skip transactions without cost`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("-100"), "USD")),
                    Posting("Expenses:Food", Amount(Decimal("100"), "USD"))
                )
            )
        )

        val (result, errors) = SellGainsPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `sellgains should skip transactions without price on cost postings`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Brokerage:AAPL", Amount(Decimal("-10"), "AAPL"),
                        cost = CostSpec(numberPer = Decimal("150"), currency = "USD")),
                    Posting("Assets:Bank", Amount(Decimal("1500"), "USD"))
                )
            )
        )

        val (result, errors) = SellGainsPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
    }

    // ===== CheckDrainedPlugin Tests =====

    @Test
    fun `check drained should insert balance checks for closed assets account`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Project:Cash", currencies = listOf("USD")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Project:Cash", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            ),
            Close(emptyMap(), LocalDate(2024, 2, 1), "Assets:Project:Cash")
        )

        val (result, errors) = CheckDrainedPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(4, result.size)

        // Check that a balance was inserted after the close
        val balance = result.filterIsInstance<Balance>().first()
        assertEquals(LocalDate(2024, 2, 2), balance.date)
        assertEquals("Assets:Project:Cash", balance.account)
        assertEquals(Decimal.ZERO, balance.amount.number)
        assertEquals("USD", balance.amount.currency)
    }

    @Test
    fun `check drained should skip income accounts`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Income:Salary", currencies = listOf("USD")),
            Close(emptyMap(), LocalDate(2024, 2, 1), "Income:Salary")
        )

        val (result, errors) = CheckDrainedPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(2, result.size) // No balance inserted
    }

    @Test
    fun `check drained should skip if balance already exists`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Project:Cash", currencies = listOf("USD")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Project:Cash", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            ),
            Balance(emptyMap(), LocalDate(2024, 2, 1), "Assets:Project:Cash", Amount(Decimal.ZERO, "USD")),
            Close(emptyMap(), LocalDate(2024, 2, 1), "Assets:Project:Cash")
        )

        val (result, errors) = CheckDrainedPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(4, result.size) // No additional balance inserted
    }

    // ===== CloseTreePlugin Tests =====

    @Test
    fun `close tree should close subaccounts when parent is closed`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Brokerage:AAPL"),
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Brokerage:GOOG"),
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Brokerage"),
            Close(emptyMap(), LocalDate(2024, 2, 1), "Assets:Brokerage")
        )

        val (result, errors) = CloseTreePlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(6, result.size)

        val closes = result.filterIsInstance<Close>()
        assertEquals(3, closes.size)
        assertTrue(closes.any { it.account == "Assets:Brokerage:AAPL" })
        assertTrue(closes.any { it.account == "Assets:Brokerage:GOOG" })
        assertTrue(closes.any { it.account == "Assets:Brokerage" })
    }

    @Test
    fun `close tree should not re-close already closed subaccounts`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Brokerage:AAPL"),
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Brokerage:GOOG"),
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Brokerage"),
            Close(emptyMap(), LocalDate(2024, 2, 1), "Assets:Brokerage:AAPL"),
            Close(emptyMap(), LocalDate(2024, 2, 1), "Assets:Brokerage")
        )

        val (result, errors) = CloseTreePlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(6, result.size)

        val closes = result.filterIsInstance<Close>()
        assertEquals(3, closes.size) // GOOG + original AAPL + Brokerage
    }

    @Test
    fun `close tree should not close unrelated accounts`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank:Checking"),
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Brokerage:AAPL"),
            Close(emptyMap(), LocalDate(2024, 2, 1), "Assets:Brokerage")
        )

        val (result, errors) = CloseTreePlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(3, result.size)

        val closes = result.filterIsInstance<Close>()
        assertEquals(1, closes.size) // Only AAPL (Brokerage not in opens)
        assertTrue(closes.any { it.account == "Assets:Brokerage:AAPL" })
        assertFalse(closes.any { it.account == "Assets:Bank:Checking" })
    }
}
