/*
 * Beancount JVM - A JVM implementation of Beancount
 * Copyright (C) 2026  Beancount JVM Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Beancount by Martin Blais
 * Original project: https://github.com/beancount/beancount
 */

package io.github.tonyzhye.beancount.parser

/**
 * Parser context for tracking parsing state and providing error reporting.
 * Based on beancount.parser.context.
 *
 * Maintains the current file, line, column, and other context information
 * during parsing to enable accurate error messages and source mapping.
 */
data class ParserContext(
    val filename: String,
    val line: Int = 1,
    val column: Int = 1,
    val sourceLines: List<String> = emptyList()
) {
    /**
     * Get the source line at the current line number.
     * Returns null if the line number is out of bounds.
     */
    fun getCurrentLine(): String? {
        val index = line - 1
        return if (index in sourceLines.indices) sourceLines[index] else null
    }

    /**
     * Get a range of source lines around the current line.
     * Useful for displaying context in error messages.
     *
     * @param contextLines Number of lines before and after to include.
     * @return List of (lineNumber, lineContent) pairs.
     */
    fun getContextLines(contextLines: Int = 2): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        val start = maxOf(1, line - contextLines)
        val end = minOf(sourceLines.size, line + contextLines)
        for (i in start..end) {
            val index = i - 1
            if (index in sourceLines.indices) {
                result.add(i to sourceLines[index])
            }
        }
        return result
    }

    /**
     * Format the current location as a string.
     */
    fun locationString(): String {
        return "$filename:$line:$column"
    }

    /**
     * Create a new context with updated line and column.
     */
    fun withPosition(line: Int, column: Int): ParserContext {
        return copy(line = line, column = column)
    }

    /**
     * Create a new context with updated filename.
     */
    fun withFilename(filename: String): ParserContext {
        return copy(filename = filename)
    }

    /**
     * Create a new context with updated source lines.
     */
    fun withSourceLines(lines: List<String>): ParserContext {
        return copy(sourceLines = lines)
    }

    /**
     * Create an error message with source context.
     *
     * @param message The error message.
     * @param contextLines Number of lines of context to include.
     * @return A formatted error message with source context.
     */
    fun formatError(message: String, contextLines: Int = 2): String {
        val sb = StringBuilder()
        sb.appendLine("${locationString()}: $message")

        val lines = getContextLines(contextLines)
        if (lines.isNotEmpty()) {
            sb.appendLine()
            val maxLineNum = lines.maxOf { it.first }
            val numWidth = maxLineNum.toString().length
            for ((num, content) in lines) {
                val marker = if (num == line) ">" else " "
                sb.appendLine("$marker ${num.toString().padStart(numWidth)} | $content")
            }
            // Add column marker
            val currentLine = getCurrentLine()
            if (currentLine != null) {
                val spaces = " ".repeat(numWidth + 3 + column - 1)
                sb.appendLine("${spaces}^")
            }
        }

        return sb.toString().trimEnd()
    }

    companion object {
        /**
         * Create a parser context from file content.
         */
        fun fromContent(filename: String, content: String): ParserContext {
            val lines = content.lines()
            return ParserContext(filename, 1, 1, lines)
        }

        /**
         * Create a parser context from a file.
         */
        fun fromFile(filename: String, content: String? = null): ParserContext {
            val lines = content?.lines() ?: emptyList()
            return ParserContext(filename, 1, 1, lines)
        }
    }
}

/**
 * Exception thrown during parsing with context information.
 */
class ParseException(
    val context: ParserContext,
    message: String,
    cause: Throwable? = null
) : Exception("${context.locationString()}: $message", cause) {
    /**
     * Get a detailed error message with source context.
     */
    fun detailedMessage(contextLines: Int = 2): String {
        return context.formatError(message ?: "Parse error", contextLines)
    }
}
