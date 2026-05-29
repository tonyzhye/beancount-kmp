package io.github.tonyzhye.beancount.plugin

import io.github.tonyzhye.beancount.core.BeancountError
import io.github.tonyzhye.beancount.core.Directive
import io.github.tonyzhye.beancount.core.LoadError
import io.github.tonyzhye.beancount.core.Options
import io.github.tonyzhye.beancount.core.newMetadata

/**
 * Pipeline for executing plugins in order.
 *
 * Manages plugin execution phases and error handling.
 *
 * Example:
 * ```kotlin
 * val pipeline = PluginPipeline()
 *     .addPhase(PluginPhase.PRE, documentsPlugin)
 *     .addPhase(PluginPhase.NORMAL, userPlugins)
 *     .addPhase(PluginPhase.POST, padPlugin, balancePlugin)
 *
 * val result = pipeline.execute(entries, options)
 * ```
 */
class PluginPipeline {
    private val phases = mutableMapOf<PluginPhase, MutableList<BeancountPlugin>>()

    /**
     * Add plugins to a phase.
     */
    fun addPhase(phase: PluginPhase, vararg plugins: BeancountPlugin): PluginPipeline {
        phases.getOrPut(phase) { mutableListOf() }.addAll(plugins)
        return this
    }

    /**
     * Add plugins from a registry to their respective phases.
     */
    fun addFromRegistry(registry: PluginRegistry): PluginPipeline {
        registry.allPlugins().forEach { plugin ->
            phases.getOrPut(plugin.phase) { mutableListOf() }.add(plugin)
        }
        return this
    }

    /**
     * Execute the pipeline.
     *
     * @param entries Initial entries
     * @param options Ledger options
     * @return Result with final entries and all errors
     */
    fun execute(entries: List<Directive>, options: Options): PluginPipelineResult {
        val allErrors = mutableListOf<BeancountError>()
        val allWarnings = mutableListOf<BeancountError>()
        var currentEntries = entries

        // Execute phases in order: PRE -> NORMAL -> POST
        for (phase in PluginPhase.entries) {
            val phasePlugins = phases[phase] ?: continue

            for (plugin in phasePlugins) {
                try {
                    val context = PluginContext(currentEntries, options)
                    val result = plugin.transform(context)

                    currentEntries = result.entries.sorted()
                    allErrors.addAll(result.errors)
                    allWarnings.addAll(result.warnings)
                } catch (e: PluginException) {
                    allErrors.add(
                        LoadError(
                            newMetadata(options.filename, 0),
                            "Plugin '${e.pluginName}' failed: ${e.message}"
                        )
                    )
                } catch (e: Exception) {
                    allErrors.add(
                        LoadError(
                            newMetadata(options.filename, 0),
                            "Plugin '${plugin.name}' failed: ${e.message}"
                        )
                    )
                }
            }
        }

        return PluginPipelineResult(
            entries = currentEntries,
            errors = allErrors,
            warnings = allWarnings
        )
    }

    /**
     * Clear all phases.
     */
    fun clear(): PluginPipeline {
        phases.clear()
        return this
    }

    /**
     * Get plugins in a specific phase.
     */
    fun getPhase(phase: PluginPhase): List<BeancountPlugin> =
        phases[phase]?.toList() ?: emptyList()
}

/**
 * Result of executing a plugin pipeline.
 */
data class PluginPipelineResult(
    val entries: List<Directive>,
    val errors: List<BeancountError>,
    val warnings: List<BeancountError> = emptyList()
)
