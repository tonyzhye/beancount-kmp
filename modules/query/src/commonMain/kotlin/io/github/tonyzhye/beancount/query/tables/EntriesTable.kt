package io.github.tonyzhye.beancount.query.tables

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.*
import io.github.tonyzhye.beancount.query.compiler.RowContext

/**
 * Entries table - all directives.
 * Based on beanquery.sources.beancount.EntriesTable.
 */
class EntriesTable(
    private val entries: List<Directive>
) : Table {

    override val name = "entries"
    override val wildcardColumns = listOf("id", "type", "date", "filename", "lineno")

    override val columns: Map<String, Column> = buildMap {
        put("id", SimpleColumn(BqlType.String) { BqlStringValue(hashEntry(it.entry)) })
        put("type", SimpleColumn(BqlType.String) { BqlStringValue(it.entry::class.simpleName?.lowercase() ?: "") })
        put("date", SimpleColumn(BqlType.Date) { BqlDateValue(it.entry.date) })
        put("year", SimpleColumn(BqlType.Integer) { BqlIntegerValue(it.entry.date.year) })
        put("month", SimpleColumn(BqlType.Integer) { BqlIntegerValue(it.entry.date.monthNumber) })
        put("day", SimpleColumn(BqlType.Integer) { BqlIntegerValue(it.entry.date.dayOfMonth) })
        put("filename", SimpleColumn(BqlType.String) {
            val filename = it.entry.meta["filename"] as? String
            filename?.let { f -> BqlStringValue(f) } ?: BqlNullValue()
        })
        put("lineno", SimpleColumn(BqlType.Integer) {
            val lineno = it.entry.meta["lineno"] as? Int
            lineno?.let { l -> BqlIntegerValue(l) } ?: BqlNullValue()
        })
        put("flag", SimpleColumn(BqlType.String) {
            val entry = it.entry
            if (entry is Transaction) BqlStringValue(entry.flag) else BqlNullValue()
        })
        put("payee", SimpleColumn(BqlType.String) {
            val entry = it.entry
            if (entry is Transaction) entry.payee?.let { p -> BqlStringValue(p) } ?: BqlNullValue() else BqlNullValue()
        })
        put("narration", SimpleColumn(BqlType.String) {
            val entry = it.entry
            if (entry is Transaction) entry.narration?.let { n -> BqlStringValue(n) } ?: BqlNullValue() else BqlNullValue()
        })
        put("description", SimpleColumn(BqlType.String) {
            val entry = it.entry
            if (entry is Transaction) {
                val parts = listOfNotNull(entry.payee, entry.narration)
                BqlStringValue(parts.joinToString(" | "))
            } else BqlNullValue()
        })
        put("tags", SimpleColumn(BqlType.Set) {
            val entry = it.entry
            if (entry is Transaction) BqlSetValue(entry.tags) else BqlNullValue()
        })
        put("links", SimpleColumn(BqlType.Set) {
            val entry = it.entry
            if (entry is Transaction) BqlSetValue(entry.links) else BqlNullValue()
        })
        put("meta", SimpleColumn(BqlType.Any) { toBqlValue(it.entry.meta) })
        put("accounts", SimpleColumn(BqlType.Set) {
            val entry = it.entry
            val accounts = when (entry) {
                is Transaction -> entry.postings.map { p -> p.account }.toSet()
                is Open -> setOf(entry.account)
                is Close -> setOf(entry.account)
                is Balance -> setOf(entry.account)
                is Note -> setOf(entry.account)
                is Pad -> setOf(entry.account, entry.sourceAccount)
                else -> emptySet<String>()
            }
            BqlSetValue(accounts)
        })
    }

    override fun iterator(): Iterator<RowContext> {
        return entries.asSequence()
            .map { entry -> SimpleRowContext(entry) }
            .iterator()
    }
}

/**
 * Transactions table.
 */
class TransactionsTable(
    private val entries: List<Directive>
) : Table {
    override val name = "transactions"
    override val wildcardColumns = listOf("date", "flag", "payee", "narration")
    override val columns: Map<String, Column> = EntriesTable(entries).columns

    override fun iterator(): Iterator<RowContext> {
        return entries.asSequence()
            .filterIsInstance<Transaction>()
            .map { entry -> SimpleRowContext(entry) }
            .iterator()
    }
}

/**
 * Simple row context for entries that don't have postings.
 */
class SimpleRowContext(
    override val entry: Directive
) : RowContext {
    override val posting: Posting? = null
}

/**
 * Generate a hash for an entry.
 * Simplified version of beanquery.hashable.
 */
private fun hashEntry(entry: Directive): String {
    return "${entry.date}-${entry::class.simpleName}-${entry.meta["lineno"] ?: 0}"
}
