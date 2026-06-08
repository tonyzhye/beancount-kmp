package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.core.Booking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for CheckAverageCostPlugin.
 */
class CheckAverageCostPluginTest {

    @Test
    fun `should pass when reducing at average cost`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2023, 1, 1), "Assets:Stock", listOf("AAPL"), booking = Booking.NONE),
            // Buy 10 AAPL at 100 USD
            Transaction(
                emptyMap(), LocalDate(2023, 1, 15), "*", narration = "Buy",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("10"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            // Sell 5 AAPL at 100 USD (exact average)
            Transaction(
                emptyMap(), LocalDate(2023, 2, 1), "*", narration = "Sell",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("-5"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("500"), "USD"))
                )
            )
        )

        val plugin = CheckAverageCostPlugin()
        val (_, errors) = plugin.transform(entries, Options())

        assertTrue(errors.isEmpty(), "Should have no errors for exact average cost: ${errors.map { it.message }}")
    }

    @Test
    fun `should pass when reducing within tolerance`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2023, 1, 1), "Assets:Stock", listOf("AAPL"), booking = Booking.NONE),
            // Buy 10 AAPL at 100 USD
            Transaction(
                emptyMap(), LocalDate(2023, 1, 15), "*", narration = "Buy",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("10"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            // Sell 5 AAPL at 100.5 USD (within 1% tolerance of 100)
            Transaction(
                emptyMap(), LocalDate(2023, 2, 1), "*", narration = "Sell",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("-5"), "AAPL"), CostSpec(Decimal("100.5"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("502.5"), "USD"))
                )
            )
        )

        val plugin = CheckAverageCostPlugin()
        val (_, errors) = plugin.transform(entries, Options())

        assertTrue(errors.isEmpty(), "Should have no errors within 1% tolerance")
    }

    @Test
    fun `should fail when reducing outside tolerance`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2023, 1, 1), "Assets:Stock", listOf("AAPL"), booking = Booking.NONE),
            // Buy 10 AAPL at 100 USD
            Transaction(
                emptyMap(), LocalDate(2023, 1, 15), "*", narration = "Buy",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("10"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            // Sell 5 AAPL at 110 USD (outside 1% tolerance of 100)
            Transaction(
                emptyMap(), LocalDate(2023, 2, 1), "*", narration = "Sell",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("-5"), "AAPL"), CostSpec(Decimal("110"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("550"), "USD"))
                )
            )
        )

        val plugin = CheckAverageCostPlugin()
        val (_, errors) = plugin.transform(entries, Options())

        assertEquals(1, errors.size, "Should have one error for cost outside tolerance")
        assertTrue(errors[0].message.contains("average cost"))
    }

    @Test
    fun `should ignore non-NONE booking accounts`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2023, 1, 1), "Assets:Stock", listOf("AAPL"), booking = Booking.STRICT),
            Transaction(
                emptyMap(), LocalDate(2023, 1, 15), "*", narration = "Buy",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("10"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            Transaction(
                emptyMap(), LocalDate(2023, 2, 1), "*", narration = "Sell",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("-5"), "AAPL"), CostSpec(Decimal("110"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("550"), "USD"))
                )
            )
        )

        val plugin = CheckAverageCostPlugin()
        val (_, errors) = plugin.transform(entries, Options())

        assertTrue(errors.isEmpty(), "Should ignore STRICT booking accounts")
    }

    @Test
    fun `should use custom tolerance from config`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2023, 1, 1), "Assets:Stock", listOf("AAPL"), booking = Booking.NONE),
            Transaction(
                emptyMap(), LocalDate(2023, 1, 15), "*", narration = "Buy",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("10"), "AAPL"), CostSpec(Decimal("100"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("-1000"), "USD"))
                )
            ),
            // Sell at 105 USD - within 5% but outside 1%
            Transaction(
                emptyMap(), LocalDate(2023, 2, 1), "*", narration = "Sell",
                postings = listOf(
                    Posting("Assets:Stock", Amount(Decimal("-5"), "AAPL"), CostSpec(Decimal("105"), null, "USD", null)),
                    Posting("Assets:Cash", Amount(Decimal("525"), "USD"))
                )
            )
        )

        // With default 1% tolerance, should fail
        val plugin1 = CheckAverageCostPlugin()
        val (_, errors1) = plugin1.transform(entries, Options())
        assertEquals(1, errors1.size, "Should fail with 1% tolerance")

        // With 5% tolerance, should pass
        val plugin5 = CheckAverageCostPlugin(Decimal("0.05"))
        val (_, errors5) = plugin5.transform(entries, Options())
        assertTrue(errors5.isEmpty(), "Should pass with 5% tolerance")
    }

    @Test
    fun `withConfig should parse config string`() {
        val plugin = CheckAverageCostPlugin.withConfig("0.05")
        assertEquals(Decimal("0.05"), plugin.tolerance)
    }
}
