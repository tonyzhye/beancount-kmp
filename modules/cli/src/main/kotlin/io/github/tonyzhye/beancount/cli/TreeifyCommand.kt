package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.github.tonyzhye.beancount.core.Treeify
import java.io.File

/**
 * treeify command implementation.
 * Based on beancount.tools.treeify.
 *
 * Identifies a column of text that contains hierarchical identifiers
 * (like "Assets:Bank:Checking") and replaces them with ASCII tree structures.
 */
class TreeifyCommand : CliktCommand(
    name = "treeify",
    help = "Convert hierarchical text columns into ASCII tree structures"
) {
    init {
        beancountVersionOption()
    }

    private val input by argument("INPUT")
        .file(mustExist = true, canBeDir = false)
        .help("Input file to process (reads from stdin if not provided)")
        .optional()

    private val output by option("-o", "--output")
        .file(canBeDir = false)
        .help("Output file (writes to stdout if not provided)")

    private val pattern by option("-r", "--pattern")
        .help("Pattern for repeatable components (default: beancount account names)")

    private val delimiter by option("-d", "--delimiter")
        .default("""[ \t]+""")
        .help("Delimiter pattern to detect the end of a column text")

    private val split by option("-s", "--split")
        .default(":")
        .help("Pattern splitting into components")

    private val filenames by option("-F", "--filenames")
        .flag(default = false)
        .help("Use pattern and split suitable for filenames")

    private val looseAccounts by option("-A", "--loose-accounts")
        .flag(default = false)
        .help("Use pattern and split suitable for loose account names")

    private val filler by option("--filler")
        .default(" ")
        .help("Filler string for new lines inserted for formatting")

    override fun run() {
        // Validate conflicting options
        val optionCount = listOf(filenames, looseAccounts, pattern != null).count { it }
        if (optionCount > 1) {
            echo("Error: Conflicting pattern options. Use only one of --filenames, --loose-accounts, or --pattern.", err = true)
            return
        }

        // Determine pattern and split
        val (actualPattern, actualSplit) = when {
            filenames -> Treeify.FILENAME_PATTERN to "/"
            looseAccounts -> Treeify.LOOSE_PATTERN to ":"
            pattern != null -> pattern!! to split
            else -> Treeify.DEFAULT_PATTERN to split
        }

        // Read input
        val inputText = if (input != null) {
            input!!.readText()
        } else {
            generateSequence { readlnOrNull() }.joinToString("\n")
        }

        // Process
        val result = Treeify.treeify(
            input = inputText,
            pattern = actualPattern,
            delimiter = delimiter,
            splitter = actualSplit,
            filler = filler
        )

        // Write output
        if (output != null) {
            output!!.writeText(result)
        } else {
            echo(result)
        }
    }
}
