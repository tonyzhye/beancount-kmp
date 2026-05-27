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
    val number: Decimal,
    val currency: Currency,
    val date: LocalDate,
    val label: String? = null
)

/**
 * CostSpec is a stand-in for an "incomplete" Cost.
 * Any field may be left unspecified (null).
 * Based on beancount.core.position.CostSpec
 */
data class CostSpec(
    val numberPer: Decimal? = null,
    val numberTotal: Decimal? = null,
    val currency: Currency? = null,
    val date: LocalDate? = null,
    val label: String? = null,
    val mergeCost: Boolean = false
)

/**
 * Position is a pair of units and optional cost.
 * Used to track inventories.
 * Based on beancount.core.position.Position
 */
data class Position(
    val units: Amount,
    val cost: Cost? = null
) {
    init {
        require(units.number != null) { "Position units must have a non-null number" }
    }
}

/**
 * Posting represents a single leg of a transaction.
 * Based on beancount.core.data.Posting
 *
 * Note: cost is CostSpec during parsing/booking, resolved to Cost in positions.
 */
data class Posting(
    val account: Account,
    val units: Amount? = null,
    val cost: CostSpec? = null,
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
