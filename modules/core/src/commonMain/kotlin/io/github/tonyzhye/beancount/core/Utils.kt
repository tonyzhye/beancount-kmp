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
 * Returns null if required fields cannot be resolved.
 *
 * Handles number_total composite calculation:
 *   cost_total = number_total + (number_per * units_number)
 *   unit_cost = cost_total / units_number
 */
fun CostSpec.toCost(defaultDate: LocalDate, unitsNumber: Decimal? = null): Cost? {
    val cur = currency ?: return null

    val unitCost = when {
        numberTotal != null && unitsNumber != null && !unitsNumber.isZero() -> {
            var costTotal = numberTotal
            if (numberPer != null) {
                costTotal += numberPer * unitsNumber.abs()
            }
            costTotal / unitsNumber.abs()
        }
        numberPer != null -> numberPer
        else -> Decimal.ZERO
    }

    return Cost(unitCost, cur, date ?: defaultDate, label)
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

/**
 * Check if an account has a specific component.
 * Based on beancount.core.data.has_component.
 *
 * @param account An account name string.
 * @param component A component to check for (e.g., "Food" in "Expenses:Food:Restaurant").
 * @return True if the account contains the component as a whole segment.
 */
fun hasComponent(account: Account, component: String): Boolean {
    return account.split(":").contains(component)
}

/**
 * Return true if one of the entry's postings has an account component.
 * Based on beancount.core.data.has_entry_account_component.
 *
 * @param entry A directive instance (typically Transaction).
 * @param component A component of an account name.
 * @return Boolean: true if the component is in one of the posting accounts.
 */
fun hasEntryAccountComponent(entry: Directive, component: String): Boolean {
    return entry is Transaction && entry.postings.any { hasComponent(it.account, component) }
}
