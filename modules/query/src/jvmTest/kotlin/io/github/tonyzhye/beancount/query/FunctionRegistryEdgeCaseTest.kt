package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.compiler.Accumulator
import io.github.tonyzhye.beancount.query.compiler.EvalNode
import io.github.tonyzhye.beancount.query.compiler.RowContext
import io.github.tonyzhye.beancount.query.functions.AggregatorFactory
import io.github.tonyzhye.beancount.query.functions.FunctionRegistry
import io.github.tonyzhye.beancount.query.functions.FunctionSignature
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Additional tests for FunctionRegistry to cover edge cases and error paths.
 */
class FunctionRegistryEdgeCaseTest {

    @Test
    fun `quarter string function`() {
        val fn = FunctionRegistry.resolveFunction("quarter", listOf(BqlType.Date))
        // There are two overloads: Integer and String - resolveFunction may find either
        val result = fn?.implementation?.invoke(listOf(BqlDateValue(LocalDate(2024, 6, 15))))
        assertNotNull(result)
    }

    @Test
    fun `weekday string function`() {
        val fn = FunctionRegistry.resolveFunction("weekday", listOf(BqlType.Date))
        val result = fn?.implementation?.invoke(listOf(BqlDateValue(LocalDate(2024, 6, 15))))
        assertNotNull(result)
    }

    @Test
    fun `getvalue with priceMap`() {
        val entries = listOf<Directive>(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val pos = Position(Amount(Decimal("100"), "EUR"), null)

        val fn = FunctionRegistry.resolveFunction("getvalue", listOf(BqlType.Position, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlPositionValue(pos),
            BqlStringValue("USD"),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
        assertFalse(result!!.isNull())
        assertEquals("USD", result.asAmount().currency)
    }

    @Test
    fun `getvalue without priceMap should return null`() {
        val pos = Position(Amount(Decimal("100"), "EUR"), null)
        val fn = FunctionRegistry.resolveFunction("getvalue", listOf(BqlType.Position, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlPositionValue(pos),
            BqlStringValue("USD")
        ))
        assertTrue(result == null || result.isNull())
    }

    @Test
    fun `convert amount without priceMap should return null`() {
        val fn = FunctionRegistry.resolveFunction("convert", listOf(BqlType.Amount, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlAmountValue(Amount(Decimal("100"), "EUR")),
            BqlStringValue("USD")
        ))
        assertTrue(result == null || result.isNull())
    }

    @Test
    fun `convert position without priceMap should return null`() {
        val pos = Position(Amount(Decimal("100"), "EUR"), null)
        val fn = FunctionRegistry.resolveFunction("convert", listOf(BqlType.Position, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlPositionValue(pos),
            BqlStringValue("USD")
        ))
        assertTrue(result == null || result.isNull())
    }

    @Test
    fun `convert inventory with priceMap`() {
        val entries = listOf<Directive>(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val inv = Inventory()
        inv.addAmount(Amount(Decimal("100"), "EUR"))

        val fn = FunctionRegistry.resolveFunction("convert", listOf(BqlType.Inventory, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlInventoryValue(inv),
            BqlStringValue("USD"),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
        assertFalse(result!!.isNull())
    }

    @Test
    fun `convert inventory with date`() {
        val entries = listOf<Directive>(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val inv = Inventory()
        inv.addAmount(Amount(Decimal("100"), "EUR"))

        val fn = FunctionRegistry.resolveFunction("convert", listOf(BqlType.Inventory, BqlType.String, BqlType.Date))
        val result = fn?.implementation?.invoke(listOf(
            BqlInventoryValue(inv),
            BqlStringValue("USD"),
            BqlDateValue(LocalDate(2024, 1, 1)),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
        assertFalse(result!!.isNull())
    }

    @Test
    fun `value position with date`() {
        val entries = listOf<Directive>(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val pos = Position(Amount(Decimal("100"), "EUR"), null)

        val fn = FunctionRegistry.resolveFunction("value", listOf(BqlType.Position, BqlType.Date))
        val result = fn?.implementation?.invoke(listOf(
            BqlPositionValue(pos),
            BqlDateValue(LocalDate(2024, 1, 1)),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
        assertFalse(result!!.isNull())
    }

    @Test
    fun `value inventory without date`() {
        val entries = listOf<Directive>(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val inv = Inventory()
        inv.addAmount(Amount(Decimal("100"), "EUR"))

        val fn = FunctionRegistry.resolveFunction("value", listOf(BqlType.Inventory))
        val result = fn?.implementation?.invoke(listOf(
            BqlInventoryValue(inv),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
        assertFalse(result!!.isNull())
    }

    @Test
    fun `value inventory with date`() {
        val entries = listOf<Directive>(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val inv = Inventory()
        inv.addAmount(Amount(Decimal("100"), "EUR"))

        val fn = FunctionRegistry.resolveFunction("value", listOf(BqlType.Inventory, BqlType.Date))
        val result = fn?.implementation?.invoke(listOf(
            BqlInventoryValue(inv),
            BqlDateValue(LocalDate(2024, 1, 1)),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
        assertFalse(result!!.isNull())
    }

    @Test
    fun `getprice with date`() {
        val entries = listOf<Directive>(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.buildPriceMap(entries)

        val fn = FunctionRegistry.resolveFunction("getprice", listOf(BqlType.String, BqlType.String, BqlType.Date))
        val result = fn?.implementation?.invoke(listOf(
            BqlStringValue("EUR"),
            BqlStringValue("USD"),
            BqlDateValue(LocalDate(2024, 1, 1)),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
        assertFalse(result!!.isNull())
        assertEquals(Decimal("1.10"), result!!.asAmount().number)
    }

    @Test
    fun `open_date with entries`() {
        val entries = listOf<Directive>(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD"))
        )
        val fn = FunctionRegistry.resolveFunction("open_date", listOf(BqlType.String))
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val result = fn?.implementation?.invoke(listOf(
            BqlStringValue("Assets:Bank"),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
        assertEquals(LocalDate(2024, 1, 1), result!!.asDate())
    }

    @Test
    fun `open_date without entries should return null`() {
        val fn = FunctionRegistry.resolveFunction("open_date", listOf(BqlType.String))
        val result = fn?.implementation?.invoke(listOf(BqlStringValue("Assets:Bank")))
        assertTrue(result == null || result.isNull())
    }

    @Test
    fun `close_date with entries`() {
        val entries = listOf<Directive>(
            Close(emptyMap(), LocalDate(2024, 12, 31), "Assets:Bank")
        )
        val fn = FunctionRegistry.resolveFunction("close_date", listOf(BqlType.String))
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val result = fn?.implementation?.invoke(listOf(
            BqlStringValue("Assets:Bank"),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
        assertEquals(LocalDate(2024, 12, 31), result!!.asDate())
    }

    @Test
    fun `open_meta with entries`() {
        val entries = listOf<Directive>(
            Open(mapOf("broker" to "Schwab"), LocalDate(2024, 1, 1), "Assets:Bank", listOf("USD"))
        )
        val fn = FunctionRegistry.resolveFunction("open_meta", listOf(BqlType.String))
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val result = fn?.implementation?.invoke(listOf(
            BqlStringValue("Assets:Bank"),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
    }

    @Test
    fun `commodity_meta with entries`() {
        val entries = listOf<Directive>(
            Commodity(mapOf("precision" to "2"), LocalDate(2024, 1, 1), "USD")
        )
        val fn = FunctionRegistry.resolveFunction("commodity_meta", listOf(BqlType.String))
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val result = fn?.implementation?.invoke(listOf(
            BqlStringValue("USD"),
            BqlPriceMapValue(priceMap, entries)
        ))
        assertNotNull(result)
    }

    @Test
    fun `has_account with transaction`() {
        val tx = Transaction(emptyMap(), LocalDate(2024, 1, 1), "*",
            postings = listOf(Posting("Assets:Bank")),
            narration = "test")
        val fn = FunctionRegistry.resolveFunction("has_account", listOf(BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlStringValue("Assets"),
            BqlTransactionValue(tx)
        ))
        assertNotNull(result)
        assertTrue(result!!.asBoolean())
    }

    @Test
    fun `has_account without transaction should return false`() {
        val fn = FunctionRegistry.resolveFunction("has_account", listOf(BqlType.String))
        val result = fn?.implementation?.invoke(listOf(BqlStringValue("Assets")))
        assertNotNull(result)
        assertFalse(result!!.asBoolean())
    }

    @Test
    fun `possign with amount`() {
        val fn = FunctionRegistry.resolveFunction("possign", listOf(BqlType.Amount, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlAmountValue(Amount(Decimal("100"), "USD")),
            BqlStringValue("Income:Salary")
        ))
        assertNotNull(result)
        assertEquals(Decimal("-100"), result!!.asAmount().number)
    }

    @Test
    fun `possign with position`() {
        val pos = Position(Amount(Decimal("100"), "USD"), null)
        val fn = FunctionRegistry.resolveFunction("possign", listOf(BqlType.Position, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlPositionValue(pos),
            BqlStringValue("Liabilities:Credit")
        ))
        assertNotNull(result)
        assertEquals(Decimal("-100"), result!!.asPosition().units.number)
    }

    @Test
    fun `possign with inventory`() {
        val inv = Inventory()
        inv.addAmount(Amount(Decimal("100"), "USD"))
        val fn = FunctionRegistry.resolveFunction("possign", listOf(BqlType.Inventory, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlInventoryValue(inv),
            BqlStringValue("Equity:Opening")
        ))
        assertNotNull(result)
    }

    @Test
    fun `filter_currency with inventory`() {
        val inv = Inventory()
        inv.addAmount(Amount(Decimal("100"), "USD"))
        inv.addAmount(Amount(Decimal("200"), "EUR"))

        val fn = FunctionRegistry.resolveFunction("filter_currency", listOf(BqlType.Inventory, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(
            BqlInventoryValue(inv),
            BqlStringValue("USD")
        ))
        assertNotNull(result)
        val filtered = result!!.asInventory()
        assertEquals(1, filtered.size())
    }

    @Test
    fun `meta with transaction`() {
        val fn = FunctionRegistry.resolveFunction("meta", listOf(BqlType.Any, BqlType.String))

        val tx = Transaction(mapOf("filename" to "test.beancount"), LocalDate(2024, 1, 1), "*", narration = "test")
        assertEquals("test.beancount", fn?.implementation?.invoke(listOf(BqlTransactionValue(tx), BqlStringValue("filename")))?.asString())
    }

    @Test
    fun `meta with unknown entry type should not throw`() {
        val fn = FunctionRegistry.resolveFunction("meta", listOf(BqlType.Any, BqlType.String))
        // When entry is not a recognized directive type, meta should return empty map
        val result = fn?.implementation?.invoke(listOf(BqlStringValue("not-an-entry"), BqlStringValue("key")))
        assertTrue(result == null || result.isNull())
    }

    @Test
    fun `meta with unknown key should return null`() {
        val tx = Transaction(emptyMap(), LocalDate(2024, 1, 1), "*", narration = "test")
        val fn = FunctionRegistry.resolveFunction("meta", listOf(BqlType.Any, BqlType.String))
        val result = fn?.implementation?.invoke(listOf(BqlTransactionValue(tx), BqlStringValue("nonexistent")))
        assertTrue(result == null || result.isNull())
    }

    @Test
    fun `units and cost inventory functions`() {
        val inv = Inventory()
        inv.addAmount(Amount(Decimal("100"), "USD"))
        inv.addAmount(Amount(Decimal("10"), "AAPL"), Cost(Decimal("150"), "USD", LocalDate(2024, 1, 1)))

        val unitsFn = FunctionRegistry.resolveFunction("units", listOf(BqlType.Inventory))
        val unitsResult = unitsFn?.implementation?.invoke(listOf(BqlInventoryValue(inv)))
        assertNotNull(unitsResult)

        val costFn = FunctionRegistry.resolveFunction("cost", listOf(BqlType.Inventory))
        val costResult = costFn?.implementation?.invoke(listOf(BqlInventoryValue(inv)))
        assertNotNull(costResult)
    }

    @Test
    fun `abs with null cost`() {
        val pos = Position(Amount(Decimal("-100"), "USD"), null)
        val fn = FunctionRegistry.resolveFunction("abs", listOf(BqlType.Position))
        val result = fn?.implementation?.invoke(listOf(BqlPositionValue(pos)))
        assertNotNull(result)
        assertEquals(Decimal("100"), result!!.asPosition().units.number)
    }

    @Test
    fun `safediv integer with zero divisor`() {
        val fn = FunctionRegistry.resolveFunction("safediv", listOf(BqlType.Decimal, BqlType.Integer))
        val result = fn?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("100")), BqlIntegerValue(0)))
        assertNotNull(result)
        assertEquals(Decimal.ZERO, result!!.asDecimal())
    }

    @Test
    fun `date with three integers`() {
        val fn = FunctionRegistry.resolveFunction("date", listOf(BqlType.Integer, BqlType.Integer, BqlType.Integer))
        val result = fn?.implementation?.invoke(listOf(BqlIntegerValue(2024), BqlIntegerValue(6), BqlIntegerValue(15)))
        assertNotNull(result)
        assertEquals(LocalDate(2024, 6, 15), result!!.asDate())
    }

    @Test
    fun `root with depth greater than parts`() {
        val fn = FunctionRegistry.resolveFunction("root", listOf(BqlType.String, BqlType.Integer))
        val result = fn?.implementation?.invoke(listOf(BqlStringValue("Assets"), BqlIntegerValue(5)))
        assertNotNull(result)
        assertEquals("Assets", result!!.asString())
    }

    @Test
    fun `register custom function`() {
        FunctionRegistry.registerFunction("custom_add", FunctionSignature(listOf(BqlType.Integer, BqlType.Integer), BqlType.Integer)) { args ->
            BqlIntegerValue(args[0].asInteger() + args[1].asInteger())
        }

        val fn = FunctionRegistry.resolveFunction("custom_add", listOf(BqlType.Integer, BqlType.Integer))
        assertNotNull(fn)
        val result = fn?.implementation?.invoke(listOf(BqlIntegerValue(2), BqlIntegerValue(3)))
        assertEquals(5, result!!.asInteger())
    }

    @Test
    fun `register custom aggregator`() {
        FunctionRegistry.registerAggregator("custom_count", FunctionSignature(listOf(BqlType.Any), BqlType.Integer),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Any), BqlType.Integer)
                override fun create(operand: EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                        override val dtype: BqlType = BqlType.Integer
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var count = 0
                            override fun update(value: BqlValue) { count++ }
                            override fun finalize(): BqlValue = BqlIntegerValue(count)
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        val agg = FunctionRegistry.resolveAggregatorFactory("custom_count", listOf(BqlType.Any))
        assertNotNull(agg)
    }
}
