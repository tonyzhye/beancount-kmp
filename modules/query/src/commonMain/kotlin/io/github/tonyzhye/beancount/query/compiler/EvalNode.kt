package io.github.tonyzhye.beancount.query.compiler

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.*
import kotlinx.datetime.LocalDate

/**
 * Row context for query evaluation.
 */
interface RowContext {
    val entry: Directive
    val posting: Posting?
}

/**
 * EvalNode - execution tree node base class.
 * Based on beanquery.query_compile.EvalNode
 */
sealed interface EvalNode {
    val dtype: BqlType
    fun evaluate(context: RowContext): BqlValue
}

/**
 * Column access node.
 */
class EvalColumn(
    override val dtype: BqlType,
    val name: String,
    private val accessor: (RowContext) -> Any?
) : EvalNode {
    override fun evaluate(context: RowContext): BqlValue {
        return toBqlValue(accessor(context))
    }
}

/**
 * Constant value node.
 */
class EvalConstant(
    override val dtype: BqlType,
    private val value: BqlValue
) : EvalNode {
    override fun evaluate(context: RowContext): BqlValue = value
}

/**
 * Function call node.
 */
class EvalFunction(
    override val dtype: BqlType,
    private val function: (List<BqlValue>) -> BqlValue,
    private val operands: List<EvalNode>,
    private val passContext: Boolean = false,
    private val contextAccessor: ((RowContext) -> BqlValue)? = null
) : EvalNode {
    override fun evaluate(context: RowContext): BqlValue {
        val args = operands.map { it.evaluate(context) }.toMutableList()
        if (passContext && contextAccessor != null) {
            args.add(contextAccessor.invoke(context))
        }
        return function(args)
    }
}

/**
 * Binary operation node.
 */
class EvalBinaryOp(
    override val dtype: BqlType,
    val operator: String,
    private val left: EvalNode,
    private val right: EvalNode
) : EvalNode {
    override fun evaluate(context: RowContext): BqlValue {
        val l = left.evaluate(context)
        val r = right.evaluate(context)
        return when (operator) {
            "+" -> evaluateAdd(l, r)
            "-" -> evaluateSubtract(l, r)
            "*" -> evaluateMultiply(l, r)
            "/" -> evaluateDivide(l, r)
            "=" -> evaluateEqual(l, r)
            "!=" -> BqlBooleanValue(!evaluateEqual(l, r).value)
            "<" -> evaluateLessThan(l, r)
            ">" -> evaluateGreaterThan(l, r)
            "<=" -> evaluateLessThanOrEqual(l, r)
            ">=" -> evaluateGreaterThanOrEqual(l, r)
            "~" -> evaluateMatch(l, r)
            "AND" -> BqlBooleanValue(l.asBoolean() && r.asBoolean())
            "OR" -> BqlBooleanValue(l.asBoolean() || r.asBoolean())
            else -> throw IllegalArgumentException("Unknown operator: $operator")
        }
    }
}

/**
 * Unary operation node.
 */
class EvalUnaryOp(
    override val dtype: BqlType,
    val operator: String,
    private val operand: EvalNode
) : EvalNode {
    override fun evaluate(context: RowContext): BqlValue {
        val value = operand.evaluate(context)
        return when (operator) {
            "NOT" -> BqlBooleanValue(!value.asBoolean())
            "-" -> when (value.type) {
                BqlType.Decimal -> BqlDecimalValue(-value.asDecimal())
                BqlType.Integer -> BqlIntegerValue(-value.asInteger())
                else -> throw IllegalArgumentException("Cannot negate $value.type")
            }
            "+" -> value
            else -> throw IllegalArgumentException("Unknown unary operator: $operator")
        }
    }
}

/**
 * Aggregator base interface.
 */
interface EvalAggregator : EvalNode {
    val operand: EvalNode
    fun createAccumulator(): Accumulator
}

interface Accumulator {
    fun update(value: BqlValue)
    fun finalize(): BqlValue
}

// Helper functions for binary operations
private fun evaluateAdd(left: BqlValue, right: BqlValue): BqlValue {
    return when {
        left.type == BqlType.Decimal && right.type == BqlType.Decimal ->
            BqlDecimalValue(left.asDecimal() + right.asDecimal())
        left.type == BqlType.Integer && right.type == BqlType.Integer ->
            BqlIntegerValue(left.asInteger() + right.asInteger())
        left.type == BqlType.String && right.type == BqlType.String ->
            BqlStringValue(left.asString() + right.asString())
        else -> throw IllegalArgumentException("Cannot add ${left.type} and ${right.type}")
    }
}

private fun evaluateSubtract(left: BqlValue, right: BqlValue): BqlValue {
    return when {
        left.type == BqlType.Decimal && right.type == BqlType.Decimal ->
            BqlDecimalValue(left.asDecimal() - right.asDecimal())
        left.type == BqlType.Integer && right.type == BqlType.Integer ->
            BqlIntegerValue(left.asInteger() - right.asInteger())
        else -> throw IllegalArgumentException("Cannot subtract ${left.type} and ${right.type}")
    }
}

private fun evaluateMultiply(left: BqlValue, right: BqlValue): BqlValue {
    return when {
        left.type == BqlType.Decimal && right.type == BqlType.Decimal ->
            BqlDecimalValue(left.asDecimal() * right.asDecimal())
        left.type == BqlType.Integer && right.type == BqlType.Integer ->
            BqlIntegerValue(left.asInteger() * right.asInteger())
        else -> throw IllegalArgumentException("Cannot multiply ${left.type} and ${right.type}")
    }
}

private fun evaluateDivide(left: BqlValue, right: BqlValue): BqlValue {
    return when {
        left.type == BqlType.Decimal && right.type == BqlType.Decimal ->
            BqlDecimalValue(left.asDecimal() / right.asDecimal())
        left.type == BqlType.Integer && right.type == BqlType.Integer ->
            BqlIntegerValue(left.asInteger() / right.asInteger())
        else -> throw IllegalArgumentException("Cannot divide ${left.type} and ${right.type}")
    }
}

private fun evaluateEqual(left: BqlValue, right: BqlValue): BqlBooleanValue {
    if (left.isNull() && right.isNull()) return BqlBooleanValue(true)
    if (left.isNull() || right.isNull()) return BqlBooleanValue(false)
    
    return BqlBooleanValue(when {
        left.type == BqlType.String && right.type == BqlType.String ->
            left.asString() == right.asString()
        left.type == BqlType.Decimal && right.type == BqlType.Decimal ->
            left.asDecimal() == right.asDecimal()
        left.type == BqlType.Integer && right.type == BqlType.Integer ->
            left.asInteger() == right.asInteger()
        left.type == BqlType.Date && right.type == BqlType.Date ->
            left.asDate() == right.asDate()
        left.type == BqlType.Boolean && right.type == BqlType.Boolean ->
            left.asBoolean() == right.asBoolean()
        else -> false
    })
}

private fun evaluateLessThan(left: BqlValue, right: BqlValue): BqlBooleanValue {
    return BqlBooleanValue(when {
        (left.type == BqlType.Decimal || left.type == BqlType.Integer) &&
        (right.type == BqlType.Decimal || right.type == BqlType.Integer) -> {
            val leftDecimal = if (left.type == BqlType.Integer) Decimal(left.asInteger().toString()) else left.asDecimal()
            val rightDecimal = if (right.type == BqlType.Integer) Decimal(right.asInteger().toString()) else right.asDecimal()
            leftDecimal.compareTo(rightDecimal) < 0
        }
        left.type == BqlType.Date && right.type == BqlType.Date ->
            left.asDate().compareTo(right.asDate()) < 0
        else -> throw IllegalArgumentException("Cannot compare ${left.type} < ${right.type}")
    })
}

private fun evaluateGreaterThan(left: BqlValue, right: BqlValue): BqlBooleanValue {
    return BqlBooleanValue(when {
        (left.type == BqlType.Decimal || left.type == BqlType.Integer) &&
        (right.type == BqlType.Decimal || right.type == BqlType.Integer) -> {
            val leftDecimal = if (left.type == BqlType.Integer) Decimal(left.asInteger().toString()) else left.asDecimal()
            val rightDecimal = if (right.type == BqlType.Integer) Decimal(right.asInteger().toString()) else right.asDecimal()
            leftDecimal.compareTo(rightDecimal) > 0
        }
        left.type == BqlType.Date && right.type == BqlType.Date ->
            left.asDate().compareTo(right.asDate()) > 0
        else -> throw IllegalArgumentException("Cannot compare ${left.type} > ${right.type}")
    })
}

private fun evaluateLessThanOrEqual(left: BqlValue, right: BqlValue): BqlBooleanValue {
    val gt = evaluateGreaterThan(left, right)
    return BqlBooleanValue(!gt.value)
}

private fun evaluateGreaterThanOrEqual(left: BqlValue, right: BqlValue): BqlBooleanValue {
    val lt = evaluateLessThan(left, right)
    return BqlBooleanValue(!lt.value)
}

private fun evaluateMatch(left: BqlValue, right: BqlValue): BqlBooleanValue {
    val str = left.asString()
    val pattern = right.asString()
    return BqlBooleanValue(str.contains(Regex(pattern)))
}
