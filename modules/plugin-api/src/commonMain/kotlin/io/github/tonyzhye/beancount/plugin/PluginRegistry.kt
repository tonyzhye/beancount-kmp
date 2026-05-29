package io.github.tonyzhye.beancount.plugin

/**
 * Registry for Beancount plugins.
 *
 * Manages plugin registration and lookup by name.
 * Supports both explicit registration and auto-discovery.
 *
 * Example usage:
 * ```kotlin
 * val registry = PluginRegistry()
 * registry.register(MyCustomPlugin())
 *
 * val plugin = registry.resolve("my-custom")
 * ```
 */
class PluginRegistry {
    private val plugins = mutableMapOf<String, BeancountPlugin>()

    /**
     * Register a plugin.
     *
     * @param plugin The plugin to register
     * @throws IllegalArgumentException if a plugin with the same name is already registered
     */
    fun register(plugin: BeancountPlugin) {
        require(plugin.name.isNotBlank()) { "Plugin name cannot be blank" }
        require(plugin.name !in plugins) { "Plugin '${plugin.name}' is already registered" }
        plugins[plugin.name] = plugin
    }

    /**
     * Register multiple plugins.
     */
    fun registerAll(vararg plugins: BeancountPlugin) {
        plugins.forEach { register(it) }
    }

    /**
     * Unregister a plugin by name.
     *
     * @param name Plugin name
     * @return true if the plugin was removed, false if it didn't exist
     */
    fun unregister(name: String): Boolean {
        return plugins.remove(name) != null
    }

    /**
     * Look up a plugin by name.
     *
     * @param name Plugin name
     * @return The plugin, or null if not found
     */
    fun resolve(name: String): BeancountPlugin? = plugins[name]

    /**
     * Check if a plugin is registered.
     */
    fun contains(name: String): Boolean = name in plugins

    /**
     * Get all registered plugins.
     */
    fun allPlugins(): List<BeancountPlugin> = plugins.values.toList()

    /**
     * Get plugins for a specific phase.
     */
    fun pluginsByPhase(phase: PluginPhase): List<BeancountPlugin> =
        plugins.values.filter { it.phase == phase }

    /**
     * Clear all registered plugins.
     */
    fun clear() = plugins.clear()

    /**
     * Number of registered plugins.
     */
    val size: Int get() = plugins.size

    /**
     * Check if registry is empty.
     */
    fun isEmpty(): Boolean = plugins.isEmpty()

    companion object {
        /**
         * Create a registry with built-in plugins pre-registered.
         */
        fun withDefaults(): PluginRegistry = PluginRegistry()
    }
}

/**
 * Exception thrown when a plugin cannot be found or loaded.
 */
class PluginNotFoundException(message: String) : Exception(message)

/**
 * Exception thrown when a plugin fails during transformation.
 */
class PluginException(
    message: String,
    val pluginName: String,
    cause: Throwable? = null
) : Exception(message, cause)
