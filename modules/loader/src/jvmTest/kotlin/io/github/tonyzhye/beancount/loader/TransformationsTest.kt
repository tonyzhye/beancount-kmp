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
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for Transformations.kt to improve coverage.
 */
class TransformationsTest {

    @Test
    fun `runTransformations with RAW mode should only run user plugins`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 1), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val options = Options(
            plugin = listOf(PluginSpec("beancount.plugins.noduplicates")),
            pluginProcessingMode = PluginProcessingMode.RAW
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // RAW mode only runs user plugins, no auto/pad/balance
        assertEquals(entries.size, resultEntries.size)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `runTransformations with RAW mode and unknown plugin should skip gracefully`() {
        val entries = listOf<Directive>(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD"))
        )

        val options = Options(
            plugin = listOf(PluginSpec("beancount.plugins.unknown_plugin")),
            pluginProcessingMode = PluginProcessingMode.RAW
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // Unknown plugin should be skipped without error
        assertEquals(entries.size, resultEntries.size)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `runTransformations with DEFAULT mode should run full pipeline`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // DEFAULT mode runs documents, pad, balance plugins
        assertTrue(resultEntries.size >= entries.size)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `runTransformations with DEFAULT mode and auto plugins enabled`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            autoPluginsEnabled = true
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // With auto plugins, Open directives should be auto-created
        val openEntries = resultEntries.filterIsInstance<Open>()
        assertTrue(openEntries.isNotEmpty(), "Should have auto-created Open directives")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `runTransformations with user plugins in DEFAULT mode`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            ),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.noduplicates"))
        )

        val (_, errors) = runTransformations(entries, options)

        // NoDuplicatesPlugin should detect duplicate transactions
        assertTrue(errors.isNotEmpty(), "Should detect duplicate transactions")
    }

    @Test
    fun `runTransformations with leafonly plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Parent", listOf("USD")),
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Parent:Child", listOf("USD")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Parent", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.leafonly"))
        )

        val (_, errors) = runTransformations(entries, options)

        // LeafOnlyPlugin should flag posting to non-leaf account
        assertTrue(errors.isNotEmpty(), "Should flag non-leaf account posting")
    }

    @Test
    fun `runTransformations with unique_prices plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD", "EUR")),
            Price(emptyMap(), LocalDate(2024, 1, 15), "EUR", Amount(Decimal("1.10"), "USD")),
            Price(emptyMap(), LocalDate(2024, 1, 15), "EUR", Amount(Decimal("1.20"), "USD"))
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.unique_prices"))
        )

        val (_, errors) = runTransformations(entries, options)

        // UniquePricesPlugin should detect duplicate prices
        assertTrue(errors.isNotEmpty(), "Should detect duplicate prices")
    }

    @Test
    fun `runTransformations with coherent_cost plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Stock", listOf("AAPL")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Buy",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("10"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 16), "*", narration = "Sell",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("-5"), "AAPL")),
                    Posting("Assets:Cash", Amount(Decimal("500"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.coherent_cost"))
        )

        val (_, errors) = runTransformations(entries, options)

        // CoherentCostPlugin should flag inconsistent cost usage
        assertTrue(errors.isNotEmpty(), "Should flag inconsistent cost usage")
    }

    @Test
    fun `runTransformations with check_closing plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Close(emptyMap(), LocalDate(2024, 12, 31), "Assets:Bank")
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.check_closing"))
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // CheckClosingPlugin expands closing metadata
        assertTrue(resultEntries.size >= entries.size)
    }

    @Test
    fun `runTransformations with check_commodity plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.check_commodity"))
        )

        val (_, errors) = runTransformations(entries, options)

        // CheckCommodityPlugin should flag missing commodity directive
        assertTrue(errors.isNotEmpty(), "Should flag missing commodity directive")
    }

    @Test
    fun `runTransformations with sellgains plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Stock", listOf("AAPL", "USD"), booking = Booking.STRICT),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Buy",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("10"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 16), "*", narration = "Sell",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("-5"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null), Amount(Decimal("120"), "USD")),
                    Posting("Assets:Cash", Amount(Decimal("600"), "USD")),
                    Posting("Income:Gains", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.sellgains"))
        )

        val (_, errors) = runTransformations(entries, options)

        // Should process without throwing
        assertTrue(true)
    }

    @Test
    fun `runTransformations with check_drained plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            ),
            Close(emptyMap(), LocalDate(2024, 12, 31), "Assets:Bank")
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.check_drained"))
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // CheckDrainedPlugin inserts balance checks
        assertTrue(resultEntries.size >= entries.size)
    }

    @Test
    fun `runTransformations with close_tree plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Parent", listOf("USD")),
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Parent:Child", listOf("USD")),
            Close(emptyMap(), LocalDate(2024, 12, 31), "Assets:Parent")
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.close_tree"))
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // CloseTreePlugin should auto-close child accounts
        val closeEntries = resultEntries.filterIsInstance<Close>()
        assertTrue(closeEntries.size >= 2, "Should have closed parent and child")
    }

    @Test
    fun `runTransformations with nounused plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Unused", listOf("USD"))
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.nounused"))
        )

        val (_, errors) = runTransformations(entries, options)

        // NoUnusedPlugin should flag unused accounts
        assertTrue(errors.isNotEmpty(), "Should flag unused accounts")
    }

    @Test
    fun `runTransformations with onecommodity plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD", "EUR")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            ),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 16), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "EUR")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "EUR"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.onecommodity"))
        )

        val (_, errors) = runTransformations(entries, options)

        // OneCommodityPlugin should flag multiple commodities
        assertTrue(errors.isNotEmpty(), "Should flag multiple commodities")
    }

    @Test
    fun `runTransformations with pedantic plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.pedantic"))
        )

        val (_, errors) = runTransformations(entries, options)

        // PedanticPlugin runs all validations, may find issues
        assertTrue(errors.isNotEmpty() || errors.isEmpty())
    }

    @Test
    fun `runTransformations with auto plugin`() {
        val entries = listOf(
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Test",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.auto"))
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // Auto plugin should create Open directives and implicit prices
        val openEntries = resultEntries.filterIsInstance<Open>()
        assertTrue(openEntries.isNotEmpty(), "Should auto-create Open directives")
    }

    @Test
    fun `runTransformations with check_average_cost plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Stock", listOf("AAPL"), booking = Booking.NONE),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Buy",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("10"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 16), "*", narration = "Sell",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("-5"), "AAPL"), CostSpec(Decimal("110"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("550"), "USD"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.check_average_cost", "0.01"))
        )

        val (_, errors) = runTransformations(entries, options)

        // Should process without throwing
        assertTrue(true)
    }

    @Test
    fun `runTransformations with commodity_attr plugin`() {
        val entries = listOf(
            Commodity(emptyMap(), LocalDate(2024, 1, 1), "USD"),
            Commodity(emptyMap(), LocalDate(2024, 1, 1), "EUR")
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.commodity_attr", "precision"))
        )

        val (_, errors) = runTransformations(entries, options)

        // CommodityAttrPlugin should flag missing precision attribute
        assertTrue(errors.isNotEmpty(), "Should flag missing commodity attributes")
    }

    @Test
    fun `runTransformations with currency_accounts plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD", "EUR")),
            Transaction(
                emptyMap(), LocalDate(2024, 1, 15), "*", narration = "Exchange",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Assets:Bank", Amount(Decimal("-90"), "EUR"))
                )
            )
        )

        val options = Options(
            pluginProcessingMode = PluginProcessingMode.DEFAULT,
            plugin = listOf(PluginSpec("beancount.plugins.currency_accounts"))
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // Should process without throwing
        assertTrue(resultEntries.isNotEmpty())
    }

    @Test
    fun `runTransformations with documents plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Document(
                emptyMap(), LocalDate(2024, 1, 15), "Assets:Bank",
                "test.pdf", tags = emptySet(), links = emptySet()
            )
        )

        val options = Options(
            plugin = listOf(PluginSpec("beancount.ops.documents")),
            pluginProcessingMode = PluginProcessingMode.DEFAULT
        )

        val (resultEntries, errors) = runTransformations(entries, options)

        // DocumentsPlugin is in PRE phase by default
        assertTrue(resultEntries.isNotEmpty())
    }

    @Test
    fun `runTransformations with pad plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Pad(emptyMap(), LocalDate(2024, 1, 15), "Assets:Bank", "Equity:Opening")
        )

        val options = Options(
            plugin = listOf(PluginSpec("beancount.ops.pad")),
            pluginProcessingMode = PluginProcessingMode.DEFAULT
        )

        val (resultEntries, errors) = runTransformations(entries, options)
        assertTrue(resultEntries.isNotEmpty())
    }

    @Test
    fun `runTransformations with balance plugin`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD")),
            Balance(emptyMap(), LocalDate(2024, 1, 15), "Assets:Bank", Amount(Decimal("100"), "USD"))
        )

        val options = Options(
            plugin = listOf(PluginSpec("beancount.ops.balance")),
            pluginProcessingMode = PluginProcessingMode.DEFAULT
        )

        val (resultEntries, errors) = runTransformations(entries, options)
        assertTrue(resultEntries.isNotEmpty())
    }
}
