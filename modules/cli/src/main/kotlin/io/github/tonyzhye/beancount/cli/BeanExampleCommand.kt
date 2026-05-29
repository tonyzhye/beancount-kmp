package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import io.github.tonyzhye.beancount.core.ExampleGenerator
import io.github.tonyzhye.beancount.core.ExampleOptions
import kotlinx.datetime.LocalDate
import java.io.File

/**
 * bean-example command implementation.
 * Based on beancount.scripts.example.
 *
 * Generates a sample Beancount ledger with fictional transactions.
 */
class BeanExampleCommand : CliktCommand(
    name = "bean-example",
    help = "Generate a sample Beancount file with fictional transactions."
) {
    private val seed by option("-s", "--seed")
        .int()
        .help("Random seed for deterministic output")

    private val beginDate by option("-b", "--begin-date")
        .help("Start date (YYYY-MM-DD)")
        .validate {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                fail("Invalid date format. Use YYYY-MM-DD")
            }
        }

    private val endDate by option("-e", "--end-date")
        .help("End date (YYYY-MM-DD)")
        .validate {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                fail("Invalid date format. Use YYYY-MM-DD")
            }
        }

    private val currency by option("-c", "--currency")
        .default("USD")
        .help("Principal currency")

    private val output: java.io.File? by option("-o", "--output")
        .file(mustExist = false, canBeDir = false)
        .help("Output file (default: stdout)")

    private val noInvestments by option("--no-investments")
        .flag(default = false)
        .help("Exclude investment transactions")

    private val noTaxes by option("--no-taxes")
        .flag(default = false)
        .help("Exclude tax-related transactions")

    override fun run() {
        val defaultOptions = ExampleOptions()
        val options = ExampleOptions(
            seed = seed,
            principalCurrency = currency,
            includeInvestments = !noInvestments,
            includeTaxes = !noTaxes,
            dateBegin = beginDate?.let { LocalDate.parse(it) } ?: defaultOptions.dateBegin,
            dateEnd = endDate?.let { LocalDate.parse(it) } ?: defaultOptions.dateEnd
        )

        echo("Generating example ledger...", err = true)
        
        val ledger = ExampleGenerator.generateString(options)

        val outFile = output
        if (outFile != null) {
            outFile.writeText(ledger)
            echo("Example ledger written to ${outFile.absolutePath}")
        } else {
            echo(ledger)
        }
    }
}
