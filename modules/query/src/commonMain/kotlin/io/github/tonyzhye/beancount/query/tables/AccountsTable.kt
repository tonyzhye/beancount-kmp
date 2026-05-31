package io.github.tonyzhye.beancount.query.tables

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.*
import io.github.tonyzhye.beancount.query.compiler.RowContext
import kotlinx.datetime.LocalDate

/**
 * Row context for accounts table.
 */
class AccountRowContext(
    override val entry: Directive,
    val accountInfo: AccountInfo
) : RowContext {
    override val posting: Posting? = null
}

/**
 * Account information extracted from Open/Close directives.
 */
data class AccountInfo(
    val account: String,
    val openDate: LocalDate?,
    val closeDate: LocalDate?,
    val currencies: Set<String>,
    val booking: String?,
    val meta: Map<String, Any>
)

/**
 * Accounts table - one row per unique account.
 * Based on beanquery.sources.beancount.AccountsTable.
 */
class AccountsTable(
    private val entries: List<Directive>
) : Table {

    override val name = "accounts"
    override val wildcardColumns = listOf("account", "open_date", "close_date")

    private val accountMap: Map<String, AccountInfo> by lazy {
        buildAccountMap()
    }

    private fun buildAccountMap(): Map<String, AccountInfo> {
        val map = mutableMapOf<String, AccountInfo>()

        for (entry in entries) {
            when (entry) {
                is Open -> {
                    val existing = map[entry.account]
                    map[entry.account] = AccountInfo(
                        account = entry.account,
                        openDate = entry.date,
                        closeDate = existing?.closeDate,
                        currencies = entry.currencies.toSet(),
                        booking = entry.booking?.name,
                        meta = entry.meta
                    )
                }
                is Close -> {
                    val existing = map[entry.account]
                    if (existing != null) {
                        map[entry.account] = existing.copy(closeDate = entry.date)
                    } else {
                        map[entry.account] = AccountInfo(
                            account = entry.account,
                            openDate = null,
                            closeDate = entry.date,
                            currencies = emptySet(),
                            booking = null,
                            meta = entry.meta
                        )
                    }
                }
                else -> {}
            }
        }

        return map
    }

    override val columns: Map<String, Column> = buildMap {
        put("account", SimpleColumn(BqlType.String) {
            BqlStringValue((it as AccountRowContext).accountInfo.account)
        })
        put("open_date", SimpleColumn(BqlType.Date) {
            val info = (it as AccountRowContext).accountInfo
            info.openDate?.let { d -> BqlDateValue(d) } ?: BqlNullValue()
        })
        put("close_date", SimpleColumn(BqlType.Date) {
            val info = (it as AccountRowContext).accountInfo
            info.closeDate?.let { d -> BqlDateValue(d) } ?: BqlNullValue()
        })
        put("currencies", SimpleColumn(BqlType.Set) {
            BqlSetValue((it as AccountRowContext).accountInfo.currencies)
        })
        put("booking", SimpleColumn(BqlType.String) {
            val info = (it as AccountRowContext).accountInfo
            info.booking?.let { b -> BqlStringValue(b) } ?: BqlNullValue()
        })
        put("meta", SimpleColumn(BqlType.Any) {
            toBqlValue((it as AccountRowContext).accountInfo.meta)
        })
    }

    override fun iterator(): Iterator<RowContext> {
        return accountMap.values.asSequence()
            .map { info -> AccountRowContext(
                entry = entries.firstOrNull { e ->
                    (e is Open && e.account == info.account) ||
                    (e is Close && e.account == info.account)
                } ?: entries.first(),
                accountInfo = info
            ) }
            .iterator()
    }
}

/**
 * Commodities table - one row per Commodity directive.
 * Based on beanquery.sources.beancount.CommoditiesTable.
 */
class CommoditiesTable(
    private val entries: List<Directive>
) : Table {

    override val name = "commodities"
    override val wildcardColumns = listOf("currency", "date")

    override val columns: Map<String, Column> = buildMap {
        put("currency", SimpleColumn(BqlType.String) {
            val entry = (it as SimpleRowContext).entry as Commodity
            BqlStringValue(entry.currency)
        })
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
        put("meta", SimpleColumn(BqlType.Any) {
            toBqlValue(it.entry.meta)
        })
    }

    override fun iterator(): Iterator<RowContext> {
        return entries.asSequence()
            .filterIsInstance<Commodity>()
            .map { entry -> SimpleRowContext(entry) }
            .iterator()
    }
}

/**
 * Prices table - one row per Price directive.
 * Based on beanquery.sources.beancount.PricesTable.
 */
class PricesTable(
    private val entries: List<Directive>
) : Table {

    override val name = "prices"
    override val wildcardColumns = listOf("date", "currency", "amount")

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
        put("currency", SimpleColumn(BqlType.String) {
            val entry = it.entry as Price
            BqlStringValue(entry.currency)
        })
        put("amount", SimpleColumn(BqlType.Amount) {
            val entry = it.entry as Price
            BqlAmountValue(entry.amount)
        })
        put("meta", SimpleColumn(BqlType.Any) {
            toBqlValue(it.entry.meta)
        })
    }

    override fun iterator(): Iterator<RowContext> {
        return entries.asSequence()
            .filterIsInstance<Price>()
            .map { entry -> SimpleRowContext(entry) }
            .iterator()
    }
}
