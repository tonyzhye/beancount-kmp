package io.github.tonyzhye.beancount.query.tables

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.*
import io.github.tonyzhye.beancount.query.compiler.RowContext

/**
 * Row context for postings table.
 */
class PostingsRowContext(
    override val entry: Transaction,
    override val posting: Posting,
    val rowid: Int,
    override val priceMap: PriceDatabase? = null,
    override val allEntries: List<Directive> = emptyList()
) : RowContext {
    var balance: Inventory = Inventory()
}

/**
 * Postings table - core table with one row per posting.
 * Based on beanquery.sources.beancount.PostingsTable.
 */
class PostingsTable(
    private val entries: List<Directive>,
    private val priceMap: PriceDatabase? = null
) : Table {

    override val name = "postings"
    override val wildcardColumns = listOf("date", "flag", "payee", "narration", "position")

    override val columns: Map<String, Column> = buildMap {
        put("type", SimpleColumn(BqlType.String) { BqlStringValue("transaction") })
        put("date", SimpleColumn(BqlType.Date) {
            val entry = it.entry as Transaction
            BqlDateValue(entry.date)
        })
        put("year", SimpleColumn(BqlType.Integer) {
            val entry = it.entry as Transaction
            BqlIntegerValue(entry.date.year)
        })
        put("month", SimpleColumn(BqlType.Integer) {
            val entry = it.entry as Transaction
            BqlIntegerValue(entry.date.monthNumber)
        })
        put("day", SimpleColumn(BqlType.Integer) {
            val entry = it.entry as Transaction
            BqlIntegerValue(entry.date.dayOfMonth)
        })
        put("flag", SimpleColumn(BqlType.String) {
            val entry = it.entry as Transaction
            BqlStringValue(entry.flag)
        })
        put("payee", SimpleColumn(BqlType.String) {
            val entry = it.entry as Transaction
            entry.payee?.let { p -> BqlStringValue(p) } ?: BqlNullValue()
        })
        put("narration", SimpleColumn(BqlType.String) {
            val entry = it.entry as Transaction
            entry.narration?.let { n -> BqlStringValue(n) } ?: BqlNullValue()
        })
        put("description", SimpleColumn(BqlType.String) {
            val entry = it.entry as Transaction
            val parts = listOfNotNull(entry.payee, entry.narration)
            BqlStringValue(parts.joinToString(" | "))
        })
        put("tags", SimpleColumn(BqlType.Set) {
            val entry = it.entry as Transaction
            BqlSetValue(entry.tags)
        })
        put("links", SimpleColumn(BqlType.Set) {
            val entry = it.entry as Transaction
            BqlSetValue(entry.links)
        })
        put("posting_flag", SimpleColumn(BqlType.String) {
            val posting = it.posting
            posting?.flag?.let { f -> BqlStringValue(f) } ?: BqlNullValue()
        })
        put("account", SimpleColumn(BqlType.String) {
            val posting = it.posting
            BqlStringValue(posting!!.account)
        })
        put("other_accounts", SimpleColumn(BqlType.Set) {
            val entry = it.entry as Transaction
            val posting = it.posting
            val otherAccounts = entry.postings.filter { p -> p !== posting }.map { p -> p.account }.toSet()
            BqlSetValue(otherAccounts)
        })
        put("number", SimpleColumn(BqlType.Decimal) {
            val posting = it.posting
            BqlDecimalValue(posting!!.units!!.number)
        })
        put("currency", SimpleColumn(BqlType.String) {
            val posting = it.posting
            BqlStringValue(posting!!.units!!.currency)
        })
        put("cost_number", SimpleColumn(BqlType.Decimal) {
            val posting = it.posting
            val cost = posting?.cost
            if (cost != null && cost is Cost) BqlDecimalValue(cost.number) else BqlNullValue()
        })
        put("cost_currency", SimpleColumn(BqlType.String) {
            val posting = it.posting
            val cost = posting?.cost
            if (cost != null && cost is Cost) BqlStringValue(cost.currency) else BqlNullValue()
        })
        put("cost_date", SimpleColumn(BqlType.Date) {
            val posting = it.posting
            val cost = posting?.cost
            if (cost != null && cost is Cost) BqlDateValue(cost.date) else BqlNullValue()
        })
        put("cost_label", SimpleColumn(BqlType.String) {
            val posting = it.posting
            val cost = posting?.cost
            if (cost != null && cost is Cost) BqlStringValue(cost.label ?: "") else BqlNullValue()
        })
        put("position", SimpleColumn(BqlType.Position) {
            val posting = it.posting
            BqlPositionValue(Position(posting!!.units!!, posting.cost as? Cost))
        })
        put("price", SimpleColumn(BqlType.Amount) {
            val posting = it.posting
            posting?.price?.let { p -> BqlAmountValue(p) } ?: BqlNullValue()
        })
        put("weight", SimpleColumn(BqlType.Amount) {
            val posting = it.posting!!
            val weight = if (posting.price != null && posting.units != null) {
                val price = posting.price
                val units = posting.units
                Amount(price!!.number * units!!.number, price.currency)
            } else {
                posting.units!!
            }
            BqlAmountValue(weight)
        })
        put("balance", BalanceColumn())
        put("meta", SimpleColumn(BqlType.Any) {
            val posting = it.posting
            toBqlValue(posting!!.meta)
        })
        put("entry", SimpleColumn(BqlType.Transaction) {
            BqlTransactionValue(it.entry as Transaction)
        })
        put("accounts", SimpleColumn(BqlType.Set) {
            val entry = it.entry as Transaction
            BqlSetValue(entry.postings.map { p -> p.account }.toSet())
        })
        put("id", SimpleColumn(BqlType.String) {
            val entry = it.entry as Transaction
            val posting = it.posting!!
            val hashStr = "${entry.date}-${entry.flag}-${posting.account}-${posting.units}".hashCode().toString(16)
            BqlStringValue(hashStr)
        })
        put("filename", SimpleColumn(BqlType.String) {
            BqlStringValue(it.entry.meta["filename"] as? String ?: "")
        })
        put("lineno", SimpleColumn(BqlType.Integer) {
            val lineno = it.entry.meta["lineno"] as? Int ?: 0
            BqlIntegerValue(lineno)
        })
        put("location", SimpleColumn(BqlType.String) {
            val filename = it.entry.meta["filename"] as? String ?: ""
            val lineno = it.entry.meta["lineno"] as? Int ?: 0
            BqlStringValue("$filename:$lineno:")
        })
    }

    override fun iterator(): Iterator<RowContext> {
        var rowid = 0
        return entries.asSequence()
            .filterIsInstance<Transaction>()
            .flatMap { entry ->
                entry.postings.map { posting ->
                    rowid++
                    PostingsRowContext(entry, posting, rowid, priceMap, entries)
                }
            }
            .iterator()
    }
}

/**
 * Simple column implementation.
 */
class SimpleColumn(
    override val dtype: BqlType,
    private val evaluator: (RowContext) -> BqlValue
) : Column {
    override fun evaluate(context: RowContext): BqlValue = evaluator(context)
}

/**
 * Balance column with caching.
 * Based on beanquery.sources.beancount.PostingsTable.balance.
 */
class BalanceColumn : Column {
    override val dtype: BqlType = BqlType.Inventory
    private var lastRowid: Int = -1

    override fun evaluate(context: RowContext): BqlValue {
        val postingsContext = context as PostingsRowContext
        if (postingsContext.rowid != lastRowid) {
            postingsContext.balance = postingsContext.balance.copy()
            val posting = postingsContext.posting!!
            postingsContext.balance.addPosition(Position(posting.units!!, posting.cost as? Cost))
            lastRowid = postingsContext.rowid
        }
        return BqlInventoryValue(postingsContext.balance.copy())
    }
}
