package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*

/**
 * Test utilities for beancount text-based tests.
 * Mimics Python's @loader.load_doc() and @parser.parse_doc() decorators.
 */
object BeancountTestUtils {

    /**
     * Parse a beancount document string into entries, errors, and options.
     */
    fun parseDoc(content: String): Triple<List<Directive>, List<BeancountError>, Options> {
        val parser = BeancountParser()
        val result = parser.parseString(content.trimIndent())
        return Triple(result.entries, result.errors, result.options)
    }

    /**
     * Filter transactions from parsed entries.
     */
    fun List<Directive>.transactions(): List<Transaction> =
        filterIsInstance<Transaction>()

    /**
     * Get the first transaction from parsed entries.
     */
    fun List<Directive>.firstTransaction(): Transaction =
        transactions().first()

    /**
     * Get a transaction by index.
     */
    fun List<Directive>.transaction(index: Int): Transaction =
        transactions()[index]
}
