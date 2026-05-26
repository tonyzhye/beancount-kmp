package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.BeancountError
import io.github.tonyzhye.beancount.core.Directive
import io.github.tonyzhye.beancount.core.Options

/**
 * Result of parsing a beancount file or string.
 */
data class ParseResult(
    val entries: List<Directive>,
    val errors: List<BeancountError>,
    val options: Options
)

/**
 * Parser interface for beancount input.
 */
interface Parser {
    /**
     * Parse a file from disk.
     */
    fun parseFile(filename: String): ParseResult
    
    /**
     * Parse a string directly.
     */
    fun parseString(content: String): ParseResult
}
