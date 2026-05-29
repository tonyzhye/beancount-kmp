package io.github.tonyzhye.beancount.plugin

import io.github.tonyzhye.beancount.core.BeancountError
import io.github.tonyzhye.beancount.core.Directive
import io.github.tonyzhye.beancount.core.Options

/**
 * Context passed to plugins during transformation.
 *
 * Contains all the information a plugin needs to process entries.
 */
class PluginContext(
    val entries: List<Directive>,
    val options: Options,
    val pluginConfig: PluginConfig = PluginConfig()
) {
    /**
     * Get directives of a specific type.
     */
    inline fun <reified T : Directive> filterIsInstance(): List<T> = entries.filterIsInstance<T>()

    /**
     * Create a new context with updated entries.
     */
    fun withEntries(newEntries: List<Directive>): PluginContext =
        PluginContext(newEntries, options, pluginConfig)
}

/**
 * Result returned by a plugin transformation.
 *
 * @property entries The transformed entries (may include new or removed entries)
 * @property errors Any errors produced during transformation
 * @property warnings Any warnings produced during transformation (non-fatal)
 */
data class PluginResult(
    val entries: List<Directive>,
    val errors: List<BeancountError> = emptyList(),
    val warnings: List<BeancountError> = emptyList()
) {
    /**
     * Combine this result with another result.
     */
    operator fun plus(other: PluginResult): PluginResult =
        PluginResult(
            entries = other.entries,
            errors = errors + other.errors,
            warnings = warnings + other.warnings
        )

    companion object {
        /**
         * Create a result with no changes.
         */
        fun noChange(entries: List<Directive>): PluginResult =
            PluginResult(entries = entries)
    }
}

/**
 * Configuration for a single plugin instance.
 *
 * @property name Plugin name/identifier
 * @property args Plugin-specific arguments (from beancount file)
 * @property priority Plugin priority (lower numbers run first)
 */
data class PluginConfig(
    val name: String = "",
    val args: Map<String, Any> = emptyMap(),
    val priority: Int = 0
)
