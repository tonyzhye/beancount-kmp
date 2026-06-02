package io.github.tonyzhye.beancount.api

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.loader.*
import io.github.tonyzhye.beancount.parser.*
import io.github.tonyzhye.beancount.query.*
import kotlinx.datetime.LocalDate

/**
 * Unified public API for Beancount JVM.
 *
 * Based on Python beancount.api module.
 * Provides a single entry point for common operations.
 *
 * Example usage:
 * ```kotlin
 * import io.github.tonyzhye.beancount.api.Beancount as bn
 *
 * val result = bn.loadFile("ledger.beancount")
 * val accounts = bn.getAccounts(result.entries)
 * ```
 */
object Beancount {

    // ------------------------------------------------------------------
    // Directive types (re-exported for convenience)
    // ------------------------------------------------------------------

    /** Type alias for account names */
    typealias Account = io.github.tonyzhye.beancount.core.Account

    /** Type alias for currency codes */
    typealias Currency = io.github.tonyzhye.beancount.core.Currency

    /** Type alias for flag strings */
    typealias Flag = io.github.tonyzhye.beancount.core.Flag

    /** Type alias for metadata maps */
    typealias Meta = io.github.tonyzhye.beancount.core.Meta

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Load a beancount file.
     *
     * @param filename Path to the beancount file
     * @return LoadResult containing entries, errors, and options
     */
    @JvmStatic
    fun loadFile(filename: String): LoadResult {
        return io.github.tonyzhye.beancount.loader.loadFile(filename)
    }

    /**
     * Load beancount content from a string.
     *
     * @param content Beancount syntax string
     * @return LoadResult containing entries, errors, and options
     */
    @JvmStatic
    fun loadString(content: String): LoadResult {
        return io.github.tonyzhye.beancount.loader.loadString(content)
    }

    // ------------------------------------------------------------------
    // Core types and constructors
    // ------------------------------------------------------------------

    /**
     * Create a new Decimal from a string.
     * Equivalent to Python's D() function.
     */
    @JvmStatic
    fun D(value: String): Decimal = io.github.tonyzhye.beancount.core.D(value)

    /**
     * Create a new Decimal from a double.
     */
    @JvmStatic
    fun D(value: Double): Decimal = io.github.tonyzhye.beancount.core.D(value)

    /**
     * Create a new Decimal from a long.
     */
    @JvmStatic
    fun D(value: Long): Decimal = io.github.tonyzhye.beancount.core.D(value)

    /**
     * Create a new Decimal from an int.
     */
    @JvmStatic
    fun D(value: Int): Decimal = io.github.tonyzhye.beancount.core.D(value)

    /**
     * Create metadata for a directive.
     */
    @JvmStatic
    @JvmOverloads
    fun newMetadata(filename: String, lineno: Int, kvlist: Meta? = null): Meta {
        return io.github.tonyzhye.beancount.core.newMetadata(filename, lineno, kvlist)
    }

    /**
     * Create a simple posting for a transaction.
     */
    @JvmStatic
    fun createSimplePosting(
        entry: Transaction,
        account: Account,
        number: Decimal,
        currency: Currency
    ): Posting {
        return io.github.tonyzhye.beancount.core.createSimplePosting(entry, account, number, currency)
    }

    // ------------------------------------------------------------------
    // Flag constants
    // ------------------------------------------------------------------

    /** OK flag */
    const val FLAG_OKAY: Flag = io.github.tonyzhye.beancount.core.Flags.FLAG_OKAY

    /** Warning flag */
    const val FLAG_WARNING: Flag = io.github.tonyzhye.beancount.core.Flags.FLAG_WARNING

    /** Padding flag */
    const val FLAG_PADDING: Flag = io.github.tonyzhye.beancount.core.Flags.FLAG_PADDING

    /** Summarize flag */
    const val FLAG_SUMMARIZE: Flag = io.github.tonyzhye.beancount.core.Flags.FLAG_SUMMARIZE

    /** Transfer flag */
    const val FLAG_TRANSFER: Flag = io.github.tonyzhye.beancount.core.Flags.FLAG_TRANSFER

    /** Conversions flag */
    const val FLAG_CONVERSIONS: Flag = io.github.tonyzhye.beancount.core.Flags.FLAG_CONVERSIONS

    /** Merging flag */
    const val FLAG_MERGING: Flag = io.github.tonyzhye.beancount.core.Flags.FLAG_MERGING

    // ------------------------------------------------------------------
    // Account utilities
    // ------------------------------------------------------------------

    /**
     * Get the root account type from a full account name.
     */
    @JvmStatic
    fun getAccountType(account: Account): String {
        return AccountTypes.getAccountType(account)
    }

    /**
     * Get the sign (+1 or -1) for an account based on its type.
     */
    @JvmStatic
    @JvmOverloads
    fun getAccountSign(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Int {
        return AccountTypes.getAccountSign(account, config)
    }

    /**
     * Get a sort key for ordering accounts.
     */
    @JvmStatic
    @JvmOverloads
    fun getAccountSortKey(account: Account, config: AccountTypesConfig = AccountTypesConfig()): String {
        return AccountTypes.getAccountSortKey(account, config)
    }

    /**
     * Check if an account is an assets account.
     */
    @JvmStatic
    @JvmOverloads
    fun isAssets(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return AccountTypes.isAssets(account, config)
    }

    /**
     * Check if an account is a liabilities account.
     */
    @JvmStatic
    @JvmOverloads
    fun isLiabilities(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return AccountTypes.isLiabilities(account, config)
    }

    /**
     * Check if an account is an equity account.
     */
    @JvmStatic
    @JvmOverloads
    fun isEquity(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return AccountTypes.isEquity(account, config)
    }

    /**
     * Check if an account is an income account.
     */
    @JvmStatic
    @JvmOverloads
    fun isIncome(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return AccountTypes.isIncome(account, config)
    }

    /**
     * Check if an account is an expenses account.
     */
    @JvmStatic
    @JvmOverloads
    fun isExpenses(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return AccountTypes.isExpenses(account, config)
    }

    /**
     * Check if an account is a balance sheet account.
     */
    @JvmStatic
    @JvmOverloads
    fun isBalanceSheetAccount(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return AccountTypes.isBalanceSheetAccount(account, config)
    }

    /**
     * Check if an account is an income statement account.
     */
    @JvmStatic
    @JvmOverloads
    fun isIncomeStatementAccount(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return AccountTypes.isIncomeStatementAccount(account, config)
    }

    /**
     * Check if an account is an equity account (alias for isEquity).
     */
    @JvmStatic
    @JvmOverloads
    fun isEquityAccount(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return AccountTypes.isEquityAccount(account, config)
    }

    /**
     * Check if an account has inverted sign.
     */
    @JvmStatic
    @JvmOverloads
    fun isInvertedAccount(account: Account, config: AccountTypesConfig = AccountTypesConfig()): Boolean {
        return AccountTypes.isInvertedAccount(account, config)
    }

    // ------------------------------------------------------------------
    // Account string manipulation
    // ------------------------------------------------------------------

    /** Join account components */
    @JvmStatic
    fun accountJoin(vararg components: String): Account = io.github.tonyzhye.beancount.core.accountJoin(*components)

    /** Split account into components */
    @JvmStatic
    fun accountSplit(account: Account): List<String> = io.github.tonyzhye.beancount.core.accountSplit(account)

    /** Get parent account */
    @JvmStatic
    fun accountParent(account: Account): Account? = io.github.tonyzhye.beancount.core.accountParent(account)

    /** Get leaf account name */
    @JvmStatic
    fun accountLeaf(account: Account): Account? = io.github.tonyzhye.beancount.core.accountLeaf(account)

    /** Get account without root */
    @JvmStatic
    fun accountSansRoot(account: Account): Account? = io.github.tonyzhye.beancount.core.accountSansRoot(account)

    /** Get first N components */
    @JvmStatic
    fun accountRoot(numComponents: Int, account: Account): Account =
        io.github.tonyzhye.beancount.core.accountRoot(numComponents, account)

    /** Check if account contains component */
    @JvmStatic
    fun accountHasComponent(account: Account, component: String): Boolean =
        io.github.tonyzhye.beancount.core.accountHasComponent(account, component)

    /** Get common prefix of accounts */
    @JvmStatic
    fun accountCommonPrefix(accounts: Iterable<Account>): Account =
        io.github.tonyzhye.beancount.core.accountCommonPrefix(accounts)

    /** Build parent matcher predicate */
    @JvmStatic
    fun parentMatcher(account: Account): (Account) -> Boolean =
        io.github.tonyzhye.beancount.core.parentMatcher(account)

    /** Get all parents of account */
    @JvmStatic
    fun accountParents(account: Account): Sequence<Account> =
        io.github.tonyzhye.beancount.core.accountParents(account)

    // ------------------------------------------------------------------
    // Getter utilities
    // ------------------------------------------------------------------

    /**
     * Get all accounts referenced in entries.
     */
    @JvmStatic
    fun getAccounts(entries: List<Directive>): Set<Account> {
        return io.github.tonyzhye.beancount.core.getAccounts(entries)
    }

    /**
     * Get open/close information for accounts.
     */
    @JvmStatic
    fun getAccountOpenClose(entries: List<Directive>): Map<Account, Pair<Open?, Close?>> {
        return io.github.tonyzhye.beancount.core.getAccountOpenClose(entries)
    }

    /**
     * Get account usage map (first and last dates).
     */
    @JvmStatic
    fun getAccountsUseMap(entries: List<Directive>): Pair<Map<Account, LocalDate>, Map<Account, LocalDate>> {
        return io.github.tonyzhye.beancount.core.getAccountsUseMap(entries)
    }

    /**
     * Get all tags from entries.
     */
    @JvmStatic
    fun getAllTags(entries: List<Directive>): List<String> {
        return io.github.tonyzhye.beancount.core.getAllTags(entries)
    }

    /**
     * Get all payees from entries.
     */
    @JvmStatic
    fun getAllPayees(entries: List<Directive>): List<String> {
        return io.github.tonyzhye.beancount.core.getAllPayees(entries)
    }

    /**
     * Get all links from entries.
     */
    @JvmStatic
    fun getAllLinks(entries: List<Directive>): List<String> {
        return io.github.tonyzhye.beancount.core.getAllLinks(entries)
    }

    /**
     * Get commodity directives.
     */
    @JvmStatic
    fun getCommodityDirectives(entries: List<Directive>): Map<Currency, Commodity> {
        return io.github.tonyzhye.beancount.core.getCommodityDirectives(entries)
    }

    /**
     * Get min and max dates from entries.
     */
    @JvmStatic
    @JvmOverloads
    fun getMinMaxDates(entries: List<Directive>, types: Set<Class<out Directive>>? = null): Pair<LocalDate?, LocalDate?> {
        return io.github.tonyzhye.beancount.core.getMinMaxDates(entries, types)
    }

    /**
     * Get active years from entries.
     */
    @JvmStatic
    fun getActiveYears(entries: List<Directive>): List<Int> {
        return io.github.tonyzhye.beancount.core.getActiveYears(entries)
    }

    /**
     * Filter only Transaction instances from entries.
     */
    @JvmStatic
    fun filterTxns(entries: List<Directive>): List<Transaction> {
        return io.github.tonyzhye.beancount.core.filterTxns(entries)
    }

    /**
     * Get the entry associated with a posting or entry.
     */
    @JvmStatic
    fun getEntry(postingOrEntry: Any): Directive {
        return io.github.tonyzhye.beancount.core.getEntry(postingOrEntry)
    }

    // ------------------------------------------------------------------
    // Conversion functions
    // ------------------------------------------------------------------

    /**
     * Get units from a Position.
     */
    @JvmStatic
    fun getUnits(position: Position): Amount = io.github.tonyzhye.beancount.core.getUnits(position)

    /**
     * Get units from a Posting.
     */
    @JvmStatic
    fun getUnits(posting: Posting): Amount = io.github.tonyzhye.beancount.core.getUnits(posting)

    /**
     * Get cost from a Position.
     */
    @JvmStatic
    fun getCost(position: Position): Amount = io.github.tonyzhye.beancount.core.getCost(position)

    /**
     * Get cost from a Posting.
     */
    @JvmStatic
    fun getCost(posting: Posting): Amount = io.github.tonyzhye.beancount.core.getCost(posting)

    /**
     * Get weight from a Position.
     */
    @JvmStatic
    fun getWeight(position: Position): Amount = io.github.tonyzhye.beancount.core.getWeight(position)

    /**
     * Get weight from a Posting.
     */
    @JvmStatic
    fun getWeight(posting: Posting): Amount = io.github.tonyzhye.beancount.core.getWeight(posting)

    /**
     * Get market value from a Position.
     */
    @JvmStatic
    @JvmOverloads
    fun getValue(position: Position, priceMap: PriceDatabase, date: LocalDate? = null): Amount {
        return io.github.tonyzhye.beancount.core.getValue(position, priceMap, date)
    }

    /**
     * Get market value from a Posting.
     */
    @JvmStatic
    @JvmOverloads
    fun getValue(posting: Posting, priceMap: PriceDatabase, date: LocalDate? = null): Amount {
        return io.github.tonyzhye.beancount.core.getValue(posting, priceMap, date)
    }

    /**
     * Convert an Amount to a target currency.
     */
    @JvmStatic
    @JvmOverloads
    fun convertAmount(
        amount: Amount,
        targetCurrency: Currency,
        priceMap: PriceDatabase,
        date: LocalDate? = null,
        via: List<Currency>? = null
    ): Amount {
        return io.github.tonyzhye.beancount.core.convertAmount(amount, targetCurrency, priceMap, date, via)
    }

    /**
     * Convert a Position to a target currency.
     */
    @JvmStatic
    @JvmOverloads
    fun convertPosition(
        position: Position,
        targetCurrency: Currency,
        priceMap: PriceDatabase,
        date: LocalDate? = null
    ): Amount {
        return io.github.tonyzhye.beancount.core.convertPosition(position, targetCurrency, priceMap, date)
    }

    /**
     * Convert a Posting to a target currency.
     */
    @JvmStatic
    @JvmOverloads
    fun convertPosting(
        posting: Posting,
        targetCurrency: Currency,
        priceMap: PriceDatabase,
        date: LocalDate? = null
    ): Amount {
        return io.github.tonyzhye.beancount.core.convertPosting(posting, targetCurrency, priceMap, date)
    }

    // ------------------------------------------------------------------
    // Price functions
    // ------------------------------------------------------------------

    /**
     * Build a price map from entries.
     */
    @JvmStatic
    fun buildPriceMap(entries: List<Directive>): PriceDatabase {
        return PriceDatabase.buildPriceMap(entries)
    }

    // ------------------------------------------------------------------
    // Realization
    // ------------------------------------------------------------------

    /**
     * Realize entries into a tree of RealAccount.
     */
    @JvmStatic
    @JvmOverloads
    fun realize(entries: List<Directive>, computeBalance: Boolean = true): RealAccount {
        return io.github.tonyzhye.beancount.core.realize(entries, computeBalance)
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    /**
     * Format a single entry as string.
     */
    @JvmStatic
    @JvmOverloads
    fun formatEntry(entry: Directive, dcontext: DisplayContext? = null): String {
        return io.github.tonyzhye.beancount.core.formatEntry(entry, dcontext)
    }

    /**
     * Format multiple entries as string.
     */
    @JvmStatic
    @JvmOverloads
    fun formatEntries(entries: List<Directive>, dcontext: DisplayContext? = null): String {
        return io.github.tonyzhye.beancount.core.formatEntries(entries, dcontext)
    }

    /**
     * Print entries with display context.
     */
    @JvmStatic
    @JvmOverloads
    fun printEntries(entries: List<Directive>, dcontext: DisplayContext? = null): String {
        return io.github.tonyzhye.beancount.core.formatEntries(entries, dcontext)
    }

    // ------------------------------------------------------------------
    // Comparison
    // ------------------------------------------------------------------

    /**
     * Compute hash for a single entry.
     */
    @JvmStatic
    @JvmOverloads
    fun hashEntry(entry: Directive, excludeMeta: Boolean = false): String {
        return io.github.tonyzhye.beancount.core.hashEntry(entry, excludeMeta)
    }

    /**
     * Compare two lists of entries.
     */
    @JvmStatic
    fun compareEntries(entries1: List<Directive>, entries2: List<Directive>): Triple<Boolean, List<Directive>, List<Directive>> {
        return io.github.tonyzhye.beancount.core.compareEntries(entries1, entries2)
    }

    // ------------------------------------------------------------------
    // Query
    // ------------------------------------------------------------------

    /**
     * Create a query engine for the given entries.
     */
    @JvmStatic
    fun queryEngine(entries: List<Directive>): QueryEngine {
        return QueryEngine(entries)
    }

    // ------------------------------------------------------------------
    // Number utilities
    // ------------------------------------------------------------------

    /**
     * Round a Decimal to given number of decimal places.
     */
    @JvmStatic
    fun roundTo(value: Decimal, digits: Int): Decimal {
        return io.github.tonyzhye.beancount.core.roundTo(value, digits)
    }

    /**
     * Auto-quantize a Decimal to remove floating-point artifacts.
     */
    @JvmStatic
    @JvmOverloads
    fun autoQuantize(value: Decimal, maxDigits: Int = 12): Decimal {
        return io.github.tonyzhye.beancount.core.autoQuantize(value, maxDigits)
    }

    /**
     * Check if two Decimals have the same sign.
     */
    @JvmStatic
    fun sameSign(a: Decimal, b: Decimal): Boolean {
        return io.github.tonyzhye.beancount.core.sameSign(a, b)
    }
}
