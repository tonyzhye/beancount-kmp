package io.github.tonyzhye.beancount.core

/**
 * Base interface for all beancount errors.
 * Based on beancount.core.data.BeancountError.
 */
interface BeancountError {
    val source: Meta
    val message: String
    val entry: Directive?
}

/**
 * Error during loading/parsing phase.
 * Based on beancount.loader.LoadError.
 */
data class LoadError(
    override val source: Meta,
    override val message: String,
    override val entry: Directive? = null
) : BeancountError

/**
 * Error from validation checks.
 * Based on beancount.ops.validation.ValidationError.
 */
data class ValidationError(
    override val source: Meta,
    override val message: String,
    override val entry: Directive? = null
) : BeancountError

/**
 * Result of loading a beancount file.
 */
data class LoadResult(
    val entries: List<Directive>,
    val errors: List<BeancountError>,
    val options: Options
)
