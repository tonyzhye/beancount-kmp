package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * Doctor tool for debugging beancount ledgers.
 *
 * Provides utilities for inspecting, analyzing, and diagnosing
 * beancount files. Can be used as a library or via CLI.
 *
 * Example usage:
 * ```kotlin
 * val doctor = BeancountDoctor()
 * val entries = loadFile("ledger.beancount").entries
 *
 * // Show context around line 42
 * println(doctor.context(entries, "ledger.beancount", 42))
 *
 * // Find missing Open directives
 * val missing = doctor.missingOpen(entries)
 * missing.forEach { println("${it.date} open ${it.account}") }
 * ```
 */
class BeancountDoctor {

    /**
     * Show context around a specific line in a file.
     *
     * Finds the entry at the given line number and displays it
     * with surrounding entries for context.
     *
     * @param entries Parsed entries from the ledger
     * @param filename Target filename to search in
     * @param lineno Line number to look up
     * @param contextLines Number of surrounding entries to show (default 3)
     * @return Formatted context string
     */
    fun context(
        entries: List<Directive>,
        filename: String,
        lineno: Int,
        contextLines: Int = 3
    ): String {
        // Find the entry at the specified line
        val targetEntry = entries.find { entry ->
            val entryFilename = entry.meta["filename"] as? String ?: ""
            val entryLineno = entry.meta["lineno"] as? Int ?: 0
            entryFilename == filename && entryLineno == lineno
        }

        if (targetEntry == null) {
            return "No entry found at $filename:$lineno"
        }

        // Find index of target entry
        val targetIndex = entries.indexOf(targetEntry)

        // Calculate range
        val startIndex = maxOf(0, targetIndex - contextLines)
        val endIndex = minOf(entries.size - 1, targetIndex + contextLines)

        // Build output
        val output = StringBuilder()
        output.append("Context for $filename:$lineno\n")
        output.append("=".repeat(60))
        output.append("\n\n")

        for (i in startIndex..endIndex) {
            val entry = entries[i]
            val prefix = if (i == targetIndex) ">>> " else "    "
            val entryStr = formatEntry(entry)

            output.append("$prefix${entry.date} ${entry::class.simpleName}")
            if (entry is Transaction) {
                entry.narration?.let { output.append(": $it") }
            }
            output.append("\n")

            if (i == targetIndex) {
                output.append("\n")
                entryStr.lines().forEach { line ->
                    output.append("    $line\n")
                }
                output.append("\n")
            }
        }

        return output.toString()
    }

    /**
     * Find accounts that are used but never opened.
     *
     * Scans all entries for account references and checks if each
     * account has a corresponding Open directive.
     *
     * @param entries Parsed entries from the ledger
     * @return List of Open directives that should be added
     */
    fun missingOpen(entries: List<Directive>): List<Open> {
        // Collect all accounts that are used
        val usedAccounts = mutableMapOf<String, LocalDate>()
        val openAccounts = mutableSetOf<String>()

        for (entry in entries) {
            when (entry) {
                is Open -> {
                    openAccounts.add(entry.account)
                }
                is Transaction -> {
                    entry.postings.forEach { posting ->
                        val account = posting.account
                        if (account !in usedAccounts) {
                            usedAccounts[account] = entry.date
                        }
                    }
                }
                is Balance -> {
                    val account = entry.account
                    if (account !in usedAccounts) {
                        usedAccounts[account] = entry.date
                    }
                }
                is Pad -> {
                    val account = entry.account
                    if (account !in usedAccounts) {
                        usedAccounts[account] = entry.date
                    }
                    val sourceAccount = entry.sourceAccount
                    if (sourceAccount !in usedAccounts) {
                        usedAccounts[sourceAccount] = entry.date
                    }
                }
                is Close -> {
                    val account = entry.account
                    if (account !in usedAccounts) {
                        usedAccounts[account] = entry.date
                    }
                }
                is Note -> {
                    val account = entry.account
                    if (account !in usedAccounts) {
                        usedAccounts[account] = entry.date
                    }
                }
                is Document -> {
                    val account = entry.account
                    if (account !in usedAccounts) {
                        usedAccounts[account] = entry.date
                    }
                }
                else -> {} // Other directives don't reference accounts
            }
        }

        // Find accounts that are used but not opened
        val missing = mutableListOf<Open>()
        for ((account, firstUseDate) in usedAccounts) {
            if (account !in openAccounts) {
                missing.add(
                    Open(
                        meta = emptyMap(),
                        date = firstUseDate,
                        account = account,
                        currencies = emptyList()
                    )
                )
            }
        }

        return missing.sortedBy { it.date }
    }

    /**
     * Display formatting context inferred from parsed numbers.
     *
     * Analyzes all amounts in the ledger and displays the precision
     * and formatting information.
     *
     * @param entries Parsed entries from the ledger
     * @return Formatted display context string
     */
    fun displayContext(entries: List<Directive>): String {
        val dcontext = buildDisplayContext(entries)
        return dcontext.toString()
    }

    /**
     * Build a DisplayContext from ledger entries.
     */
    fun buildDisplayContext(entries: List<Directive>): DisplayContext {
        val dcontext = DisplayContext()

        // Collect all amounts
        for (entry in entries) {
            when (entry) {
                is Transaction -> {
                    entry.postings.forEach { posting ->
                        posting.units?.let { amount ->
                            dcontext.update(amount.number, amount.currency)
                        }
                        posting.price?.let { price ->
                            dcontext.update(price.number, price.currency)
                        }
                        posting.cost?.numberPer?.let { costNumber ->
                            dcontext.update(costNumber, posting.cost?.currency ?: "")
                        }
                    }
                }
                is Balance -> {
                    dcontext.update(entry.amount.number, entry.amount.currency)
                }
                is Price -> {
                    dcontext.update(entry.amount.number, entry.amount.currency)
                }
                else -> {}
            }
        }

        return dcontext
    }

    /**
     * Perform a roundtrip test on a ledger file.
     *
     * Parses the file and re-formats it, returning both the original
     * entries and the formatted output for comparison.
     *
     * @param entries Parsed entries from the ledger
     * @return Roundtrip result with original and formatted output
     */
    fun roundtrip(entries: List<Directive>): RoundtripResult {
        val formatted = formatEntries(entries)
        return RoundtripResult(
            entryCount = entries.size,
            formattedOutput = formatted
        )
    }

    /**
     * List all options from the ledger.
     *
     * @param options Parsed options from the ledger
     * @return Formatted options string
     */
    fun listOptions(options: Options): String {
        val output = StringBuilder()
        output.append("Options\n")
        output.append("=".repeat(60))
        output.append("\n\n")

        output.append("  filename: ${options.filename}\n")
        output.append("  title: ${options.title}\n")
        output.append("  operating_currencies: ${options.operatingCurrencies.joinToString(", ")}\n")
        output.append("  plugin_processing_mode: ${options.pluginProcessingMode}\n")

        if (options.plugin.isNotEmpty()) {
            output.append("\n  plugins:\n")
            options.plugin.forEach { spec ->
                output.append("    - ${spec.moduleName}\n")
                spec.config?.let { config ->
                    output.append("      config: $config\n")
                }
            }
        }

        return output.toString()
    }

    /**
     * List all available beancount option names (without values).
     *
     * @return Formatted list of all option names
     */
    fun listAllOptions(): String {
        val allOptions = listOf(
            "account_previous_balances",
            "account_previous_earnings",
            "account_previous_earnings_statement",
            "account_rounding",
            "commodity_previous_balances",
            "commodity_previous_earnings",
            "commodity_previous_earnings_statement",
            "commodity_rounding",
            "conversion_currency",
            "default_tolerance",
            "documents",
            "infer_tolerance_from_cost",
            "inferred_tolerance_default",
            "inferred_tolerance_multiplier",
            "long_string_maxlines",
            "name_assets",
            "name_equity",
            "name_expenses",
            "name_income",
            "name_liabilities",
            "operating_currency",
            "plugin",
            "plugin_processing_mode",
            "pop_tag",
            "popmeta",
            "pushtag",
            "pushmeta",
            "render_commas",
            "title",
            "tubly_positions"
        )

        val output = StringBuilder()
        output.append("Available Options\n")
        output.append("=".repeat(60))
        output.append("\n\n")

        allOptions.forEach { option ->
            output.append("  $option\n")
        }

        output.append("\nTotal: ${allOptions.size} options\n")
        return output.toString()
    }

}

/**
 * Result of a roundtrip test.
 */
data class RoundtripResult(
    val entryCount: Int,
    val formattedOutput: String
)
