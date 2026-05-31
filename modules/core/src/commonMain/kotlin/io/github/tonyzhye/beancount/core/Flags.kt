package io.github.tonyzhye.beancount.core

/**
 * Flag constants.
 * Based on beancount.core.flags
 */
object Flags {
    /** Transactions that have been checked. */
    const val FLAG_OKAY = "*"
    /** Mark by the user as something to be looked at later on. */
    const val FLAG_WARNING = "!"
    /** Transactions created from padding directives. */
    const val FLAG_PADDING = "P"
    /** Transactions created due to summarization. */
    const val FLAG_SUMMARIZE = "S"
    /** Transactions created due to balance transfers. */
    const val FLAG_TRANSFER = "T"
    /** Transactions created to account for price conversions. */
    const val FLAG_CONVERSIONS = "C"
    /** A flag to mark postings merging together legs for average cost. */
    const val FLAG_MERGING = "M"
}
