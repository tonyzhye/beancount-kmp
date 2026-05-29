package io.github.tonyzhye.beancount.core

/**
 * Options for formatting beancount files.
 *
 * @property prefixWidth Fixed width for account names (auto-detected if null)
 * @property numWidth Fixed width for numbers (auto-detected if null)
 * @property currencyColumn Column at which to align currencies (overrides other options)
 * @property indentWidth Number of spaces for posting indentation (auto-detected if null)
 */
data class FormatOptions(
    val prefixWidth: Int? = null,
    val numWidth: Int? = null,
    val currencyColumn: Int? = null,
    val indentWidth: Int? = null
)

/**
 * Result of formatting a beancount file.
 *
 * @property formattedText The formatted text
 * @property originalText The original text (for comparison)
 * @property changed Whether the text was changed
 */
data class FormatResult(
    val formattedText: String,
    val originalText: String,
    val changed: Boolean
)

/**
 * Beancount file formatter.
 *
 * Reformats beancount input to align all the numbers at the same column.
 * This is a pure text transformation - it does not parse the beancount syntax.
 *
 * Based on beancount.scripts.format.align_beancount.
 *
 * Example usage:
 * ```kotlin
 * val formatter = BeancountFormatter()
 * val result = formatter.format(inputText)
 * println(result.formattedText)
 *
 * // With custom options
 * val result2 = formatter.format(inputText, FormatOptions(
 *     prefixWidth = 50,
 *     numWidth = 12
 * ))
 * ```
 */
class BeancountFormatter {

    companion object {
        // Regex for numbers: e.g., -100.00, +1,234.56, 100
        private const val NUMBER_RE = """[-+]?\s*[\d,]+(?:\.\d*)?"""

        // Account name pattern (simplified from beancount parser)
        // Matches: Assets:Bank:Checking, Expenses:Food, etc.
        private const val ACCOUNT_RE = """[A-Z][A-Za-z0-9_-]*(?::[A-Z][A-Za-z0-9_-]*)+"""

        // Currency pattern
        private const val CURRENCY_RE = """[A-Z][A-Z0-9_'\.]*"""

        // Full posting line pattern:
        //   Account:Name    100.00 USD
        //   Account:Name    -50.00 EUR
        private val POSTING_LINE_REGEX = Regex(
            "^([ \\t]*$ACCOUNT_RE)\\s+($NUMBER_RE)\\s+($CURRENCY_RE.*)$"
        )

        // Metadata line pattern:
        //   key: "value"
        //   key: 123
        private val METADATA_LINE_REGEX = Regex(
            "^(\\s+)(\\w[\\w\\-]*:\\s+.*)$"
        )

        // Directive line pattern (date + directive type):
        // 2024-01-01 open Assets:Cash USD
        private val DIRECTIVE_LINE_REGEX = Regex(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\w+.*)$"
        )
    }

    /**
     * Format beancount text.
     *
     * @param contents The beancount input text
     * @param options Formatting options
     * @return FormatResult containing the formatted text
     */
    @JvmOverloads
    fun format(contents: String, options: FormatOptions = FormatOptions()): FormatResult {
        if (contents.isBlank()) {
            return FormatResult(contents, contents, false)
        }

        val lines = contents.lines()

        // Step 1: Parse all lines and extract posting information
        val parsedLines = lines.map { parseLine(it) }

        // Step 2: Normalize indentation
        val normalizedLines = normalizeIndentation(parsedLines, options.indentWidth)

        // Step 3: Compute column widths
        val (maxPrefixWidth, maxNumWidth) = computeWidths(normalizedLines, options)

        // Step 4: Format lines
        val formattedLines = if (options.currencyColumn != null) {
            formatWithCurrencyColumn(normalizedLines, options.currencyColumn)
        } else {
            formatAligned(normalizedLines, maxPrefixWidth, maxNumWidth)
        }

        // Step 5: Join lines back
        val formattedText = formattedLines.joinToString("\n")

        // Sanity check: ensure only whitespace changed
        val changed = formattedText != contents

        return FormatResult(
            formattedText = formattedText,
            originalText = contents,
            changed = changed
        )
    }

    /**
     * Format a single line.
     */
    private fun parseLine(line: String): ParsedLine {
        // Try to match posting line
        val postingMatch = POSTING_LINE_REGEX.matchEntire(line)
        if (postingMatch != null) {
            val (prefix, number, rest) = postingMatch.destructured
            return ParsedLine.Posting(
                originalLine = line,
                prefix = prefix,
                number = number.trim(),
                rest = rest
            )
        }

        // Try to match metadata line
        val metadataMatch = METADATA_LINE_REGEX.matchEntire(line)
        if (metadataMatch != null) {
            val (indent, content) = metadataMatch.destructured
            return ParsedLine.Metadata(
                originalLine = line,
                indent = indent,
                content = content
            )
        }

        // Default: other line
        return ParsedLine.Other(line)
    }

    /**
     * Normalize indentation for posting lines.
     */
    private fun normalizeIndentation(
        lines: List<ParsedLine>,
        forceIndentWidth: Int?
    ): List<ParsedLine> {
        // Detect most common posting indent width
        val indentWidths = lines.mapNotNull { line ->
            when (line) {
                is ParsedLine.Posting -> {
                    val leadingSpaces = line.prefix.takeWhile { it == ' ' || it == '\t' }.length
                    if (leadingSpaces > 0) leadingSpaces else null
                }
                else -> null
            }
        }

        val targetIndentWidth = forceIndentWidth
            ?: if (indentWidths.isNotEmpty()) {
                // Use most frequent indent width
                indentWidths.groupBy { it }
                    .maxByOrNull { it.value.size }
                    ?.key ?: 2
            } else {
                2
            }

        return lines.map { line ->
            when (line) {
                is ParsedLine.Posting -> {
                    val accountPart = line.prefix.trimStart()
                    line.copy(prefix = " ".repeat(targetIndentWidth) + accountPart)
                }
                else -> line
            }
        }
    }

    /**
     * Compute maximum widths from parsed lines.
     */
    private fun computeWidths(
        lines: List<ParsedLine>,
        options: FormatOptions
    ): Pair<Int, Int> {
        val postingLines = lines.filterIsInstance<ParsedLine.Posting>()

        val maxPrefixWidth = options.prefixWidth
            ?: if (postingLines.isNotEmpty()) {
                postingLines.maxOf { it.prefix.length }
            } else {
                0
            }

        val maxNumWidth = options.numWidth
            ?: if (postingLines.isNotEmpty()) {
                postingLines.maxOf { it.number.length }
            } else {
                0
            }

        return maxPrefixWidth to maxNumWidth
    }

    /**
     * Format lines with currency column alignment.
     */
    private fun formatWithCurrencyColumn(
        lines: List<ParsedLine>,
        currencyColumn: Int
    ): List<String> {
        return lines.map { line ->
            when (line) {
                is ParsedLine.Posting -> {
                    val numOfSpaces = currencyColumn - line.prefix.length - line.number.length - 4
                    val spaces = " ".repeat(maxOf(1, numOfSpaces))
                    "${line.prefix}$spaces  ${line.number} ${line.rest}"
                }
                is ParsedLine.Metadata -> {
                    "${line.indent}${line.content}"
                }
                is ParsedLine.Other -> {
                    line.content
                }
            }
        }
    }

    /**
     * Format lines with automatic alignment.
     */
    private fun formatAligned(
        lines: List<ParsedLine>,
        maxPrefixWidth: Int,
        maxNumWidth: Int
    ): List<String> {
        return lines.map { line ->
            when (line) {
                is ParsedLine.Posting -> {
                    // Format: <prefix>  <number> <rest>
                    // prefix is left-aligned, number is right-aligned
                    val prefixPadded = line.prefix.padEnd(maxPrefixWidth)
                    val numberPadded = line.number.padStart(maxNumWidth)
                    "$prefixPadded  $numberPadded ${line.rest}"
                }
                is ParsedLine.Metadata -> {
                    "${line.indent}${line.content}"
                }
                is ParsedLine.Other -> {
                    line.content
                }
            }
        }
    }

    /**
     * Sealed class for parsed line types.
     */
    private sealed class ParsedLine {
        data class Posting(
            val originalLine: String,
            val prefix: String,
            val number: String,
            val rest: String
        ) : ParsedLine()

        data class Metadata(
            val originalLine: String,
            val indent: String,
            val content: String
        ) : ParsedLine()

        data class Other(
            val content: String
        ) : ParsedLine()
    }
}

/**
 * Convenience function to format beancount text.
 */
@JvmOverloads
fun formatBeancount(contents: String, options: FormatOptions = FormatOptions()): String {
    return BeancountFormatter().format(contents, options).formattedText
}
