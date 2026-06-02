package io.github.tonyzhye.beancount.query.tables

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.query.*
import io.github.tonyzhye.beancount.query.compiler.RowContext

/**
 * Notes table - one row per Note directive.
 * Based on beanquery.sources.beancount.NotesTable.
 */
class NotesTable(
    private val entries: List<Directive>
) : Table {

    override val name = "notes"
    override val wildcardColumns = listOf("date", "account", "comment")

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
            val entry = it.entry as Note
            BqlStringValue(entry.account)
        })
        put("comment", SimpleColumn(BqlType.String) {
            val entry = it.entry as Note
            BqlStringValue(entry.comment)
        })
        put("tags", SimpleColumn(BqlType.Set) {
            val entry = it.entry as Note
            BqlSetValue(entry.tags ?: emptySet())
        })
        put("links", SimpleColumn(BqlType.Set) {
            val entry = it.entry as Note
            BqlSetValue(entry.links ?: emptySet())
        })
        put("meta", SimpleColumn(BqlType.Any) {
            toBqlValue(it.entry.meta)
        })
    }

    override fun iterator(): Iterator<RowContext> {
        return entries.asSequence()
            .filterIsInstance<Note>()
            .map { entry -> SimpleRowContext(entry, allEntries = entries) }
            .iterator()
    }
}

/**
 * Events table - one row per Event directive.
 * Based on beanquery.sources.beancount.EventsTable.
 */
class EventsTable(
    private val entries: List<Directive>
) : Table {

    override val name = "events"
    override val wildcardColumns = listOf("date", "type", "description")

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
        put("type", SimpleColumn(BqlType.String) {
            val entry = it.entry as Event
            BqlStringValue(entry.type)
        })
        put("description", SimpleColumn(BqlType.String) {
            val entry = it.entry as Event
            BqlStringValue(entry.description)
        })
        put("meta", SimpleColumn(BqlType.Any) {
            toBqlValue(it.entry.meta)
        })
    }

    override fun iterator(): Iterator<RowContext> {
        return entries.asSequence()
            .filterIsInstance<Event>()
            .map { entry -> SimpleRowContext(entry, allEntries = entries) }
            .iterator()
    }
}

/**
 * Documents table - one row per Document directive.
 * Based on beanquery.sources.beancount.DocumentsTable.
 */
class DocumentsTable(
    private val entries: List<Directive>
) : Table {

    override val name = "documents"
    override val wildcardColumns = listOf("date", "account", "filename")

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
            val entry = it.entry as Document
            BqlStringValue(entry.account)
        })
        put("filename", SimpleColumn(BqlType.String) {
            val entry = it.entry as Document
            BqlStringValue(entry.filename)
        })
        put("tags", SimpleColumn(BqlType.Set) {
            val entry = it.entry as Document
            BqlSetValue(entry.tags ?: emptySet())
        })
        put("links", SimpleColumn(BqlType.Set) {
            val entry = it.entry as Document
            BqlSetValue(entry.links ?: emptySet())
        })
        put("meta", SimpleColumn(BqlType.Any) {
            toBqlValue(it.entry.meta)
        })
    }

    override fun iterator(): Iterator<RowContext> {
        return entries.asSequence()
            .filterIsInstance<Document>()
            .map { entry -> SimpleRowContext(entry, allEntries = entries) }
            .iterator()
    }
}
