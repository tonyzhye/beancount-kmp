package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.compiler.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for EvalNode operations.
 */
class EvalNodeTest {

    private val dummyRow = object : RowContext {
        override val entry: Directive = Transaction(emptyMap(), LocalDate(2024, 1, 1), "*", postings = emptyList(), narration = "test")
        override val posting: Posting? = null
    }

    @Test
    fun `EvalConstant should return constant value`() {
        val node = EvalConstant(BqlType.Integer, BqlIntegerValue(42))
        assertEquals(42, node.evaluate(dummyRow).asInteger())
    }

    @Test
    fun `EvalColumn should access row context`() {
        val node = EvalColumn(BqlType.String, "test") { "hello" }
        assertEquals("hello", node.evaluate(dummyRow).asString())
    }

    @Test
    fun `EvalBinaryOp addition`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("10")))
        val right = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("5")))
        val node = EvalBinaryOp(BqlType.Decimal, "+", left, right)
        assertEquals(Decimal("15"), node.evaluate(dummyRow).asDecimal())
    }

    @Test
    fun `EvalBinaryOp subtraction`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("10")))
        val right = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("3")))
        val node = EvalBinaryOp(BqlType.Decimal, "-", left, right)
        assertEquals(Decimal("7"), node.evaluate(dummyRow).asDecimal())
    }

    @Test
    fun `EvalBinaryOp multiplication`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("6")))
        val right = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("7")))
        val node = EvalBinaryOp(BqlType.Decimal, "*", left, right)
        assertEquals(Decimal("42"), node.evaluate(dummyRow).asDecimal())
    }

    @Test
    fun `EvalBinaryOp division`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("10")))
        val right = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("2")))
        val node = EvalBinaryOp(BqlType.Decimal, "/", left, right)
        assertEquals(Decimal("5"), node.evaluate(dummyRow).asDecimal())
    }

    @Test
    fun `EvalBinaryOp integer addition`() {
        val left = EvalConstant(BqlType.Integer, BqlIntegerValue(10))
        val right = EvalConstant(BqlType.Integer, BqlIntegerValue(5))
        val node = EvalBinaryOp(BqlType.Integer, "+", left, right)
        assertEquals(15, node.evaluate(dummyRow).asInteger())
    }

    @Test
    fun `EvalBinaryOp string concatenation`() {
        val left = EvalConstant(BqlType.String, BqlStringValue("hello "))
        val right = EvalConstant(BqlType.String, BqlStringValue("world"))
        val node = EvalBinaryOp(BqlType.String, "+", left, right)
        assertEquals("hello world", node.evaluate(dummyRow).asString())
    }

    @Test
    fun `EvalBinaryOp equality`() {
        val left = EvalConstant(BqlType.String, BqlStringValue("test"))
        val right = EvalConstant(BqlType.String, BqlStringValue("test"))
        val node = EvalBinaryOp(BqlType.Boolean, "=", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp inequality`() {
        val left = EvalConstant(BqlType.String, BqlStringValue("a"))
        val right = EvalConstant(BqlType.String, BqlStringValue("b"))
        val node = EvalBinaryOp(BqlType.Boolean, "!=", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp less than`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("5")))
        val right = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("10")))
        val node = EvalBinaryOp(BqlType.Boolean, "<", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp greater than`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("10")))
        val right = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("5")))
        val node = EvalBinaryOp(BqlType.Boolean, ">", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp less than or equal`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("5")))
        val right = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("5")))
        val node = EvalBinaryOp(BqlType.Boolean, "<=", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp greater than or equal`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("10")))
        val right = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("5")))
        val node = EvalBinaryOp(BqlType.Boolean, ">=", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp regex match`() {
        val left = EvalConstant(BqlType.String, BqlStringValue("hello world"))
        val right = EvalConstant(BqlType.String, BqlStringValue("world"))
        val node = EvalBinaryOp(BqlType.Boolean, "~", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp regex no match`() {
        val left = EvalConstant(BqlType.String, BqlStringValue("hello"))
        val right = EvalConstant(BqlType.String, BqlStringValue("xyz"))
        val node = EvalBinaryOp(BqlType.Boolean, "~", left, right)
        assertFalse(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp AND`() {
        val left = EvalConstant(BqlType.Boolean, BqlBooleanValue(true))
        val right = EvalConstant(BqlType.Boolean, BqlBooleanValue(false))
        val node = EvalBinaryOp(BqlType.Boolean, "AND", left, right)
        assertFalse(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp OR`() {
        val left = EvalConstant(BqlType.Boolean, BqlBooleanValue(false))
        val right = EvalConstant(BqlType.Boolean, BqlBooleanValue(true))
        val node = EvalBinaryOp(BqlType.Boolean, "OR", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp null equality`() {
        val left = EvalConstant(BqlType.Null, BqlNullValue())
        val right = EvalConstant(BqlType.Null, BqlNullValue())
        val node = EvalBinaryOp(BqlType.Boolean, "=", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp null vs value`() {
        val left = EvalConstant(BqlType.Null, BqlNullValue())
        val right = EvalConstant(BqlType.String, BqlStringValue("test"))
        val node = EvalBinaryOp(BqlType.Boolean, "=", left, right)
        assertFalse(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp date comparison`() {
        val left = EvalConstant(BqlType.Date, BqlDateValue(LocalDate(2024, 1, 1)))
        val right = EvalConstant(BqlType.Date, BqlDateValue(LocalDate(2024, 6, 1)))
        val node = EvalBinaryOp(BqlType.Boolean, "<", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBinaryOp decimal integer comparison`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("5.5")))
        val right = EvalConstant(BqlType.Integer, BqlIntegerValue(10))
        val node = EvalBinaryOp(BqlType.Boolean, "<", left, right)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalUnaryOp NOT`() {
        val operand = EvalConstant(BqlType.Boolean, BqlBooleanValue(true))
        val node = EvalUnaryOp(BqlType.Boolean, "NOT", operand)
        assertFalse(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalUnaryOp negate decimal`() {
        val operand = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("42")))
        val node = EvalUnaryOp(BqlType.Decimal, "-", operand)
        assertEquals(Decimal("-42"), node.evaluate(dummyRow).asDecimal())
    }

    @Test
    fun `EvalUnaryOp negate integer`() {
        val operand = EvalConstant(BqlType.Integer, BqlIntegerValue(42))
        val node = EvalUnaryOp(BqlType.Integer, "-", operand)
        assertEquals(-42, node.evaluate(dummyRow).asInteger())
    }

    @Test
    fun `EvalUnaryOp positive`() {
        val operand = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("42")))
        val node = EvalUnaryOp(BqlType.Decimal, "+", operand)
        assertEquals(Decimal("42"), node.evaluate(dummyRow).asDecimal())
    }

    @Test
    fun `EvalInOp should match`() {
        val expr = EvalConstant(BqlType.String, BqlStringValue("b"))
        val values = listOf(
            EvalConstant(BqlType.String, BqlStringValue("a")),
            EvalConstant(BqlType.String, BqlStringValue("b")),
            EvalConstant(BqlType.String, BqlStringValue("c"))
        )
        val node = EvalInOp(expr, values)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalInOp should not match`() {
        val expr = EvalConstant(BqlType.String, BqlStringValue("z"))
        val values = listOf(
            EvalConstant(BqlType.String, BqlStringValue("a")),
            EvalConstant(BqlType.String, BqlStringValue("b"))
        )
        val node = EvalInOp(expr, values)
        assertFalse(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalInOp not in`() {
        val expr = EvalConstant(BqlType.String, BqlStringValue("z"))
        val values = listOf(
            EvalConstant(BqlType.String, BqlStringValue("a")),
            EvalConstant(BqlType.String, BqlStringValue("b"))
        )
        val node = EvalInOp(expr, values, notIn = true)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBetweenOp should be in range`() {
        val expr = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("5")))
        val low = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("1")))
        val high = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("10")))
        val node = EvalBetweenOp(expr, low, high)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBetweenOp should be out of range`() {
        val expr = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("15")))
        val low = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("1")))
        val high = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("10")))
        val node = EvalBetweenOp(expr, low, high)
        assertFalse(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBetweenOp not between`() {
        val expr = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("15")))
        val low = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("1")))
        val high = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("10")))
        val node = EvalBetweenOp(expr, low, high, notBetween = true)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalBetweenOp date range`() {
        val expr = EvalConstant(BqlType.Date, BqlDateValue(LocalDate(2024, 3, 15)))
        val low = EvalConstant(BqlType.Date, BqlDateValue(LocalDate(2024, 1, 1)))
        val high = EvalConstant(BqlType.Date, BqlDateValue(LocalDate(2024, 6, 1)))
        val node = EvalBetweenOp(expr, low, high)
        assertTrue(node.evaluate(dummyRow).asBoolean())
    }

    @Test
    fun `EvalFunction should call registered function`() {
        val operand = EvalConstant(BqlType.String, BqlStringValue("hello"))
        val fn = { args: List<BqlValue> -> BqlStringValue(args[0].asString().uppercase()) }
        val node = EvalFunction(BqlType.String, fn, listOf(operand))
        assertEquals("HELLO", node.evaluate(dummyRow).asString())
    }

    @Test
    fun `EvalFunction with context passing`() {
        val operand = EvalConstant(BqlType.String, BqlStringValue("hello"))
        val fn = { args: List<BqlValue> -> BqlStringValue(args[0].asString() + ":" + (args.getOrNull(1)?.asString() ?: "none")) }
        val node = EvalFunction(BqlType.String, fn, listOf(operand), passContext = true) { BqlStringValue("ctx") }
        assertEquals("hello:ctx", node.evaluate(dummyRow).asString())
    }

    @Test
    fun `unknown binary operator should throw`() {
        val left = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("1")))
        val right = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("2")))
        val node = EvalBinaryOp(BqlType.Boolean, "UNKNOWN", left, right)
        assertThrows(IllegalArgumentException::class.java) {
            node.evaluate(dummyRow)
        }
    }

    @Test
    fun `unknown unary operator should throw`() {
        val operand = EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal("1")))
        val node = EvalUnaryOp(BqlType.Decimal, "UNKNOWN", operand)
        assertThrows(IllegalArgumentException::class.java) {
            node.evaluate(dummyRow)
        }
    }
}
