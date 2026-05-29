package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate

/**
 * BQL type system for query engine.
 * Based on beanquery.types
 */
sealed interface BqlType {
    object String : BqlType
    object Decimal : BqlType
    object Date : BqlType
    object Boolean : BqlType
    object Integer : BqlType
    object Set : BqlType
    object Inventory : BqlType
    object Position : BqlType
    object Amount : BqlType
    object Cost : BqlType
    object Transaction : BqlType
    object Any : BqlType
    object Null : BqlType
}

/**
 * BQL value wrapper for type-safe evaluation.
 */
sealed interface BqlValue {
    val type: BqlType
    val raw: Any?
    
    fun isNull(): Boolean = this is BqlNullValue
    
    fun asString(): String = (this as? BqlStringValue)?.value
        ?: throw TypeCastException("Cannot cast $type to String")
    
    fun asDecimal(): io.github.tonyzhye.beancount.core.Decimal = (this as? BqlDecimalValue)?.value
        ?: throw TypeCastException("Cannot cast $type to Decimal")
    
    fun asDate(): LocalDate = (this as? BqlDateValue)?.value
        ?: throw TypeCastException("Cannot cast $type to Date")
    
    fun asBoolean(): Boolean = (this as? BqlBooleanValue)?.value
        ?: throw TypeCastException("Cannot cast $type to Boolean")
    
    fun asInteger(): Int = (this as? BqlIntegerValue)?.value
        ?: throw TypeCastException("Cannot cast $type to Integer")
    
    fun asSet(): kotlin.collections.Set<String> = (this as? BqlSetValue)?.value
        ?: throw TypeCastException("Cannot cast $type to Set")
    
    fun asInventory(): io.github.tonyzhye.beancount.core.Inventory = (this as? BqlInventoryValue)?.value
        ?: throw TypeCastException("Cannot cast $type to Inventory")
    
    fun asPosition(): io.github.tonyzhye.beancount.core.Position = (this as? BqlPositionValue)?.value
        ?: throw TypeCastException("Cannot cast $type to Position")
    
    fun asAmount(): io.github.tonyzhye.beancount.core.Amount = (this as? BqlAmountValue)?.value
        ?: throw TypeCastException("Cannot cast $type to Amount")
}

data class BqlNullValue(override val raw: Nothing? = null) : BqlValue {
    override val type: BqlType = BqlType.Null
}

data class BqlStringValue(val value: String) : BqlValue {
    override val type: BqlType = BqlType.String
    override val raw: Any = value
}

data class BqlDecimalValue(val value: io.github.tonyzhye.beancount.core.Decimal) : BqlValue {
    override val type: BqlType = BqlType.Decimal
    override val raw: Any = value
}

data class BqlDateValue(val value: LocalDate) : BqlValue {
    override val type: BqlType = BqlType.Date
    override val raw: Any = value
}

data class BqlBooleanValue(val value: Boolean) : BqlValue {
    override val type: BqlType = BqlType.Boolean
    override val raw: Any = value
}

data class BqlIntegerValue(val value: Int) : BqlValue {
    override val type: BqlType = BqlType.Integer
    override val raw: Any = value
}

data class BqlSetValue(val value: kotlin.collections.Set<String>) : BqlValue {
    override val type: BqlType = BqlType.Set
    override val raw: Any = value
}

data class BqlInventoryValue(val value: io.github.tonyzhye.beancount.core.Inventory) : BqlValue {
    override val type: BqlType = BqlType.Inventory
    override val raw: Any = value
}

data class BqlPositionValue(val value: io.github.tonyzhye.beancount.core.Position) : BqlValue {
    override val type: BqlType = BqlType.Position
    override val raw: Any = value
}

data class BqlAmountValue(val value: io.github.tonyzhye.beancount.core.Amount) : BqlValue {
    override val type: BqlType = BqlType.Amount
    override val raw: Any = value
}

data class BqlCostValue(val value: io.github.tonyzhye.beancount.core.Cost) : BqlValue {
    override val type: BqlType = BqlType.Cost
    override val raw: Any = value
}

data class BqlTransactionValue(val value: io.github.tonyzhye.beancount.core.Transaction) : BqlValue {
    override val type: BqlType = BqlType.Transaction
    override val raw: Any = value
}

class TypeCastException(message: String) : RuntimeException(message)

/**
 * Convert a Kotlin value to BqlValue.
 */
fun toBqlValue(value: Any?): BqlValue {
    return when (value) {
        null -> BqlNullValue()
        is String -> BqlStringValue(value)
        is io.github.tonyzhye.beancount.core.Decimal -> BqlDecimalValue(value)
        is LocalDate -> BqlDateValue(value)
        is Boolean -> BqlBooleanValue(value)
        is Int -> BqlIntegerValue(value)
        is kotlin.collections.Set<*> -> BqlSetValue(value.filterIsInstance<String>().toSet())
        is io.github.tonyzhye.beancount.core.Inventory -> BqlInventoryValue(value)
        is io.github.tonyzhye.beancount.core.Position -> BqlPositionValue(value)
        is io.github.tonyzhye.beancount.core.Amount -> BqlAmountValue(value)
        is io.github.tonyzhye.beancount.core.Cost -> BqlCostValue(value)
        is io.github.tonyzhye.beancount.core.Transaction -> BqlTransactionValue(value)
        else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
    }
}
