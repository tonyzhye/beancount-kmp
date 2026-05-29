package io.github.tonyzhye.beancount.core

/**
 * Account types configuration.
 * Based on beancount.parser.options.
 */
data class AccountTypesConfig(
    val assets: String = "Assets",
    val liabilities: String = "Liabilities",
    val equity: String = "Equity",
    val income: String = "Income",
    val expenses: String = "Expenses"
)

/**
 * Plugin specification.
 */
data class PluginSpec(
    val moduleName: String,
    val config: String? = null
)

/**
 * Plugin processing mode.
 */
enum class PluginProcessingMode {
    RAW,
    DEFAULT
}

/**
 * Precision type for number rendering.
 */
enum class Precision {
    MOST_COMMON,
    MAXIMUM
}

/**
 * Alignment style for number columns.
 */
enum class Align {
    NATURAL,
    DOT,
    RIGHT
}

/**
 * Currency context - collects precision information for a single currency.
 */
class CurrencyContext {
    /** Whether any number has a sign */
    var hasSign: Boolean = true  // Always assume sign for safety
        private set

    /** Maximum number of integer digits seen */
    var integerMax: Int = 1
        private set

    /** Distribution of fractional digit counts */
    private val fractionalDist = mutableMapOf<Int, Int>()

    /** Update context with a number */
    fun update(number: Decimal) {
        val plainStr = number.toPlainString()

        // Update sign
        if (number.isNegative()) {
            hasSign = true
        }

        // Update integer max
        val integerDigits = countIntegerDigits(plainStr)
        integerMax = maxOf(integerMax, integerDigits)

        // Update fractional distribution
        val fractionalDigits = countFractionalDigits(plainStr)
        fractionalDist[fractionalDigits] = fractionalDist.getOrDefault(fractionalDigits, 0) + 1
    }

    /** Update from another context */
    fun updateFrom(other: CurrencyContext) {
        hasSign = hasSign || other.hasSign
        integerMax = maxOf(integerMax, other.integerMax)
        other.fractionalDist.forEach { (digits, count) ->
            fractionalDist[digits] = fractionalDist.getOrDefault(digits, 0) + count
        }
    }

    /** Get most common fractional digits */
    fun getFractionalCommon(): Int? {
        if (fractionalDist.isEmpty()) return null
        return fractionalDist.maxByOrNull { it.value }?.key
    }

    /** Get maximum fractional digits */
    fun getFractionalMax(): Int? {
        if (fractionalDist.isEmpty()) return null
        return fractionalDist.keys.maxOrNull()
    }

    /** Get fractional digits for given precision */
    fun getFractional(precision: Precision): Int? {
        return when (precision) {
            Precision.MOST_COMMON -> getFractionalCommon()
            Precision.MAXIMUM -> getFractionalMax()
        }
    }

    /** Check if any numbers have been recorded */
    fun isEmpty(): Boolean = fractionalDist.isEmpty()

    override fun toString(): String {
        val fractionalCommon = getFractionalCommon() ?: "_"
        val fractionalMax = getFractionalMax() ?: "_"

        val exampleCommon = buildExample(getFractionalCommon())
        val exampleMax = buildExample(getFractionalMax())

        return "sign=${if (hasSign) 1 else 0}  integer_max=$integerMax  " +
               "fractional_common=$fractionalCommon  fractional_max=$fractionalMax  " +
               "\"$exampleCommon\" \"$exampleMax\""
    }

    private fun buildExample(fractional: Int?): String {
        val sb = StringBuilder()
        if (hasSign) sb.append("-")
        sb.append("0".repeat(integerMax))
        if (fractional != null) {
            if (fractional > 0) {
                sb.append(".")
                sb.append("0".repeat(fractional))
            }
        } else {
            sb.append(".*")
        }
        return sb.toString()
    }

    companion object {
        private fun countIntegerDigits(plainStr: String): Int {
            val withoutSign = if (plainStr.startsWith("-") || plainStr.startsWith("+")) {
                plainStr.substring(1)
            } else plainStr

            val withoutCommas = withoutSign.replace(",", "")
            val dotIndex = withoutCommas.indexOf('.')

            return if (dotIndex >= 0) {
                maxOf(1, dotIndex)
            } else {
                maxOf(1, withoutCommas.length)
            }
        }

        private fun countFractionalDigits(plainStr: String): Int {
            val dotIndex = plainStr.indexOf('.')
            return if (dotIndex >= 0) {
                plainStr.length - dotIndex - 1
            } else {
                0
            }
        }
    }
}

/**
 * Display context for formatting numbers.
 * Based on beancount.core.display_context.
 *
 * Collects precision information from numbers and builds formatters
 * for consistent number rendering.
 */
class DisplayContext {
    private val contexts = mutableMapOf<String, CurrencyContext>()
    private val defaultContext = CurrencyContext()

    /** Whether to render commas in numbers */
    var commas: Boolean = false

    /** Update with a number for a specific currency */
    fun update(number: Decimal, currency: String = "__default__") {
        getOrCreateContext(currency).update(number)
    }

    /** Update from another DisplayContext */
    fun updateFrom(other: DisplayContext) {
        other.contexts.forEach { (currency, context) ->
            getOrCreateContext(currency).updateFrom(context)
        }
        defaultContext.updateFrom(other.defaultContext)
    }

    /** Quantize a number to the context's precision */
    fun quantize(number: Decimal, currency: String = "__default__", precision: Precision = Precision.MOST_COMMON): Decimal {
        val context = contexts[currency] ?: defaultContext
        val fractionalDigits = context.getFractional(precision)
            ?: return number

        if (fractionalDigits == 0) {
            // No fractional part - round to integer
            return Decimal(number.toDouble().toLong().toString())
        }

        // Build quantizer pattern (e.g., "0.00" for 2 digits)
        val quantizer = buildString {
            append("0.")
            append("0".repeat(fractionalDigits))
        }

        // Use string manipulation for quantization
        val plainStr = number.toPlainString()
        val dotIndex = plainStr.indexOf('.')

        return if (dotIndex >= 0) {
            val currentFractional = plainStr.length - dotIndex - 1
            if (currentFractional == fractionalDigits) {
                number // Already correct precision
            } else if (currentFractional < fractionalDigits) {
                // Pad with zeros
                Decimal(plainStr + "0".repeat(fractionalDigits - currentFractional))
            } else {
                // Truncate (not rounding - matches Python behavior)
                Decimal(plainStr.substring(0, dotIndex + fractionalDigits + 1))
            }
        } else {
            // Add decimal point and zeros
            Decimal("$plainStr.${"0".repeat(fractionalDigits)}")
        }
    }

    /** Get context for a currency */
    fun getContext(currency: String): CurrencyContext? = contexts[currency]

    /** Get all currency contexts */
    fun getContexts(): Map<String, CurrencyContext> = contexts.toMap()

    /** Build a formatter */
    fun buildFormatter(
        alignment: Align = Align.NATURAL,
        precision: Precision = Precision.MOST_COMMON,
        useCommas: Boolean? = null,
        reserved: Int = 0
    ): DisplayFormatter {
        return DisplayFormatter(this, alignment, precision, useCommas ?: commas, reserved)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("DisplayContext:")

        val allContexts = (contexts + ("__default__" to defaultContext))
            .toSortedMap()

        allContexts.forEach { (currency, context) ->
            sb.appendLine("  ${currency.padEnd(16)}: $context")
        }

        return sb.toString()
    }

    private fun getOrCreateContext(currency: String): CurrencyContext {
        return contexts.getOrPut(currency) { CurrencyContext() }
    }
}

/**
 * Formatter for displaying numbers with consistent precision.
 */
class DisplayFormatter(
    private val dcontext: DisplayContext,
    private val alignment: Align = Align.NATURAL,
    private val precision: Precision = Precision.MOST_COMMON,
    private val commas: Boolean = false,
    private val reserved: Int = 0
) {
    /** Format a number for a specific currency */
    fun format(number: Decimal, currency: String = "__default__"): String {
        val quantized = dcontext.quantize(number, currency, precision)
        val plainStr = quantized.toPlainString()

        return if (commas) {
            addCommas(plainStr)
        } else {
            plainStr
        }
    }

    /** Format an amount */
    fun formatAmount(amount: Amount): String {
        return "${format(amount.number, amount.currency)} ${amount.currency}"
    }

    private fun addCommas(plainStr: String): String {
        val negative = plainStr.startsWith("-")
        val positiveStr = if (negative) plainStr.substring(1) else plainStr

        val dotIndex = positiveStr.indexOf('.')
        val integerPart = if (dotIndex >= 0) positiveStr.substring(0, dotIndex) else positiveStr
        val fractionalPart = if (dotIndex >= 0) positiveStr.substring(dotIndex) else ""

        val withCommas = integerPart.reversed().chunked(3).joinToString(",").reversed()

        return (if (negative) "-" else "") + withCommas + fractionalPart
    }
}

/**
 * Strong-typed options for beancount processing.
 * Based on beancount.parser.options.Options.
 */
data class Options(
    val title: String = "",
    val accountTypes: AccountTypesConfig = AccountTypesConfig(),
    val operatingCurrencies: List<Currency> = emptyList(),
    val documents: List<String> = emptyList(),
    val include: List<String> = emptyList(),
    val plugin: List<PluginSpec> = emptyList(),
    val pluginProcessingMode: PluginProcessingMode = PluginProcessingMode.DEFAULT,
    val dcontext: DisplayContext = DisplayContext(),
    val filename: String = "",
    val line: Int = 0,
    val toleranceMap: Map<Currency, Decimal> = emptyMap(),
    val inferToleranceFromCost: Boolean = false,
    val allowDeprecatedNoneForTagsAndLinks: Boolean = false,
    val insertPythonpath: Boolean = false
)
