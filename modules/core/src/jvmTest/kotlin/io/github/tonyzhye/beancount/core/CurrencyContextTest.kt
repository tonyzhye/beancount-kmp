package io.github.tonyzhye.beancount.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for CurrencyContext to improve coverage.
 */
class CurrencyContextTest {

    @Test
    fun `update should track number properties`() {
        val ctx = CurrencyContext()
        ctx.update(Decimal("123.45"))

        assertTrue(ctx.hasSign)
        assertEquals(3, ctx.integerMax)
        assertEquals(2, ctx.getFractionalCommon())
        assertEquals(2, ctx.getFractionalMax())
    }

    @Test
    fun `update should track negative numbers`() {
        val ctx = CurrencyContext()
        ctx.update(Decimal("-100.50"))

        assertTrue(ctx.hasSign)
        assertEquals(3, ctx.integerMax)
    }

    @Test
    fun `update should track integer only`() {
        val ctx = CurrencyContext()
        ctx.update(Decimal("1000"))

        assertEquals(4, ctx.integerMax)
        assertEquals(0, ctx.getFractionalCommon())
        assertEquals(0, ctx.getFractionalMax())
    }

    @Test
    fun `updateFrom should merge contexts`() {
        val ctx1 = CurrencyContext()
        ctx1.update(Decimal("100.00"))

        val ctx2 = CurrencyContext()
        ctx2.update(Decimal("50.0"))

        ctx1.updateFrom(ctx2)

        assertEquals(3, ctx1.integerMax) // max of 3 and 2
        assertEquals(2, ctx1.getFractionalCommon()) // 2 occurrences of 2, 1 of 1
        assertEquals(2, ctx1.getFractionalMax())
    }

    @Test
    fun `getFractionalCommon should return most common`() {
        val ctx = CurrencyContext()
        ctx.update(Decimal("1.00"))
        ctx.update(Decimal("2.00"))
        ctx.update(Decimal("3.0"))

        assertEquals(2, ctx.getFractionalCommon())
    }

    @Test
    fun `getFractionalMax should return maximum`() {
        val ctx = CurrencyContext()
        ctx.update(Decimal("1.0"))
        ctx.update(Decimal("2.000"))

        assertEquals(3, ctx.getFractionalMax())
    }

    @Test
    fun `getFractional with MOST_COMMON should return common`() {
        val ctx = CurrencyContext()
        ctx.update(Decimal("1.00"))

        assertEquals(2, ctx.getFractional(Precision.MOST_COMMON))
    }

    @Test
    fun `getFractional with MAXIMUM should return max`() {
        val ctx = CurrencyContext()
        ctx.update(Decimal("1.000"))

        assertEquals(3, ctx.getFractional(Precision.MAXIMUM))
    }

    @Test
    fun `isEmpty should return true for new context`() {
        val ctx = CurrencyContext()
        assertTrue(ctx.isEmpty())
    }

    @Test
    fun `isEmpty should return false after update`() {
        val ctx = CurrencyContext()
        ctx.update(Decimal("1.0"))
        assertFalse(ctx.isEmpty())
    }

    @Test
    fun `getFractionalCommon should return null for empty context`() {
        val ctx = CurrencyContext()
        assertNull(ctx.getFractionalCommon())
    }

    @Test
    fun `getFractionalMax should return null for empty context`() {
        val ctx = CurrencyContext()
        assertNull(ctx.getFractionalMax())
    }

    @Test
    fun `toString should return formatted string`() {
        val ctx = CurrencyContext()
        ctx.update(Decimal("123.45"))

        val str = ctx.toString()
        assertTrue(str.contains("integer_max=3"))
        assertTrue(str.contains("fractional_common=2"))
        assertTrue(str.contains("fractional_max=2"))
    }

    @Test
    fun `toString with empty context should handle nulls`() {
        val ctx = CurrencyContext()
        val str = ctx.toString()
        assertTrue(str.contains("fractional_common=_"))
        assertTrue(str.contains("fractional_max=_"))
    }
}
