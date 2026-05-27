package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*

/**
 * Balance plugin - verifies balance assertions.
 * Based on beancount.ops.balance.
 *
 * For each Balance directive, this plugin checks if the actual account balance
 * at that date matches the asserted balance (within optional tolerance).
 *
 * The balance is computed by accumulating all postings from transactions
 * that occur before the balance assertion date.
 */
object BalancePlugin {

    /**
     * Plugin entry point.
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()

        // Build a map of account balances at each balance assertion date.
        // We process entries in chronological order, accumulating balances.
        // When we hit a Balance directive, we check the current accumulated balance.
        
        val accountBalances = mutableMapOf<Pair<Account, Currency>, Decimal>()
        val checkedBalances = mutableSetOf<Balance>()

        for (entry in entries) {
            when (entry) {
                is Transaction -> {
                    // Accumulate transaction postings into balances
                    for (posting in entry.postings) {
                        val units = posting.units ?: continue
                        val key = Pair(posting.account, units.currency)
                        val current = accountBalances.getOrDefault(key, Decimal.ZERO)
                        accountBalances[key] = current + units.number
                    }
                }
                is Balance -> {
                    // Check balance assertion against accumulated balance
                    if (entry in checkedBalances) continue
                    checkedBalances.add(entry)
                    
                    val key = Pair(entry.account, entry.amount.currency)
                    val actualBalance = accountBalances.getOrDefault(key, Decimal.ZERO)
                    val expectedBalance = entry.amount.number
                    val tolerance = entry.tolerance ?: getDefaultTolerance(options, entry.amount.currency)

                    val diff = (actualBalance - expectedBalance).abs()

                    if (diff > tolerance) {
                        errors.add(
                            ValidationError(
                                entry.meta,
                                "Balance failed for '${entry.account}': expected ${expectedBalance.toPlainString()} " +
                                "${entry.amount.currency} but got ${actualBalance.toPlainString()} " +
                                "${entry.amount.currency} (diff: ${diff.toPlainString()})",
                                entry
                            )
                        )
                    }
                }
                else -> {}
            }
        }

        return Pair(entries, errors)
    }

    /**
     * Get default tolerance for a currency from options.
     */
    private fun getDefaultTolerance(options: Options, currency: Currency): Decimal {
        return options.toleranceMap[currency] ?: Decimal("0.005")
    }
}
