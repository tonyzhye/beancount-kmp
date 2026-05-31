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

class ValidationPluginsTest {

    // ===== UniquePricesPlugin Tests =====

    @Test
    fun `unique prices should pass with no duplicates`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("1.25"), "CAD")),
            Price(emptyMap(), LocalDate(2024, 1, 2), "USD", Amount(Decimal("1.26"), "CAD"))
        )

        val (result, errors) = UniquePricesPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(entries, result)
    }

    @Test
    fun `unique prices should pass with same price on same date`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("1.25"), "CAD")),
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("1.25"), "CAD"))
        )

        val (result, errors) = UniquePricesPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `unique prices should fail with different prices on same date`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("1.25"), "CAD")),
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("1.26"), "CAD"))
        )

        val (result, errors) = UniquePricesPlugin.transform(entries, Options())

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Disagreeing price entries"))
    }

    @Test
    fun `unique prices should pass with different currency pairs`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("1.25"), "CAD")),
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )

        val (result, errors) = UniquePricesPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
    }

    // ===== CoherentCostPlugin Tests =====

    @Test
    fun `coherent cost should pass when currency always used without cost`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Expenses:Food", Amount(Decimal("50"), "USD")),
                    Posting("Assets:Bank", Amount(Decimal("-50"), "USD"))
                )
            )
        )

        val (result, errors) = CoherentCostPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `coherent cost should pass when currency always used with cost`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"),
                        cost = CostSpec(numberPer = Decimal("150"), currency = "USD", date = LocalDate(2024, 1, 15))),
                    Posting("Assets:Bank", Amount(Decimal("-1500"), "USD"))
                )
            )
        )

        val (result, errors) = CoherentCostPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `coherent cost should fail when currency used with and without cost`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL"),
                        cost = CostSpec(numberPer = Decimal("150"), currency = "USD", date = LocalDate(2024, 1, 15))),
                    Posting("Assets:Bank", Amount(Decimal("-1500"), "USD"))
                )
            ),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 16), "*",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("5"), "AAPL")),
                    Posting("Assets:Bank", Amount(Decimal("-750"), "USD"))
                )
            )
        )

        val (result, errors) = CoherentCostPlugin.transform(entries, Options())

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("AAPL"))
        assertTrue(errors[0].message.contains("used both with and without cost"))
    }

    // ===== CheckClosingPlugin Tests =====

    @Test
    fun `check closing should expand closing metadata to balance`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                narration = "Sell stock",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("-10"), "AAPL"),
                        cost = CostSpec(numberPer = Decimal("150"), currency = "USD", date = LocalDate(2024, 1, 1)),
                        meta = mapOf("closing" to true)),
                    Posting("Assets:Bank", Amount(Decimal("1500"), "USD"))
                )
            )
        )

        val (result, errors) = CheckClosingPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(2, result.size)

        // Check that the balance was inserted
        val balance = result.filterIsInstance<Balance>().first()
        assertEquals(LocalDate(2024, 1, 16), balance.date)
        assertEquals("Assets:Invest", balance.account)
        assertEquals(Decimal.ZERO, balance.amount.number)
        assertEquals("AAPL", balance.amount.currency)

        // Check that closing metadata was removed
        val txn = result.filterIsInstance<Transaction>().first()
        val posting = txn.postings[0]
        assertNull(posting.meta?.get("closing"))
    }

    @Test
    fun `check closing should not affect non-closing postings`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL")),
                    Posting("Assets:Bank", Amount(Decimal("-1500"), "USD"))
                )
            )
        )

        val (result, errors) = CheckClosingPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(1, result.size)
    }

    // ===== PedanticPlugin Tests =====

    @Test
    fun `pedantic should run all validations`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Invest"),
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Invest:Sub"),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Invest", Amount(Decimal("10"), "AAPL")),
                    Posting("Assets:Bank", Amount(Decimal("-1500"), "USD"))
                )
            ),
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("1.25"), "CAD")),
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("1.26"), "CAD"))
        )

        val (result, errors) = PedanticPlugin.transform(entries, Options())

        // Should have errors from leafonly (posting to parent) and unique_prices (duplicate prices)
        assertTrue(errors.isNotEmpty())
    }

    // ===== CheckCommodityPlugin Tests =====

    @Test
    fun `check commodity should pass when all commodities declared`() {
        val entries = listOf(
            Commodity(emptyMap(), LocalDate(2024, 1, 1), "USD"),
            Commodity(emptyMap(), LocalDate(2024, 1, 1), "AAPL"),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("10"), "AAPL")),
                    Posting("Assets:Cash", Amount(Decimal("-1500"), "USD"))
                )
            )
        )

        val (result, errors) = CheckCommodityPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        assertEquals(entries, result)
    }

    @Test
    fun `check commodity should fail for missing commodity in transaction`() {
        val entries = listOf(
            Commodity(emptyMap(), LocalDate(2024, 1, 1), "USD"),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("10"), "AAPL")),
                    Posting("Assets:Cash", Amount(Decimal("-1500"), "USD"))
                )
            )
        )

        val (result, errors) = CheckCommodityPlugin.transform(entries, Options())

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("AAPL"))
        assertTrue(errors[0].message.contains("Missing Commodity directive"))
    }

    @Test
    fun `check commodity should fail for missing commodity in open directive`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", currencies = listOf("USD", "AAPL"))
        )

        val (result, errors) = CheckCommodityPlugin.transform(entries, Options())

        assertEquals(2, errors.size)
        val currencies = errors.map { (it as CheckCommodityError).currency }.toSet()
        assertTrue("USD" in currencies)
        assertTrue("AAPL" in currencies)
    }

    @Test
    fun `check commodity should fail for missing commodity in price directive`() {
        val entries = listOf(
            Commodity(emptyMap(), LocalDate(2024, 1, 1), "USD"),
            Price(emptyMap(), LocalDate(2024, 1, 1), "AAPL", Amount(Decimal("150"), "USD"))
        )

        val (result, errors) = CheckCommodityPlugin.transform(entries, Options())

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("AAPL"))
    }

    @Test
    fun `check commodity should not report same currency twice`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("10"), "AAPL")),
                    Posting("Assets:Cash", Amount(Decimal("-1500"), "USD"))
                )
            ),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 16), "*",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("5"), "AAPL")),
                    Posting("Assets:Cash", Amount(Decimal("-750"), "USD"))
                )
            )
        )

        val (result, errors) = CheckCommodityPlugin.transform(entries, Options())

        assertEquals(2, errors.size)
        val currencies = errors.map { (it as CheckCommodityError).currency }.toSet()
        assertEquals(setOf("AAPL", "USD"), currencies)
    }
}
