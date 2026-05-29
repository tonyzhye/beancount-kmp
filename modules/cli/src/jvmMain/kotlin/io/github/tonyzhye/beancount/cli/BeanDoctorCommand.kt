package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import io.github.tonyzhye.beancount.core.BEANCOUNT_VERSION
import io.github.tonyzhye.beancount.core.BeancountDoctor
import io.github.tonyzhye.beancount.core.formatEntries
import io.github.tonyzhye.beancount.loader.loadFile

/**
 * bean-doctor main command with subcommands.
 *
 * Based on beancount.scripts.doctor.
 */
class BeanDoctorCommand : NoOpCliktCommand(
    name = "bean-doctor",
    help = "Debugging tool for beancount ledgers"
) {
    init {
        subcommands(
            DoctorContextCommand(),
            DoctorMissingOpenCommand(),
            DoctorParseCommand(),
            DoctorDisplayContextCommand(),
            DoctorRoundtripCommand(),
            DoctorListOptionsCommand()
        )
    }

    override fun run() {}
}

/**
 * Context subcommand - show context around a specific line.
 */
class DoctorContextCommand : CliktCommand(
    name = "context",
    help = "Describe transaction context at a specific line"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    private val location by argument("LOCATION")
        .help("Line number or filename:lineno")

    private val contextLines by option("-c", "--context")
        .int()
        .default(3)
        .help("Number of surrounding entries to show")

    override fun run() {
        val result = loadFile(filename.absolutePath)

        // Parse location
        val (searchFilename, lineno) = parseLocation(location, filename.absolutePath)

        val doctor = BeancountDoctor()
        val output = doctor.context(
            result.entries,
            searchFilename,
            lineno,
            contextLines
        )

        echo(output)
    }
}

/**
 * Missing Open subcommand - find accounts without Open directives.
 */
class DoctorMissingOpenCommand : CliktCommand(
    name = "missing-open",
    help = "Print Open directives missing from the ledger"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val doctor = BeancountDoctor()
        val missing = doctor.missingOpen(result.entries)

        if (missing.isEmpty()) {
            echo("All accounts have Open directives.")
        } else {
            echo("Missing Open directives (${missing.size}):")
            echo("")
            missing.forEach { openEntry ->
                echo("${openEntry.date} open ${openEntry.account}")
            }
        }
    }
}

/**
 * Parse subcommand - parse a ledger in debug mode.
 */
class DoctorParseCommand : CliktCommand(
    name = "parse",
    help = "Parse a ledger and show the AST"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)

        echo("Parsed ${result.entries.size} entries")
        echo("Errors: ${result.errors.size}")

        if (result.errors.isNotEmpty()) {
            echo("")
            result.errors.forEach { error ->
                echo("ERROR: ${error.message}")
            }
        }

        // Show entry type summary
        echo("")
        echo("Entry types:")
        val typeCounts = result.entries.groupBy { it::class.simpleName }
            .mapValues { it.value.size }
            .toSortedMap()

        typeCounts.forEach { (type, count) ->
            echo("  $type: $count")
        }
    }
}

/**
 * Display Context subcommand - show formatting context.
 */
class DoctorDisplayContextCommand : CliktCommand(
    name = "display-context",
    help = "Print the precision inferred from parsed numbers"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val doctor = BeancountDoctor()
        echo(doctor.displayContext(result.entries))
    }
}

/**
 * Roundtrip subcommand - parse and re-format.
 */
class DoctorRoundtripCommand : CliktCommand(
    name = "roundtrip",
    help = "Round-trip test: parse and re-format the ledger"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val doctor = BeancountDoctor()
        val roundtrip = doctor.roundtrip(result.entries)

        echo("Entries: ${roundtrip.entryCount}")
        echo("")
        echo(roundtrip.formattedOutput)
    }
}

/**
 * List Options subcommand - show parsed options.
 */
class DoctorListOptionsCommand : CliktCommand(
    name = "list-options",
    help = "List options parsed from the ledger"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val doctor = BeancountDoctor()
        echo(doctor.listOptions(result.options))
    }
}

// Helper functions

private fun parseLocation(location: String, defaultFilename: String): Pair<String, Int> {
    return if (location.contains(":")) {
        val parts = location.split(":")
        if (parts.size == 2) {
            val file = if (parts[0].isEmpty()) defaultFilename else parts[0]
            file to parts[1].toInt()
        } else {
            defaultFilename to location.toInt()
        }
    } else {
        defaultFilename to location.toInt()
    }
}
