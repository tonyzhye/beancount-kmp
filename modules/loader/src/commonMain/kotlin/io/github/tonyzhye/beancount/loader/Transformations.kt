package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.loader.plugins.BalancePlugin
import io.github.tonyzhye.beancount.loader.plugins.DocumentsPlugin
import io.github.tonyzhye.beancount.loader.plugins.PadPlugin

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
    val allErrors = mutableListOf<BeancountError>()
    var currentEntries = entries

    // Build plugin chain based on processing mode
    val pluginChain = buildPluginChain(options)

    // Execute each plugin in sequence
    for ((name, transform) in pluginChain) {
        try {
            val (newEntries, pluginErrors) = transform(currentEntries, options)
            currentEntries = newEntries.sorted()
            allErrors.addAll(pluginErrors)
        } catch (e: Exception) {
            allErrors.add(
                LoadError(
                    newMetadata(options.filename, 0),
                    "Plugin '$name' failed: ${e.message}"
                )
            )
        }
    }

    return Pair(currentEntries, allErrors)
}

/**
 * Built-in pre-processing plugins.
 */
private val PLUGINS_PRE: List<Pair<String, PluginTransform>> = listOf(
    "documents" to DocumentsPlugin::transform
)

/**
 * Built-in auto-plugins (enabled with --auto flag).
 */
private val PLUGINS_AUTO: List<Pair<String, PluginTransform>> = listOf(
    // TODO: Add auto-plugins when needed
)

/**
 * Built-in post-processing plugins.
 */
private val PLUGINS_POST: List<Pair<String, PluginTransform>> = listOf(
    "pad" to PadPlugin::transform,
    "balance" to BalancePlugin::transform
)

/**
 * Build the plugin chain based on options.
 */
private fun buildPluginChain(options: Options): List<Pair<String, PluginTransform>> {
    return when (options.pluginProcessingMode) {
        PluginProcessingMode.RAW -> {
            // Only user-specified plugins
            options.plugin.mapNotNull { spec ->
                resolvePlugin(spec.moduleName)
            }
        }
        PluginProcessingMode.DEFAULT -> {
            // Full chain: PRE -> user -> AUTO -> POST
            val chain = mutableListOf<Pair<String, PluginTransform>>()
            chain.addAll(PLUGINS_PRE)
            chain.addAll(options.plugin.mapNotNull { spec ->
                resolvePlugin(spec.moduleName)
            })
            // TODO: Add auto plugins when --auto flag is supported
            // if (options.autoPluginsEnabled) chain.addAll(PLUGINS_AUTO)
            chain.addAll(PLUGINS_POST)
            chain
        }
    }
}

/**
 * Resolve a plugin name to a transform function.
 * For now, only built-in plugins are supported.
 */
private fun resolvePlugin(moduleName: String): Pair<String, PluginTransform>? {
    return when (moduleName) {
        "beancount.ops.documents" -> "documents" to DocumentsPlugin::transform
        "beancount.ops.pad" -> "pad" to PadPlugin::transform
        "beancount.ops.balance" -> "balance" to BalancePlugin::transform
        else -> null // Unknown plugin - skip with warning
    }
}
