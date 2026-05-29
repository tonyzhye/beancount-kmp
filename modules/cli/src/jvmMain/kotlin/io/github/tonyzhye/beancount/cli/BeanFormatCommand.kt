package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import io.github.tonyzhye.beancount.core.BeancountFormatter
import io.github.tonyzhye.beancount.core.FormatOptions
import java.io.File

/**
 * bean-format command implementation.
 *
 * Based on beancount.scripts.format.main.
 *
 * Automatically format a Beancount ledger by aligning
 * all the amounts to the same column.
 */
class BeanFormatCommand : CliktCommand(
    name = "bean-format",
    help = "Automatically format a Beancount ledger"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    private val output by option("-o", "--output")
        .help("Output file (default: stdout)")

    private val prefixWidth by option("-w", "--prefix-width")
        .int()
        .help("Force fixed prefix width for account names")

    private val numWidth by option("-W", "--num-width")
        .int()
        .help("Force fixed number width")

    private val currencyColumn by option("-c", "--currency-column")
        .int()
        .help("Align currencies to this column")

    private val inPlace by option("-i", "--in-place")
        .flag(default = false)
        .help("Format file in place")

    override fun run() {
        val contents = filename.readText()

        val options = FormatOptions(
            prefixWidth = prefixWidth,
            numWidth = numWidth,
            currencyColumn = currencyColumn
        )

        val formatter = BeancountFormatter()
        val result = formatter.format(contents, options)

        if (inPlace) {
            // Write back to the same file
            filename.writeText(result.formattedText)
            echo("Formatted ${filename.absolutePath}")
        } else if (output != null) {
            // Write to specified output file
            File(output!!).writeText(result.formattedText)
        } else {
            // Write to stdout
            echo(result.formattedText)
        }
    }
}
