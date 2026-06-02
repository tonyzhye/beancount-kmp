package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * Booking method for positions on accounts.
 * Based on beancount.core.data.Booking
 */
enum class Booking {
    STRICT,
    STRICT_WITH_SIZE,
    NONE,
    AVERAGE,
    FIFO,
    LIFO,
    HIFO
}

/**
 * Base class for all directives in a beancount ledger.
 * Based on beancount.core.data directives.
 */
sealed class Directive : Comparable<Directive> {
    abstract val meta: Meta
    abstract val date: LocalDate
    
    private val typeOrder: Int
        get() = when (this) {
            is Open -> -2
            is Balance -> -1
            is Document -> 1
            is Close -> 2
            else -> 0
        }
    
    private val lineNumber: Int
        get() = meta["lineno"] as? Int ?: 0
    
    override fun compareTo(other: Directive): Int {
        return compareValuesBy(this, other,
            { it.date },
            { it.typeOrder },
            { it.lineNumber }
        )
    }
}

/**
 * Include directive - includes another beancount file.
 */
data class Include(
    override val meta: Meta,
    override val date: LocalDate,
    val filename: String
) : Directive()

/**
 * Open account directive.
 */
data class Open(
    override val meta: Meta,
    override val date: LocalDate,
    val account: Account,
    val currencies: List<Currency> = emptyList(),
    val booking: Booking? = null
) : Directive()

/**
 * Close account directive.
 */
data class Close(
    override val meta: Meta,
    override val date: LocalDate,
    val account: Account
) : Directive()

/**
 * Commodity declaration directive.
 */
data class Commodity(
    override val meta: Meta,
    override val date: LocalDate,
    val currency: Currency
) : Directive()

/**
 * Pad directive - automatically insert transactions to make balance succeed.
 */
data class Pad(
    override val meta: Meta,
    override val date: LocalDate,
    val account: Account,
    val sourceAccount: Account
) : Directive()

/**
 * Balance assertion directive.
 */
data class Balance(
    override val meta: Meta,
    override val date: LocalDate,
    val account: Account,
    val amount: Amount,
    val tolerance: Decimal? = null,
    val diffAmount: Amount? = null
) : Directive()

/**
 * Transaction directive - the main type of object.
 */
data class Transaction(
    override val meta: Meta,
    override val date: LocalDate,
    val flag: Flag,
    val payee: String? = null,
    val narration: String? = null,
    val tags: Set<String> = emptySet(),
    val links: Set<String> = emptySet(),
    val postings: List<Posting> = emptyList()
) : Directive()

/**
 * Note directive - general note attached to an account.
 */
data class Note(
    override val meta: Meta,
    override val date: LocalDate,
    val account: Account,
    val comment: String,
    val tags: Set<String>? = null,
    val links: Set<String>? = null
) : Directive()

/**
 * Event directive - string variables that change over time.
 */
data class Event(
    override val meta: Meta,
    override val date: LocalDate,
    val type: String,
    val description: String
) : Directive()

/**
 * Query directive - pre-canned queries.
 */
data class Query(
    override val meta: Meta,
    override val date: LocalDate,
    val name: String,
    val queryString: String
) : Directive()

/**
 * Price directive - establishes price of a commodity.
 */
data class Price(
    override val meta: Meta,
    override val date: LocalDate,
    val currency: Currency,
    val amount: Amount
) : Directive()

/**
 * Document directive - attaches a statement to an account.
 */
data class Document(
    override val meta: Meta,
    override val date: LocalDate,
    val account: Account,
    val filename: String,
    val tags: Set<String>? = null,
    val links: Set<String>? = null
) : Directive()

/**
 * Custom directive - for experimental features.
 */
data class Custom(
    override val meta: Meta,
    override val date: LocalDate,
    val type: String,
    val values: List<Any> = emptyList()
) : Directive()

/**
 * PushTag directive - pushes a tag onto the tag stack.
 */
data class PushTag(
    override val meta: Meta,
    override val date: LocalDate,
    val tag: String
) : Directive()

/**
 * PopTag directive - pops a tag from the tag stack.
 */
data class PopTag(
    override val meta: Meta,
    override val date: LocalDate,
    val tag: String
) : Directive()

/**
 * PushMeta directive - pushes metadata onto the meta stack.
 */
data class PushMeta(
    override val meta: Meta,
    override val date: LocalDate,
    val key: String,
    val value: Any
) : Directive()

/**
 * PopMeta directive - pops metadata from the meta stack.
 */
data class PopMeta(
    override val meta: Meta,
    override val date: LocalDate,
    val key: String
) : Directive()

/**
 * List of all directive types for validation.
 */
val ALL_DIRECTIVES = listOf(
    Open::class, Close::class, Commodity::class,
    Pad::class, Balance::class, Transaction::class,
    Note::class, Event::class, Query::class,
    Price::class, Document::class, Custom::class,
    PushTag::class, PopTag::class, PushMeta::class, PopMeta::class
)
