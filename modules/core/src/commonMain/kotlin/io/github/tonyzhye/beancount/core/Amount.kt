package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * Amount represents a number with a currency.
 * Based on beancount.core.amount.Amount
 */
data class Amount(
    val number: Decimal,
    val currency: Currency
) {
    override fun toString(): String = "$number $currency"
}

/**
 * Cost represents the cost basis of a position.
 * Based on beancount.core.position.Cost
 */
data class Cost(
    val number: Decimal? = null,
    val currency: Currency,
    val date: LocalDate? = null,
    val label: String? = null
)

/**
 * Posting represents a single leg of a transaction.
 * Based on beancount.core.data.Posting
 */
data class Posting(
    val account: Account,
    val units: Amount? = null,
    val cost: Cost? = null,
    val price: Amount? = null,
    val flag: Flag? = null,
    val meta: Meta? = null
)

/**
 * TxnPosting is a pair of Transaction and Posting,
 * used in realization.
 */
data class TxnPosting(
    val txn: Transaction,
    val posting: Posting
)
