package io.github.tonyzhye.beancount.query.tables

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.*
import io.github.tonyzhye.beancount.query.compiler.RowContext
import kotlinx.datetime.LocalDate

/**
 * Balances table - account balance assertions.
 * Based on beanquery.sources.beancount.BalancesTable.
 *
 * Each row represents a Balance directive from the ledger.
 * For computed balances, use the postings table with SUM(balance).
 */
class BalancesTable(
    private val entries: List<Directive>
) : Table {

    override val name = "balances"
    override val wildcardColumns = listOf("date", "account", "amount")

    override val columns: Map<String, Column> = buildMap {
        put("date", SimpleColumn(BqlType.Date) {
            BqlDateValue(it.entry.date)
        })
        put("year", SimpleColumn(BqlType.Integer) {
            BqlIntegerValue(it.entry.date.year)
        })
        put("month", SimpleColumn(BqlType.Integer) {
            BqlIntegerValue(it.entry.date.monthNumber)
        })
        put("day", SimpleColumn(BqlType.Integer) {
            BqlIntegerValue(it.entry.date.dayOfMonth)
        })
        put("account", SimpleColumn(BqlType.String) {
            val entry = it.entry as Balance
            BqlStringValue(entry.account)
        })
        put("amount", SimpleColumn(BqlType.Amount) {
            val entry = it.entry as Balance
            BqlAmountValue(entry.amount)
        })
        put("tolerance", SimpleColumn(BqlType.Decimal) {
            val entry = it.entry as Balance
            entry.tolerance?.let { t -> BqlDecimalValue(t) } ?: BqlNullValue()
        })
        put("diff_amount", SimpleColumn(BqlType.Amount) {
            val entry = it.entry as Balance
            entry.diffAmount?.let { d -> BqlAmountValue(d) } ?: BqlNullValue()
        })
        put("meta", SimpleColumn(BqlType.Any) {
            toBqlValue(it.entry.meta)
        })
    }

    override fun iterator(): Iterator<RowContext> {
        return entries.asSequence()
            .filterIsInstance<Balance>()
            .map { entry -> SimpleRowContext(entry) }
            .iterator()
    }
}
