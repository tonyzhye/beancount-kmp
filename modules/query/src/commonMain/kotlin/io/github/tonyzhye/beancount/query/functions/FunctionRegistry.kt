package io.github.tonyzhye.beancount.query.functions

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.*
import io.github.tonyzhye.beancount.query.compiler.Accumulator
import io.github.tonyzhye.beancount.query.compiler.EvalAggregator
import io.github.tonyzhye.beancount.query.compiler.EvalFunction
import io.github.tonyzhye.beancount.query.compiler.RowContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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

        registerFunction("length", FunctionSignature(listOf(BqlType.Set), BqlType.Integer)) { args ->
            BqlIntegerValue(args[0].asSet().size)
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

        registerFunction("getweight", FunctionSignature(listOf(BqlType.Position), BqlType.Amount)) { args ->
            val position = args[0].asPosition()
            val cost = position.cost
            val weight = if (cost != null) {
                Amount(cost.number * position.units.number, cost.currency)
            } else {
                position.units
            }
            BqlAmountValue(weight)
        }

        registerFunction("getvalue", FunctionSignature(listOf(BqlType.Position, BqlType.String), BqlType.Amount, passContext = true)) { args ->
            val position = args[0].asPosition()
            val targetCurrency = args[1].asString()
            val priceMap = (args.getOrNull(2) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null && position.units.currency != targetCurrency) {
                try {
                    val converted = io.github.tonyzhye.beancount.core.convertPosition(
                        position, targetCurrency, priceMap, null
                    )
                    BqlAmountValue(converted)
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                val value = if (position.units.currency == targetCurrency) {
                    position.units
                } else {
                    null
                }
                value?.let { BqlAmountValue(it) } ?: BqlNullValue()
            }
        }

        registerFunction("convert", FunctionSignature(listOf(BqlType.Amount, BqlType.String), BqlType.Amount, passContext = true)) { args ->
            val amount = args[0].asAmount()
            val targetCurrency = args[1].asString()
            val priceMap = (args.getOrNull(2) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null && amount.currency != targetCurrency) {
                try {
                    val converted = io.github.tonyzhye.beancount.core.convertAmount(
                        amount, targetCurrency, priceMap, null
                    )
                    BqlAmountValue(converted)
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                val value = if (amount.currency == targetCurrency) amount else null
                value?.let { BqlAmountValue(it) } ?: BqlNullValue()
            }
        }

        registerFunction("convert", FunctionSignature(listOf(BqlType.Position, BqlType.String), BqlType.Amount, passContext = true)) { args ->
            val position = args[0].asPosition()
            val targetCurrency = args[1].asString()
            val priceMap = (args.getOrNull(2) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null && position.units.currency != targetCurrency) {
                try {
                    val converted = io.github.tonyzhye.beancount.core.convertPosition(
                        position, targetCurrency, priceMap, null
                    )
                    BqlAmountValue(converted)
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                val value = if (position.units.currency == targetCurrency) position.units else null
                value?.let { BqlAmountValue(it) } ?: BqlNullValue()
            }
        }

        registerFunction("getprice", FunctionSignature(listOf(BqlType.String, BqlType.String), BqlType.Amount, passContext = true)) { args ->
            val currency = args[0].asString()
            val targetCurrency = args[1].asString()
            val priceMap = (args.getOrNull(2) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null) {
                try {
                    val (_, rate) = priceMap.getPrice(currency, targetCurrency)
                    if (rate != null) {
                        BqlAmountValue(Amount(rate, targetCurrency))
                    } else {
                        BqlNullValue()
                    }
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("getunits", FunctionSignature(listOf(BqlType.Position), BqlType.Amount)) { args ->
            val position = args[0].asPosition()
            BqlAmountValue(position.units)
        }

        // Type casting functions
        registerFunction("int", FunctionSignature(listOf(BqlType.Any), BqlType.Integer)) { args ->
            val value = args[0].raw
            val intValue = when (value) {
                is Int -> value
                is Long -> value.toInt()
                is Double -> value.toInt()
                is Decimal -> value.toDouble().toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            }
            BqlIntegerValue(intValue)
        }

        registerFunction("decimal", FunctionSignature(listOf(BqlType.Any), BqlType.Decimal)) { args ->
            val value = args[0].raw
            val decimalValue = when (value) {
                is Decimal -> value
                is Int -> Decimal(value.toString())
                is Long -> Decimal(value.toString())
                is Double -> Decimal(value.toString())
                is String -> Decimal(value)
                else -> Decimal.ZERO
            }
            BqlDecimalValue(decimalValue)
        }

        registerFunction("date", FunctionSignature(listOf(BqlType.String), BqlType.Date)) { args ->
            val dateStr = args[0].asString()
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                try {
                    BqlDateValue(LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()))
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
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

        // Additional date functions
        registerFunction("weekday", FunctionSignature(listOf(BqlType.Date), BqlType.Integer)) { args ->
            val date = args[0].asDate()
            // Compute day of week (1=Monday, 7=Sunday)
            // Using Zeller's congruence simplified
            val q = date.dayOfMonth
            val m = if (date.monthNumber < 3) date.monthNumber + 12 else date.monthNumber
            val year = if (date.monthNumber < 3) date.year - 1 else date.year
            val k = year % 100
            val j = year / 100
            val h = (q + ((13 * (m + 1)) / 5) + k + (k / 4) + (j / 4) - 2 * j) % 7
            val dow = if (h <= 0) h + 7 else h
            // h: 0=Sat, 1=Sun, 2=Mon... convert to 1=Mon, 7=Sun
            val result = if (dow == 1) 7 else dow - 1
            BqlIntegerValue(result)
        }

        registerFunction("quarter", FunctionSignature(listOf(BqlType.Date), BqlType.Integer)) { args ->
            val month = args[0].asDate().monthNumber
            BqlIntegerValue((month - 1) / 3 + 1)
        }

        registerFunction("days_between", FunctionSignature(listOf(BqlType.Date, BqlType.Date), BqlType.Integer)) { args ->
            val d1 = args[0].asDate()
            val d2 = args[1].asDate()
            BqlIntegerValue(d1.daysUntil(d2))
        }

        registerFunction("date_add", FunctionSignature(listOf(BqlType.Date, BqlType.Integer), BqlType.Date)) { args ->
            val date = args[0].asDate()
            val days = args[1].asInteger()
            BqlDateValue(date.plus(days, DateTimeUnit.DAY))
        }

        registerFunction("date_diff", FunctionSignature(listOf(BqlType.Date, BqlType.Date), BqlType.Integer)) { args ->
            val d1 = args[0].asDate()
            val d2 = args[1].asDate()
            BqlIntegerValue(d1.daysUntil(d2))
        }

        registerFunction("date_trunc", FunctionSignature(listOf(BqlType.Date, BqlType.String), BqlType.Date)) { args ->
            val date = args[0].asDate()
            val unit = args[1].asString().lowercase()
            val truncated = when (unit) {
                "year", "y" -> LocalDate(date.year, 1, 1)
                "quarter", "q" -> {
                    val quarterMonth = ((date.monthNumber - 1) / 3) * 3 + 1
                    LocalDate(date.year, quarterMonth, 1)
                }
                "month", "m" -> LocalDate(date.year, date.monthNumber, 1)
                "week", "w" -> {
                    // Find Monday of the week
                    val daysSinceMonday = (date.dayOfWeek.value + 6) % 7
                    date.minus(daysSinceMonday, DateTimeUnit.DAY)
                }
                else -> date
            }
            BqlDateValue(truncated)
        }

        // String functions
        registerFunction("join", FunctionSignature(listOf(BqlType.String, BqlType.String), BqlType.String)) { args ->
            // Simple 2-arg join
            BqlStringValue(args[0].asString() + args[1].asString())
        }

        registerFunction("replace", FunctionSignature(listOf(BqlType.String, BqlType.String, BqlType.String), BqlType.String)) { args ->
            BqlStringValue(args[0].asString().replace(args[1].asString(), args[2].asString()))
        }

        registerFunction("lstrip", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            BqlStringValue(args[0].asString().trimStart())
        }

        registerFunction("rstrip", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            BqlStringValue(args[0].asString().trimEnd())
        }

        registerFunction("strip", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            BqlStringValue(args[0].asString().trim())
        }

        // Math functions
        registerFunction("floor", FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal)) { args ->
            val num = args[0].asDecimal()
            val truncated = num.truncate()
            // If num is negative and has fractional part, floor is truncated - 1
            BqlDecimalValue(if (num.isNegative() && num != truncated) truncated - Decimal.ONE else truncated)
        }

        registerFunction("ceil", FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal)) { args ->
            val num = args[0].asDecimal()
            val truncated = num.truncate()
            // If num is positive and has fractional part, ceil is truncated + 1
            BqlDecimalValue(if (num.isPositive() && num != truncated) truncated + Decimal.ONE else truncated)
        }

        registerFunction("sign", FunctionSignature(listOf(BqlType.Decimal), BqlType.Integer)) { args ->
            val num = args[0].asDecimal()
            BqlIntegerValue(when {
                num.isZero() -> 0
                num.isNegative() -> -1
                else -> 1
            })
        }

        registerFunction("safediv", FunctionSignature(listOf(BqlType.Decimal, BqlType.Decimal), BqlType.Decimal)) { args ->
            val dividend = args[0].asDecimal()
            val divisor = args[1].asDecimal()
            BqlDecimalValue(if (divisor.isZero()) Decimal.ZERO else dividend / divisor)
        }

        // Account functions
        registerFunction("depth", FunctionSignature(listOf(BqlType.String), BqlType.Integer)) { args ->
            val account = args[0].asString()
            BqlIntegerValue(account.count { it == ':' } + 1)
        }

        registerFunction("leaf", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            val account = args[0].asString()
            val lastColon = account.lastIndexOf(':')
            BqlStringValue(if (lastColon >= 0) account.substring(lastColon + 1) else account)
        }

        registerFunction("split", FunctionSignature(listOf(BqlType.String), BqlType.Set)) { args ->
            BqlSetValue(args[0].asString().split(':').toSet())
        }

        // Null/empty check functions
        registerFunction("empty", FunctionSignature(listOf(BqlType.Any), BqlType.Boolean)) { args ->
            val value = args[0]
            BqlBooleanValue(when {
                value.isNull() -> true
                value.type == BqlType.String -> value.asString().isEmpty()
                value.type == BqlType.Set -> value.asSet().isEmpty()
                else -> false
            })
        }

        registerFunction("present", FunctionSignature(listOf(BqlType.Any), BqlType.Boolean)) { args ->
            val value = args[0]
            BqlBooleanValue(when {
                value.isNull() -> false
                value.type == BqlType.String -> value.asString().isNotEmpty()
                value.type == BqlType.Set -> value.asSet().isNotEmpty()
                else -> true
            })
        }

        // Negation function
        registerFunction("neg", FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal)) { args ->
            BqlDecimalValue(-args[0].asDecimal())
        }

        // Today function
        registerFunction("today", FunctionSignature(emptyList(), BqlType.Date)) { _ ->
            val now = kotlinx.datetime.Clock.System.now()
            val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
            BqlDateValue(now.toLocalDateTime(tz).date)
        }

        // String functions (enhanced)
        registerFunction("maxwidth", FunctionSignature(listOf(BqlType.String, BqlType.Integer), BqlType.String)) { args ->
            val str = args[0].asString()
            val width = args[1].asInteger()
            if (str.length > width) {
                BqlStringValue(str.take(width))
            } else {
                BqlStringValue(str)
            }
        }

        registerFunction("grep", FunctionSignature(listOf(BqlType.String, BqlType.String), BqlType.String)) { args ->
            val pattern = args[0].asString()
            val text = args[1].asString()
            val match = Regex(pattern).find(text)
            BqlStringValue(match?.value ?: "")
        }

        registerFunction("grepn", FunctionSignature(listOf(BqlType.String, BqlType.String, BqlType.Integer), BqlType.String)) { args ->
            val pattern = args[0].asString()
            val text = args[1].asString()
            val groupIndex = args[2].asInteger()
            val match = Regex(pattern).find(text)
            val groups = match?.groupValues
            if (groups != null && groupIndex in groups.indices) {
                BqlStringValue(groups[groupIndex])
            } else {
                BqlStringValue("")
            }
        }

        registerFunction("subst", FunctionSignature(listOf(BqlType.String, BqlType.String, BqlType.String), BqlType.String)) { args ->
            val pattern = args[0].asString()
            val replacement = args[1].asString()
            val text = args[2].asString()
            BqlStringValue(text.replaceFirst(Regex(pattern), replacement))
        }

        registerFunction("findfirst", FunctionSignature(listOf(BqlType.String, BqlType.Set), BqlType.String)) { args ->
            val pattern = args[0].asString()
            val set = args[1].asSet()
            val match = set.find { Regex(pattern).containsMatchIn(it) }
            BqlStringValue(match ?: "")
        }

        registerFunction("joinstr", FunctionSignature(listOf(BqlType.Set), BqlType.String)) { args ->
            val set = args[0].asSet()
            BqlStringValue(set.joinToString(", "))
        }

        // Math functions (enhanced)
        registerFunction("abs", FunctionSignature(listOf(BqlType.Amount), BqlType.Amount)) { args ->
            val amount = args[0].asAmount()
            BqlAmountValue(Amount(amount.number.abs(), amount.currency))
        }

        registerFunction("abs", FunctionSignature(listOf(BqlType.Position), BqlType.Position)) { args ->
            val pos = args[0].asPosition()
            BqlPositionValue(Position(Amount(pos.units.number.abs(), pos.units.currency), pos.cost))
        }

        registerFunction("abs", FunctionSignature(listOf(BqlType.Inventory), BqlType.Inventory)) { args ->
            val inv = args[0].asInventory()
            val result = Inventory()
            for (pos in inv) {
                result.addAmount(Amount(pos.units.number.abs(), pos.units.currency), pos.cost)
            }
            BqlInventoryValue(result)
        }

        registerFunction("safediv", FunctionSignature(listOf(BqlType.Decimal, BqlType.Integer), BqlType.Decimal)) { args ->
            val dividend = args[0].asDecimal()
            val divisor = args[1].asInteger()
            BqlDecimalValue(if (divisor == 0) Decimal.ZERO else dividend / Decimal(divisor.toString()))
        }

        // Date functions (enhanced)
        registerFunction("ymonth", FunctionSignature(listOf(BqlType.Date), BqlType.Date)) { args ->
            val date = args[0].asDate()
            BqlDateValue(LocalDate(date.year, date.monthNumber, 1))
        }

        registerFunction("quarter", FunctionSignature(listOf(BqlType.Date), BqlType.String)) { args ->
            val date = args[0].asDate()
            val q = (date.monthNumber - 1) / 3 + 1
            BqlStringValue("${date.year}-Q$q")
        }

        registerFunction("weekday", FunctionSignature(listOf(BqlType.Date), BqlType.String)) { args ->
            val date = args[0].asDate()
            val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            BqlStringValue(names[date.dayOfWeek.value - 1])
        }

        registerFunction("date", FunctionSignature(listOf(BqlType.Integer, BqlType.Integer, BqlType.Integer), BqlType.Date)) { args ->
            val year = args[0].asInteger()
            val month = args[1].asInteger()
            val day = args[2].asInteger()
            BqlDateValue(LocalDate(year, month, day))
        }

        // Position/Inventory functions (enhanced)
        registerFunction("root", FunctionSignature(listOf(BqlType.String, BqlType.Integer), BqlType.String)) { args ->
            val account = args[0].asString()
            val depth = args[1].asInteger()
            val parts = account.split(':')
            if (parts.size <= depth) {
                BqlStringValue(account)
            } else {
                BqlStringValue(parts.take(depth).joinToString(":"))
            }
        }

        registerFunction("units", FunctionSignature(listOf(BqlType.Inventory), BqlType.Inventory)) { args ->
            val inv = args[0].asInventory()
            BqlInventoryValue(io.github.tonyzhye.beancount.core.getUnits(inv))
        }

        registerFunction("cost", FunctionSignature(listOf(BqlType.Inventory), BqlType.Inventory)) { args ->
            val inv = args[0].asInventory()
            BqlInventoryValue(io.github.tonyzhye.beancount.core.getCost(inv))
        }

        registerFunction("convert", FunctionSignature(listOf(BqlType.Inventory, BqlType.String), BqlType.Inventory, passContext = true)) { args ->
            val inv = args[0].asInventory()
            val targetCurrency = args[1].asString()
            val priceMap = (args.getOrNull(2) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null) {
                try {
                    val converted = io.github.tonyzhye.beancount.core.convertInventory(inv, targetCurrency, priceMap, null)
                    BqlInventoryValue(converted)
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("convert", FunctionSignature(listOf(BqlType.Inventory, BqlType.String, BqlType.Date), BqlType.Inventory, passContext = true)) { args ->
            val inv = args[0].asInventory()
            val targetCurrency = args[1].asString()
            val date = args[2].asDate()
            val priceMap = (args.getOrNull(3) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null) {
                try {
                    val converted = io.github.tonyzhye.beancount.core.convertInventory(inv, targetCurrency, priceMap, date)
                    BqlInventoryValue(converted)
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("value", FunctionSignature(listOf(BqlType.Position, BqlType.Date), BqlType.Amount, passContext = true)) { args ->
            val pos = args[0].asPosition()
            val date = args[1].asDate()
            val priceMap = (args.getOrNull(2) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null) {
                try {
                    val valued = io.github.tonyzhye.beancount.core.getValue(pos, priceMap, date)
                    BqlAmountValue(valued)
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("value", FunctionSignature(listOf(BqlType.Inventory), BqlType.Inventory, passContext = true)) { args ->
            val inv = args[0].asInventory()
            val priceMap = (args.getOrNull(1) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null) {
                try {
                    val valued = io.github.tonyzhye.beancount.core.getValue(inv, priceMap, null)
                    BqlInventoryValue(valued)
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("value", FunctionSignature(listOf(BqlType.Inventory, BqlType.Date), BqlType.Inventory, passContext = true)) { args ->
            val inv = args[0].asInventory()
            val date = args[1].asDate()
            val priceMap = (args.getOrNull(2) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null) {
                try {
                    val valued = io.github.tonyzhye.beancount.core.getValue(inv, priceMap, date)
                    BqlInventoryValue(valued)
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("getprice", FunctionSignature(listOf(BqlType.String, BqlType.String, BqlType.Date), BqlType.Amount, passContext = true)) { args ->
            val currency = args[0].asString()
            val targetCurrency = args[1].asString()
            val date = args[2].asDate()
            val priceMap = (args.getOrNull(3) as? BqlPriceMapValue)?.priceMap
            if (priceMap != null) {
                try {
                    val (_, rate) = priceMap.getPrice(currency, targetCurrency, date)
                    if (rate != null) {
                        BqlAmountValue(Amount(rate, targetCurrency))
                    } else {
                        BqlNullValue()
                    }
                } catch (e: Exception) {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("possign", FunctionSignature(listOf(BqlType.Decimal, BqlType.String), BqlType.Decimal)) { args ->
            val value = args[0].asDecimal()
            val account = args[1].asString()
            val sign = AccountTypes.getAccountSign(account)
            BqlDecimalValue(value * Decimal(sign.toString()))
        }

        registerFunction("possign", FunctionSignature(listOf(BqlType.Amount, BqlType.String), BqlType.Amount)) { args ->
            val amount = args[0].asAmount()
            val account = args[1].asString()
            val sign = AccountTypes.getAccountSign(account)
            BqlAmountValue(Amount(amount.number * Decimal(sign.toString()), amount.currency))
        }

        registerFunction("possign", FunctionSignature(listOf(BqlType.Position, BqlType.String), BqlType.Position)) { args ->
            val pos = args[0].asPosition()
            val account = args[1].asString()
            val sign = AccountTypes.getAccountSign(account)
            BqlPositionValue(Position(
                Amount(pos.units.number * Decimal(sign.toString()), pos.units.currency),
                pos.cost
            ))
        }

        registerFunction("possign", FunctionSignature(listOf(BqlType.Inventory, BqlType.String), BqlType.Inventory)) { args ->
            val inv = args[0].asInventory()
            val account = args[1].asString()
            val sign = AccountTypes.getAccountSign(account)
            val result = Inventory()
            for (pos in inv) {
                result.addAmount(
                    Amount(pos.units.number * Decimal(sign.toString()), pos.units.currency),
                    pos.cost
                )
            }
            BqlInventoryValue(result)
        }

        registerFunction("filter_currency", FunctionSignature(listOf(BqlType.Position, BqlType.String), BqlType.Position)) { args ->
            val pos = args[0].asPosition()
            val currency = args[1].asString()
            if (pos.units.currency == currency) {
                BqlPositionValue(pos)
            } else {
                BqlNullValue()
            }
        }

        registerFunction("filter_currency", FunctionSignature(listOf(BqlType.Inventory, BqlType.String), BqlType.Inventory)) { args ->
            val inv = args[0].asInventory()
            val currency = args[1].asString()
            val result = Inventory()
            for (pos in inv) {
                if (pos.units.currency == currency) {
                    result.addPosition(pos)
                }
            }
            BqlInventoryValue(result)
        }

        registerFunction("open_date", FunctionSignature(listOf(BqlType.String), BqlType.Date, passContext = true)) { args ->
            val account = args[0].asString()
            val entries = (args.getOrNull(1) as? BqlPriceMapValue)?.entries
            if (entries != null) {
                val openDates = entries
                    .filterIsInstance<io.github.tonyzhye.beancount.core.Open>()
                    .filter { it.account == account }
                    .map { it.date }
                if (openDates.isNotEmpty()) {
                    BqlDateValue(openDates.minOrNull()!!)
                } else {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("close_date", FunctionSignature(listOf(BqlType.String), BqlType.Date, passContext = true)) { args ->
            val account = args[0].asString()
            val entries = (args.getOrNull(1) as? BqlPriceMapValue)?.entries
            if (entries != null) {
                val closeDates = entries
                    .filterIsInstance<io.github.tonyzhye.beancount.core.Close>()
                    .filter { it.account == account }
                    .map { it.date }
                if (closeDates.isNotEmpty()) {
                    BqlDateValue(closeDates.minOrNull()!!)
                } else {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("account_sortkey", FunctionSignature(listOf(BqlType.String), BqlType.String)) { args ->
            val account = args[0].asString()
            BqlStringValue(AccountTypes.getAccountSortKey(account))
        }

        // Inventory functions
        registerFunction("inventory", FunctionSignature(listOf(BqlType.Amount), BqlType.Inventory)) { args ->
            val amount = args[0].asAmount()
            val inv = Inventory()
            inv.addAmount(amount)
            BqlInventoryValue(inv)
        }

        // Meta function (get entry meta)
        registerFunction("meta", FunctionSignature(listOf(BqlType.Any, BqlType.String), BqlType.Any)) { args ->
            val entry = args[0].raw
            val key = args[1].asString()
            val meta = when (entry) {
                is io.github.tonyzhye.beancount.core.Transaction -> entry.meta
                is io.github.tonyzhye.beancount.core.Open -> entry.meta
                is io.github.tonyzhye.beancount.core.Close -> entry.meta
                is io.github.tonyzhye.beancount.core.Balance -> entry.meta
                is io.github.tonyzhye.beancount.core.Note -> entry.meta
                is io.github.tonyzhye.beancount.core.Document -> entry.meta
                is io.github.tonyzhye.beancount.core.Commodity -> entry.meta
                is io.github.tonyzhye.beancount.core.Event -> entry.meta
                is io.github.tonyzhye.beancount.core.Price -> entry.meta
                else -> emptyMap()
            }
            meta[key]?.let { toBqlValue(it) } ?: BqlNullValue()
        }

        registerFunction("open_meta", FunctionSignature(listOf(BqlType.String), BqlType.Any, passContext = true)) { args ->
            val account = args[0].asString()
            val entries = (args.getOrNull(1) as? BqlPriceMapValue)?.entries
            if (entries != null) {
                val openEntry = entries
                    .filterIsInstance<io.github.tonyzhye.beancount.core.Open>()
                    .find { it.account == account }
                if (openEntry != null) {
                    toBqlValue(openEntry.meta)
                } else {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        registerFunction("commodity_meta", FunctionSignature(listOf(BqlType.String), BqlType.Any, passContext = true)) { args ->
            val currency = args[0].asString()
            val entries = (args.getOrNull(1) as? BqlPriceMapValue)?.entries
            if (entries != null) {
                val commodityEntry = entries
                    .filterIsInstance<io.github.tonyzhye.beancount.core.Commodity>()
                    .find { it.currency == currency }
                if (commodityEntry != null) {
                    toBqlValue(commodityEntry.meta)
                } else {
                    BqlNullValue()
                }
            } else {
                BqlNullValue()
            }
        }

        // Entry context function
        registerFunction("has_account", FunctionSignature(listOf(BqlType.String), BqlType.Boolean, passContext = true)) { args ->
            val pattern = args[0].asString()
            val context = (args.getOrNull(1) as? BqlTransactionValue)?.value
            if (context != null) {
                val hasMatch = context.postings.any { Regex(pattern).containsMatchIn(it.account) }
                BqlBooleanValue(hasMatch)
            } else {
                BqlBooleanValue(false)
            }
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

        // Avg aggregator for Decimal
        registerAggregator(
            "avg",
            FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Decimal), BqlType.Decimal)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Decimal
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var total = Decimal.ZERO
                            var count = 0
                            override fun update(value: BqlValue) {
                                if (value.isNull()) return
                                total += value.asDecimal()
                                count++
                            }
                            override fun finalize(): BqlValue =
                                if (count > 0) BqlDecimalValue(total / Decimal(count.toString()))
                                else BqlNullValue()
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )

        // Avg aggregator for Integer
        registerAggregator(
            "avg",
            FunctionSignature(listOf(BqlType.Integer), BqlType.Decimal),
            object : AggregatorFactory {
                override val signature = FunctionSignature(listOf(BqlType.Integer), BqlType.Decimal)
                override fun create(operand: io.github.tonyzhye.beancount.query.compiler.EvalNode): io.github.tonyzhye.beancount.query.compiler.EvalAggregator {
                    return object : EvalAggregator {
                        override val dtype: BqlType = BqlType.Decimal
                        override val operand = operand
                        override fun createAccumulator(): Accumulator = object : Accumulator {
                            var total = 0
                            var count = 0
                            override fun update(value: BqlValue) {
                                if (value.isNull()) return
                                total += value.asInteger()
                                count++
                            }
                            override fun finalize(): BqlValue =
                                if (count > 0) BqlDecimalValue(Decimal(total.toString()) / Decimal(count.toString()))
                                else BqlNullValue()
                        }
                        override fun evaluate(context: RowContext): BqlValue = BqlNullValue()
                    }
                }
            }
        )
    }
}
