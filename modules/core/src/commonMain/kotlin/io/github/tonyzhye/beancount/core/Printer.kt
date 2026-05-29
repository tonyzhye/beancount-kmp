package io.github.tonyzhye.beancount.core

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
 * Format an error into a string.
 * Based on beancount.parser.printer.format_error.
 *
 * Format:
 *   filename:lineno: message
 *
 *   [indented entry if present]
 */
fun formatError(error: BeancountError): String {
    val builder = StringBuilder()
    builder.append("${renderSource(error.source)} ${error.message}\n")

    val entry = error.entry
    if (entry != null) {
        builder.append("\n")
        val entryStr = formatEntry(entry)
        entryStr.lines().forEach { line ->
            builder.append("   $line\n")
        }
    }

    return builder.toString()
}

/**
 * Format a list of errors into a single string.
 * Based on beancount.parser.printer.print_errors.
 */
fun formatErrors(errors: List<BeancountError>): String {
    val output = StringBuilder()
    errors.forEach { error ->
        output.append(formatError(error))
        output.append("\n")
    }
    return output.toString()
}

/**
 * Format a single directive entry into beancount syntax.
 * Based on beancount.parser.printer.EntryPrinter.
 */
fun formatEntry(entry: Directive): String = formatEntry(entry, null)

/**
 * Format a single directive with optional DisplayFormatter for number precision.
 */
private fun formatEntry(entry: Directive, formatter: DisplayFormatter?): String {
    return when (entry) {
        is Transaction -> formatTransaction(entry, formatter)
        is Open -> formatOpen(entry)
        is Close -> formatClose(entry)
        is Balance -> formatBalance(entry, formatter)
        is Commodity -> formatCommodity(entry)
        is Price -> formatPrice(entry, formatter)
        is Note -> formatNote(entry)
        is Document -> formatDocument(entry)
        is Pad -> formatPad(entry)
        is Event -> formatEvent(entry)
        is Query -> formatQuery(entry)
        is Custom -> formatCustom(entry)
        is Include -> formatInclude(entry)
    }
}

private fun formatInclude(entry: Include): String {
    val builder = StringBuilder()
    builder.append("${entry.date} include \"${escapeString(entry.filename)}\"\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

/**
 * Format a list of entries into a single string with automatic blank line insertion.
 * Based on beancount.parser.printer.print_entries.
 *
 * Inserts blank lines between transactions and between blocks of same-type directives.
 */
fun formatEntries(entries: List<Directive>): String {
    return formatEntries(entries, null)
}

/**
 * Format entries with DisplayContext for number precision.
 */
fun formatEntries(entries: List<Directive>, dcontext: DisplayContext?): String {
    if (entries.isEmpty()) return ""

    val formatter = dcontext?.buildFormatter()

    val output = StringBuilder()
    var previousType: KClass<out Directive>? = null
    entries.forEach { entry ->
        val entryType = entry::class
        if (entry is Transaction || entry is Commodity || entryType != previousType) {
            output.append("\n")
            previousType = entryType
        }

        output.append(formatEntry(entry, formatter))
    }

    return output.toString()
}

// ---- Private formatting helpers ----

private fun formatTransaction(entry: Transaction, formatter: DisplayFormatter? = null): String {
    val builder = StringBuilder()

    // Header line: date flag payee narration tags links
    val parts = mutableListOf<String>()
    entry.payee?.let { parts.add("\"${escapeString(it)}\"") }
    entry.narration?.let { parts.add("\"${escapeString(it)}\"") }
        ?: entry.payee?.let { parts.add("\"\"") } // empty narration if payee exists

    entry.tags.sorted().forEach { parts.add("#$it") }
    entry.links.sorted().forEach { parts.add("^$it") }

    val flagStr = entry.flag?.let { "$it " } ?: ""
    builder.append("${entry.date} ${flagStr}${parts.joinToString(" ")}\n")

    // Metadata
    formatMetadata(entry.meta, builder)

    // Postings - with simple alignment
    val postingsStr = entry.postings.map { formatPosting(it, formatter) }
    val maxAccountWidth = postingsStr.maxOfOrNull { it.first.length } ?: 0
    val minAccountWidth = maxOf(maxAccountWidth, 47) // Match Python default

    postingsStr.forEach { (accountStr, positionStr, postingMeta) ->
        if (positionStr.isNotEmpty()) {
            builder.append("  ${accountStr.padEnd(minAccountWidth)}  $positionStr\n")
        } else {
            builder.append("  $accountStr\n")
        }

        // Posting metadata
        postingMeta?.let {
            formatMetadata(it, builder, prefix = "    ")
        }
    }

    return builder.toString()
}

/**
 * Format a posting into (account, position_string, metadata?) triple.
 */
private fun formatPosting(posting: Posting, formatter: DisplayFormatter? = null): Triple<String, String, Meta?> {
    val flagStr = posting.flag?.let { "$it " } ?: ""
    val accountStr = flagStr + posting.account

    val positionStr = buildString {
        posting.units?.let { units ->
            val formattedNumber = formatter?.format(units.number, units.currency)
                ?: units.number.toPlainString()
            append("$formattedNumber ${units.currency}")

            posting.cost?.let { costSpec ->
                append(" {")
                // Print numberPer if available
                costSpec.numberPer?.let {
                    val formattedCost = formatter?.format(it, costSpec.currency ?: units.currency)
                        ?: it.toPlainString()
                    append(formattedCost)
                }
                // Print currency if available
                costSpec.currency?.let { append(" $it") }
                // Note: date is not typically displayed in cost formatting
                costSpec.label?.let { append(", \"$it\"") }
                append("}")
            }

            posting.price?.let { price ->
                val formattedPrice = formatter?.format(price.number, price.currency)
                    ?: price.number.toPlainString()
                append(" @ $formattedPrice ${price.currency}")
            }
        }
    }

    return Triple(accountStr, positionStr, posting.meta)
}

private fun formatOpen(entry: Open): String {
    val builder = StringBuilder()
    val currenciesStr = entry.currencies.joinToString(",")
    val bookingStr = entry.booking?.let { " \"${it.name}\"" } ?: ""
    val line = "${entry.date} open ${entry.account} $currenciesStr$bookingStr".trimEnd()
    builder.append("$line\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatClose(entry: Close): String {
    val builder = StringBuilder()
    builder.append("${entry.date} close ${entry.account}\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatBalance(entry: Balance, formatter: DisplayFormatter? = null): String {
    val builder = StringBuilder()
    val formattedNumber = formatter?.format(entry.amount.number, entry.amount.currency)
        ?: entry.amount.number.toPlainString()
    val toleranceStr = entry.tolerance?.let { "~ ${it.toPlainString()} " } ?: ""
    val diffStr = entry.diffAmount?.let { "   ; Diff: $it" } ?: ""
    builder.append("${entry.date} balance ${entry.account} $formattedNumber ${toleranceStr}${entry.amount.currency}$diffStr\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatCommodity(entry: Commodity): String {
    val builder = StringBuilder()
    builder.append("${entry.date} commodity ${entry.currency}\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatPrice(entry: Price, formatter: DisplayFormatter? = null): String {
    val builder = StringBuilder()
    val formattedNumber = formatter?.format(entry.amount.number, entry.amount.currency)
        ?: entry.amount.number.toPlainString()
    builder.append("${entry.date} price ${entry.currency} $formattedNumber ${entry.amount.currency}\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatNote(entry: Note): String {
    val builder = StringBuilder()
    builder.append("${entry.date} note ${entry.account} \"${escapeString(entry.comment)}\"")
    entry.tags?.sorted()?.forEach { builder.append(" #$it") }
    entry.links?.sorted()?.forEach { builder.append(" ^$it") }
    builder.append("\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatDocument(entry: Document): String {
    val builder = StringBuilder()
    builder.append("${entry.date} document ${entry.account} \"${escapeString(entry.filename)}\"")
    entry.tags?.sorted()?.forEach { builder.append(" #$it") }
    entry.links?.sorted()?.forEach { builder.append(" ^$it") }
    builder.append("\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatPad(entry: Pad): String {
    val builder = StringBuilder()
    builder.append("${entry.date} pad ${entry.account} ${entry.sourceAccount}\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatEvent(entry: Event): String {
    val builder = StringBuilder()
    builder.append("${entry.date} event \"${escapeString(entry.type)}\" \"${escapeString(entry.description)}\"\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatQuery(entry: Query): String {
    val builder = StringBuilder()
    builder.append("${entry.date} query \"${escapeString(entry.name)}\" \"${escapeString(entry.queryString)}\"\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

private fun formatCustom(entry: Custom): String {
    val builder = StringBuilder()
    val valuesStr = entry.values.joinToString(" ") { value ->
        when (value) {
            is String -> "\"${escapeString(value)}\""
            is Decimal -> value.toPlainString()
            is kotlinx.datetime.LocalDate -> value.toString()
            is Boolean -> if (value) "TRUE" else "FALSE"
            is Amount -> "${value.number.toPlainString()} ${value.currency}"
            else -> value.toString()
        }
    }
    builder.append("${entry.date} custom \"${escapeString(entry.type)}\" $valuesStr\n")
    formatMetadata(entry.meta, builder)
    return builder.toString()
}

/**
 * Format metadata, excluding internal fields.
 */
private fun formatMetadata(meta: Meta, builder: StringBuilder, prefix: String = "  ") {
    meta.forEach { (key, value) ->
        if (key == "filename" || key == "lineno" || key.startsWith("__")) return@forEach

        val valueStr = when (value) {
            is String -> "\"${escapeString(value)}\""
            is Decimal -> value.toPlainString()
            is kotlinx.datetime.LocalDate -> value.toString()
            is Boolean -> if (value) "TRUE" else "FALSE"
            is Amount -> value.toString()
            else -> return@forEach // Skip unknown types
        }
        builder.append("$prefix$key: $valueStr\n")
    }
}

/**
 * Escape special characters in strings for beancount output.
 */
private fun escapeString(value: String): String {
    return value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}
