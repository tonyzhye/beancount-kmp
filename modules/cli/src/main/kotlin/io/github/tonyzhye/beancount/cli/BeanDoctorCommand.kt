package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import java.io.File
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
            DoctorListOptionsCommand(),
            DoctorAccountsCommand(),
            DoctorCommoditiesCommand(),
            DoctorPricesCommand(),
            DoctorStatsCommand(),
            DoctorErrorsCommand(),
            DoctorLinkedCommand(),
            DoctorLexCommand(),
            DoctorRegionCommand(),
            DoctorDirectoriesCommand()
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
            .toSortedMap(compareBy { it })

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

/**
 * Accounts subcommand - list all accounts.
 */
class DoctorAccountsCommand : CliktCommand(
    name = "accounts",
    help = "List all accounts in the ledger"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val accounts = result.entries
            .filterIsInstance<io.github.tonyzhye.beancount.core.Open>()
            .map { it.account }
            .sorted()

        echo("Accounts (${accounts.size}):")
        accounts.forEach { echo("  $it") }
    }
}

/**
 * Commodities subcommand - list all commodities.
 */
class DoctorCommoditiesCommand : CliktCommand(
    name = "commodities",
    help = "List all commodities in the ledger"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val commodities = result.entries
            .filterIsInstance<io.github.tonyzhye.beancount.core.Commodity>()
            .map { it.currency }
            .sorted()
            .distinct()

        echo("Commodities (${commodities.size}):")
        commodities.forEach { echo("  $it") }
    }
}

/**
 * Prices subcommand - list all price entries.
 */
class DoctorPricesCommand : CliktCommand(
    name = "prices",
    help = "List all price entries in the ledger"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val prices = result.entries
            .filterIsInstance<io.github.tonyzhye.beancount.core.Price>()
            .sortedBy { it.date }

        echo("Prices (${prices.size}):")
        prices.forEach { price ->
            echo("  ${price.date} ${price.currency} ${price.amount}")
        }
    }
}

/**
 * Stats subcommand - show ledger statistics.
 */
class DoctorStatsCommand : CliktCommand(
    name = "stats",
    help = "Show statistics about the ledger"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val entries = result.entries

        val transactions = entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
        val opens = entries.filterIsInstance<io.github.tonyzhye.beancount.core.Open>()
        val closes = entries.filterIsInstance<io.github.tonyzhye.beancount.core.Close>()
        val commodities = entries.filterIsInstance<io.github.tonyzhye.beancount.core.Commodity>()
        val prices = entries.filterIsInstance<io.github.tonyzhye.beancount.core.Price>()
        val balances = entries.filterIsInstance<io.github.tonyzhye.beancount.core.Balance>()
        val notes = entries.filterIsInstance<io.github.tonyzhye.beancount.core.Note>()
        val documents = entries.filterIsInstance<io.github.tonyzhye.beancount.core.Document>()
        val events = entries.filterIsInstance<io.github.tonyzhye.beancount.core.Event>()

        val postings = transactions.sumOf { it.postings.size }
        val accounts = opens.map { it.account }.toSet()
        val currencies = commodities.map { it.currency }.toSet()

        val dates = entries.map { it.date }
        val minDate = dates.minOrNull()
        val maxDate = dates.maxOrNull()

        echo("Ledger Statistics")
        echo("=================")
        echo("")
        echo("Entries: ${entries.size}")
        echo("  Transactions: ${transactions.size}")
        echo("  Open: ${opens.size}")
        echo("  Close: ${closes.size}")
        echo("  Commodity: ${commodities.size}")
        echo("  Price: ${prices.size}")
        echo("  Balance: ${balances.size}")
        echo("  Note: ${notes.size}")
        echo("  Document: ${documents.size}")
        echo("  Event: ${events.size}")
        echo("")
        echo("Postings: $postings")
        echo("Unique Accounts: ${accounts.size}")
        echo("Unique Commodities: ${currencies.size}")
        echo("")
        echo("Date Range: ${minDate ?: "N/A"} to ${maxDate ?: "N/A"}")
        echo("Errors: ${result.errors.size}")
    }
}

/**
 * Errors subcommand - show detailed error information.
 */
class DoctorErrorsCommand : CliktCommand(
    name = "errors",
    help = "Show detailed error information"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val result = loadFile(filename.absolutePath)

        if (result.errors.isEmpty()) {
            echo("No errors found.")
            return
        }

        echo("Errors (${result.errors.size}):")
        echo("")

        // Group errors by type
        val byType = result.errors.groupBy { error ->
            val message = error.message
            when {
                message.contains("balance", ignoreCase = true) -> "Balance"
                message.contains("open", ignoreCase = true) -> "Open"
                message.contains("close", ignoreCase = true) -> "Close"
                message.contains("commodity", ignoreCase = true) -> "Commodity"
                message.contains("duplicate", ignoreCase = true) -> "Duplicate"
                message.contains("pad", ignoreCase = true) -> "Pad"
                else -> "Other"
            }
        }

        byType.toSortedMap().forEach { (type, errors) ->
            echo("$type (${errors.size}):")
            errors.forEach { error ->
                val meta = error.source
                val file = meta["filename"] as? String ?: "unknown"
                val line = meta["lineno"] as? Int ?: 0
                echo("  $file:$line: ${error.message}")
            }
            echo("")
        }
    }
}

/**
 * Linked subcommand - find linked entries.
 */
class DoctorLinkedCommand : CliktCommand(
    name = "linked",
    help = "Find entries linked by tag or link"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    private val tagOrLink by argument("TAG_OR_LINK")
        .help("Tag (#tag) or link (^link) to search for")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val entries = result.entries

        val linkedEntries = when {
            tagOrLink.startsWith("#") -> {
                val tag = tagOrLink.substring(1)
                entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
                    .filter { it.tags.contains(tag) }
            }
            tagOrLink.startsWith("^") -> {
                val link = tagOrLink.substring(1)
                entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
                    .filter { it.links.contains(link) }
            }
            else -> {
                echo("TAG_OR_LINK must start with # (tag) or ^ (link)")
                return
            }
        }

        echo("Linked Entries (${linkedEntries.size}):")
        linkedEntries.forEach { entry ->
            echo("  ${entry.date} ${entry.flag} ${entry.narration}")
        }
    }
}

// Helper functions

/**
 * Lex subcommand - dump lexer tokens.
 */
class DoctorLexCommand : CliktCommand(
    name = "lex",
    help = "Dump the lexer output for a Beancount syntax file"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    override fun run() {
        val content = filename.readText()
        val lexer = io.github.tonyzhye.beancount.parser.Lexer(content)
        val tokens = lexer.tokenize()

        echo(String.format("%-15s %6s %s", "TOKEN", "LINE", "TEXT"))
        echo("-".repeat(60))

        for (token in tokens) {
            val tokenName = when (token) {
                is io.github.tonyzhye.beancount.parser.Token.EOF -> "EOF"
                is io.github.tonyzhye.beancount.parser.Token.EOL -> "EOL"
                is io.github.tonyzhye.beancount.parser.Token.INDENT -> "INDENT"
                is io.github.tonyzhye.beancount.parser.Token.DATE -> "DATE"
                is io.github.tonyzhye.beancount.parser.Token.NUMBER -> "NUMBER"
                is io.github.tonyzhye.beancount.parser.Token.STRING -> "STRING"
                is io.github.tonyzhye.beancount.parser.Token.ACCOUNT -> "ACCOUNT"
                is io.github.tonyzhye.beancount.parser.Token.CURRENCY -> "CURRENCY"
                is io.github.tonyzhye.beancount.parser.Token.KEYWORD -> "KEYWORD"
                is io.github.tonyzhye.beancount.parser.Token.FLAG -> "FLAG"
                is io.github.tonyzhye.beancount.parser.Token.TAG -> "TAG"
                is io.github.tonyzhye.beancount.parser.Token.LINK -> "LINK"
                is io.github.tonyzhye.beancount.parser.Token.KEY -> "KEY"
                is io.github.tonyzhye.beancount.parser.Token.BOOL -> "BOOL"
                is io.github.tonyzhye.beancount.parser.Token.NONE -> "NONE"
                is io.github.tonyzhye.beancount.parser.Token.COLON -> "COLON"
                is io.github.tonyzhye.beancount.parser.Token.COMMA -> "COMMA"
                is io.github.tonyzhye.beancount.parser.Token.SLASH -> "SLASH"
                is io.github.tonyzhye.beancount.parser.Token.MINUS -> "MINUS"
                is io.github.tonyzhye.beancount.parser.Token.PLUS -> "PLUS"
                is io.github.tonyzhye.beancount.parser.Token.ASTERISK -> "ASTERISK"
                is io.github.tonyzhye.beancount.parser.Token.AT -> "AT"
                is io.github.tonyzhye.beancount.parser.Token.DOUBLE_AT -> "DOUBLE_AT"
                is io.github.tonyzhye.beancount.parser.Token.LCURLY -> "LCURLY"
                is io.github.tonyzhye.beancount.parser.Token.RCURLY -> "RCURLY"
                is io.github.tonyzhye.beancount.parser.Token.LPAREN -> "LPAREN"
                is io.github.tonyzhye.beancount.parser.Token.RPAREN -> "RPAREN"
                is io.github.tonyzhye.beancount.parser.Token.TILDE -> "TILDE"
                is io.github.tonyzhye.beancount.parser.Token.HASH -> "HASH"
                is io.github.tonyzhye.beancount.parser.Token.SYMBOL -> "SYMBOL"
                is io.github.tonyzhye.beancount.parser.Token.COMMENT -> "COMMENT"
                is io.github.tonyzhye.beancount.parser.Token.ERROR -> "ERROR"
            }
            echo(String.format("%-15s %6d %s", tokenName, token.line, token.text))
        }

        val errors = lexer.getErrors()
        if (errors.isNotEmpty()) {
            echo("")
            echo("Lexer Errors:")
            errors.forEach { error ->
                echo("  ${error.line}:${error.column}: ${error.message}")
            }
        }
    }
}

/**
 * Region subcommand - show transactions in a region with balances.
 */
class DoctorRegionCommand : CliktCommand(
    name = "region",
    help = "Print transactions within a region and compute balances"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    private val region by argument("REGION")
        .help("Region as start:end or filename:start:end")

    private val conversion by option("--conversion")
        .help("Convert balances to 'value' or 'cost'")

    override fun run() {
        val result = loadFile(filename.absolutePath)
        val (searchFilename, startLine, endLine) = parseRegion(region, filename.absolutePath)

        // Find transactions in the region
        val regionEntries = result.entries
            .filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
            .filter { entry ->
                val entryFilename = entry.meta["filename"] as? String ?: ""
                val entryLine = entry.meta["lineno"] as? Int ?: 0
                entryFilename == searchFilename && entryLine in startLine..endLine
            }

        if (regionEntries.isEmpty()) {
            echo("No transactions found in region $searchFilename:$startLine:$endLine")
            return
        }

        echo("Transactions in region ${searchFilename}:$startLine:$endLine")
        echo("=".repeat(60))
        echo("")

        regionEntries.forEach { entry ->
            echo("${entry.date} ${entry.flag} ${entry.narration ?: ""}")
            entry.postings.forEach { posting ->
                val units = posting.units?.let { "${it.number} ${it.currency}" } ?: ""
                echo("  ${posting.account}  $units")
            }
            echo("")
        }

        // Compute balances
        val realRoot = io.github.tonyzhye.beancount.core.realize(regionEntries)

        echo("Balances")
        echo("-".repeat(60))

        realRoot.iterate()
            .filter { it.account.isNotEmpty() }
            .sortedBy { it.account }
            .forEach { account ->
                val balanceStr = account.balance.toString()
                if (balanceStr.isNotEmpty()) {
                    echo("  ${account.account}  $balanceStr")
                }
            }
    }
}

/**
 * Directories subcommand - validate document directory hierarchy.
 */
class DoctorDirectoriesCommand : CliktCommand(
    name = "directories",
    help = "Validate directory hierarchy against ledger account names"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    private val dirs by argument("DIRS")
        .help("Root directories to validate")
        .multiple(required = true)

    override fun run() {
        val result = loadFile(filename.absolutePath)

        // Get all account names from Open directives
        val accounts = result.entries
            .filterIsInstance<io.github.tonyzhye.beancount.core.Open>()
            .map { it.account }
            .toSet()

        echo("Validating ${dirs.size} directorie(s) against ${accounts.size} accounts")
        echo("")

        var hasErrors = false

        dirs.forEach { dir ->
            val dirFile = java.io.File(dir)
            if (!dirFile.exists() || !dirFile.isDirectory) {
                echo("ERROR: $dir is not a valid directory")
                hasErrors = true
                return@forEach
            }

            echo("Directory: $dir")

            // Check that each capitalized subdirectory matches an account
            val subdirs = dirFile.listFiles { file -> file.isDirectory }
                ?.map { it.name }
                ?.filter { it[0].isUpperCase() }
                ?.toSet() ?: emptySet()

            val accountRoots = accounts
                .map { it.split(":").first() }
                .toSet()

            val unmatchedDirs = subdirs - accountRoots
            val missingDirs = accountRoots - subdirs

            if (unmatchedDirs.isNotEmpty()) {
                echo("  Unmatched directories (not in ledger):")
                unmatchedDirs.sorted().forEach { echo("    - $it") }
                hasErrors = true
            }

            if (missingDirs.isNotEmpty()) {
                echo("  Missing directories (in ledger but not on disk):")
                missingDirs.sorted().forEach { echo("    - $it") }
                hasErrors = true
            }

            if (unmatchedDirs.isEmpty() && missingDirs.isEmpty()) {
                echo("  OK - All directories match account names")
            }

            echo("")
        }

        if (hasErrors) {
            throw PrintMessage("Validation failed", statusCode = 1)
        } else {
            echo("All directories validated successfully.")
        }
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

private fun parseRegion(region: String, defaultFilename: String): Triple<String, Int, Int> {
    val parts = region.split(":")
    return when (parts.size) {
        2 -> {
            // start:end
            Triple(defaultFilename, parts[0].toInt(), parts[1].toInt())
        }
        3 -> {
            // filename:start:end or start:end:??
            if (parts[0].toIntOrNull() != null) {
                // start:end:something - treat first two as range
                Triple(defaultFilename, parts[0].toInt(), parts[1].toInt())
            } else {
                // filename:start:end
                Triple(parts[0], parts[1].toInt(), parts[2].toInt())
            }
        }
        else -> throw IllegalArgumentException("Invalid region format. Use start:end or filename:start:end")
    }
}
