package io.github.tonyzhye.beancount.core

/**
 * Number utilities for beancount.
 *
 * Based on beancount.core.number
 */

/**
 * Create a Decimal from a string (like Python's D() function).
 *
 * Convenience function: D("1.23") == Decimal("1.23")
 */
fun D(value: String): Decimal = Decimal(value)

/**
 * Create a Decimal from a double.
 */
fun D(value: Double): Decimal = Decimal(value)

/**
 * Create a Decimal from a long.
 */
fun D(value: Long): Decimal = Decimal(value)

/**
 * Create a Decimal from an int.
 */
fun D(value: Int): Decimal = Decimal(value.toLong())

/**
 * Constant for missing/unset values.
 * Similar to Python's beancount.core.number.MISSING.
 */
object MISSING

/**
 * Round a Decimal to the given number of decimal places.
 *
 * @param value The value to round
 * @param digits Number of fractional digits
 * @return Rounded value
 */
fun roundTo(value: Decimal, digits: Int): Decimal {
    val scale = Decimal.ONE.scaleByPowerOfTen(-digits)
    val sign = if (value.isNegative()) -1 else 1
    val absValue = value.abs()
    val scaled = absValue / scale
    val truncated = scaled.truncate()
    val remainder = scaled - truncated
    val rounded = if (remainder * Decimal("2") >= Decimal.ONE) truncated + Decimal.ONE else truncated
    val result = rounded * scale
    return if (sign < 0) -result else result
}

/**
 * Check if two Decimals have the same sign.
 */
fun sameSign(a: Decimal, b: Decimal): Boolean {
    return (a.isPositive() && b.isPositive()) ||
           (a.isNegative() && b.isNegative()) ||
           (a.isZero() && b.isZero())
}

/**
 * Get the number of fractional digits in a Decimal.
 */
fun numFractionalDigits(value: Decimal): Int {
    val plain = value.toPlainString()
    val dotIndex = plain.indexOf('.')
    return if (dotIndex >= 0) plain.length - dotIndex - 1 else 0
}

/**
 * Auto-quantize a Decimal based on its precision.
 *
 * This rounds the value to remove floating-point artifacts.
 * For example, autoQuantize(Decimal("1.23456789012345")) might return Decimal("1.23")
 * depending on the inferred precision.
 *
 * @param value The Decimal to quantize
 * @param maxDigits Maximum number of fractional digits (default 12)
 * @return Quantized value
 */
fun autoQuantize(value: Decimal, maxDigits: Int = 12): Decimal {
    val plain = value.toPlainString()
    val dotIndex = plain.indexOf('.')
    if (dotIndex < 0) return value

    val fractional = plain.substring(dotIndex + 1)
    // Find trailing zeros or repeating patterns that suggest float artifacts
    val significantDigits = fractional.length

    return if (significantDigits > maxDigits) {
        roundTo(value, maxDigits)
    } else {
        value
    }
}

/**
 * Infer the quantum (smallest unit) from a list of Decimals.
 *
 * @param values List of Decimal values
 * @return The inferred quantum (e.g., 0.01 for currency)
 */
fun inferQuantumFromList(values: List<Decimal>): Decimal {
    if (values.isEmpty()) return Decimal.ONE

    var minQuantum: Decimal? = null
    for (value in values) {
        val digits = numFractionalDigits(value)
        if (digits > 0) {
            val quantum = Decimal.ONE.scaleByPowerOfTen(-digits)
            minQuantum = if (minQuantum == null || quantum < minQuantum) quantum else minQuantum
        }
    }

    return minQuantum ?: Decimal.ONE
}
