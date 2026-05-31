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

package io.github.tonyzhye.beancount.core

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.Writer
import kotlin.reflect.KClass

/**
 * Render source location from metadata for error messages.
 * Based on beancount.parser.printer.render_source.
 *
 * Format: filename:lineno:
 */
fun renderSource(meta: Meta): String {
    val filename = meta["filename"] as? String ?: ""
    val lineno = meta["lineno"] as? Int ?: 0
    return "$filename:${lineno}:"
}

/**
 * Render a flag into a string.
 * Based on beancount.parser.printer.render_flag.
 */
fun renderFlag(flag: String?): String {
    return flag ?: ""
}

/**
 * Format an error into a string.
 * Based on beancount.parser.printer.format_error.
 */
fun formatError(error: BeancountError): String {
    val builder = StringBuilder()
    builder.append("${renderSource(error.source)} ${error.message}\n")

    error.entry?.let { entry ->
        builder.append("\n")
        formatEntry(entry).lines().forEach { line ->
            builder.append("   $line\n")
        }
    }

    return builder.toString()
}

/**
 * Format a list of errors into a single string.
 */
fun formatErrors(errors: List<BeancountError>): String {
    return errors.joinToString("\n") { formatError(it) }
}

/**
 * Print errors to a writer.
 * Based on beancount.parser.printer.print_errors.
 */
fun printErrors(errors: List<BeancountError>, writer: Writer = OutputStreamWriter(System.out)) {
    errors.forEach { error ->
        writer.write(formatError(error))
        writer.write("\n")
    }
    writer.flush()
}

/**
 * Format a single directive entry into beancount syntax.
 * Based on beancount.parser.printer.format_entry.
 *
 * @param entry The directive to format.
 * @param dcontext Optional DisplayContext for number formatting.
 * @param renderWeights If true, render posting weights as comments (for debugging).
 * @param writeSource If true, prepend source file/line information.
 * @return Formatted beancount syntax string.
 */
fun formatEntry(
    entry: Directive,
    dcontext: DisplayContext? = null,
    renderWeights: Boolean = false,
    writeSource: Boolean = false
): String {
    val printer = EntryPrinter(dcontext, renderWeights, writeSource = writeSource)
    return printer.format(entry)
}

/**
 * Format a list of entries into a single string with automatic blank line insertion.
 * Based on beancount.parser.printer.format_entries.
 *
 * Inserts blank lines between transactions and between blocks of same-type directives.
 *
 * @param entries List of directives to format.
 * @param dcontext Optional DisplayContext for number precision.
 * @param renderWeights If true, render posting weights as comments.
 * @param writeSource If true, prepend source file/line information.
 * @return Formatted beancount syntax string.
 */
fun formatEntries(
    entries: List<Directive>,
    dcontext: DisplayContext? = null,
    renderWeights: Boolean = false,
    writeSource: Boolean = false
): String {
    if (entries.isEmpty()) return ""

    val printer = EntryPrinter(dcontext, renderWeights, writeSource = writeSource)
    val writer = StringWriter()

    var previousType: KClass<out Directive>? = null
    for (entry in entries) {
        val entryType = entry::class
        if (entry is Transaction || entry is Commodity || entryType != previousType || writeSource) {
            writer.write("\n")
            previousType = entryType
        }

        writer.write(printer.format(entry))
    }

    return writer.toString()
}

/**
 * Print entries to a writer (file, stdout, etc.).
 * Based on beancount.parser.printer.print_entries.
 *
 * @param entries List of directives to print.
 * @param dcontext Optional DisplayContext for number precision.
 * @param renderWeights If true, render posting weights as comments.
 * @param writer Optional writer to write to (defaults to stdout).
 * @param prefix Optional prefix for custom indentation (for Fava integration).
 * @param writeSource If true, prepend source file/line information.
 */
fun printEntries(
    entries: List<Directive>,
    dcontext: DisplayContext? = null,
    renderWeights: Boolean = false,
    writer: Writer = OutputStreamWriter(System.out),
    prefix: String = "",
    writeSource: Boolean = false
) {
    if (entries.isEmpty()) return

    val printer = EntryPrinter(dcontext, renderWeights, prefix = prefix, writeSource = writeSource)

    if (prefix.isNotEmpty()) {
        writer.write(prefix)
    }

    var previousType: KClass<out Directive>? = null
    for (entry in entries) {
        val entryType = entry::class
        if (entry is Transaction || entry is Commodity || entryType != previousType || writeSource) {
            writer.write("\n")
            previousType = entryType
        }

        writer.write(printer.format(entry))
    }

    writer.flush()
}

/**
 * Print entries to an OutputStream.
 * Convenience overload for printEntries.
 */
fun printEntries(
    entries: List<Directive>,
    dcontext: DisplayContext? = null,
    renderWeights: Boolean = false,
    output: OutputStream = System.out,
    prefix: String = "",
    writeSource: Boolean = false
) {
    printEntries(entries, dcontext, renderWeights, OutputStreamWriter(output, "UTF-8"), prefix, writeSource)
}

/**
 * Format a single posting into a string.
 * Based on beancount.parser.printer.EntryPrinter.render_posting_strings.
 */
fun formatPosting(
    posting: Posting,
    dcontext: DisplayContext? = null
): String {
    val printer = EntryPrinter(dcontext)
    return printer.renderPosting(posting).let { (account, position, _) ->
        if (position.isNotEmpty()) {
            "$account  $position"
        } else {
            account
        }
    }
}

// ===== Internal EntryPrinter class =====

/**
 * Multi-method interface for printing all directive types.
 * Based on beancount.parser.printer.EntryPrinter.
 */
private class EntryPrinter(
    dcontext: DisplayContext? = null,
    private val renderWeights: Boolean = false,
    private val minWidthAccount: Int? = null,
    private val prefix: String = "  ",
    private val writeSource: Boolean = false
) {
    private val dformat: DisplayFormatter? = dcontext?.buildFormatter(
        precision = Precision.MOST_COMMON
    )
    private val dformatMax: DisplayFormatter? = dcontext?.buildFormatter(
        precision = Precision.MAXIMUM
    )

    companion object {
        private val META_IGNORE = setOf("filename", "lineno")
    }

    fun format(entry: Directive): String {
        val writer = StringWriter()
        writeEntrySource(entry.meta, writer, "")

        when (entry) {
            is Transaction -> formatTransaction(entry, writer)
            is Open -> formatOpen(entry, writer)
            is Close -> formatClose(entry, writer)
            is Balance -> formatBalance(entry, writer)
            is Commodity -> formatCommodity(entry, writer)
            is Price -> formatPrice(entry, writer)
            is Note -> formatNote(entry, writer)
            is Document -> formatDocument(entry, writer)
            is Pad -> formatPad(entry, writer)
            is Event -> formatEvent(entry, writer)
            is Query -> formatQuery(entry, writer)
            is Custom -> formatCustom(entry, writer)
            is Include -> formatInclude(entry, writer)
        }

        return writer.toString()
    }

    fun renderPosting(posting: Posting): Triple<String, String, String> {
        val flag = posting.flag?.let { "$it " } ?: ""
        val flagAccount = flag + posting.account

        val positionStr = buildString {
            posting.units?.let { units ->
                val formattedNumber = dformat?.format(units.number, units.currency)
                    ?: units.number.toPlainString()
                append("$formattedNumber ${units.currency}")

                posting.cost?.let { costSpec ->
                    append(" {")
                    costSpec.numberPer?.let {
                        val formattedCost = dformat?.format(it, costSpec.currency ?: units.currency)
                            ?: it.toPlainString()
                        append(formattedCost)
                    }
                    costSpec.currency?.let { append(" $it") }
                    // Note: date is not typically displayed in cost formatting for Posting
                    costSpec.label?.let { append(", \"$it\"") }
                    append("}")
                }
            }
        }

        val weightStr = if (renderWeights && posting.units != null) {
            getWeight(posting).toString()
        } else ""

        val priceStr = posting.price?.let { price ->
            val formattedPrice = dformatMax?.format(price.number, price.currency)
                ?: price.number.toPlainString()
            " @ $formattedPrice ${price.currency}"
        } ?: ""

        return Triple(flagAccount, positionStr + priceStr, weightStr)
    }

    // ---- Formatting methods ----

    private fun formatTransaction(entry: Transaction, writer: Writer) {
        // Header line
        val parts = mutableListOf<String>()
        entry.payee?.let { parts.add("\"${escapeString(it)}\"") }
        if (entry.narration != null) {
            parts.add("\"${escapeString(entry.narration)}\"")
        } else if (entry.payee != null) {
            parts.add("\"\"")
        }

        entry.tags.sorted().forEach { parts.add("#$it") }
        entry.links.sorted().forEach { parts.add("^$it") }

        val flagStr = entry.flag?.let { "$it " } ?: ""
        writer.write("${entry.date} ${flagStr}${parts.joinToString(" ")}\n")

        // Metadata
        writeMetadata(entry.meta, writer)

        // Postings with alignment
        val rows = entry.postings.map { renderPosting(it) }
        val accountStrs = rows.map { it.first }
        val positionStrs = rows.map { it.second }
        val weightStrs = rows.map { it.third }

        var widthAccount = accountStrs.maxOfOrNull { it.length } ?: 1
        if (minWidthAccount != null && minWidthAccount > widthAccount) {
            widthAccount = minWidthAccount
        }

        val (alignedPositions, _) = alignPositionStrings(positionStrs)
        val (alignedWeights, widthWeight) = alignPositionStrings(weightStrs)

        val hasNontrivialWeights = renderWeights && weightStrs.any { it.isNotEmpty() } && widthWeight > 0

        for (i in entry.postings.indices) {
            val account = accountStrs[i]
            val position = alignedPositions[i]
            val weight = alignedWeights.getOrElse(i) { "" }

            if (hasNontrivialWeights && weight.isNotEmpty()) {
                writer.write("${prefix}${account.padEnd(widthAccount)}  ${position.padEnd(1)}  ; ${weight.padEnd(1)}".trimEnd())
                writer.write("\n")
            } else {
                writer.write("${prefix}${account.padEnd(widthAccount)}  ${position}".trimEnd())
                writer.write("\n")
            }

            entry.postings[i].meta?.let {
                writeMetadata(it, writer, "    ")
            }
        }
    }

    private fun formatOpen(entry: Open, writer: Writer) {
        val currenciesStr = entry.currencies.joinToString(",")
        val bookingStr = entry.booking?.let { " \"${it.name}\"" } ?: ""
        val accountStr = if (minWidthAccount != null) {
            entry.account.padEnd(minWidthAccount)
        } else {
            entry.account
        }
        val line = "${entry.date} open $accountStr $currenciesStr$bookingStr".trimEnd()
        writer.write("$line\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatClose(entry: Close, writer: Writer) {
        writer.write("${entry.date} close ${entry.account}\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatBalance(entry: Balance, writer: Writer) {
        val formattedNumber = dformat?.format(entry.amount.number, entry.amount.currency)
            ?: entry.amount.number.toPlainString()
        val toleranceStr = entry.tolerance?.let { "~ ${it.toPlainString()} " } ?: ""
        writer.write("${entry.date} balance ${entry.account} $formattedNumber ${toleranceStr}${entry.amount.currency}\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatCommodity(entry: Commodity, writer: Writer) {
        writer.write("${entry.date} commodity ${entry.currency}\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatPrice(entry: Price, writer: Writer) {
        val formattedNumber = dformatMax?.format(entry.amount.number, entry.amount.currency)
            ?: entry.amount.number.toPlainString()
        writer.write("${entry.date} price ${entry.currency} $formattedNumber ${entry.amount.currency}\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatNote(entry: Note, writer: Writer) {
        writer.write("${entry.date} note ${entry.account} \"${escapeString(entry.comment)}\"")
        entry.tags?.sorted()?.forEach { writer.write(" #$it") }
        entry.links?.sorted()?.forEach { writer.write(" ^$it") }
        writer.write("\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatDocument(entry: Document, writer: Writer) {
        writer.write("${entry.date} document ${entry.account} \"${escapeString(entry.filename)}\"")
        entry.tags?.sorted()?.forEach { writer.write(" #$it") }
        entry.links?.sorted()?.forEach { writer.write(" ^$it") }
        writer.write("\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatPad(entry: Pad, writer: Writer) {
        writer.write("${entry.date} pad ${entry.account} ${entry.sourceAccount}\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatEvent(entry: Event, writer: Writer) {
        writer.write("${entry.date} event \"${escapeString(entry.type)}\" \"${escapeString(entry.description)}\"\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatQuery(entry: Query, writer: Writer) {
        writer.write("${entry.date} query \"${escapeString(entry.name)}\" \"${escapeString(entry.queryString)}\"\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatCustom(entry: Custom, writer: Writer) {
        val valuesStr = entry.values.joinToString(" ") { value ->
            when (value) {
                is String -> "\"${escapeString(value)}\""
                is Decimal -> value.toPlainString()
                is kotlinx.datetime.LocalDate -> value.toString()
                is Boolean -> if (value) "TRUE" else "FALSE"
                is Amount -> "${value.number.toPlainString()} ${value.currency}"
                is Enum<*> -> value.name
                else -> value.toString()
            }
        }
        writer.write("${entry.date} custom \"${escapeString(entry.type)}\" $valuesStr\n")
        writeMetadata(entry.meta, writer)
    }

    private fun formatInclude(entry: Include, writer: Writer) {
        writer.write("${entry.date} include \"${escapeString(entry.filename)}\"\n")
        writeMetadata(entry.meta, writer)
    }

    // ---- Helper methods ----

    private fun writeMetadata(meta: Meta, writer: Writer, indent: String = prefix) {
        meta.forEach { (key, value) ->
            if (key in META_IGNORE || key.startsWith("__")) return@forEach

            val valueStr = when (value) {
                is String -> "\"${escapeString(value)}\""
                is Decimal -> value.toPlainString()
                is kotlinx.datetime.LocalDate -> value.toString()
                is Boolean -> if (value) "TRUE" else "FALSE"
                is Amount -> value.toString()
                is Enum<*> -> value.name
                else -> return@forEach
            }
            writer.write("$indent$key: $valueStr\n")
        }
    }

    private fun writeEntrySource(meta: Meta, writer: Writer, indent: String = prefix) {
        if (!writeSource) return
        writer.write("$indent; source: ${renderSource(meta)}\n")
    }
}

/**
 * Align rendered position strings based on the first currency character (uppercase letter).
 * Based on beancount.parser.printer.align_position_strings.
 *
 * This aligns position strings so that the first currency word aligns vertically.
 *
 * Example:
 * ```
 *       45 HOOL {504.30 USD}
 *        4 HOOL {504.30 USD, 2014-11-11}
 *     9.95 USD
 * -22473.32 CAD @ 1.10 USD
 *          ^
 * ```
 *
 * @param strings List of rendered position strings.
 * @return Pair of (aligned strings, total width).
 */
private fun alignPositionStrings(strings: List<String>): Pair<List<String>, Int> {
    if (strings.isEmpty()) return emptyList<String>() to 0

    var maxBefore = 0
    var maxAfter = 0
    var maxUnknown = 0

    val stringItems = mutableListOf<Pair<Int?, String>>()

    for (string in strings) {
        val match = Regex("[A-Z]").find(string)
        if (match != null && match.range.first > 0) {
            val index = match.range.first
            maxBefore = maxOf(maxBefore, index)
            maxAfter = maxOf(maxAfter, string.length - index)
            stringItems.add(index to string)
        } else {
            maxUnknown = maxOf(maxUnknown, string.length)
            stringItems.add(null to string)
        }
    }

    val maxTotal = maxOf(maxBefore + maxAfter, maxUnknown)
    val maxAfterPrime = maxTotal - maxBefore

    val alignedStrings = mutableListOf<String>()
    for ((index, string) in stringItems) {
        if (index != null) {
            val before = string.substring(0, index)
            val after = string.substring(index)
            alignedStrings.add(before.padStart(maxBefore) + after.padEnd(maxAfterPrime))
        } else {
            alignedStrings.add(string.padEnd(maxTotal))
        }
    }

    return alignedStrings to maxTotal
}

/**
 * Escape special characters in strings for beancount output.
 */
private fun escapeString(value: String): String {
    return value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}
