package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
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
    init {
        beancountVersionOption()
    }
    private val filenames by argument("FILENAMES")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file(s)")
        .multiple(required = true)

    private val output by option("-o", "--output")
        .help("Output file (default: stdout, only valid with single input)")

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
        .help("Format file(s) in place")

    override fun run() {
        if (output != null && filenames.size > 1) {
            echo("Error: --output can only be used with a single input file.", err = true)
            throw ProgramResult(1)
        }

        val options = FormatOptions(
            prefixWidth = prefixWidth,
            numWidth = numWidth,
            currencyColumn = currencyColumn
        )

        val formatter = BeancountFormatter()

        for (file in filenames) {
            val contents = file.readText()
            val result = formatter.format(contents, options)

            when {
                inPlace -> {
                    file.writeText(result.formattedText)
                    echo("Formatted ${file.absolutePath}")
                }
                output != null -> {
                    File(output!!).writeText(result.formattedText)
                }
                else -> {
                    echo(result.formattedText)
                }
            }
        }
    }
}
