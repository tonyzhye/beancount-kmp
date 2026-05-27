package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * Create metadata for a directive.
 * Based on beancount.core.data.new_metadata.
 */
fun newMetadata(filename: String, lineno: Int, kvlist: Meta? = null): Meta {
    val meta = mutableMapOf<String, Any>(
        "filename" to filename,
        "lineno" to lineno
    )
    kvlist?.let { meta.putAll(it) }
    return meta
}

/**
 * Create a simple posting on a transaction.
 * Based on beancount.core.data.create_simple_posting.
 */
fun createSimplePosting(
    entry: Transaction,
    account: Account,
    number: Decimal,
    currency: Currency
): Posting {
    val posting = Posting(
        account = account,
        units = Amount(number, currency)
    )
    return posting
}

/**
 * Check if a posting involves a conversion.
 * Based on beancount.core.data.posting_has_conversion.
 */
fun postingHasConversion(posting: Posting): Boolean {
    return posting.cost == null && posting.price != null
}

/**
 * Convert a CostSpec to a Cost using defaults for missing fields.
 * Returns null if required fields (numberPer, currency) are missing.
 */
fun CostSpec.toCost(defaultDate: LocalDate): Cost? {
    val num = numberPer ?: return null
    val cur = currency ?: return null
    return Cost(num, cur, date ?: defaultDate, label)
}

/**
 * Get the weight of a posting for balance checking.
 * Based on beancount.core.convert.get_weight.
 *
 * The weight is the amount used to balance a transaction:
 * - If posting has cost: weight = cost.number * units.number (in cost.currency)
 * - If posting has price: weight = price.number * units.number (in price.currency)
 * - Otherwise: weight = units
 */
fun getWeight(posting: Posting): Amount? {
    val units = posting.units ?: return null

    // If the posting has a cost, use that as the weight.
    val costSpec = posting.cost
    if (costSpec != null && costSpec.numberPer != null && costSpec.currency != null) {
        return Amount(costSpec.numberPer * units.number, costSpec.currency)
    }

    // Otherwise use the units.
    var weight = units

    // Unless there is a price available; use that if present.
    val price = posting.price
    if (price != null) {
        weight = Amount(price.number * units.number, price.currency)
    }

    return weight
}

/**
 * Check if a transaction has at least one conversion posting.
 * Based on beancount.core.data.transaction_has_conversion.
 */
fun transactionHasConversion(transaction: Transaction): Boolean {
    return transaction.postings.any { postingHasConversion(it) }
}

/**
 * Get the entry associated with a posting or entry.
 * Based on beancount.core.data.get_entry.
 */
fun getEntry(postingOrEntry: Any): Directive {
    return when (postingOrEntry) {
        is TxnPosting -> postingOrEntry.txn
        is Directive -> postingOrEntry
        else -> throw IllegalArgumentException("Expected Directive or TxnPosting")
    }
}

/**
 * Filter only Transaction instances from entries.
 * Based on beancount.core.data.filter_txns.
 */
fun filterTxns(entries: List<Directive>): List<Transaction> {
    return entries.filterIsInstance<Transaction>()
}
