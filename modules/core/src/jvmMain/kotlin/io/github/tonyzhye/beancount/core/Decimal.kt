package io.github.tonyzhye.beancount.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * JVM implementation of Decimal using java.math.BigDecimal.
 * 
 * Provides exact decimal arithmetic with configurable precision.
 * This is the production-ready implementation for JVM platforms.
 */
actual class Decimal actual constructor(value: String) : Comparable<Decimal> {
    
    private val bigDecimal: BigDecimal = BigDecimal(value)
    
    actual constructor(value: Double) : this(value.toString())
    actual constructor(value: Long) : this(value.toString())
    
    private constructor(bigDecimal: BigDecimal) : this(bigDecimal.toPlainString())
    
    actual operator fun plus(other: Decimal): Decimal = Decimal(this.bigDecimal + other.bigDecimal)
    actual operator fun minus(other: Decimal): Decimal = Decimal(this.bigDecimal - other.bigDecimal)
    actual operator fun times(other: Decimal): Decimal = Decimal(this.bigDecimal * other.bigDecimal)
    actual operator fun div(other: Decimal): Decimal = Decimal(
        this.bigDecimal.divide(other.bigDecimal, DEFAULT_SCALE, DEFAULT_ROUNDING)
    )
    
    actual operator fun unaryMinus(): Decimal = Decimal(-this.bigDecimal)
    
    actual fun abs(): Decimal = Decimal(this.bigDecimal.abs())
    actual fun negate(): Decimal = Decimal(this.bigDecimal.negate())
    actual fun isZero(): Boolean = this.bigDecimal.compareTo(BigDecimal.ZERO) == 0
    actual fun isPositive(): Boolean = this.bigDecimal > BigDecimal.ZERO
    actual fun isNegative(): Boolean = this.bigDecimal < BigDecimal.ZERO
    
    actual fun toPlainString(): String = this.bigDecimal.toPlainString()
    actual fun toDouble(): Double = this.bigDecimal.toDouble()
    
    actual fun scaleByPowerOfTen(n: Int): Decimal = Decimal(this.bigDecimal.scaleByPowerOfTen(n))
    
    override actual fun compareTo(other: Decimal): Int = this.bigDecimal.compareTo(other.bigDecimal)
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Decimal) return false
        return this.bigDecimal.compareTo(other.bigDecimal) == 0
    }
    
    override fun hashCode(): Int = bigDecimal.stripTrailingZeros().hashCode()
    
    override fun toString(): String = toPlainString()
    
    actual companion object {
        actual val ZERO: Decimal = Decimal(BigDecimal.ZERO)
        actual val ONE: Decimal = Decimal(BigDecimal.ONE)
        
        private const val DEFAULT_SCALE = 20
        private val DEFAULT_ROUNDING = RoundingMode.HALF_EVEN
    }
}
