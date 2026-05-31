package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.loader.plugins.AutoAccountsPlugin
import io.github.tonyzhye.beancount.loader.plugins.BalancePlugin
import io.github.tonyzhye.beancount.loader.plugins.CheckClosingPlugin
import io.github.tonyzhye.beancount.loader.plugins.CoherentCostPlugin
import io.github.tonyzhye.beancount.loader.plugins.CurrencyAccountsPlugin
import io.github.tonyzhye.beancount.loader.plugins.DocumentsPlugin
import io.github.tonyzhye.beancount.loader.plugins.ImplicitPricesPlugin
import io.github.tonyzhye.beancount.loader.plugins.LeafOnlyPlugin
import io.github.tonyzhye.beancount.loader.plugins.PadPlugin
import io.github.tonyzhye.beancount.loader.plugins.PedanticPlugin
import io.github.tonyzhye.beancount.loader.plugins.UniquePricesPlugin
import io.github.tonyzhye.beancount.plugin.*

/**
 * Run plugin transformations on parsed entries.
 * Based on beancount.loader.run_transformations.
 *
 * This is the plugin execution engine that processes entries through
 * a chain of plugins before final validation.
 *
 * Plugin processing order (DEFAULT mode):
 * 1. PLUGINS_PRE (documents)
 * 2. User-specified plugins (from Options.plugin)
 * 3. PLUGINS_AUTO (if --auto flag enabled)
 * 4. PLUGINS_POST (pad, balance)
 *
 * Each plugin receives (entries, options) and returns (entries, errors).
 * Entries are re-sorted after each plugin to maintain chronological order.
 */
fun runTransformations(
    entries: List<Directive>,
    options: Options
): Pair<List<Directive>, List<BeancountError>> {
    // Build the plugin pipeline
    val pipeline = buildPipeline(options)

    // Execute the pipeline
    val result = pipeline.execute(entries, options)

    return Pair(result.entries, result.errors + result.warnings)
}

/**
 * Build a plugin pipeline based on options.
 */
private fun buildPipeline(options: Options): PluginPipeline {
    val pipeline = PluginPipeline()

    when (options.pluginProcessingMode) {
        PluginProcessingMode.RAW -> {
            // Only user-specified plugins
            options.plugin.forEach { spec ->
                resolvePlugin(spec.moduleName)?.let { plugin ->
                    pipeline.addPhase(PluginPhase.NORMAL, plugin)
                }
            }
        }
        PluginProcessingMode.DEFAULT -> {
            // Full chain: PRE -> user -> AUTO -> POST
            // PRE phase
            pipeline.addPhase(PluginPhase.PRE, DocumentsPluginAdapter())

            // User-specified plugins
            options.plugin.forEach { spec ->
                val plugin = resolvePlugin(spec.moduleName, spec.config)
                if (plugin != null) {
                    pipeline.addPhase(PluginPhase.NORMAL, plugin)
                }
            }

            // Auto plugins (when --auto flag is enabled)
            if (options.autoPluginsEnabled) {
                pipeline.addPhase(PluginPhase.NORMAL, AutoAccountsPluginAdapter())
                pipeline.addPhase(PluginPhase.NORMAL, ImplicitPricesPluginAdapter())
            }

            // POST phase
            pipeline.addPhase(PluginPhase.POST, PadPluginAdapter())
            pipeline.addPhase(PluginPhase.POST, BalancePluginAdapter())
        }
    }

    return pipeline
}

/**
 * Resolve a plugin name to a BeancountPlugin instance.
 * For now, only built-in plugins are supported.
 *
 * @param moduleName Plugin module name
 * @param config Optional plugin configuration string
 * @return BeancountPlugin instance or null if not found
 */
private fun resolvePlugin(moduleName: String, config: String? = null): BeancountPlugin? {
    return when (moduleName) {
        "beancount.ops.documents" -> DocumentsPluginAdapter()
        "beancount.ops.pad" -> PadPluginAdapter()
        "beancount.ops.balance" -> BalancePluginAdapter()
        "beancount.plugins.auto_accounts" -> AutoAccountsPluginAdapter()
        "beancount.plugins.implicit_prices" -> ImplicitPricesPluginAdapter()
        "beancount.plugins.currency_accounts" -> CurrencyAccountsPluginAdapter(config)
        "beancount.plugins.leafonly" -> LeafOnlyPluginAdapter()
        "beancount.plugins.unique_prices" -> UniquePricesPluginAdapter()
        "beancount.plugins.coherent_cost" -> CoherentCostPluginAdapter()
        "beancount.plugins.check_closing" -> CheckClosingPluginAdapter()
        "beancount.plugins.pedantic" -> PedanticPluginAdapter()
        "beancount.plugins.auto" -> AutoPluginAdapter()
        else -> null // Unknown plugin - skip with warning
    }
}

// Adapters for existing plugins

private class DocumentsPluginAdapter : BeancountPlugin {
    override val name = "documents"
    override val description = "Process document directives"
    override val phase = PluginPhase.PRE

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = DocumentsPlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}

private class PadPluginAdapter : BeancountPlugin {
    override val name = "pad"
    override val description = "Generate padding transactions for balance assertions"
    override val phase = PluginPhase.POST

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = PadPlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}

private class BalancePluginAdapter : BeancountPlugin {
    override val name = "balance"
    override val description = "Validate balance assertions"
    override val phase = PluginPhase.POST

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = BalancePlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}

private class AutoAccountsPluginAdapter : BeancountPlugin {
    override val name = "auto_accounts"
    override val description = "Automatically insert Open directives for accounts not opened"
    override val phase = PluginPhase.NORMAL

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = AutoAccountsPlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}

private class ImplicitPricesPluginAdapter : BeancountPlugin {
    override val name = "implicit_prices"
    override val description = "Synthesize Price directives from transactions"
    override val phase = PluginPhase.NORMAL

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = ImplicitPricesPlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}

private class CurrencyAccountsPluginAdapter(private val config: String? = null) : BeancountPlugin {
    override val name = "currency_accounts"
    override val description = "Insert currency trading postings"
    override val phase = PluginPhase.NORMAL

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = CurrencyAccountsPlugin.transform(
            context.entries,
            context.options,
            config ?: ""
        )
        return PluginResult(entries = entries, errors = errors)
    }
}

/**
 * Auto plugin adapter - combines auto_accounts and implicit_prices.
 * Based on beancount.plugins.auto.
 */
private class AutoPluginAdapter : BeancountPlugin {
    override val name = "auto"
    override val description = "Enable all automatic plugins (auto_accounts, implicit_prices)"
    override val phase = PluginPhase.NORMAL

    override fun transform(context: PluginContext): PluginResult {
        // Run auto_accounts first
        val (entries1, errors1) = AutoAccountsPlugin.transform(context.entries, context.options)
        // Then implicit_prices
        val (entries2, errors2) = ImplicitPricesPlugin.transform(entries1, context.options)
        return PluginResult(
            entries = entries2.sorted(),
            errors = errors1 + errors2
        )
    }
}

private class LeafOnlyPluginAdapter : BeancountPlugin {
    override val name = "leafonly"
    override val description = "Validate that only leaf accounts have postings"
    override val phase = PluginPhase.NORMAL

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = LeafOnlyPlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}

private class UniquePricesPluginAdapter : BeancountPlugin {
    override val name = "unique_prices"
    override val description = "Validate unique prices per day for each currency pair"
    override val phase = PluginPhase.NORMAL

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = UniquePricesPlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}

private class CoherentCostPluginAdapter : BeancountPlugin {
    override val name = "coherent_cost"
    override val description = "Validate currencies are used consistently with or without cost"
    override val phase = PluginPhase.NORMAL

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = CoherentCostPlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}

private class CheckClosingPluginAdapter : BeancountPlugin {
    override val name = "check_closing"
    override val description = "Expand closing metadata to zero balance checks"
    override val phase = PluginPhase.NORMAL

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = CheckClosingPlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}

private class PedanticPluginAdapter : BeancountPlugin {
    override val name = "pedantic"
    override val description = "Enable all pedantic validation plugins"
    override val phase = PluginPhase.NORMAL

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = PedanticPlugin.transform(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}
