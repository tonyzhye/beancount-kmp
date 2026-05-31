package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate

/**
 * Pad plugin - automatically generates transactions to make balance assertions succeed.
 * Based on beancount.ops.pad.
 *
 * This is a complete implementation that:
 * 1. Groups pad entries by account
 * 2. Processes each account's postings (including child accounts)
 * 3. Tracks running balance and detects when padding is needed
 * 4. Generates padding transactions at Balance assertion points
 * 5. Reports errors for unused pad entries and cost position padding attempts
 */
object PadPlugin {

    /**
     * Plugin entry point.
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val padErrors = mutableListOf<BeancountError>()

        // Find all pad entries and group them by account
        val pads = entries.filterIsInstance<Pad>()
        val padDict = pads.groupBy { it.account }

        // Build postings by account map (including child accounts)
        val byAccount = buildPostingsByAccount(entries)

        // Map of pad -> list of entries to be inserted
        val newEntries = mutableMapOf<Pad, MutableList<Transaction>>()
        pads.forEach { newEntries[it] = mutableListOf() }

        // Track which pads were used (even if no transaction was generated)
        val usedPads = mutableSetOf<Pad>()

        // Process each account that has a padding group
        for ((account, padList) in padDict.toList().sortedBy { it.first }) {
            processAccountPadding(
                account, padList, byAccount, entries,
                newEntries, usedPads, padErrors, options
            )
        }

        // Insert newly created entries right after the pad entries that created them
        val paddedEntries = mutableListOf<Directive>()
        for (entry in entries) {
            paddedEntries.add(entry)
            if (entry is Pad) {
                val entryList = newEntries[entry]
                if (entryList != null && entryList.isNotEmpty()) {
                    paddedEntries.addAll(entryList)
                } else if (entry !in usedPads) {
                    // Generate errors on unused pad entries
                    padErrors.add(
                        LoadError(entry.meta, "Unused Pad entry", entry)
                    )
                }
            }
        }

        return paddedEntries to padErrors
    }

    /**
     * Process padding for a single account.
     */
    private fun processAccountPadding(
        account: Account,
        padList: List<Pad>,
        byAccount: Map<Account, List<Any>>,
        allEntries: List<Directive>,
        newEntries: MutableMap<Pad, MutableList<Transaction>>,
        usedPads: MutableSet<Pad>,
        padErrors: MutableList<BeancountError>,
        options: Options
    ) {
        // Gather all postings for the account and its children
        val postings = mutableListOf<Any>()
        val isChild = parentMatcher(account)

        for ((itemAccount, itemPostings) in byAccount) {
            if (isChild(itemAccount)) {
                postings.addAll(itemPostings)
            }
        }

        // Sort by date and line number
        postings.sortWith(postingComparator)

        // Active pad entry and padded lots tracking
        var activePad: Pad? = null
        val paddedLots = mutableSetOf<Currency>()

        val padBalance = Inventory()

        for (entry in postings) {
            when (entry) {
                is TxnPosting -> {
                    // Update running balance
                    val units = entry.posting.units
                    if (units == null) continue
                    val cost = entry.posting.cost?.let { spec ->
                        val numberPer = spec.numberPer
                        val currency = spec.currency
                        if (numberPer != null && currency != null) {
                            Cost(numberPer, currency, spec.date ?: entry.txn.date, spec.label)
                        } else null
                    }
                    padBalance.addAmount(units, cost)
                }
                is Pad -> {
                    if (entry.account == account) {
                        activePad = entry
                        paddedLots.clear()
                    }
                }
                is Balance -> {
                    processBalanceCheck(
                        entry, account, activePad, padBalance,
                        paddedLots, newEntries, usedPads, padErrors, options
                    )
                }
            }
        }
    }

    /**
     * Process a Balance check and generate padding if needed.
     */
    private fun processBalanceCheck(
        balanceEntry: Balance,
        account: Account,
        activePad: Pad?,
        padBalance: Inventory,
        paddedLots: MutableSet<Currency>,
        newEntries: MutableMap<Pad, MutableList<Transaction>>,
        usedPads: MutableSet<Pad>,
        padErrors: MutableList<BeancountError>,
        options: Options
    ) {
        val checkAmount = balanceEntry.amount

        // Mark active pad as used if it exists (even if no padding is needed)
        if (activePad != null) {
            usedPads.add(activePad)
        }

        // Get current balance for this currency
        val balanceAmount = padBalance.getCurrencyUnits(checkAmount.currency)
        val diffNumber = balanceAmount.number - checkAmount.number

        // Use tolerance
        val tolerance = getBalanceTolerance(balanceEntry, options)

        if (diffNumber.abs() > tolerance) {
            // Need to pad
            if (activePad != null && checkAmount.currency !in paddedLots) {
                // Check for positions at cost
                val positions = padBalance.getPositions(checkAmount.currency)
                for (position in positions) {
                    if (position.cost != null) {
                        padErrors.add(
                            LoadError(
                                balanceEntry.meta,
                                "Attempt to pad an entry with cost for balance: $padBalance",
                                activePad
                            )
                        )
                        return
                    }
                }

                // Create padding transaction
                val diffAmount = Amount(checkAmount.number - balanceAmount.number, checkAmount.currency)
                val narration = "(Padding inserted for Balance of $checkAmount for difference $diffAmount)"

                val padTransaction = Transaction(
                    meta = activePad.meta,
                    date = activePad.date,
                    flag = Flags.FLAG_PADDING,
                    payee = null,
                    narration = narration,
                    tags = emptySet(),
                    links = emptySet(),
                    postings = listOf(
                        Posting(
                            account = activePad.account,
                            units = diffAmount,
                            cost = null,
                            price = null,
                            flag = null,
                            meta = balanceEntry.meta
                        ),
                        Posting(
                            account = activePad.sourceAccount,
                            units = Amount(-diffAmount.number, diffAmount.currency),
                            cost = null,
                            price = null,
                            flag = null,
                            meta = balanceEntry.meta
                        )
                    )
                )

                // Save for later insertion
                newEntries.getOrPut(activePad) { mutableListOf() }.add(padTransaction)

                // Fixup running balance
                padBalance.addAmount(diffAmount)
            }
        }

        // Mark this lot as padded
        paddedLots.add(checkAmount.currency)
    }

    /**
     * Build a predicate that returns whether an account is under the given one.
     */
    private fun parentMatcher(accountName: Account): (Account) -> Boolean {
        val pattern = Regex("${Regex.escape(accountName)}($|\\:.+)")
        return { s: Account -> pattern.matches(s) }
    }

    /**
     * Build postings by account map.
     * Similar to realization.postings_by_account in Python.
     */
    private fun buildPostingsByAccount(entries: List<Directive>): Map<Account, List<Any>> {
        val postingsMap = mutableMapOf<Account, MutableList<Any>>()

        for (entry in entries.sorted()) {
            when (entry) {
                is Transaction -> {
                    entry.postings.forEach { posting ->
                        postingsMap.getOrPut(posting.account) { mutableListOf() }
                            .add(TxnPosting(entry, posting))
                    }
                }
                is Pad -> {
                    postingsMap.getOrPut(entry.account) { mutableListOf() }.add(entry)
                }
                is Balance -> {
                    postingsMap.getOrPut(entry.account) { mutableListOf() }.add(entry)
                }
                else -> {}
            }
        }

        return postingsMap
    }

    /**
     * Comparator for sorting postings by date and line number.
     */
    private val postingComparator = Comparator<Any> { a, b ->
        val dateA = when (a) {
            is TxnPosting -> a.txn.date
            is Directive -> a.date
            else -> LocalDate(1970, 1, 1)
        }
        val dateB = when (b) {
            is TxnPosting -> b.txn.date
            is Directive -> b.date
            else -> LocalDate(1970, 1, 1)
        }

        val dateCompare = dateA.compareTo(dateB)
        if (dateCompare != 0) return@Comparator dateCompare

        val lineA: Int = when (a) {
            is TxnPosting -> a.txn.meta["lineno"] as? Int ?: 0
            is Directive -> a.meta["lineno"] as? Int ?: 0
            else -> 0
        }
        val lineB: Int = when (b) {
            is TxnPosting -> b.txn.meta["lineno"] as? Int ?: 0
            is Directive -> b.meta["lineno"] as? Int ?: 0
            else -> 0
        }

        lineA.compareTo(lineB)
    }

    /**
     * Compute tolerance for a balance assertion.
     */
    private fun getBalanceTolerance(balanceEntry: Balance, options: Options): Decimal {
        val tolerance = balanceEntry.tolerance
        if (tolerance != null) {
            return tolerance
        }

        // Infer from amount precision
        val plainStr = balanceEntry.amount.number.toPlainString()
        val dotIndex = plainStr.indexOf('.')

        return if (dotIndex >= 0) {
            val fractionalDigits = plainStr.length - dotIndex - 1
            Decimal.ONE.scaleByPowerOfTen(-fractionalDigits)
        } else {
            Decimal.ZERO
        }
    }
}
