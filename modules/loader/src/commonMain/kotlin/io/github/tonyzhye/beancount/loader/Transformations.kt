package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.loader.plugins.BalancePlugin
import io.github.tonyzhye.beancount.loader.plugins.DocumentsPlugin
import io.github.tonyzhye.beancount.loader.plugins.PadPlugin
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
                resolvePlugin(spec.moduleName)?.let { plugin ->
                    pipeline.addPhase(PluginPhase.NORMAL, plugin)
                }
            }

            // TODO: Add auto plugins when --auto flag is supported
            // if (options.autoPluginsEnabled) { ... }

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
 */
private fun resolvePlugin(moduleName: String): BeancountPlugin? {
    return when (moduleName) {
        "beancount.ops.documents" -> DocumentsPluginAdapter()
        "beancount.ops.pad" -> PadPluginAdapter()
        "beancount.ops.balance" -> BalancePluginAdapter()
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
