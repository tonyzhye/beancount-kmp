package io.github.tonyzhye.beancount.query.functions

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.*
import io.github.tonyzhye.beancount.query.compiler.Accumulator
import io.github.tonyzhye.beancount.query.compiler.EvalAggregator
import io.github.tonyzhye.beancount.query.compiler.EvalFunction
import io.github.tonyzhye.beancount.query.compiler.RowContext
import kotlinx.datetime.LocalDate

/**
 * Function signature for type checking.
 */
data class FunctionSignature(
    val parameterTypes: List<BqlType>,
    val returnType: BqlType,
    val passContext: Boolean = false
)

/**
 * Registered function entry.
 */
data class RegisteredFunction(
    val signature: FunctionSignature,
    val implementation: (List<BqlValue>) -> BqlValue
)

/**
 * Aggregator factory interface.
 */
interface AggregatorFactory {
    val signature: FunctionSignature
    fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator
}

/**
 * Global function registry.
 * Based on beanquery.query_env.FUNCTIONS.
 */
object FunctionRegistry {
    private val functions = mutableMapOf<String, MutableList<RegisteredFunction>>()
    private val aggregatorFactories = mutableMapOf<String, MutableList<AggregatorFactory>>()

    init {
        registerBuiltInFunctions()
        registerBuiltInAggregators()
    }

    fun registerFunction(name: String, signature: FunctionSignature, implementation: (List<BqlValue>) -> BqlValue) {
        functions.getOrPut(name.lowercase()) { mutableListOf() }
            .add(RegisteredFunction(signature, implementation))
    }

    fun registerAggregator(name: String, signature: FunctionSignature, factory: AggregatorFactory) {
        aggregatorFactories.getOrPut(name.lowercase()) { mutableListOf() }
            .add(factory)
    }

    fun resolveFunction(name: String, argumentTypes: List<BqlType>): RegisteredFunction? {
        val candidates = functions[name.lowercase()] ?: return null
        return candidates.find { matchesSignature(it.signature, argumentTypes) }
            ?: candidates.find { isGenericMatch(it.signature, argumentTypes) }
    }

    fun resolveAggregatorFactory(name: String, argumentTypes: List<BqlType>): AggregatorFactory? {
        val candidates = aggregatorFactories[name.lowercase()] ?: return null
        return candidates.find { matchesSignature(it.signature, argumentTypes) }
            ?: candidates.find { isGenericMatch(it.signature, argumentTypes) }
    }

    private fun matchesSignature(signature: FunctionSignature, argumentTypes: List<BqlType>): Boolean {
        if (signature.parameterTypes.size != argumentTypes.size) return false
        return signature.parameterTypes.zip(argumentTypes).all { (expected, actual) ->
            expected == BqlType.Any || expected == actual
        }
    }

    private fun isGenericMatch(signature: FunctionSignature, argumentTypes: List<BqlType>): Boolean {
        return signature.parameterTypes.all { it == BqlType.Any }
    }

    private fun registerBuiltInFunctions() {
        // Type conversion functions
        registerFunction("bool", FunctionSignature(listOf(BqlType.Any), BqlType.Boolean)) { args ->
            BqlBooleanValue(args[0].raw?.let { it as? Boolean ?: it.toString().toBoolean() } ?: false)
        }

        registerFunction("str", FunctionSignature(listOf(BqlType.Any), BqlType.String)) { args ->
            BqlStringValue(args[0].raw?.toString() ?: "")
        }

        registerFunction("lower", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            BqlStringValue(args[0].asString().lowercase())
        }

        registerFunction("upper", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            BqlStringValue(args[0].asString().uppercase())
        }

        registerFunction("length", FunctionSignature(listOf(BqlType.String), BqlType.Integer)) { args ->
            BqlIntegerValue(args[0].asString().length)
        }

        // Date functions
        registerFunction("year", FunctionSignature(listOf(BqlType.Date), BqlType.Integer)) { args ->
            BqlIntegerValue(args[0].asDate().year)
        }

        registerFunction("month", FunctionSignature(listOf(BqlType.Date), BqlType.Integer)) { args ->
            BqlIntegerValue(args[0].asDate().monthNumber)
        }

        registerFunction("day", FunctionSignature(listOf(BqlType.Date), BqlType.Integer)) { args ->
            BqlIntegerValue(args[0].asDate().dayOfMonth)
        }

        // Math functions
        registerFunction("abs", FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal)) { args ->
            BqlDecimalValue(args[0].asDecimal().abs())
        }

        // Coalesce
        registerFunction("coalesce", FunctionSignature(listOf(BqlType.Any, BqlType.Any), BqlType.Any)) { args ->
            if (!args[0].isNull()) args[0] else args[1]
        }

        // Account functions
        registerFunction("account", FunctionSignature(listOf(BqlType.Any), BqlType.String)) { args ->
            when {
                args[0].type == BqlType.String -> BqlStringValue(args[0].asString())
                args[0].type == BqlType.Transaction -> {
                    val txn = args[0].raw as? io.github.tonyzhye.beancount.core.Transaction
                    BqlStringValue(txn?.postings?.firstOrNull()?.account ?: "")
                }
                args[0].type == BqlType.Position -> {
                    BqlStringValue(args[0].asPosition().units.currency)
                }
                else -> BqlStringValue("")
            }
        }

        registerFunction("currency", FunctionSignature(listOf(BqlType.Any), BqlType.String)) { args ->
            when {
                args[0].type == BqlType.Amount -> BqlStringValue(args[0].asAmount().currency)
                args[0].type == BqlType.Position -> BqlStringValue(args[0].asPosition().units.currency)
                else -> BqlStringValue("")
            }
        }

        registerFunction("number", FunctionSignature(listOf(BqlType.Any), BqlType.Decimal)) { args ->
            when {
                args[0].type == BqlType.Amount -> BqlDecimalValue(args[0].asAmount().number)
                args[0].type == BqlType.Position -> BqlDecimalValue(args[0].asPosition().units.number)
                args[0].type == BqlType.Decimal -> args[0]
                else -> BqlDecimalValue(Decimal.ZERO)
            }
        }

        registerFunction("parent", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            val account = args[0].asString()
            val lastColon = account.lastIndexOf(':')
            if (lastColon > 0) {
                BqlStringValue(account.substring(0, lastColon))
            } else {
                BqlStringValue("")
            }
        }

        registerFunction("root", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            val account = args[0].asString()
            val firstColon = account.indexOf(':')
            if (firstColon > 0) {
                BqlStringValue(account.substring(0, firstColon))
            } else {
                BqlStringValue(account)
            }
        }

        // Position/Cost functions
        registerFunction("position", FunctionSignature(listOf(BqlType.Amount, BqlType.Cost), BqlType.Position)) { args ->
            val amount = args[0].asAmount()
            val cost = args[1].raw as? io.github.tonyzhye.beancount.core.Cost
            BqlPositionValue(io.github.tonyzhye.beancount.core.Position(amount, cost))
        }

        registerFunction("position", FunctionSignature(listOf(BqlType.Amount), BqlType.Position)) { args ->
            val amount = args[0].asAmount()
            BqlPositionValue(io.github.tonyzhye.beancount.core.Position(amount, null))
        }

        registerFunction("cost", FunctionSignature(listOf(BqlType.Position), BqlType.Cost)) { args ->
            val position = args[0].asPosition()
            position.cost?.let {
                toBqlValue(it)
            } ?: BqlNullValue()
        }

        // Round function
        registerFunction("round", FunctionSignature(listOf(BqlType.Decimal, BqlType.Integer), BqlType.Decimal)) { args ->
            val number = args[0].asDecimal()
            val digits = args[1].asInteger()
            // Simple rounding: multiply by 10^digits, add 0.5, truncate, divide by 10^digits
            val multiplierStr = if (digits > 0) "1${"0".repeat(digits)}" else "1"
            val multiplier = Decimal(multiplierStr)
            val scaled = number * multiplier
            val sign = if (scaled.isNegative()) -1 else 1
            val absScaled = scaled.abs()
            val rounded = (absScaled + Decimal("0.5")) / multiplier
            BqlDecimalValue(if (sign < 0) -rounded else rounded)
        }

        // Date formatting
        registerFunction("format_date", FunctionSignature(listOf(BqlType.Date, BqlType.String), BqlType.String)) { args ->
            val date = args[0].asDate()
            val format = args[1].asString()
            val formatted = when (format) {
                "%Y-%m-%d" -> date.toString()
                "%Y" -> date.year.toString()
                "%m" -> date.monthNumber.toString().padStart(2, '0')
                "%d" -> date.dayOfMonth.toString().padStart(2, '0')
                "%Y-%m" -> "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
                else -> date.toString()
            }
            BqlStringValue(formatted)
        }
    }

    private fun registerBuiltInAggregators() {
        // Count aggregator
        registerAggregator(
            "count",
            FunctionSignature(listOf(BqlType.Any), BqlType.Integer),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Any), BqlType.Integer)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Integer
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var count = 0
                            override fun update(value: BqlValue) { count++ }
                            override fun finalize(): BqlValue = BqlIntegerValue(count)
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        // Sum aggregator for Decimal
        registerAggregator(
            "sum",
            FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Decimal
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var total = Decimal.ZERO
                            override fun update(value: BqlValue) {
                                if (!value.isNull()) total += value.asDecimal()
                            }
                            override fun finalize(): BqlValue = BqlDecimalValue(total)
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        // Sum aggregator for Integer
        registerAggregator(
            "sum",
            FunctionSignature(listOf(BqlType.Integer), BqlType.Integer),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Integer), BqlType.Integer)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Integer
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var total = 0
                            override fun update(value: BqlValue) {
                                if (!value.isNull()) total += value.asInteger()
                            }
                            override fun finalize(): BqlValue = BqlIntegerValue(total)
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        // Sum aggregator for Amount
        registerAggregator(
            "sum",
            FunctionSignature(listOf(BqlType.Amount), BqlType.Amount),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Amount), BqlType.Amount)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Amount
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var total: Amount? = null
                            override fun update(value: BqlValue) {
                                if (value.isNull()) return
                                val amount = value.asAmount()
                                total = if (total == null) {
                                    amount
                                } else {
                                    Amount(total!!.number + amount.number, total!!.currency)
                                }
                            }
                            override fun finalize(): BqlValue = total?.let { BqlAmountValue(it) } ?: BqlNullValue()
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        // Sum aggregator for Inventory
        registerAggregator(
            "sum",
            FunctionSignature(listOf(BqlType.Inventory), BqlType.Inventory),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Inventory), BqlType.Inventory)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Inventory
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            val inventory = Inventory()
                            override fun update(value: BqlValue) {
                                if (value.isNull()) return
                                inventory.addInventory(value.asInventory())
                            }
                            override fun finalize(): BqlValue = BqlInventoryValue(inventory.copy())
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        // First aggregator
        registerAggregator(
            "first",
            FunctionSignature(listOf(BqlType.Any), BqlType.Any),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Any), BqlType.Any)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Any
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var value: BqlValue = BqlNullValue()
                            var hasValue = false
                            override fun update(value: BqlValue) {
                                if (!hasValue && !value.isNull()) {
                                    this.value = value
                                    hasValue = true
                                }
                            }
                            override fun finalize(): BqlValue = value
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        // Last aggregator
        registerAggregator(
            "last",
            FunctionSignature(listOf(BqlType.Any), BqlType.Any),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Any), BqlType.Any)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Any
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var value: BqlValue = BqlNullValue()
                            override fun update(value: BqlValue) {
                                if (!value.isNull()) this.value = value
                            }
                            override fun finalize(): BqlValue = value
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        // Max aggregator
        registerAggregator(
            "max",
            FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Decimal
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var max: Decimal? = null
                            override fun update(value: BqlValue) {
                                if (value.isNull()) return
                                val decimal = value.asDecimal()
                                if (max == null || decimal > max!!) max = decimal
                            }
                            override fun finalize(): BqlValue = max?.let { BqlDecimalValue(it) } ?: BqlNullValue()
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        // Min aggregator
        registerAggregator(
            "min",
            FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Decimal
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var min: Decimal? = null
                            override fun update(value: BqlValue) {
                                if (value.isNull()) return
                                val decimal = value.asDecimal()
                                if (min == null || decimal < min!!) min = decimal
                            }
                            override fun finalize(): BqlValue = min?.let { BqlDecimalValue(it) } ?: BqlNullValue()
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )
    }
}
