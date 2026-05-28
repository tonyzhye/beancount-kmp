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
                    val tolerance = getBalanceTolerance(entry, options)

                    val diff = actualBalance - expectedBalance
                    val absDiff = diff.abs()

                    if (absDiff > tolerance) {
                        val diffStr = diff.toPlainString()
                        val tooMuchOrLittle = if (diff.isPositive()) "too much" else "too little"
                        errors.add(
                            ValidationError(
                                entry.meta,
                                "Balance failed for '${entry.account}': expected ${expectedBalance.toPlainString()} " +
                                "${entry.amount.currency} != accumulated ${actualBalance.toPlainString()} " +
                                "(${absDiff.toPlainString()} $tooMuchOrLittle)",
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
     * Get tolerance for a balance entry.
     * If the entry has a specific tolerance, use it.
     * Otherwise, calculate from the amount's decimal places.
     * Based on beancount.ops.balance.get_balance_tolerance.
     */
    private fun getBalanceTolerance(balanceEntry: Balance, options: Options): Decimal {
        // Use balance-specific tolerance if provided
        val entryTolerance = balanceEntry.tolerance
        if (entryTolerance != null) {
            return entryTolerance
        }
        
        // Try to get tolerance from options map
        // Check specific currency first, then wildcard
        val currency = balanceEntry.amount.currency
        val toleranceMap = options.toleranceMap
        if (toleranceMap.isNotEmpty()) {
            toleranceMap[currency]?.let { return it }
            toleranceMap["*"]?.let { return it }
        }
        
        // Default tolerance: infer from amount precision
        // For amounts with decimal places, use 0.005 * 10^exponent
        // For integer amounts, use 0
        val numberStr = balanceEntry.amount.number.toPlainString()
        val decimalIndex = numberStr.indexOf('.')
        if (decimalIndex >= 0) {
            val decimalPlaces = numberStr.length - decimalIndex - 1
            val exponent = -decimalPlaces
            // Python: tolerance_multiplier (0.005) * 2 * 10^expo
            // Default multiplier is 0.005, doubled for balance checks
            return Decimal("0.01") * Decimal("1").let { 
                var result = it
                repeat(decimalPlaces) { result = result / Decimal("10") }
                result
            }
        }
        
        return Decimal.ZERO
    }
}
