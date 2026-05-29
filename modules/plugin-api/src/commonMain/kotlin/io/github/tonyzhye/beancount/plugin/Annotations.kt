package io.github.tonyzhye.beancount.plugin

/**
 * Marks a class as an auto-discoverable Beancount plugin.
 *
 * When the [PluginRegistry] scans the classpath, it looks for classes
 * annotated with this annotation and registers them automatically.
 *
 * Example:
 * ```kotlin
 * @AutoPlugin(
 *     name = "custom-filter",
 *     description = "Filters out specific transactions",
 *     phase = PluginPhase.NORMAL
 * )
 * class CustomFilterPlugin : BeancountPlugin {
 *     // ...
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoPlugin(
    /**
     * Plugin name/identifier.
     */
    val name: String,

    /**
     * Plugin description.
     */
    val description: String = "",

    /**
     * Plugin execution phase.
     */
    val phase: PluginPhase = PluginPhase.NORMAL
)

/**
 * Marks a plugin as experimental.
 * Experimental plugins may have unstable APIs.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExperimentalPlugin

/**
 * Marks a plugin as deprecated.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeprecatedPlugin(
    val message: String,
    val replaceWith: String = ""
)
