package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.Directive
import io.github.tonyzhye.beancount.core.LoadResult
import io.github.tonyzhye.beancount.core.Options

/**
 * Test utility for loading beancount content from string.
 *
 * Equivalent to Python beancount's `load_doc` decorator.
 * Extracts beancount content from a Kotlin multiline string and loads it.
 *
 * Example usage:
 * ```kotlin
 * val (entries, errors, options) = loadDoc("""
 *   2024-01-01 open Assets:Bank USD
 *   2024-01-15 * "Paycheck"
 *     Assets:Bank  100.00 USD
 *     Income:Salary
 * """)
 * ```
 */
fun loadDoc(content: String): LoadResult {
    return loadString(content.trimIndent())
}

/**
 * Load beancount content with auto-plugins enabled.
 * Convenience method for tests that want auto_accounts and implicit_prices.
 */
fun loadDocWithAuto(content: String): LoadResult {
    return loadString(content.trimIndent(), autoPluginsEnabled = true)
}

/**
 * Extract entries from a beancount string.
 * Convenience method that returns only the entries.
 */
fun loadEntries(content: String): List<Directive> {
    return loadString(content.trimIndent()).entries
}

/**
 * Extract entries with auto-plugins enabled.
 */
fun loadEntriesWithAuto(content: String): List<Directive> {
    return loadString(content.trimIndent(), autoPluginsEnabled = true).entries
}

/**
 * Assert that loading a beancount string produces no errors.
 * Throws AssertionError if there are errors.
 */
fun assertNoErrors(result: LoadResult) {
    if (result.errors.isNotEmpty()) {
        throw AssertionError(
            "Expected no errors but got ${result.errors.size}:\n" +
            result.errors.joinToString("\n") { "  - ${it.message}" }
        )
    }
}

/**
 * Load beancount content and assert no errors.
 * Returns the load result.
 */
fun loadDocAssertNoErrors(content: String): LoadResult {
    val result = loadString(content.trimIndent())
    assertNoErrors(result)
    return result
}
