package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for categorizeByCurrency with ante-inventory inference.
 * Ported from Python beancount.parser.booking_full_test.
 */
class CategorizeByCurrencyTest {

    private val txnDate = LocalDate(2023, 1, 15)
    private val emptyMeta = emptyMap<String, Any>()

    // ---- Ante-inventory inference tests ----

    @Test
    fun `categorizeByCurrency infers units currency from ante-inventory`() {
        // Account has only USD in inventory; posting with missing units currency
        // should be inferred as USD.
        val inventory = Inventory()
        inventory.addAmount(Amount(Decimal("100"), "USD"))

        val transaction = Transaction(
            meta = emptyMeta, date = txnDate, flag = "*", narration = "Test",
            postings = listOf(
                Posting("Assets:Account", Amount(Decimal("50"), "USD")),
                Posting("Assets:Other", null) // missing units
            )
        )

        val (groups, errors) = Booking.categorizeByCurrency(
            transaction, mapOf("Assets:Account" to inventory)
        )

        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")
        assertEquals(1, groups.size)
        assertEquals("USD", groups[0].first)
        // Both postings should be in the USD group
        assertEquals(2, groups[0].second.size)
    }

    @Test
    fun `categorizeByCurrency infers cost currency from ante-inventory`() {
        // Account has only USD cost currency; posting with missing cost currency
        // should be inferred as USD.
        val inventory = Inventory()
        inventory.addAmount(Amount(Decimal("10"), "AAPL"), Cost(Decimal("100"), "USD", txnDate))

        val transaction = Transaction(
            meta = emptyMeta, date = txnDate, flag = "*", narration = "Test",
            postings = listOf(
                Posting("Assets:Account", Amount(Decimal("5"), "AAPL"), CostSpec(currency = "USD")),
                Posting("Assets:Other", Amount(Decimal("-500"), "USD"))
            )
        )

        val (groups, errors) = Booking.categorizeByCurrency(
            transaction, mapOf("Assets:Account" to inventory)
        )

        assertEquals(0, errors.size, "Expected no errors: ${errors.map { it.message }}")
        assertEquals(1, groups.size)
        assertEquals("USD", groups[0].first)
    }

    @Test
    fun `categorizeByCurrency does not infer when multiple inventory currencies exist`() {
        // Account has both USD and CAD; missing currency should not be inferred.
        val inventory = Inventory()
        inventory.addAmount(Amount(Decimal("100"), "USD"))
        inventory.addAmount(Amount(Decimal("100"), "CAD"))

        val transaction = Transaction(
            meta = emptyMeta, date = txnDate, flag = "*", narration = "Test",
            postings = listOf(
                Posting("Assets:Account", Amount(Decimal("50"), "USD")),
                Posting("Assets:Other", null) // missing units, ambiguous inventory
            )
        )

        val (groups, errors) = Booking.categorizeByCurrency(
            transaction, mapOf("Assets:Other" to inventory)
        )

        // Unknown posting falls back to single existing group
        assertEquals(1, groups.size)
        assertEquals("USD", groups[0].first)
        assertEquals(2, groups[0].second.size)
    }

    // ---- Auto-postings tests ----

    @Test
    fun `categorizeByCurrency auto-posting copied to single group`() {
        val transaction = Transaction(
            meta = emptyMeta, date = txnDate, flag = "*", narration = "Test",
            postings = listOf(
                Posting("Assets:Account", Amount(Decimal("100"), "USD")),
                Posting("Assets:Other", null) // auto-posting
            )
        )

        val (groups, errors) = Booking.categorizeByCurrency(transaction, emptyMap())

        assertEquals(0, errors.size)
        assertEquals(1, groups.size)
        assertEquals("USD", groups[0].first)
        // Auto-posting should be replicated into the USD group
        assertEquals(2, groups[0].second.size)
    }

    @Test
    fun `categorizeByCurrency multiple auto-postings report error`() {
        val transaction = Transaction(
            meta = emptyMeta, date = txnDate, flag = "*", narration = "Test",
            postings = listOf(
                Posting("Assets:Account", Amount(Decimal("100"), "USD")),
                Posting("Assets:Other", null), // auto-posting 1
                Posting("Assets:Third", null)  // auto-posting 2
            )
        )

        val (groups, errors) = Booking.categorizeByCurrency(transaction, emptyMap())

        assertTrue(errors.isNotEmpty(), "Expected error for multiple auto-postings")
        assertTrue(errors.any { it.message.contains("more than one auto-posting") },
            "Error should mention auto-posting: ${errors.map { it.message }}")
        // Only the first auto-posting is kept and replicated into the group
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].second.size) // original + 1 auto-posting (first kept, second dropped with error)
    }

    @Test
    fun `categorizeByCurrency auto-postings with no groups result in empty groups`() {
        // All postings are auto-postings (no known currencies)
        val transaction = Transaction(
            meta = emptyMeta, date = txnDate, flag = "*", narration = "Test",
            postings = listOf(
                Posting("Assets:Account", null),
                Posting("Assets:Other", null)
            )
        )

        val (groups, errors) = Booking.categorizeByCurrency(transaction, emptyMap())

        assertEquals(0, groups.size)
        // No auto-posting error when groups is empty (caller handles this case)
        assertEquals(0, errors.size)
    }

    // ---- Cost/price currency consistency tests ----

    @Test
    fun `categorizeByCurrency enforces cost price currency consistency`() {
        val transaction = Transaction(
            meta = emptyMeta, date = txnDate, flag = "*", narration = "Test",
            postings = listOf(
                Posting(
                    "Assets:Account",
                    Amount(Decimal("10"), "AAPL"),
                    CostSpec(numberPer = Decimal("100"), currency = "USD"),
                    price = Amount(Decimal("110"), "USD")
                ),
                Posting("Assets:Other", Amount(Decimal("-1000"), "USD"))
            )
        )

        val (groups, errors) = Booking.categorizeByCurrency(transaction, emptyMap())

        assertEquals(0, errors.size)
        assertEquals(1, groups.size)
        assertEquals("USD", groups[0].first)
    }

    // ---- Multiple groups tests ----

    @Test
    fun `categorizeByCurrency multiple currency groups`() {
        val transaction = Transaction(
            meta = emptyMeta, date = txnDate, flag = "*", narration = "Test",
            postings = listOf(
                Posting("Assets:Account1", Amount(Decimal("100"), "USD")),
                Posting("Assets:Account2", Amount(Decimal("-80"), "USD")),
                Posting("Assets:Account3", Amount(Decimal("200"), "CAD")),
                Posting("Assets:Account4", Amount(Decimal("-200"), "CAD"))
            )
        )

        val (groups, errors) = Booking.categorizeByCurrency(transaction, emptyMap())

        assertEquals(0, errors.size)
        assertEquals(2, groups.size)
        val currencies = groups.map { it.first }.toSet()
        assertTrue(currencies.contains("USD"))
        assertTrue(currencies.contains("CAD"))
    }

    // ---- replaceCurrencies tests ----

    @Test
    fun `replaceCurrencies preserves postings with known currencies`() {
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("100"), "USD")),
            Posting("Assets:Other", Amount(Decimal("-100"), "USD"))
        )
        val referGroups = listOf(
            "USD" to listOf(
                Booking.CurrencyRefer(0, "USD", null, null),
                Booking.CurrencyRefer(1, "USD", null, null)
            )
        )

        val result = Booking.replaceCurrencies(postings, referGroups)

        assertEquals(1, result.size)
        assertEquals("USD", result[0].first)
        assertEquals(2, result[0].second.size)
        assertEquals(Amount(Decimal("100"), "USD"), result[0].second[0].units)
        assertEquals(Amount(Decimal("-100"), "USD"), result[0].second[1].units)
    }

    @Test
    fun `replaceCurrencies keeps null units for interpolation`() {
        // Missing units posting should remain with null units after replaceCurrencies
        val postings = listOf(
            Posting("Assets:Account", Amount(Decimal("100"), "USD")),
            Posting("Assets:Other", null)
        )
        val referGroups = listOf(
            "USD" to listOf(
                Booking.CurrencyRefer(0, "USD", null, null),
                Booking.CurrencyRefer(1, "USD", null, null)
            )
        )

        val result = Booking.replaceCurrencies(postings, referGroups)

        assertEquals(1, result.size)
        assertEquals(2, result[0].second.size)
        assertEquals(Amount(Decimal("100"), "USD"), result[0].second[0].units)
        assertEquals(null, result[0].second[1].units)
    }
}
