package io.github.tonyzhye.beancount.plugin

import io.github.tonyzhye.beancount.core.Directive
import io.github.tonyzhye.beancount.core.Options

/**
 * DSL builder for creating plugin pipelines.
 *
 * Example:
 * ```kotlin
 * val pipeline = pluginPipeline {
 *     pre {
 *         +DocumentsPlugin()
 *     }
 *
 *     normal {
 *         +MyCustomPlugin()
 *     }
 *
 *     post {
 *         +PadPlugin()
 *         +BalancePlugin()
 *     }
 * }
 * ```
 */
fun pluginPipeline(block: PluginPipelineBuilder.() -> Unit): PluginPipeline {
    val builder = PluginPipelineBuilder()
    builder.block()
    return builder.build()
}

/**
 * Builder for plugin pipelines.
 */
class PluginPipelineBuilder {
    private val pipeline = PluginPipeline()

    /**
     * Add plugins to the PRE phase.
     */
    fun pre(vararg plugins: BeancountPlugin) {
        pipeline.addPhase(PluginPhase.PRE, *plugins)
    }

    /**
     * Add plugins to the NORMAL phase.
     */
    fun normal(vararg plugins: BeancountPlugin) {
        pipeline.addPhase(PluginPhase.NORMAL, *plugins)
    }

    /**
     * Add plugins to the POST phase.
     */
    fun post(vararg plugins: BeancountPlugin) {
        pipeline.addPhase(PluginPhase.POST, *plugins)
    }

    /**
     * Unary plus operator for adding plugins.
     */
    operator fun BeancountPlugin.unaryPlus() {
        pipeline.addPhase(this.phase, this)
    }

    /**
     * Build the pipeline.
     */
    fun build(): PluginPipeline = pipeline
}

/**
 * DSL builder for creating registries.
 *
 * Example:
 * ```kotlin
 * val registry = pluginRegistry {
 *     register(MyPlugin())
 *     register(AnotherPlugin())
 * }
 * ```
 */
fun pluginRegistry(block: PluginRegistryBuilder.() -> Unit): PluginRegistry {
    val builder = PluginRegistryBuilder()
    builder.block()
    return builder.build()
}

/**
 * Builder for plugin registries.
 */
class PluginRegistryBuilder {
    private val registry = PluginRegistry()

    /**
     * Register a plugin.
     */
    fun register(plugin: BeancountPlugin) {
        registry.register(plugin)
    }

    /**
     * Build the registry.
     */
    fun build(): PluginRegistry = registry
}

/**
 * Extension function to create a PluginContext from entries and options.
 */
fun List<Directive>.toPluginContext(
    options: Options,
    config: PluginConfig = PluginConfig()
): PluginContext = PluginContext(this, options, config)
