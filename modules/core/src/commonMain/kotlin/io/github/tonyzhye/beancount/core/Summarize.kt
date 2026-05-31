package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/**
 * Summarization of entries.
 *
 * This code is used to summarize a sequence of entries (e.g. during a time period)
 * into a few "opening balance" entries. This is when computing a balance sheet for
 * a specific time period: we don't want to see the entries from before some period
 * of time, so we fold them into a single transaction per account that has the sum
 * total amount of that account.
 *
 * Based on beancount.ops.summarize
 */
object Summarize {

    /**
     * Summarize entries before a date and transfer income/expenses to equity.
     *
     * This method prepares a list of directives to contain only transactions that
     * occur after a particular date. It truncates the past.
     */
    fun openEntries(
        entries: List<Directive>,
        date: LocalDate,
        accountTypes: AccountTypesUtil,
        conversionCurrency: Currency,
        accountEarnings: Account,
        accountOpening: Account,
        accountConversions: Account
    ): Pair<List<Directive>, Int> {
        var result = entries

        // Insert conversion entries.
        result = conversions(result, accountConversions, conversionCurrency, date)

        // Transfer income and expenses before the period to equity.
        result = transferBalances(
            result, date,
            { account -> accountTypes.isIncomeStatementAccount(account) },
            accountEarnings
        )

        // Summarize all the previous balances.
        return summarize(result, date, accountOpening)
    }

    /**
     * Truncate entries that occur after a particular date and ensure balance.
     */
    fun closeEntries(
        entries: List<Directive>,
        date: LocalDate?,
        conversionCurrency: Currency,
        accountConversions: Account
    ): Pair<List<Directive>, Int> {
        var result = entries

        if (date != null) {
            result = truncate(result, date)
        }

        val index = result.size

        result = conversions(result, accountConversions, conversionCurrency, date)

        return result to index
    }

    /**
     * Transfer income and expenses balances at the given date to the equity accounts.
     */
    fun clearEntries(
        entries: List<Directive>,
        date: LocalDate?,
        accountTypes: AccountTypesUtil,
        accountEarnings: Account
    ): Pair<List<Directive>, Int> {
        val index = entries.size
        val result = transferBalances(
            entries, date,
            { account -> accountTypes.isIncomeStatementAccount(account) },
            accountEarnings
        )
        return result to index
    }

    /**
     * Filter entries to include only those during a specified time period.
     */
    fun clamp(
        entries: List<Directive>,
        beginDate: LocalDate,
        endDate: LocalDate,
        accountTypes: AccountTypesUtil,
        conversionCurrency: Currency,
        accountEarnings: Account,
        accountOpening: Account,
        accountConversions: Account
    ): Pair<List<Directive>, Int> {
        var result = entries

        // Transfer income and expenses before the period to equity.
        result = transferBalances(
            result, beginDate,
            { account -> accountTypes.isIncomeStatementAccount(account) },
            accountEarnings
        )

        // Summarize all previous balances.
        result = summarize(result, beginDate, accountOpening).first

        // Truncate after end date.
        result = truncate(result, endDate)

        // Insert conversion entries.
        result = conversions(result, accountConversions, conversionCurrency, endDate)

        // Find index: first entry on or after beginDate
        val index = result.indexOfFirst { it.date >= beginDate }.coerceAtLeast(0)

        return result to index
    }

    /**
     * Transfer net income to equity and insert a final conversion entry.
     */
    fun cap(
        entries: List<Directive>,
        accountTypes: AccountTypesUtil,
        conversionCurrency: Currency,
        accountEarnings: Account,
        accountConversions: Account
    ): List<Directive> {
        var result = entries

        result = transferBalances(
            result, null,
            { account -> accountTypes.isIncomeStatementAccount(account) },
            accountEarnings
        )

        result = conversions(result, accountConversions, conversionCurrency, null)

        return result
    }

    /**
     * Synthesize transactions to transfer balances from some accounts at a given date.
     */
    fun transferBalances(
        entries: List<Directive>,
        date: LocalDate?,
        accountPred: (Account) -> Boolean,
        transferAccount: Account
    ): List<Directive> {
        if (entries.isEmpty()) return entries

        val (balances, index) = balanceByAccount(entries, date)

        val transferBalances = balances.filter { (account, _) -> accountPred(account) }

        val transferDate = if (date != null) {
            date.minus(1, DateTimeUnit.DAY)
        } else {
            entries.last().date
        }

        val transferEntries = createEntriesFromBalances(
            transferBalances,
            transferDate,
            transferAccount,
            direction = false,
            meta = newMetadata("<transfer_balances>", 0),
            flag = Flags.FLAG_TRANSFER,
            narrationTemplate = "Transfer balance for '{account}' (Transfer balance)"
        )

        // Remove balance assertions after transfer on transferred accounts
        val afterEntries = entries.drop(index).filter { entry ->
            !(entry is Balance && entry.account in transferBalances)
        }

        return entries.take(index) + transferEntries + afterEntries
    }

    /**
     * Summarize all entries before a date by replacing them with summarization entries.
     */
    fun summarize(
        entries: List<Directive>,
        date: LocalDate,
        accountOpening: Account
    ): Pair<List<Directive>, Int> {
        val (balances, index) = balanceByAccount(entries, date)

        val summarizeDate = date.minus(1, DateTimeUnit.DAY)

        val summarizingEntries = createEntriesFromBalances(
            balances,
            summarizeDate,
            accountOpening,
            direction = true,
            meta = newMetadata("<summarize>", 0),
            flag = Flags.FLAG_SUMMARIZE,
            narrationTemplate = "Opening balance for '{account}' (Summarization)"
        )

        // Get last price entries before date
        val priceEntries = getLastPriceEntries(entries, date)

        // Get active open entries at date
        val openEntries = getOpenEntries(entries, date)

        val beforeEntries = (openEntries + priceEntries + summarizingEntries).sorted()
        val afterEntries = entries.drop(index)

        return (beforeEntries + afterEntries) to beforeEntries.size
    }

    /**
     * Insert a conversion entry at date.
     */
    fun conversions(
        entries: List<Directive>,
        conversionAccount: Account,
        conversionCurrency: Currency,
        date: LocalDate? = null
    ): List<Directive> {
        val conversionBalance = computeEntriesBalance(entries, date = date)

        val conversionCostBalance = conversionBalance.reduce { pos ->
            getCost(pos)
        }

        if (conversionCostBalance.isEmpty()) return entries

        val (index, lastDate) = if (date != null) {
            val idx = bisectLeftByDate(entries, date)
            idx to date.minus(1, DateTimeUnit.DAY)
        } else {
            entries.size to entries.last().date
        }

        val meta = newMetadata("<conversions>", -1)
        val narration = "Conversion for $conversionBalance"

        val postings = conversionCostBalance.getPositions().map { position ->
            val negUnits = Amount(-position.units.number, position.units.currency)
            val zeroPrice = Amount(Decimal.ZERO, conversionCurrency)
            Posting(
                account = conversionAccount,
                units = negUnits,
                cost = position.cost?.let { cost ->
                    CostSpec(numberPer = cost.number, currency = cost.currency, date = cost.date, label = cost.label)
                },
                price = zeroPrice,
                flag = null,
                meta = null
            )
        }

        val conversionEntry = Transaction(
            meta = meta,
            date = lastDate,
            flag = Flags.FLAG_CONVERSIONS,
            payee = null,
            narration = narration,
            tags = emptySet(),
            links = emptySet(),
            postings = postings
        )

        val newEntries = entries.toMutableList()
        newEntries.add(index, conversionEntry)
        return newEntries
    }

    /**
     * Filter out all entries at and after date.
     */
    fun truncate(entries: List<Directive>, date: LocalDate): List<Directive> {
        val index = bisectLeftByDate(entries, date)
        return entries.take(index)
    }

    /**
     * Create entries from a map of balances.
     */
    fun createEntriesFromBalances(
        balances: Map<Account, Inventory>,
        date: LocalDate,
        sourceAccount: Account,
        direction: Boolean,
        meta: Meta,
        flag: Flag,
        narrationTemplate: String
    ): List<Directive> {
        val newEntries = mutableListOf<Directive>()

        for ((account, accountBalance) in balances.toList().sortedBy { it.first }) {
            if (accountBalance.isEmpty()) continue

            val narration = narrationTemplate
                .replace("{account}", account)
                .replace("{date}", date.toString())

            val effectiveBalance = if (direction) accountBalance else accountBalance.negate()

            val postings = mutableListOf<Posting>()

            for (position in effectiveBalance.getPositions()) {
                postings.add(Posting(
                    account = account,
                    units = position.units,
                    cost = position.cost?.let { cost ->
                        CostSpec(numberPer = cost.number, currency = cost.currency, date = cost.date, label = cost.label)
                    },
                    price = null,
                    flag = null,
                    meta = null
                ))

                val cost = getCost(position)
                val negCost = Amount(-cost.number, cost.currency)
                postings.add(Posting(
                    account = sourceAccount,
                    units = negCost,
                    cost = null,
                    price = null,
                    flag = null,
                    meta = null
                ))
            }

            newEntries.add(Transaction(
                meta = meta,
                date = date,
                flag = flag,
                payee = null,
                narration = narration,
                tags = emptySet(),
                links = emptySet(),
                postings = postings
            ))
        }

        return newEntries
    }

    /**
     * Sum up the balance per account for all entries strictly before date.
     */
    fun balanceByAccount(
        entries: List<Directive>,
        date: LocalDate? = null
    ): Pair<Map<Account, Inventory>, Int> {
        val balances = mutableMapOf<Account, Inventory>()
        var index = 0

        for ((i, entry) in entries.withIndex()) {
            if (date != null && entry.date >= date) {
                index = i
                break
            }

            if (entry is Transaction) {
                for (posting in entry.postings) {
                    val accountBalance = balances.getOrPut(posting.account) { Inventory() }
                    val units = posting.units ?: continue
                    val cost = posting.cost?.let { spec ->
                        if (spec.numberPer != null && spec.currency != null) {
                            Cost(spec.numberPer, spec.currency, spec.date ?: entry.date, spec.label)
                        } else null
                    }
                    accountBalance.addAmount(units, cost)
                }
            }
            index = i + 1
        }

        return balances to index
    }

    /**
     * Gather the list of active Open entries at date.
     */
    fun getOpenEntries(entries: List<Directive>, date: LocalDate? = null): List<Open> {
        val openEntries = mutableMapOf<Account, Pair<Int, Open>>()

        for ((index, entry) in entries.withIndex()) {
            if (date != null && entry.date >= date) break

            if (entry is Open) {
                val existing = openEntries[entry.account]
                if (existing == null || entry.date < existing.second.date) {
                    openEntries[entry.account] = index to entry
                }
            } else if (entry is Close) {
                openEntries.remove(entry.account)
            }
        }

        return openEntries.values.sortedBy { it.first }.map { it.second }
    }

    /**
     * Compute the balance of all postings of a list of entries.
     */
    fun computeEntriesBalance(
        entries: List<Directive>,
        prefix: Account? = null,
        date: LocalDate? = null
    ): Inventory {
        val totalBalance = Inventory()

        for (entry in entries) {
            if (date != null && entry.date >= date) break

            if (entry is Transaction) {
                for (posting in entry.postings) {
                    if (prefix != null && !posting.account.startsWith(prefix)) continue
                    val units = posting.units ?: continue
                    val cost = posting.cost?.let { spec ->
                        if (spec.numberPer != null && spec.currency != null) {
                            Cost(spec.numberPer, spec.currency, spec.date ?: entry.date, spec.label)
                        } else null
                    }
                    totalBalance.addAmount(units, cost)
                }
            }
        }

        return totalBalance
    }

    /**
     * Get last price entries before date for each (currency, cost-currency) pair.
     */
    fun getLastPriceEntries(entries: List<Directive>, date: LocalDate? = null): List<Price> {
        val priceMap = mutableMapOf<Pair<Currency, Currency>, Price>()

        for (entry in entries) {
            if (date != null && entry.date >= date) break

            if (entry is Price) {
                val key = entry.currency to entry.amount.currency
                priceMap[key] = entry
            }
        }

        return priceMap.values.sorted()
    }

    /**
     * Binary search for first entry with date >= target date.
     */
    private fun bisectLeftByDate(entries: List<Directive>, date: LocalDate): Int {
        var lo = 0
        var hi = entries.size

        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (entries[mid].date < date) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        return lo
    }

    /**
     * Create new metadata for synthesized entries.
     */
    private fun newMetadata(filename: String, lineno: Int): Meta {
        return mapOf(
            "filename" to filename,
            "lineno" to lineno
        )
    }
}

/**
 * Utility class for account type classification used by Summarize.
 * Wraps AccountTypes functions for easier use.
 */
class AccountTypesUtil {
    fun isIncomeStatementAccount(account: Account): Boolean {
        return isIncomeAccount(account) || isExpensesAccount(account)
    }

    private fun isIncomeAccount(account: Account): Boolean {
        return account.startsWith("Income:")
    }

    private fun isExpensesAccount(account: Account): Boolean {
        return account.startsWith("Expenses:")
    }
}
