package io.github.tonyzhye.beancount.plugin

import io.github.tonyzhye.beancount.core.Directive

/**
 * Core interface for all Beancount plugins.
 *
 * Plugins implement this interface to transform ledger entries.
 * The plugin system supports three phases:
 * - PRE: Run before user-specified plugins (e.g., documents)
 * - NORMAL: User-specified plugins from beancount file
 * - POST: Run after user plugins (e.g., pad, balance)
 *
 * Example implementation:
 * ```kotlin
 * class MyPlugin : BeancountPlugin {
 *     override val name = "my-plugin"
 *     override val phase = PluginPhase.NORMAL
 *
 *     override fun transform(context: PluginContext): PluginResult {
 *         val transactions = context.filterIsInstance<Transaction>()
 *         // ... process transactions
 *         return PluginResult.noChange(context.entries)
 *     }
 * }
 * ```
 */
interface BeancountPlugin {
    /**
     * Plugin name/identifier.
     * Used for logging, error messages, and plugin resolution.
     */
    val name: String

    /**
     * Plugin description.
     */
    val description: String
        get() = ""

    /**
     * Plugin execution phase.
     * Determines when this plugin runs relative to others.
     */
    val phase: PluginPhase
        get() = PluginPhase.NORMAL

    /**
     * Transform ledger entries.
     *
     * @param context The plugin context containing entries and options
     * @return The transformation result with new entries and any errors
     */
    fun transform(context: PluginContext): PluginResult
}

/**
 * Plugin execution phases.
 */
enum class PluginPhase {
    /**
     * Pre-processing phase.
     * Runs before user-specified plugins.
     * Examples: documents plugin.
     */
    PRE,

    /**
     * Normal phase.
     * User-specified plugins from beancount file run here.
     */
    NORMAL,

    /**
     * Post-processing phase.
     * Runs after user-specified plugins.
     * Examples: pad, balance plugins.
     */
    POST
}

/**
 * Convenience typealias for the old-style transform function.
 * Maintains backward compatibility with existing plugins.
 */
typealias PluginTransform = (List<Directive>, io.github.tonyzhye.beancount.core.Options) ->
    Pair<List<Directive>, List<io.github.tonyzhye.beancount.core.BeancountError>>

/**
 * Adapter to convert old-style transform functions to new BeancountPlugin interface.
 */
class PluginAdapter(
    override val name: String,
    override val phase: PluginPhase = PluginPhase.NORMAL,
    private val transformFn: PluginTransform
) : BeancountPlugin {

    override fun transform(context: PluginContext): PluginResult {
        val (entries, errors) = transformFn(context.entries, context.options)
        return PluginResult(entries = entries, errors = errors)
    }
}
