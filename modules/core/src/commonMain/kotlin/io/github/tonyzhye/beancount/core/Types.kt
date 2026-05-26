package io.github.tonyzhye.beancount.core

typealias Account = String
typealias Currency = String
typealias Flag = String
typealias Meta = Map<String, Any>

/**
 * Platform-independent decimal type.
 * 
 * Design Decision:
 * - JVM: java.math.BigDecimal (exact precision)
 * - iOS/Native: Swift NSDecimalNumber bridge (TBD in future phase)
 * - JS: TBD
 * 
 * This provides a unified interface while allowing platform-specific 
 * high-precision implementations.
 */
expect class Decimal : Comparable<Decimal> {
    constructor(value: String)
    constructor(value: Double)
    constructor(value: Long)
    
    operator fun plus(other: Decimal): Decimal
    operator fun minus(other: Decimal): Decimal
    operator fun times(other: Decimal): Decimal
    operator fun div(other: Decimal): Decimal
    operator fun unaryMinus(): Decimal
    
    fun abs(): Decimal
    fun negate(): Decimal
    fun isZero(): Boolean
    fun isPositive(): Boolean
    fun isNegative(): Boolean
    
    fun toPlainString(): String
    fun toDouble(): Double
    
    override fun compareTo(other: Decimal): Int
    
    companion object {
        val ZERO: Decimal
        val ONE: Decimal
    }
}
