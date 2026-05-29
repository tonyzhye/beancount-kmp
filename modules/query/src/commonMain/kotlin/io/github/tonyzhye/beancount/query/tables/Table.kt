package io.github.tonyzhye.beancount.query.tables

import io.github.tonyzhye.beancount.query.BqlType
import io.github.tonyzhye.beancount.query.BqlValue
import io.github.tonyzhye.beancount.query.compiler.RowContext

/**
 * Table interface for query data sources.
 * Based on beanquery.tables.Table.
 */
interface Table {
    val name: String
    val columns: Map<String, Column>
    val wildcardColumns: List<String>
    fun iterator(): Iterator<RowContext>
}

/**
 * Column definition.
 */
interface Column {
    val dtype: BqlType
    fun evaluate(context: RowContext): BqlValue
}
