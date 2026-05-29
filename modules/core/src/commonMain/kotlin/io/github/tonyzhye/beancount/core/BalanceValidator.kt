package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * Error raised when a balance assertion fails.
 * Based on beancount.core.interpolate.BalanceError.
 */
data class BalanceError(
    override val source: Meta,
    override val message: String,
    override val entry: Directive? = null
) : BeancountError

/**
 * Validate balance assertions against computed account balances.
 *
 * For each Balance directive, check that the expected balance corresponds to
 * the actual balance computed at that point in time. Returns updated entries
 * (with diff_amount set on failing balances) and a list of errors.
 *
 * Based on beancount.ops.balance.check.
 *
 * @param entries List of directives to process
 * @param options Parsed options from the ledger
 * @return Pair of (updated entries, balance errors)
 */
fun validateBalances(
    entries: List<Directive>,
    options: Options
): Pair<List<Directive>, List<BalanceError>> {
    val newEntries = mutableListOf<Directive>()
    val errors = mutableListOf<BalanceError>()

    // Build realization tree to track running balances
    val realRoot = RealAccount("")

    // Find all accounts with balance assertions
    val assertedAccounts = entries.filterIsInstance<Balance>().map { it.account }.toSet()

    // Pre-create accounts that need tracking (asserted accounts + their children)
    val allAccounts = getAllAccounts(entries)
    for (account in allAccounts) {
        if (account in assertedAccounts || isChildOfAny(account, assertedAccounts)) {
            createAccountPath(realRoot, account)
        }
    }

    // Get open directives for currency validation
    val openMap = entries.filterIsInstance<Open>().associateBy { it.account }

    for (entry in entries) {
        when (entry) {
            is Transaction -> {
                // Update balances for tracked accounts
                for (posting in entry.postings) {
                    val realAccount = findAccount(realRoot, posting.account)
                    if (realAccount != null && posting.units != null) {
                        realAccount.balance.addAmount(
                            posting.units,
                            posting.cost?.let { costSpec ->
                                if (costSpec.currency != null) {
                                    Cost(
                                        costSpec.numberPer ?: Decimal.ZERO,
                                        costSpec.currency,
                                        entry.date,
                                        costSpec.label
                                    )
                                } else null
                            }
                        )
                    }
                }
                newEntries.add(entry)
            }

            is Balance -> {
                val expectedAmount = entry.amount

                // Validate account exists
                val openEntry = openMap[entry.account]
                if (openEntry == null) {
                    errors.add(BalanceError(
                        entry.meta,
                        "Invalid reference to unknown account '${entry.account}'",
                        entry
                    ))
                    newEntries.add(entry)
                    continue
                }

                // Validate currency is allowed
                if (openEntry.currencies.isNotEmpty() &&
                    expectedAmount.currency !in openEntry.currencies) {
                    errors.add(BalanceError(
                        entry.meta,
                        "Invalid currency '${expectedAmount.currency}' for Balance directive",
                        entry
                    ))
                    newEntries.add(entry)
                    continue
                }

                // Get subtree balance (account + subaccounts)
                val realAccount = findAccount(realRoot, entry.account)
                if (realAccount == null) {
                    errors.add(BalanceError(
                        entry.meta,
                        "Missing account '${entry.account}' in realization",
                        entry
                    ))
                    newEntries.add(entry)
                    continue
                }

                val subtreeBalance = computeBalance(realAccount, leafOnly = false)
                val balanceAmount = subtreeBalance.getCurrencyUnits(expectedAmount.currency)

                // Compute difference
                val diffAmount = Amount(balanceAmount.number - expectedAmount.number, expectedAmount.currency)

                // Determine tolerance
                val tolerance = computeTolerance(entry, options)

                // Check if within tolerance
                if (diffAmount.number.abs() > tolerance) {
                    val diffAbs = diffAmount.number.abs()
                    val diffSign = if (diffAmount.number.isPositive()) "too much" else "too little"

                    errors.add(BalanceError(
                        entry.meta,
                        "Balance failed for '${entry.account}': expected ${expectedAmount.number.toPlainString()} ${expectedAmount.currency} != accumulated ${balanceAmount.number.toPlainString()} ${balanceAmount.currency} (${diffAbs.toPlainString()} $diffSign)",
                        entry
                    ))

                    // Update entry with diff_amount
                    newEntries.add(entry.copy(diffAmount = diffAmount))
                } else {
                    newEntries.add(entry)
                }
            }

            else -> {
                newEntries.add(entry)
            }
        }
    }

    return newEntries to errors
}

/**
 * Compute tolerance for a balance assertion.
 *
 * If the balance entry has an explicit tolerance, use it.
 * Otherwise, infer from the amount's decimal precision.
 *
 * Based on beancount.ops.balance.get_balance_tolerance.
 */
private fun computeTolerance(balanceEntry: Balance, options: Options): Decimal {
    if (balanceEntry.tolerance != null) {
        return balanceEntry.tolerance
    }

    // Infer tolerance from amount precision.
    // Python logic: tolerance = tolerance_multiplier * 2 * 10^exponent
    // Default tolerance_multiplier is 0.5, so: 1.0 * 10^exponent
    val plainStr = balanceEntry.amount.number.toPlainString()
    val dotIndex = plainStr.indexOf('.')

    return if (dotIndex >= 0) {
        val fractionalDigits = plainStr.length - dotIndex - 1
        // 10^(-fractionalDigits) gives the place value of the last digit
        Decimal.ONE.scaleByPowerOfTen(-fractionalDigits)
    } else {
        Decimal.ZERO
    }
}

/**
 * Get all unique accounts referenced in entries.
 */
private fun getAllAccounts(entries: List<Directive>): Set<String> {
    val accounts = mutableSetOf<String>()
    for (entry in entries) {
        when (entry) {
            is Transaction -> entry.postings.forEach { accounts.add(it.account) }
            is Open -> accounts.add(entry.account)
            is Close -> accounts.add(entry.account)
            is Balance -> accounts.add(entry.account)
            is Note -> accounts.add(entry.account)
            is Document -> accounts.add(entry.account)
            is Pad -> {
                accounts.add(entry.account)
                accounts.add(entry.sourceAccount)
            }
            else -> {}
        }
    }
    return accounts
}

/**
 * Check if an account is a child of any of the parent accounts.
 */
private fun isChildOfAny(account: String, parents: Set<String>): Boolean {
    return parents.any { parent ->
        account != parent && account.startsWith("$parent:")
    }
}

/**
 * Create account path in realization tree.
 */
private fun createAccountPath(root: RealAccount, account: String) {
    val parts = account.split(":")
    var current = root
    for (part in parts) {
        current = current.getOrCreate(part)
    }
}

/**
 * Find an account in the realization tree.
 */
private fun findAccount(root: RealAccount, account: String): RealAccount? {
    val parts = account.split(":")
    var current = root
    for (part in parts) {
        current = current[part] ?: return null
    }
    return current
}
