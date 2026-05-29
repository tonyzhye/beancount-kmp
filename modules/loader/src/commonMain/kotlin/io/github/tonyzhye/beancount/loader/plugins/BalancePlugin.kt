package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*

/**
 * Balance plugin - verifies balance assertions against computed account balances.
 * Based on beancount.ops.balance.
 *
 * For each Balance directive, this plugin checks if the actual account balance
 * at that date matches the asserted balance (within optional tolerance).
 *
 * Features:
 * - Uses RealAccount tree to track running balances
 * - Supports subaccount balance aggregation (parent accounts include children)
 * - Validates account existence and currency constraints
 * - Sets diffAmount on failing balance entries
 * - Computes tolerance from amount precision if not explicitly specified
 */
object BalancePlugin {

    /**
     * Plugin entry point.
     * Validates all balance assertions and returns updated entries with diffs.
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val (newEntries, balanceErrors) = validateBalances(entries, options)
        return newEntries to balanceErrors
    }
}
