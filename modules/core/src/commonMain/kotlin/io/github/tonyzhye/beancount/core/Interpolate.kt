package io.github.tonyzhye.beancount.core

/**
 * Interpolate missing postings and compute transaction residuals.
 *
 * Based on beancount.core.interpolate
 */

/** Upper bound on tolerance value. */
val MAXIMUM_TOLERANCE = Decimal("0.5")

/** Maximum number of user-specified tolerance digits. */
const val MAX_TOLERANCE_DIGITS = 5

/** Meta field for automatically inserted postings. */
const val AUTOMATIC_META = "__automatic__"

/** Meta field for residual postings. */
const val AUTOMATIC_RESIDUAL = "__residual__"

/** Meta field for inferred tolerances. */
const val AUTOMATIC_TOLERANCES = "__tolerances__"

/**
 * Check if tolerance was user-specified.
 */
fun isToleranceUserSpecified(tolerance: Decimal): Boolean {
    return tolerance.toPlainString().replace("-", "").replace(".", "").length <= MAX_TOLERANCE_DIGITS
}

/**
 * Check if a posting has non-trivial balance (cost or price).
 */
fun hasNontrivialBalance(posting: Posting): Boolean {
    return posting.cost != null || posting.price != null
}

/**
 * Compute the residual of a set of postings.
 *
 * Returns an Inventory with the residual. Used to cross-check balanced transactions.
 */
fun computeResidual(postings: List<Posting>): Inventory {
    val inventory = Inventory()
    for (posting in postings) {
        // Skip auto-postings inserted to absorb residual
        if (posting.meta?.get(AUTOMATIC_RESIDUAL) == true) continue
        // Add to total residual balance
        val weight = getWeight(posting)
        inventory.addAmount(weight)
    }
    return inventory
}

/**
 * Infer tolerances from a list of postings.
 *
 * @param postings List of postings
 * @param toleranceMultiplier Default tolerance multiplier (0.5)
 * @param useCost Whether to infer tolerance from cost/price
 * @param mode "max" or "min" for aggregation
 * @return Map of currency to tolerance Decimal
 */
fun inferTolerances(
    postings: List<Posting>,
    toleranceMultiplier: Decimal = Decimal("0.5"),
    useCost: Boolean = false,
    mode: String = "max"
): Map<Currency, Decimal> {
    require(mode in setOf("max", "min")) { "mode must be 'max' or 'min'" }
    val agg: (Decimal, Decimal) -> Decimal = if (mode == "max") ::maxOf else ::minOf

    val seenCurrencies = mutableSetOf<Currency>()
    for (posting in postings) {
        posting.units?.let { seenCurrencies.add(it.currency) }
        posting.cost?.currency?.let { seenCurrencies.add(it) }
        posting.price?.currency?.let { seenCurrencies.add(it) }
    }

    val tolerances = mutableMapOf<Currency, Decimal>()
    val costTolerances = mutableMapOf<Currency, Decimal>()

    for (posting in postings) {
        // Skip automatically inferred postings
        if (posting.meta?.containsKey(AUTOMATIC_META) == true) continue

        val units = posting.units ?: continue
        val number = units.number
        val plainStr = number.toPlainString()
        val dotIndex = plainStr.indexOf('.')

        // Compute tolerance from number precision
        val tolerance = if (dotIndex >= 0) {
            val fractionalDigits = plainStr.length - dotIndex - 1
            Decimal.ONE.scaleByPowerOfTen(-fractionalDigits) * toleranceMultiplier
        } else {
            null
        }

        if (tolerance != null) {
            val currency = units.currency
            tolerances[currency] = if (currency in tolerances) agg(tolerance, tolerances.getValue(currency)) else tolerance
        }

        if (!useCost) continue

        // Compute cost tolerance
        val cost = posting.cost
        if (cost != null && cost.numberPer != null && tolerance != null) {
            val costCurrency = cost.currency ?: continue
            val costTolerance = minOf(tolerance * cost.numberPer, MAXIMUM_TOLERANCE)
            costTolerances[costCurrency] = (costTolerances[costCurrency] ?: Decimal.ZERO) + costTolerance
        }

        // Compute price tolerance
        val price = posting.price
        if (price != null && tolerance != null) {
            val priceCurrency = price.currency
            val priceTolerance = minOf(tolerance * price.number, MAXIMUM_TOLERANCE)
            costTolerances[priceCurrency] = (costTolerances[priceCurrency] ?: Decimal.ZERO) + priceTolerance
        }
    }

    // Merge cost tolerances
    for ((currency, tolerance) in costTolerances) {
        tolerances[currency] = if (currency in tolerances) agg(tolerance, tolerances.getValue(currency)) else tolerance
    }

    return tolerances
}

/**
 * Create postings to book the given residuals.
 *
 * @param residual Residual inventory
 * @param accountRounding Rounding account name
 * @return List of residual postings
 */
fun getResidualPostings(residual: Inventory, accountRounding: Account): List<Posting> {
    val meta = mutableMapOf<String, Any>(AUTOMATIC_META to true, AUTOMATIC_RESIDUAL to true)
    return residual.map { position ->
        Posting(
            account = accountRounding,
            units = Amount(-position.units.number, position.units.currency),
            cost = null,
            price = null,
            flag = null,
            meta = meta
        )
    }
}

/**
 * Insert a posting to absorb residual if necessary.
 *
 * @param entry Transaction to modify
 * @param accountRounding Rounding account name
 * @return Modified transaction (or original if no residual)
 */
fun fillResidualPosting(entry: Transaction, accountRounding: Account): Transaction {
    val residual = computeResidual(entry.postings)
    return if (!residual.isEmpty()) {
        val newPostings = entry.postings + getResidualPostings(residual, accountRounding)
        entry.copy(postings = newPostings)
    } else {
        entry
    }
}

/**
 * Compute the balance of all postings in a list of entries.
 *
 * @param entries List of directives
 * @param prefix Optional account prefix to filter by
 * @param date Optional exclusive date cutoff
 * @return Total balance inventory
 */
fun computeEntriesBalance(
    entries: List<Directive>,
    prefix: Account? = null,
    date: kotlinx.datetime.LocalDate? = null
): Inventory {
    val totalBalance = Inventory()
    for (entry in entries) {
        if (date != null && entry.date >= date) break
        if (entry is Transaction) {
            for (posting in entry.postings) {
                if (prefix == null || posting.account.startsWith(prefix)) {
                    posting.units?.let { totalBalance.addAmount(it) }
                }
            }
        }
    }
    return totalBalance
}

/**
 * Compute the context (before and after balances) for an entry.
 *
 * @param entries All entries
 * @param contextEntry The entry to get context for
 * @param additionalAccounts Additional accounts to include
 * @return Pair of (before balances, after balances) maps
 */
fun computeEntryContext(
    entries: List<Directive>,
    contextEntry: Directive,
    additionalAccounts: Set<Account>? = null
): Pair<Map<Account, Inventory>, Map<Account, Inventory>> {
    val contextAccounts = getEntryAccounts(contextEntry).toMutableSet()
    additionalAccounts?.let { contextAccounts.addAll(it) }

    // Compute before context
    val contextBefore = mutableMapOf<Account, Inventory>()
    for (entry in entries) {
        if (entry === contextEntry) break
        if (entry is Transaction) {
            for (posting in entry.postings) {
                if (posting.account in contextAccounts) {
                    val balance = contextBefore.getOrPut(posting.account) { Inventory() }
                    posting.units?.let { balance.addAmount(it) }
                }
            }
        }
    }

    // Compute after context
    val contextAfter = contextBefore.mapValues { it.value.copy() }.toMutableMap()
    if (contextEntry is Transaction) {
        for (posting in contextEntry.postings) {
            val balance = contextAfter.getOrPut(posting.account) { Inventory() }
            posting.units?.let { balance.addAmount(it) }
        }
    }

    return contextBefore to contextAfter
}

/**
 * Quantize a number using tolerance.
 *
 * @param tolerances Map of currency to tolerance
 * @param currency Currency to look up
 * @param number Number to quantize
 * @return Quantized number (or original if no tolerance)
 */
fun quantizeWithTolerance(
    tolerances: Map<Currency, Decimal>,
    currency: Currency,
    number: Decimal
): Decimal {
    val tolerance = tolerances[currency] ?: return number
    val quantum = tolerance * Decimal("2")
    return if (isToleranceUserSpecified(quantum)) {
        // Quantize to the quantum precision
        val plainStr = quantum.toPlainString()
        val dotIndex = plainStr.indexOf('.')
        val scale = if (dotIndex >= 0) plainStr.length - dotIndex - 1 else 0
        if (scale > 0) {
            number.scaleByPowerOfTen(-scale)
        } else {
            number
        }
    } else {
        number
    }
}
