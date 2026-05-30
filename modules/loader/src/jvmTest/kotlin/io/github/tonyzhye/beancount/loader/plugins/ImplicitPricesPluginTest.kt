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

class ImplicitPricesPluginTest {

    @Test
    fun `should create Price from explicit price posting`() {
        val entries = listOf(
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Currency conversion",
                postings = listOf(
                    Posting(
                        account = "Assets:Bank:Checking",
                        units = Amount(Decimal("100"), "USD"),
                        price = Amount(Decimal("1.10"), "CAD")
                    ),
                    Posting(
                        account = "Assets:Bank:Checking",
                        units = Amount(Decimal("-110"), "CAD")
                    )
                )
            )
        )

        val (result, errors) = ImplicitPricesPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        val priceEntries = result.filterIsInstance<Price>()
        assertEquals(1, priceEntries.size)
        assertEquals("USD", priceEntries[0].currency)
        assertEquals(Amount(Decimal("1.10"), "CAD"), priceEntries[0].amount)
        assertEquals(LocalDate(2024, 1, 15), priceEntries[0].date)
    }

    @Test
    fun `should create Price from cost posting`() {
        val entries = listOf(
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Buy stock",
                postings = listOf(
                    Posting(
                        account = "Assets:Investments:Stocks",
                        units = Amount(Decimal("10"), "HOOL"),
                        cost = CostSpec(
                            numberPer = Decimal("564.20"),
                            currency = "USD",
                            date = LocalDate(2024, 1, 15)
                        )
                    ),
                    Posting(
                        account = "Assets:Bank:Checking",
                        units = Amount(Decimal("-5642.00"), "USD")
                    )
                )
            )
        )

        val (result, errors) = ImplicitPricesPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        val priceEntries = result.filterIsInstance<Price>()
        assertEquals(1, priceEntries.size)
        assertEquals("HOOL", priceEntries[0].currency)
        assertEquals(Amount(Decimal("564.20"), "USD"), priceEntries[0].amount)
    }

    @Test
    fun `should deduplicate same price entries`() {
        val entries = listOf(
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "First",
                postings = listOf(
                    Posting(
                        account = "Assets:Bank:Checking",
                        units = Amount(Decimal("100"), "USD"),
                        price = Amount(Decimal("1.10"), "CAD")
                    ),
                    Posting("Assets:Bank:Checking", Amount(Decimal("-110"), "CAD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 10),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Second",
                postings = listOf(
                    Posting(
                        account = "Assets:Bank:Checking",
                        units = Amount(Decimal("50"), "USD"),
                        price = Amount(Decimal("1.10"), "CAD")
                    ),
                    Posting("Assets:Bank:Checking", Amount(Decimal("-55"), "CAD"))
                )
            )
        )

        val (result, errors) = ImplicitPricesPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        val priceEntries = result.filterIsInstance<Price>()
        assertEquals(1, priceEntries.size)
    }

    @Test
    fun `should keep existing Price entries`() {
        val entries = listOf(
            Price(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                currency = "USD",
                amount = Amount(Decimal("1.35"), "CAD")
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(
                        account = "Assets:Bank:Checking",
                        units = Amount(Decimal("100"), "USD"),
                        price = Amount(Decimal("1.10"), "CAD")
                    ),
                    Posting("Assets:Bank:Checking", Amount(Decimal("-110"), "CAD"))
                )
            )
        )

        val (result, errors) = ImplicitPricesPlugin.transform(entries, Options())

        assertTrue(errors.isEmpty())
        val priceEntries = result.filterIsInstance<Price>()
        assertEquals(2, priceEntries.size)
    }
}
