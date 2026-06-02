package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
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
    init {
        beancountVersionOption()
    }

    private val seed by option("-s", "--seed")
        .int()
        .help("Random seed for deterministic output")

    private val dateBegin by option("-b", "--date-begin")
        .help("Start date (YYYY-MM-DD)")
        .validate {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                fail("Invalid date format. Use YYYY-MM-DD")
            }
        }

    private val dateEnd by option("-e", "--date-end")
        .help("End date (YYYY-MM-DD)")
        .validate {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                fail("Invalid date format. Use YYYY-MM-DD")
            }
        }

    private val dateBirth by option("--date-birth")
        .help("Birth date (YYYY-MM-DD)")
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

    private val noReformat by option("--no-reformat")
        .flag(default = false)
        .help("Do not reformat the output (emit a single long line)")

    private val verbose by option("-v", "--verbose")
        .choice("true" to true, "false" to false)
        .default(false)
        .help("Produce logging output")

    override fun run() {
        val defaultOptions = ExampleOptions()
        val options = ExampleOptions(
            seed = seed,
            principalCurrency = currency,
            includeInvestments = noInvestments == false,
            includeTaxes = noTaxes == false,
            dateBegin = dateBegin?.let { LocalDate.parse(it) } ?: defaultOptions.dateBegin,
            dateEnd = dateEnd?.let { LocalDate.parse(it) } ?: defaultOptions.dateEnd,
            dateBirth = dateBirth?.let { LocalDate.parse(it) } ?: defaultOptions.dateBirth,
            reformat = noReformat == false
        )

        if (verbose) {
            echo("Generating example ledger...", err = true)
        }

        val ledger = ExampleGenerator.generateString(options)

        val outFile = output
        if (outFile != null) {
            outFile.writeText(ledger)
            if (verbose) {
                echo("Example ledger written to ${outFile.absolutePath}")
            }
        } else {
            echo(ledger)
        }
    }
}
