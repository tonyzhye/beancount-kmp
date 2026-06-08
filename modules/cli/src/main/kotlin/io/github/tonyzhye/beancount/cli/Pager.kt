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

package io.github.tonyzhye.beancount.cli

/**
 * Conditional pager that automatically pipes output through a system pager
 * when the line count exceeds a threshold.
 * Based on beancount.utils.pager.ConditionalPager.
 *
 * This is useful for CLI commands that may produce a lot of output.
 * When output is small, it prints directly to stdout.
 * When output is large, it pipes through `less` or `more`.
 *
 * Example usage:
 * ```kotlin
 * val pager = ConditionalPager()
 * pager.use { out ->
 *     out.println("Line 1")
 *     out.println("Line 2")
 *     // ... many lines
 * }
 * ```
 */
class ConditionalPager(
    /** Minimum number of lines before activating the pager */
    private val threshold: Int = 24,
    /** The pager command to use. Defaults to "less -FRSX" or "more" */
    private val pagerCommand: String = defaultPager()
) : AutoCloseable {

    private val lines = mutableListOf<String>()
    private var closed = false

    /** Add a line of output */
    fun println(line: String) {
        check(!closed) { "Pager is already closed" }
        lines.add(line)
    }

    /** Add multiple lines of output */
    fun printLines(newLines: List<String>) {
        newLines.forEach { println(it) }
    }

    /**
     * Execute a block with this pager and automatically close it.
     * Similar to Kotlin's `use` but with a PrintWriter-like interface.
     */
    inline fun <T> use(block: (ConditionalPager) -> T): T {
        return try {
            block(this)
        } finally {
            close()
        }
    }

    /**
     * Flush output to the appropriate destination.
     * If the number of lines exceeds the threshold, pipe through the pager.
     * Otherwise, print directly to stdout.
     */
    override fun close() {
        if (closed) return
        closed = true

        if (lines.size > threshold && pagerCommand.isNotBlank()) {
            pipeToPager()
        } else {
            lines.forEach { kotlin.io.println(it) }
        }
    }

    private fun pipeToPager() {
        try {
            val process = ProcessBuilder(*pagerCommand.split(" ").toTypedArray())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()

            process.outputStream.bufferedWriter().use { writer ->
                lines.forEach { writer.write(it); writer.newLine() }
            }

            process.waitFor()
        } catch (e: Exception) {
            // Fallback: print directly if pager fails
            lines.forEach { kotlin.io.println(it) }
        }
    }

    companion object {
        /**
         * Determine the default pager command.
         * Prefers "less" with sensible options, falls back to "more".
         */
        fun defaultPager(): String {
            return when {
                isCommandAvailable("less") -> "less -FRSX"
                isCommandAvailable("more") -> "more"
                else -> ""
            }
        }

        /**
         * Check if a command is available in the system PATH.
         */
        private fun isCommandAvailable(command: String): Boolean {
            return try {
                ProcessBuilder(command, "--version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * Create a conditional pager and execute a block.
 * Convenience function for one-off pager usage.
 *
 * @param threshold Minimum lines before paging.
 * @param block Code block that writes to the pager.
 */
inline fun <T> withPager(threshold: Int = 24, block: (ConditionalPager) -> T): T {
    return ConditionalPager(threshold).use(block)
}
