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
 * Display context for formatting numbers.
 * Based on beancount.core.display_context.
 */
class DisplayContext {
    fun updateFrom(other: DisplayContext) {
        // TODO: Implement when needed
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
