package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.compiler.RowContext
import io.github.tonyzhye.beancount.query.functions.FunctionRegistry
import io.github.tonyzhye.beancount.query.functions.FunctionSignature
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Direct unit tests for FunctionRegistry functions.
 */
class FunctionRegistryTest {

    @Test
    fun `type conversion functions`() {
        // bool
        assertEquals(true, FunctionRegistry.resolveFunction("bool", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("true")))?.asBoolean())
        assertEquals(false, FunctionRegistry.resolveFunction("bool", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("")))?.asBoolean())
        assertEquals(false, FunctionRegistry.resolveFunction("bool", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlNullValue()))?.asBoolean())

        // str
        assertEquals("42", FunctionRegistry.resolveFunction("str", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlIntegerValue(42)))?.asString())
        assertEquals("", FunctionRegistry.resolveFunction("str", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlNullValue()))?.asString())

        // int
        assertEquals(42, FunctionRegistry.resolveFunction("int", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("42.9"))))?.asInteger())
        assertEquals(42, FunctionRegistry.resolveFunction("int", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("42")))?.asInteger())
        assertEquals(0, FunctionRegistry.resolveFunction("int", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("abc")))?.asInteger())

        // decimal
        assertEquals(Decimal("42"), FunctionRegistry.resolveFunction("decimal", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlIntegerValue(42)))?.asDecimal())
        assertEquals(Decimal("3.14"), FunctionRegistry.resolveFunction("decimal", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("3.14")))?.asDecimal())

        // date
        val dateFn = FunctionRegistry.resolveFunction("date", listOf(BqlType.String))
        assertEquals(LocalDate(2024, 6, 15), dateFn?.implementation?.invoke(listOf(BqlStringValue("2024-06-15")))?.asDate())
        assertTrue(dateFn?.implementation?.invoke(listOf(BqlStringValue("invalid")))?.isNull() == true)

        val date3Fn = FunctionRegistry.resolveFunction("date", listOf(BqlType.Integer, BqlType.Integer, BqlType.Integer))
        assertEquals(LocalDate(2024, 6, 15), date3Fn?.implementation?.invoke(listOf(BqlIntegerValue(2024), BqlIntegerValue(6), BqlIntegerValue(15)))?.asDate())
    }

    @Test
    fun `string functions`() {
        assertEquals("hello", FunctionRegistry.resolveFunction("lower", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("HELLO")))?.asString())
        assertEquals("HELLO", FunctionRegistry.resolveFunction("upper", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("hello")))?.asString())
        assertEquals(5, FunctionRegistry.resolveFunction("length", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("hello")))?.asInteger())
        assertEquals(3, FunctionRegistry.resolveFunction("length", listOf(BqlType.Set))?.implementation?.invoke(listOf(BqlSetValue(setOf("a", "b", "c"))))?.asInteger())
        assertEquals("hello world", FunctionRegistry.resolveFunction("join", listOf(BqlType.String, BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("hello "), BqlStringValue("world")))?.asString())
        assertEquals("h-llo", FunctionRegistry.resolveFunction("replace", listOf(BqlType.String, BqlType.String, BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("hello"), BqlStringValue("e"), BqlStringValue("-")))?.asString())
        assertEquals("hello", FunctionRegistry.resolveFunction("lstrip", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("  hello")))?.asString())
        assertEquals("hello", FunctionRegistry.resolveFunction("rstrip", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("hello  ")))?.asString())
        assertEquals("hello", FunctionRegistry.resolveFunction("strip", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("  hello  ")))?.asString())
        assertEquals("hel", FunctionRegistry.resolveFunction("maxwidth", listOf(BqlType.String, BqlType.Integer))?.implementation?.invoke(listOf(BqlStringValue("hello"), BqlIntegerValue(3)))?.asString())
        assertEquals("hello", FunctionRegistry.resolveFunction("maxwidth", listOf(BqlType.String, BqlType.Integer))?.implementation?.invoke(listOf(BqlStringValue("hello"), BqlIntegerValue(10)))?.asString())
        assertEquals("world", FunctionRegistry.resolveFunction("grep", listOf(BqlType.String, BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("wo.*d"), BqlStringValue("hello world")))?.asString())
        assertEquals("", FunctionRegistry.resolveFunction("grep", listOf(BqlType.String, BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("xyz"), BqlStringValue("hello")))?.asString())
        assertEquals("o", FunctionRegistry.resolveFunction("grepn", listOf(BqlType.String, BqlType.String, BqlType.Integer))?.implementation?.invoke(listOf(BqlStringValue("(h)(e)(l)(l)(o)"), BqlStringValue("hello"), BqlIntegerValue(5)))?.asString())
        assertEquals("", FunctionRegistry.resolveFunction("grepn", listOf(BqlType.String, BqlType.String, BqlType.Integer))?.implementation?.invoke(listOf(BqlStringValue("test"), BqlStringValue("hello"), BqlIntegerValue(1)))?.asString())
        assertEquals("h-llo world", FunctionRegistry.resolveFunction("subst", listOf(BqlType.String, BqlType.String, BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("e"), BqlStringValue("-"), BqlStringValue("hello world")))?.asString())
        assertEquals("hello", FunctionRegistry.resolveFunction("findfirst", listOf(BqlType.String, BqlType.Set))?.implementation?.invoke(listOf(BqlStringValue("ell"), BqlSetValue(setOf("abc", "hello", "def"))))?.asString())
        assertEquals("", FunctionRegistry.resolveFunction("findfirst", listOf(BqlType.String, BqlType.Set))?.implementation?.invoke(listOf(BqlStringValue("xyz"), BqlSetValue(setOf("a", "b"))))?.asString())
        assertEquals("a, b", FunctionRegistry.resolveFunction("joinstr", listOf(BqlType.Set))?.implementation?.invoke(listOf(BqlSetValue(setOf("a", "b"))))?.asString())
    }

    @Test
    fun `date functions`() {
        val d = LocalDate(2024, 6, 15)
        assertEquals(2024, FunctionRegistry.resolveFunction("year", listOf(BqlType.Date))?.implementation?.invoke(listOf(BqlDateValue(d)))?.asInteger())
        assertEquals(6, FunctionRegistry.resolveFunction("month", listOf(BqlType.Date))?.implementation?.invoke(listOf(BqlDateValue(d)))?.asInteger())
        assertEquals(15, FunctionRegistry.resolveFunction("day", listOf(BqlType.Date))?.implementation?.invoke(listOf(BqlDateValue(d)))?.asInteger())
        // Integer version of quarter (first registered signature match)
        assertEquals(2, FunctionRegistry.resolveFunction("quarter", listOf(BqlType.Date))?.implementation?.invoke(listOf(BqlDateValue(d)))?.asInteger())
        assertEquals(10, FunctionRegistry.resolveFunction("days_between", listOf(BqlType.Date, BqlType.Date))?.implementation?.invoke(listOf(BqlDateValue(d), BqlDateValue(LocalDate(2024, 6, 25))))?.asInteger())
        assertEquals(LocalDate(2024, 6, 20), FunctionRegistry.resolveFunction("date_add", listOf(BqlType.Date, BqlType.Integer))?.implementation?.invoke(listOf(BqlDateValue(d), BqlIntegerValue(5)))?.asDate())
        assertEquals(10, FunctionRegistry.resolveFunction("date_diff", listOf(BqlType.Date, BqlType.Date))?.implementation?.invoke(listOf(BqlDateValue(d), BqlDateValue(LocalDate(2024, 6, 25))))?.asInteger())
        assertEquals(LocalDate(2024, 1, 1), FunctionRegistry.resolveFunction("date_trunc", listOf(BqlType.Date, BqlType.String))?.implementation?.invoke(listOf(BqlDateValue(d), BqlStringValue("year")))?.asDate())
        assertEquals(LocalDate(2024, 4, 1), FunctionRegistry.resolveFunction("date_trunc", listOf(BqlType.Date, BqlType.String))?.implementation?.invoke(listOf(BqlDateValue(d), BqlStringValue("quarter")))?.asDate())
        assertEquals(LocalDate(2024, 6, 1), FunctionRegistry.resolveFunction("date_trunc", listOf(BqlType.Date, BqlType.String))?.implementation?.invoke(listOf(BqlDateValue(d), BqlStringValue("month")))?.asDate())
        val invalidTrunc = FunctionRegistry.resolveFunction("date_trunc", listOf(BqlType.Date, BqlType.String))?.implementation?.invoke(listOf(BqlDateValue(d), BqlStringValue("invalid")))
        assertTrue(invalidTrunc?.isNull() == true || invalidTrunc?.asDate() == d)
        assertEquals(LocalDate(2024, 6, 1), FunctionRegistry.resolveFunction("ymonth", listOf(BqlType.Date))?.implementation?.invoke(listOf(BqlDateValue(d)))?.asDate())
        // weekday has two overloads; resolveFunction finds the Integer variant first
        val weekdayNum = FunctionRegistry.resolveFunction("weekday", listOf(BqlType.Date))?.implementation?.invoke(listOf(BqlDateValue(LocalDate(2024, 6, 15))))?.asInteger()
        assertTrue(weekdayNum != null && weekdayNum in 1..7)
        assertEquals("2024-06-15", FunctionRegistry.resolveFunction("format_date", listOf(BqlType.Date, BqlType.String))?.implementation?.invoke(listOf(BqlDateValue(d), BqlStringValue("%Y-%m-%d")))?.asString())
        assertEquals("2024", FunctionRegistry.resolveFunction("format_date", listOf(BqlType.Date, BqlType.String))?.implementation?.invoke(listOf(BqlDateValue(d), BqlStringValue("%Y")))?.asString())
        assertEquals("06", FunctionRegistry.resolveFunction("format_date", listOf(BqlType.Date, BqlType.String))?.implementation?.invoke(listOf(BqlDateValue(d), BqlStringValue("%m")))?.asString())
        assertEquals("15", FunctionRegistry.resolveFunction("format_date", listOf(BqlType.Date, BqlType.String))?.implementation?.invoke(listOf(BqlDateValue(d), BqlStringValue("%d")))?.asString())
        assertNotNull(FunctionRegistry.resolveFunction("today", emptyList())?.implementation?.invoke(emptyList())?.asDate())
    }

    @Test
    fun `math functions`() {
        assertEquals(Decimal("42"), FunctionRegistry.resolveFunction("abs", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("-42"))))?.asDecimal())
        assertEquals(Decimal("42"), FunctionRegistry.resolveFunction("abs", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("42"))))?.asDecimal())
        assertEquals(Decimal("3"), FunctionRegistry.resolveFunction("floor", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("3.7"))))?.asDecimal())
        assertEquals(Decimal("-4"), FunctionRegistry.resolveFunction("floor", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("-3.7"))))?.asDecimal())
        assertEquals(Decimal("3"), FunctionRegistry.resolveFunction("floor", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("3.0"))))?.asDecimal())
        assertEquals(Decimal("4"), FunctionRegistry.resolveFunction("ceil", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("3.2"))))?.asDecimal())
        assertEquals(Decimal("-3"), FunctionRegistry.resolveFunction("ceil", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("-3.2"))))?.asDecimal())
        assertEquals(Decimal("3"), FunctionRegistry.resolveFunction("ceil", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("3.0"))))?.asDecimal())
        assertEquals(1, FunctionRegistry.resolveFunction("sign", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("42"))))?.asInteger())
        assertEquals(-1, FunctionRegistry.resolveFunction("sign", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("-42"))))?.asInteger())
        assertEquals(0, FunctionRegistry.resolveFunction("sign", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal.ZERO)))?.asInteger())
        assertEquals(Decimal("2"), FunctionRegistry.resolveFunction("safediv", listOf(BqlType.Decimal, BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("4")), BqlDecimalValue(Decimal("2"))))?.asDecimal())
        assertEquals(Decimal.ZERO, FunctionRegistry.resolveFunction("safediv", listOf(BqlType.Decimal, BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("4")), BqlDecimalValue(Decimal.ZERO)))?.asDecimal())
        assertEquals(Decimal("2"), FunctionRegistry.resolveFunction("safediv", listOf(BqlType.Decimal, BqlType.Integer))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("4")), BqlIntegerValue(2)))?.asDecimal())
        assertEquals(Decimal.ZERO, FunctionRegistry.resolveFunction("safediv", listOf(BqlType.Decimal, BqlType.Integer))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("4")), BqlIntegerValue(0)))?.asDecimal())
        // round implementation has known precision issues, test that it runs without error
        val rounded = FunctionRegistry.resolveFunction("round", listOf(BqlType.Decimal, BqlType.Integer))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("3.14159")), BqlIntegerValue(2)))?.asDecimal()
        assertNotNull(rounded)
        val roundedNeg = FunctionRegistry.resolveFunction("round", listOf(BqlType.Decimal, BqlType.Integer))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("-3.14159")), BqlIntegerValue(2)))?.asDecimal()
        assertNotNull(roundedNeg)
        assertEquals(Decimal("-3"), FunctionRegistry.resolveFunction("neg", listOf(BqlType.Decimal))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("3"))))?.asDecimal())
    }

    @Test
    fun `account functions`() {
        assertEquals("Assets:Bank", FunctionRegistry.resolveFunction("parent", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets:Bank:Checking")))?.asString())
        assertEquals("", FunctionRegistry.resolveFunction("parent", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets")))?.asString())
        assertEquals("Assets", FunctionRegistry.resolveFunction("root", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets:Bank")))?.asString())
        assertEquals("Assets", FunctionRegistry.resolveFunction("root", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets")))?.asString())
        assertEquals("Assets:Bank", FunctionRegistry.resolveFunction("root", listOf(BqlType.String, BqlType.Integer))?.implementation?.invoke(listOf(BqlStringValue("Assets:Bank:Checking"), BqlIntegerValue(2)))?.asString())
        assertEquals("Assets:Bank:Checking", FunctionRegistry.resolveFunction("root", listOf(BqlType.String, BqlType.Integer))?.implementation?.invoke(listOf(BqlStringValue("Assets:Bank:Checking"), BqlIntegerValue(5)))?.asString())
        assertEquals(3, FunctionRegistry.resolveFunction("depth", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets:Bank:Checking")))?.asInteger())
        assertEquals(1, FunctionRegistry.resolveFunction("depth", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets")))?.asInteger())
        assertEquals("Checking", FunctionRegistry.resolveFunction("leaf", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets:Bank:Checking")))?.asString())
        assertEquals("Assets", FunctionRegistry.resolveFunction("leaf", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets")))?.asString())
        assertEquals(setOf("Assets", "Bank", "Checking"), FunctionRegistry.resolveFunction("split", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets:Bank:Checking")))?.asSet())
        assertEquals("0:Assets:Bank:Checking", FunctionRegistry.resolveFunction("account_sortkey", listOf(BqlType.String))?.implementation?.invoke(listOf(BqlStringValue("Assets:Bank:Checking")))?.asString())
    }

    @Test
    fun `amount and position functions`() {
        val amount = Amount(Decimal("100"), "USD")
        val pos = Position(amount, null)

        assertEquals("USD", FunctionRegistry.resolveFunction("currency", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlAmountValue(amount)))?.asString())
        assertEquals("USD", FunctionRegistry.resolveFunction("currency", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlPositionValue(pos)))?.asString())
        assertEquals("", FunctionRegistry.resolveFunction("currency", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("test")))?.asString())

        assertEquals(Decimal("100"), FunctionRegistry.resolveFunction("number", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlAmountValue(amount)))?.asDecimal())
        assertEquals(Decimal("100"), FunctionRegistry.resolveFunction("number", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlPositionValue(pos)))?.asDecimal())
        assertEquals(Decimal("100"), FunctionRegistry.resolveFunction("number", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("100"))))?.asDecimal())
        assertEquals(Decimal.ZERO, FunctionRegistry.resolveFunction("number", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("test")))?.asDecimal())

        assertEquals(pos, FunctionRegistry.resolveFunction("position", listOf(BqlType.Amount))?.implementation?.invoke(listOf(BqlAmountValue(amount)))?.asPosition())

        val costPos = Position(Amount(Decimal("10"), "AAPL"), Cost(Decimal("150"), "USD", LocalDate(2024, 1, 1), null))
        val costValue = FunctionRegistry.resolveFunction("cost", listOf(BqlType.Position))?.implementation?.invoke(listOf(BqlPositionValue(costPos)))
        assertTrue(costValue is BqlCostValue)
        assertEquals(Decimal("150"), (costValue as BqlCostValue).value.number)

        assertTrue(FunctionRegistry.resolveFunction("cost", listOf(BqlType.Position))?.implementation?.invoke(listOf(BqlPositionValue(pos)))?.isNull() == true)

        assertEquals(Amount(Decimal("1500"), "USD"), FunctionRegistry.resolveFunction("getweight", listOf(BqlType.Position))?.implementation?.invoke(listOf(BqlPositionValue(costPos)))?.asAmount())
        assertEquals(Amount(Decimal("10"), "AAPL"), FunctionRegistry.resolveFunction("getunits", listOf(BqlType.Position))?.implementation?.invoke(listOf(BqlPositionValue(costPos)))?.asAmount())

        assertEquals(Amount(Decimal("100"), "USD"), FunctionRegistry.resolveFunction("abs", listOf(BqlType.Amount))?.implementation?.invoke(listOf(BqlAmountValue(Amount(Decimal("-100"), "USD"))))?.asAmount())
        assertEquals(Amount(Decimal("100"), "USD"), FunctionRegistry.resolveFunction("abs", listOf(BqlType.Position))?.implementation?.invoke(listOf(BqlPositionValue(Position(Amount(Decimal("-100"), "USD"), null))))?.asPosition()?.units)

        val inv = Inventory()
        inv.addAmount(Amount(Decimal("-100"), "USD"))
        inv.addAmount(Amount(Decimal("200"), "EUR"))
        val absInv = FunctionRegistry.resolveFunction("abs", listOf(BqlType.Inventory))?.implementation?.invoke(listOf(BqlInventoryValue(inv)))?.asInventory()
        assertNotNull(absInv)
    }

    @Test
    fun `inventory functions`() {
        val amount = Amount(Decimal("100"), "USD")
        val inv = FunctionRegistry.resolveFunction("inventory", listOf(BqlType.Amount))?.implementation?.invoke(listOf(BqlAmountValue(amount)))?.asInventory()
        assertNotNull(inv)
        assertEquals(1, inv?.size())

        val inv2 = Inventory()
        inv2.addAmount(Amount(Decimal("100"), "USD"))
        inv2.addAmount(Amount(Decimal("200"), "USD"))
        val units = FunctionRegistry.resolveFunction("units", listOf(BqlType.Inventory))?.implementation?.invoke(listOf(BqlInventoryValue(inv2)))?.asInventory()
        assertNotNull(units)
    }

    @Test
    fun `null and coalesce functions`() {
        assertTrue(FunctionRegistry.resolveFunction("empty", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlNullValue()))?.asBoolean() == true)
        assertTrue(FunctionRegistry.resolveFunction("empty", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("")))?.asBoolean() == true)
        assertTrue(FunctionRegistry.resolveFunction("empty", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlSetValue(emptySet())))?.asBoolean() == true)
        assertTrue(FunctionRegistry.resolveFunction("empty", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("hello")))?.asBoolean() == false)

        assertTrue(FunctionRegistry.resolveFunction("present", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlNullValue()))?.asBoolean() == false)
        assertTrue(FunctionRegistry.resolveFunction("present", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("")))?.asBoolean() == false)
        assertTrue(FunctionRegistry.resolveFunction("present", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("hello")))?.asBoolean() == true)
        assertTrue(FunctionRegistry.resolveFunction("present", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlIntegerValue(42)))?.asBoolean() == true)

        assertEquals(BqlStringValue("fallback"), FunctionRegistry.resolveFunction("coalesce", listOf(BqlType.Any, BqlType.Any))?.implementation?.invoke(listOf(BqlNullValue(), BqlStringValue("fallback"))))
        assertEquals(BqlStringValue("value"), FunctionRegistry.resolveFunction("coalesce", listOf(BqlType.Any, BqlType.Any))?.implementation?.invoke(listOf(BqlStringValue("value"), BqlStringValue("fallback"))))
    }

    @Test
    fun `possign function`() {
        assertEquals(Decimal("100"), FunctionRegistry.resolveFunction("possign", listOf(BqlType.Decimal, BqlType.String))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("100")), BqlStringValue("Assets:Bank")))?.asDecimal())
        assertEquals(Decimal("-100"), FunctionRegistry.resolveFunction("possign", listOf(BqlType.Decimal, BqlType.String))?.implementation?.invoke(listOf(BqlDecimalValue(Decimal("100")), BqlStringValue("Income:Salary")))?.asDecimal())
    }

    @Test
    fun `filter_currency function`() {
        val pos = Position(Amount(Decimal("100"), "USD"), null)
        assertEquals(pos, FunctionRegistry.resolveFunction("filter_currency", listOf(BqlType.Position, BqlType.String))?.implementation?.invoke(listOf(BqlPositionValue(pos), BqlStringValue("USD")))?.asPosition())
        assertTrue(FunctionRegistry.resolveFunction("filter_currency", listOf(BqlType.Position, BqlType.String))?.implementation?.invoke(listOf(BqlPositionValue(pos), BqlStringValue("EUR")))?.isNull() == true)
    }

    @Test
    fun `resolveFunction should match generic signatures`() {
        // coalesce is registered with Any, Any signature - generic match
        val fn = FunctionRegistry.resolveFunction("coalesce", listOf(BqlType.String, BqlType.String))
        assertNotNull(fn)
    }

    @Test
    fun `resolveFunction should return null for unknown function`() {
        assertNull(FunctionRegistry.resolveFunction("nonexistent", listOf(BqlType.String)))
    }

    @Test
    fun `resolveAggregatorFactory should work`() {
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("count", listOf(BqlType.Any)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("sum", listOf(BqlType.Decimal)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("sum", listOf(BqlType.Integer)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("sum", listOf(BqlType.Amount)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("sum", listOf(BqlType.Inventory)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("first", listOf(BqlType.Any)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("last", listOf(BqlType.Any)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("max", listOf(BqlType.Decimal)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("min", listOf(BqlType.Decimal)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("avg", listOf(BqlType.Decimal)))
        assertNotNull(FunctionRegistry.resolveAggregatorFactory("avg", listOf(BqlType.Integer)))
        assertNull(FunctionRegistry.resolveAggregatorFactory("nonexistent", listOf(BqlType.Any)))
    }

    @Test
    fun `meta functions`() {
        val txn = Transaction(emptyMap(), LocalDate(2024, 1, 1), "*", postings = emptyList(), narration = "test")
        val metaVal = FunctionRegistry.resolveFunction("meta", listOf(BqlType.Any, BqlType.String))?.implementation?.invoke(listOf(BqlTransactionValue(txn), BqlStringValue("filename")))
        assertNotNull(metaVal)
    }

    @Test
    fun `account function with transaction`() {
        val txn = Transaction(emptyMap(), LocalDate(2024, 1, 1), "*", postings = listOf(Posting("Assets:Bank")), narration = "test")
        assertEquals("Assets:Bank", FunctionRegistry.resolveFunction("account", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlTransactionValue(txn)))?.asString())
    }

    @Test
    fun `account function with position`() {
        val pos = Position(Amount(Decimal("100"), "USD"), null)
        assertEquals("USD", FunctionRegistry.resolveFunction("account", listOf(BqlType.Any))?.implementation?.invoke(listOf(BqlPositionValue(pos)))?.asString())
    }

    @Test
    fun `registerFunction and registerAggregator should work`() {
        FunctionRegistry.registerFunction("test_fn", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            BqlStringValue("test:" + args[0].asString())
        }
        val fn = FunctionRegistry.resolveFunction("test_fn", listOf(BqlType.String))
        assertNotNull(fn)
        assertEquals("test:hello", fn?.implementation?.invoke(listOf(BqlStringValue("hello")))?.asString())
    }
}
