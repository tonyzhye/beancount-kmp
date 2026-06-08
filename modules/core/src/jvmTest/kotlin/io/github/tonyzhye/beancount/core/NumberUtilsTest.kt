package io.github.tonyzhye.beancount.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for number utilities to improve coverage.
 */
class NumberUtilsTest {

    @Test
    fun `D should create Decimal from string`() {
        assertEquals(Decimal("1.23"), D("1.23"))
        assertEquals(Decimal("100"), D("100"))
        assertEquals(Decimal("0"), D("0"))
        assertEquals(Decimal("-5.5"), D("-5.5"))
    }

    @Test
    fun `D should create Decimal from double`() {
        assertEquals(Decimal("1.5"), D(1.5))
        assertEquals(Decimal("100.0"), D(100.0))
        assertEquals(Decimal("0.0"), D(0.0))
    }

    @Test
    fun `D should create Decimal from long`() {
        assertEquals(Decimal("100"), D(100L))
        assertEquals(Decimal("0"), D(0L))
        assertEquals(Decimal("-50"), D(-50L))
    }

    @Test
    fun `D should create Decimal from int`() {
        assertEquals(Decimal("42"), D(42))
        assertEquals(Decimal("0"), D(0))
        assertEquals(Decimal("-10"), D(-10))
    }

    @Test
    fun `roundTo should round positive values`() {
        assertEquals(Decimal("1.23"), roundTo(Decimal("1.234"), 2))
        assertEquals(Decimal("1.24"), roundTo(Decimal("1.235"), 2))
        assertEquals(Decimal("100.00"), roundTo(Decimal("100.004"), 2))
        assertEquals(Decimal("100.01"), roundTo(Decimal("100.005"), 2))
    }

    @Test
    fun `roundTo should round negative values`() {
        assertEquals(Decimal("-1.23"), roundTo(Decimal("-1.234"), 2))
        assertEquals(Decimal("-1.24"), roundTo(Decimal("-1.235"), 2))
    }

    @Test
    fun `roundTo should handle zero digits`() {
        assertEquals(Decimal("1"), roundTo(Decimal("1.4"), 0))
        assertEquals(Decimal("2"), roundTo(Decimal("1.5"), 0))
    }

    @Test
    fun `sameSign should return true for same sign values`() {
        assertTrue(sameSign(Decimal("5"), Decimal("10")))
        assertTrue(sameSign(Decimal("-5"), Decimal("-10")))
        assertTrue(sameSign(Decimal("0"), Decimal("0")))
    }

    @Test
    fun `sameSign should return false for different sign values`() {
        assertFalse(sameSign(Decimal("5"), Decimal("-10")))
        assertFalse(sameSign(Decimal("-5"), Decimal("10")))
    }

    @Test
    fun `sameSign should handle zero`() {
        assertTrue(sameSign(Decimal("0"), Decimal("0")))
        assertFalse(sameSign(Decimal("0"), Decimal("5")))
        assertFalse(sameSign(Decimal("5"), Decimal("0")))
    }

    @Test
    fun `numFractionalDigits should count fractional digits`() {
        assertEquals(2, numFractionalDigits(Decimal("1.23")))
        assertEquals(0, numFractionalDigits(Decimal("100")))
        assertEquals(5, numFractionalDigits(Decimal("1.23456")))
        assertEquals(1, numFractionalDigits(Decimal("-5.5")))
    }

    @Test
    fun `autoQuantize should quantize high precision values`() {
        val highPrecision = Decimal("1.234567890123456789")
        val result = autoQuantize(highPrecision, 12)
        assertEquals(12, numFractionalDigits(result))
    }

    @Test
    fun `autoQuantize should not change values within maxDigits`() {
        val value = Decimal("1.23")
        assertEquals(value, autoQuantize(value))
    }

    @Test
    fun `autoQuantize should not change integers`() {
        val value = Decimal("100")
        assertEquals(value, autoQuantize(value))
    }

    @Test
    fun `inferQuantumFromList should find smallest quantum`() {
        val values = listOf(Decimal("1.00"), Decimal("2.5"), Decimal("3.123"))
        val quantum = inferQuantumFromList(values)
        assertEquals(Decimal("0.001"), quantum)
    }

    @Test
    fun `inferQuantumFromList should handle empty list`() {
        assertEquals(Decimal.ONE, inferQuantumFromList(emptyList()))
    }

    @Test
    fun `inferQuantumFromList should handle integers only`() {
        val values = listOf(Decimal("1"), Decimal("2"), Decimal("3"))
        assertEquals(Decimal.ONE, inferQuantumFromList(values))
    }

    @Test
    fun `inferQuantumFromList should handle mixed values`() {
        val values = listOf(Decimal("1.0"), Decimal("2.00"), Decimal("3"))
        val quantum = inferQuantumFromList(values)
        // 1.0 -> quantum 0.1, 2.00 -> quantum 0.01, so min is 0.01
        assertEquals(Decimal("0.01"), quantum)
    }
}
