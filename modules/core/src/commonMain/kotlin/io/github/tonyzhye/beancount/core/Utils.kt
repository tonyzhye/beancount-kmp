package io.github.tonyzhye.beancount.core

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
