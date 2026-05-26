package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*

/**
 * Balance plugin - verifies balance assertions.
 * Based on beancount.ops.balance.
 *
 * For each Balance directive, this plugin checks if the actual account balance
 * matches the asserted balance (within optional tolerance).
 *
 * This is a simplified implementation that validates balance assertions
 * against accumulated transaction postings.
 */
object BalancePlugin {

    /**
     * Plugin entry point.
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val balanceAssertions = entries.filterIsInstance<Balance>()

        // Pre-compute account balances from all transactions
        val accountBalances = mutableMapOf<Pair<Account, Currency>, Decimal>()

        for (entry in entries) {
            if (entry is Transaction) {
                for (posting in entry.postings) {
                    val units = posting.units ?: continue
                    val key = Pair(posting.account, units.currency)
                    val current = accountBalances.getOrDefault(key, Decimal.ZERO)
                    accountBalances[key] = current + units.number
                }
            }
        }

        // Check each balance assertion
        for (balance in balanceAssertions) {
            val key = Pair(balance.account, balance.amount.currency)
            val actualBalance = accountBalances.getOrDefault(key, Decimal.ZERO)
            val expectedBalance = balance.amount.number
            val tolerance = balance.tolerance ?: getDefaultTolerance(options, balance.amount.currency)

            val diff = (actualBalance - expectedBalance).abs()

            if (diff > tolerance) {
                errors.add(
                    ValidationError(
                        balance.meta,
                        "Balance failed for '${balance.account}': expected ${expectedBalance.toPlainString()} " +
                        "${balance.amount.currency} but got ${actualBalance.toPlainString()} " +
                        "${balance.amount.currency} (diff: ${diff.toPlainString()})",
                        balance
                    )
                )
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
