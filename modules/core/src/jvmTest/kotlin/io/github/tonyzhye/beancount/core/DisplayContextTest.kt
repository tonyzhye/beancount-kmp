package io.github.tonyzhye.beancount.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DisplayContextTest {

    @Test
    fun `should collect precision for single currency`() {
        val dcontext = DisplayContext()

        dcontext.update(Decimal("100.00"), "USD")
        dcontext.update(Decimal("50.00"), "USD")
        dcontext.update(Decimal("25.50"), "USD")

        val context = dcontext.getContext("USD")
        assertNotNull(context)
        assertEquals(2, context!!.getFractionalCommon())
        assertEquals(2, context.getFractionalMax())
    }

    @Test
    fun `should collect different precisions`() {
        val dcontext = DisplayContext()

        dcontext.update(Decimal("100.00"), "USD")
        dcontext.update(Decimal("50.0"), "USD")
        dcontext.update(Decimal("25.123"), "USD")

        val context = dcontext.getContext("USD")
        assertNotNull(context)
        assertEquals(2, context!!.getFractionalCommon())  // Most common
        assertEquals(3, context.getFractionalMax())       // Maximum
    }

    @Test
    fun `should handle multiple currencies`() {
        val dcontext = DisplayContext()

        dcontext.update(Decimal("100.00"), "USD")
        dcontext.update(Decimal("50.00"), "EUR")
        dcontext.update(Decimal("200.000"), "JPY")

        assertNotNull(dcontext.getContext("USD"))
        assertNotNull(dcontext.getContext("EUR"))
        assertNotNull(dcontext.getContext("JPY"))

        assertEquals(2, dcontext.getContext("USD")!!.getFractionalCommon())
        assertEquals(2, dcontext.getContext("EUR")!!.getFractionalCommon())
        assertEquals(3, dcontext.getContext("JPY")!!.getFractionalCommon())
    }

    @Test
    fun `should quantize to most common precision`() {
        val dcontext = DisplayContext()

        dcontext.update(Decimal("100.00"), "USD")
        dcontext.update(Decimal("50.00"), "USD")

        val quantized = dcontext.quantize(Decimal("25"), "USD", Precision.MOST_COMMON)
        assertEquals(Decimal("25.00"), quantized)
    }

    @Test
    fun `should quantize to maximum precision`() {
        val dcontext = DisplayContext()

        dcontext.update(Decimal("100.00"), "USD")
        dcontext.update(Decimal("50.123"), "USD")

        val quantized = dcontext.quantize(Decimal("25"), "USD", Precision.MAXIMUM)
        assertEquals(Decimal("25.000"), quantized)
    }

    @Test
    fun `should handle integer numbers`() {
        val dcontext = DisplayContext()

        dcontext.update(Decimal("100"), "USD")
        dcontext.update(Decimal("50"), "USD")

        val context = dcontext.getContext("USD")
        assertNotNull(context)
        assertEquals(0, context!!.getFractionalCommon())

        val quantized = dcontext.quantize(Decimal("25.50"), "USD")
        assertEquals(Decimal("25"), quantized)
    }

    @Test
    fun `should update from another context`() {
        val dcontext1 = DisplayContext()
        dcontext1.update(Decimal("100.00"), "USD")

        val dcontext2 = DisplayContext()
        dcontext2.update(Decimal("50.00"), "USD")
        dcontext2.update(Decimal("25.00"), "EUR")

        dcontext1.updateFrom(dcontext2)

        assertEquals(2, dcontext1.getContext("USD")!!.getFractionalCommon())
        assertNotNull(dcontext1.getContext("EUR"))
    }

    @Test
    fun `should build formatter`() {
        val dcontext = DisplayContext()
        dcontext.update(Decimal("100.00"), "USD")

        val formatter = dcontext.buildFormatter()

        val formatted = formatter.format(Decimal("50"), "USD")
        assertEquals("50.00", formatted)
    }

    @Test
    fun `should format with commas`() {
        val dcontext = DisplayContext()
        dcontext.commas = true

        val formatter = dcontext.buildFormatter()

        val formatted = formatter.format(Decimal("1234.56"), "USD")
        assertEquals("1,234.56", formatted)
    }

    @Test
    fun `should handle empty context`() {
        val dcontext = DisplayContext()

        // Should not throw
        val quantized = dcontext.quantize(Decimal("100.50"), "USD")
        assertEquals(Decimal("100.50"), quantized)
    }

    @Test
    fun `should track integer max`() {
        val dcontext = DisplayContext()

        dcontext.update(Decimal("1.00"), "USD")
        dcontext.update(Decimal("1000.00"), "USD")
        dcontext.update(Decimal("999999.00"), "USD")

        val context = dcontext.getContext("USD")
        assertNotNull(context)
        assertEquals(6, context!!.integerMax)
    }

    @Test
    fun `should format amount`() {
        val dcontext = DisplayContext()
        dcontext.update(Decimal("100.00"), "USD")

        val formatter = dcontext.buildFormatter()

        val amount = Amount(Decimal("50"), "USD")
        val formatted = formatter.formatAmount(amount)
        assertEquals("50.00 USD", formatted)
    }

    @Test
    fun `should render to string`() {
        val dcontext = DisplayContext()
        dcontext.update(Decimal("100.00"), "USD")

        val str = dcontext.toString()
        assertTrue(str.contains("USD"))
        assertTrue(str.contains("fractional_common"))
    }
}
