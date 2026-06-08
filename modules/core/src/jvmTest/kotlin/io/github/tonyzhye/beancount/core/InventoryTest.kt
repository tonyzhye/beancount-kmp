package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Inventory class.
 * Based on beancount.core.inventory tests.
 */
class InventoryTest {

    private fun createAmount(number: String, currency: String) = Amount(Decimal(number), currency)
    
    private fun createCost(number: String, currency: String, date: LocalDate = LocalDate(2023, 1, 15), label: String? = null) = 
        Cost(Decimal(number), currency, date, label)

    @Test
    fun `should create empty inventory`() {
        val inv = Inventory()
        assertTrue(inv.isEmpty())
        assertEquals(0, inv.size())
    }

    @Test
    fun `should add amount without cost`() {
        val inv = Inventory()
        val (oldPos, result) = inv.addAmount(createAmount("100", "USD"))
        
        assertNull(oldPos)
        assertEquals(MatchResult.CREATED, result)
        assertEquals(1, inv.size())
        assertEquals(createAmount("100", "USD"), inv.getPositions()[0].units)
    }

    @Test
    fun `should add amount with cost`() {
        val inv = Inventory()
        val cost = createCost("50", "USD")
        val (oldPos, result) = inv.addAmount(createAmount("10", "AAPL"), cost)
        
        assertNull(oldPos)
        assertEquals(MatchResult.CREATED, result)
        assertEquals(1, inv.size())
        
        val position = inv.getPositions()[0]
        assertEquals(createAmount("10", "AAPL"), position.units)
        assertEquals(cost, position.cost)
    }

    @Test
    fun `should augment existing position`() {
        val inv = Inventory()
        inv.addAmount(createAmount("10", "AAPL"), createCost("50", "USD"))
        
        val (oldPos, result) = inv.addAmount(createAmount("5", "AAPL"), createCost("50", "USD"))
        
        assertEquals(createAmount("10", "AAPL"), oldPos?.units)
        assertEquals(MatchResult.AUGMENTED, result)
        assertEquals(1, inv.size())
        assertEquals(createAmount("15", "AAPL"), inv.getPositions()[0].units)
    }

    @Test
    fun `should reduce existing position`() {
        val inv = Inventory()
        inv.addAmount(createAmount("10", "AAPL"), createCost("50", "USD"))
        
        val (oldPos, result) = inv.addAmount(createAmount("-3", "AAPL"), createCost("50", "USD"))
        
        assertEquals(createAmount("10", "AAPL"), oldPos?.units)
        assertEquals(MatchResult.REDUCED, result)
        assertEquals(1, inv.size())
        assertEquals(createAmount("7", "AAPL"), inv.getPositions()[0].units)
    }

    @Test
    fun `should remove position when reduced to zero`() {
        val inv = Inventory()
        inv.addAmount(createAmount("10", "AAPL"), createCost("50", "USD"))
        
        val (oldPos, result) = inv.addAmount(createAmount("-10", "AAPL"), createCost("50", "USD"))
        
        assertEquals(createAmount("10", "AAPL"), oldPos?.units)
        assertEquals(MatchResult.REDUCED, result)
        assertTrue(inv.isEmpty())
    }

    @Test
    fun `should track multiple currencies separately`() {
        val inv = Inventory()
        inv.addAmount(createAmount("100", "USD"))
        inv.addAmount(createAmount("50", "EUR"))
        inv.addAmount(createAmount("10", "AAPL"), createCost("150", "USD"))
        
        assertEquals(3, inv.size())
        assertEquals(createAmount("100", "USD"), inv.getCurrencyUnits("USD"))
        assertEquals(createAmount("50", "EUR"), inv.getCurrencyUnits("EUR"))
        assertEquals(createAmount("10", "AAPL"), inv.getCurrencyUnits("AAPL"))
    }

    @Test
    fun `should track different costs separately`() {
        val inv = Inventory()
        inv.addAmount(createAmount("10", "AAPL"), createCost("100", "USD"))
        inv.addAmount(createAmount("5", "AAPL"), createCost("150", "USD"))
        
        assertEquals(2, inv.size())
        assertEquals(createAmount("15", "AAPL"), inv.getCurrencyUnits("AAPL"))
    }

    @Test
    fun `should detect empty inventory`() {
        val inv = Inventory()
        assertTrue(inv.isEmpty())
        
        inv.addAmount(createAmount("10", "USD"))
        assertFalse(inv.isEmpty())
    }

    @Test
    fun `should detect small positions`() {
        val inv = Inventory()
        inv.addAmount(createAmount("0.001", "USD"))
        inv.addAmount(createAmount("0.005", "EUR"))
        
        assertTrue(inv.isSmall(mapOf("USD" to Decimal("0.01"), "EUR" to Decimal("0.01"))))
        assertFalse(inv.isSmall(mapOf("USD" to Decimal("0.0001"))))
    }

    @Test
    fun `should detect mixed inventory`() {
        // Mixed: same currency with positive and negative positions (different costs)
        val inv1 = Inventory()
        inv1.addAmount(createAmount("10", "AAPL"), createCost("100", "USD"))
        inv1.addAmount(createAmount("-5", "AAPL"), createCost("150", "USD"))
        assertTrue(inv1.isMixed())
        
        // Not mixed: all positive
        val inv2 = Inventory()
        inv2.addAmount(createAmount("10", "USD"))
        inv2.addAmount(createAmount("5", "EUR"))
        assertFalse(inv2.isMixed())
        
        // Not mixed: same currency, all negative
        val inv3 = Inventory()
        inv3.addAmount(createAmount("-10", "AAPL"), createCost("100", "USD"))
        inv3.addAmount(createAmount("-5", "AAPL"), createCost("150", "USD"))
        assertFalse(inv3.isMixed())
    }

    @Test
    fun `should support iteration`() {
        val inv = Inventory()
        inv.addAmount(createAmount("10", "USD"))
        inv.addAmount(createAmount("20", "EUR"))
        
        val currencies = inv.map { it.units.currency }.toSet()
        assertEquals(setOf("USD", "EUR"), currencies)
    }

    @Test
    fun `should support copy`() {
        val inv1 = Inventory()
        inv1.addAmount(createAmount("10", "USD"))
        
        val inv2 = inv1.copy()
        inv2.addAmount(createAmount("5", "USD"))
        
        assertEquals(1, inv1.size())
        assertEquals(1, inv2.size())
        assertEquals(createAmount("10", "USD"), inv1.getPositions()[0].units)
        assertEquals(createAmount("15", "USD"), inv2.getPositions()[0].units)
    }

    @Test
    fun `should support plus operator`() {
        val inv1 = Inventory()
        inv1.addAmount(createAmount("10", "USD"))
        
        val inv2 = Inventory()
        inv2.addAmount(createAmount("5", "USD"))
        
        val inv3 = inv1 + inv2
        
        assertEquals(1, inv3.size())
        assertEquals(createAmount("15", "USD"), inv3.getPositions()[0].units)
        // Original inventories unchanged
        assertEquals(1, inv1.size())
        assertEquals(1, inv2.size())
    }

    @Test
    fun `should reduce inventory`() {
        val inv = Inventory()
        inv.addAmount(createAmount("10", "AAPL"), createCost("100", "USD"))
        inv.addAmount(createAmount("5", "AAPL"), createCost("150", "USD"))
        
        // Reduce to units only (strip cost)
        val reduced = inv.reduce { position -> position.units }
        
        assertEquals(1, reduced.size())
        assertEquals(createAmount("15", "AAPL"), reduced.getPositions()[0].units)
        assertNull(reduced.getPositions()[0].cost)
    }

    @Test
    fun `should create from list of positions`() {
        val positions = listOf(
            Position(createAmount("10", "USD")),
            Position(createAmount("5", "AAPL"), createCost("100", "USD"))
        )
        
        val inv = Inventory(positions)
        assertEquals(2, inv.size())
    }

    @Test
    fun `should support fromString for testing`() {
        val inv = Inventory.fromString("10 USD, 5 AAPL {100.00 USD}")
        
        assertEquals(2, inv.size())
        assertEquals(createAmount("10", "USD"), inv.getPositions()[0].units)
        assertEquals(createAmount("5", "AAPL"), inv.getPositions()[1].units)
        assertEquals("USD", inv.getPositions()[1].cost?.currency)
    }

    @Test
    fun `costMatches should match cost spec`() {
        val pos = Position(createAmount("10", "AAPL"), createCost("150", "USD"))
        val matchingSpec = CostSpec(Decimal("150"), null, "USD", null, null)
        val nonMatchingSpec = CostSpec(Decimal("200"), null, "USD", null, null)

        assertTrue(pos.costMatches(matchingSpec))
        assertFalse(pos.costMatches(nonMatchingSpec))
    }
}
