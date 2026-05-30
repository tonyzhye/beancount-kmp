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

package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LoaderTest {

    @Test
    fun `loadString should parse basic ledger`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent()

        val result = loadString(content)

        assertTrue(result.errors.isEmpty(), "Should have no errors: ${result.errors}")
        assertEquals(3, result.entries.size) // 2 Open + 1 Transaction
    }

    @Test
    fun `loadString with autoPluginsEnabled should auto-create Open directives`() {
        val content = """
            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent()

        // Without auto plugins
        val resultWithoutAuto = loadString(content, autoPluginsEnabled = false)
        val errorsWithoutAuto = resultWithoutAuto.errors.filter { it.message.contains("not open") }
        assertTrue(errorsWithoutAuto.isNotEmpty() || resultWithoutAuto.errors.isNotEmpty(),
            "Should have validation errors without auto plugins")

        // With auto plugins
        val resultWithAuto = loadString(content, autoPluginsEnabled = true)
        assertTrue(resultWithAuto.errors.isEmpty(),
            "Should have no errors with auto plugins: ${resultWithAuto.errors}")

        val openEntries = resultWithAuto.entries.filterIsInstance<Open>()
        assertEquals(2, openEntries.size)
        assertTrue(openEntries.any { it.account == "Assets:Bank:Checking" })
        assertTrue(openEntries.any { it.account == "Income:Salary" })
    }

    @Test
    fun `loadString with autoPluginsEnabled should create implicit prices`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking
            2024-01-01 open Assets:Investments:Stocks

            2024-01-15 * "Buy stock"
              Assets:Investments:Stocks  10 HOOL {564.20 USD}
              Assets:Bank:Checking
        """.trimIndent()

        val result = loadString(content, autoPluginsEnabled = true)

        assertTrue(result.errors.isEmpty(),
            "Should have no errors: ${result.errors}")

        val priceEntries = result.entries.filterIsInstance<Price>()
        assertEquals(1, priceEntries.size, "Should have 1 implicit price entry")
        assertEquals("HOOL", priceEntries[0].currency)
        assertEquals(Amount(Decimal("564.20"), "USD"), priceEntries[0].amount)
    }

    @Test
    fun `loadString with logTimings should log operations`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            
            2024-01-15 * "Test"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent()

        val timings = mutableListOf<String>()
        val logger = LoadTimingsLogger { operation, indent ->
            timings.add("  ".repeat(indent) + operation)
        }

        loadString(content, logTimings = logger, autoPluginsEnabled = true)

        assertTrue(timings.isNotEmpty())
        assertTrue(timings.any { it.contains("parse") })
        assertTrue(timings.any { it.contains("booking") })
        assertTrue(timings.any { it.contains("run_transformations") })
        assertTrue(timings.any { it.contains("validate") })
    }

    @Test
    fun `loadString should handle price postings with implicit prices`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking
            2024-01-01 open Assets:Bank:Checking:CAD

            2024-01-15 * "Currency conversion"
              Assets:Bank:Checking    100.00 USD @ 1.10 CAD
              Assets:Bank:Checking:CAD
        """.trimIndent()

        val result = loadString(content, autoPluginsEnabled = true)

        assertTrue(result.errors.isEmpty(),
            "Should have no errors: ${result.errors}")

        val priceEntries = result.entries.filterIsInstance<Price>()
        assertEquals(1, priceEntries.size)
        assertEquals("USD", priceEntries[0].currency)
        assertEquals(Amount(Decimal("1.10"), "CAD"), priceEntries[0].amount)
    }
}
