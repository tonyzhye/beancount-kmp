package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * Price database for managing and querying historical prices.
 * Based on beancount.core.prices.
 *
 * Features:
 * - Build from Price directives
 * - Lookup prices by date (with interpolation)
 * - Get latest price
 * - Currency projection (convert prices between currencies)
 * - Automatic inverse rate generation
 */
class PriceDatabase private constructor(
    private val priceMap: Map<Pair<String, String>, List<Pair<LocalDate, Decimal>>>
) {
    /**
     * Forward pairs that were explicitly defined in the ledger.
     */
    val forwardPairs: Set<Pair<String, String>> = priceMap.keys.toSet()

    /**
     * Get all prices for a currency pair.
     *
     * @param base Base currency/commodity
     * @param quote Quote currency (denomination)
     * @return List of (date, rate) pairs, sorted by date
     */
    fun getAllPrices(base: String, quote: String): List<Pair<LocalDate, Decimal>> {
        return priceMap[Pair(base, quote)] ?: emptyList()
    }

    /**
     * Get the price as of the given date.
     * Returns the most recent price on or before the given date.
     * If no date is specified, returns the latest price.
     *
     * @param base Base currency/commodity
     * @param quote Quote currency
     * @param date Date to lookup (null for latest)
     * @return Pair of (price_date, rate), or (null, null) if not found
     */
    fun getPrice(base: String, quote: String, date: LocalDate? = null): Pair<LocalDate?, Decimal?> {
        if (date == null) {
            return getLatestPrice(base, quote)
        }

        // Handle degenerate case: currency priced into itself
        if (base == quote) {
            return Pair(null, Decimal.ONE)
        }

        val prices = getAllPrices(base, quote)
        if (prices.isEmpty()) {
            return Pair(null, null)
        }

        // Binary search for the rightmost price with date <= target date
        val index = bisectRight(prices, date)
        return if (index > 0) {
            val (priceDate, rate) = prices[index - 1]
            Pair(priceDate, rate)
        } else {
            Pair(null, null)
        }
    }

    /**
     * Get the latest price for a currency pair.
     *
     * @param base Base currency/commodity
     * @param quote Quote currency
     * @return Pair of (price_date, rate), or (null, null) if not found
     */
    fun getLatestPrice(base: String, quote: String): Pair<LocalDate?, Decimal?> {
        // Handle degenerate case: currency priced into itself
        if (base == quote) {
            return Pair(null, Decimal.ONE)
        }

        val prices = getAllPrices(base, quote)
        return if (prices.isNotEmpty()) {
            val (date, rate) = prices.last()
            Pair(date, rate)
        } else {
            Pair(null, null)
        }
    }

    /**
     * Get the price at a specific date, with interpolation.
     * If no exact price exists at the given date, linearly interpolate
     * between the nearest two price points.
     *
     * @param base Base currency/commodity
     * @param quote Quote currency
     * @param date Target date
     * @return Interpolated price, or null if not enough data
     */
    fun getInterpolatedPrice(base: String, quote: String, date: LocalDate): Decimal? {
        // Handle degenerate case
        if (base == quote) {
            return Decimal.ONE
        }

        val prices = getAllPrices(base, quote)
        if (prices.isEmpty()) {
            return null
        }

        // Find the index where date would be inserted
        val index = bisectRight(prices, date)

        if (index == 0) {
            // Date is before first price point - return first price
            return prices[0].second
        }
        if (index >= prices.size) {
            // Date is after last price point - return last price
            return prices.last().second
        }

        // We have prices on both sides - interpolate
        val (date1, price1) = prices[index - 1]
        val (date2, price2) = prices[index]

        return interpolate(date1, price1, date2, price2, date)
    }

    /**
     * Convert an amount from one currency to another at a given date.
     *
     * @param amount Amount to convert
     * @param targetCurrency Target currency
     * @param date Date for conversion rate
     * @return Converted amount, or null if rate not found
     */
    fun convert(amount: Amount, targetCurrency: String, date: LocalDate): Amount? {
        val (_, rate) = getPrice(amount.currency, targetCurrency, date)
        return rate?.let {
            Amount(amount.number * it, targetCurrency)
        }
    }

    /**
     * Project prices from one quote currency to another.
     * E.g., if you have HOOL/USD prices, project them to HOOL/CAD using USD/CAD rates.
     *
     * @param fromCurrency Source quote currency (e.g., "USD")
     * @param toCurrency Target quote currency (e.g., "CAD")
     * @param baseCurrencies Optional set of base commodities to restrict projection
     * @return New PriceDatabase with projected prices
     */
    fun project(fromCurrency: String, toCurrency: String, baseCurrencies: Set<String>? = null): PriceDatabase {
        if (fromCurrency == toCurrency) {
            return this
        }

        val newMap = priceMap.mapValues { (_, list) -> list.toList() }.toMutableMap()
        val currencyPair = Pair(fromCurrency, toCurrency)

        for ((baseQuote, prices) in priceMap) {
            val (base, quote) = baseQuote
            if (quote != fromCurrency) continue
            if (baseCurrencies != null && base !in baseCurrencies) continue

            // Get existing prices for the target pair to avoid date collisions
            val existingDates = newMap[Pair(base, toCurrency)]?.map { it.first }?.toSet() ?: emptySet()

            val newProjected = mutableListOf<Pair<LocalDate, Decimal>>()
            for ((priceDate, price) in prices) {
                val (_, rate) = getPrice(fromCurrency, toCurrency, priceDate)
                if (rate == null) continue
                if (priceDate in existingDates) continue

                newProjected.add(Pair(priceDate, price * rate))
            }

            if (newProjected.isNotEmpty()) {
                // Insert projected prices
                val targetList = newMap.getOrPut(Pair(base, toCurrency)) { mutableListOf() }.toMutableList()
                targetList.addAll(newProjected)
                targetList.sortBy { it.first }
                newMap[Pair(base, toCurrency)] = targetList

                // Insert inverse rates
                val inverseList = newMap.getOrPut(Pair(toCurrency, base)) { mutableListOf() }.toMutableList()
                for ((projDate, projRate) in newProjected) {
                    if (projRate.isZero()) continue
                    inverseList.add(Pair(projDate, Decimal.ONE / projRate))
                }
                inverseList.sortBy { it.first }
                newMap[Pair(toCurrency, base)] = inverseList
            }
        }

        return PriceDatabase(newMap)
    }

    companion object {
        /**
         * Build a PriceDatabase from a list of entries.
         *
         * @param entries List of directives (should include Price directives)
         * @return PriceDatabase instance
         */
        fun build(entries: List<Directive>): PriceDatabase {
            // Extract Price entries
            val priceEntries = entries.filterIsInstance<Price>()

            // Build map: (base, quote) -> list of (date, rate)
            val priceMap = mutableMapOf<Pair<String, String>, MutableList<Pair<LocalDate, Decimal>>>()

            for (price in priceEntries) {
                val baseQuote = Pair(price.currency, price.amount.currency)
                priceMap.getOrPut(baseQuote) { mutableListOf() }
                    .add(Pair(price.date, price.amount.number))
            }

            // Handle inverse pairs - merge the smaller into the larger
            val inversedUnits = priceMap.keys.filter { (base, quote) ->
                priceMap.containsKey(Pair(quote, base))
            }.toList()

            val processedPairs = mutableSetOf<Pair<String, String>>()
            for ((base, quote) in inversedUnits) {
                val pair = Pair(base, quote)
                if (pair in processedPairs) continue
                
                val inversePair = Pair(quote, base)
                val bqPrices = priceMap[pair]
                val qbPrices = priceMap[inversePair]
                
                if (bqPrices == null || qbPrices == null) continue

                val remove = if (bqPrices.size < qbPrices.size) pair else inversePair
                val keep = if (remove == pair) inversePair else pair

                val removeList = priceMap.remove(remove)
                if (removeList != null) {
                    val insertList = priceMap[keep]
                    if (insertList != null) {
                        // Convert inverted rates and add to the kept list
                        for ((date, rate) in removeList) {
                            if (!rate.isZero()) {
                                insertList.add(Pair(date, Decimal.ONE / rate))
                            }
                        }
                    }
                }
                
                processedPairs.add(pair)
                processedPairs.add(inversePair)
            }

            // Sort each list and remove duplicate dates (keep last)
            val sortedMap = mutableMapOf<Pair<String, String>, List<Pair<LocalDate, Decimal>>>()
            for ((baseQuote, dateRates) in priceMap) {
                // Sort by date
                val sorted = dateRates.sortedBy { it.first }
                // Remove duplicates (keep last entry for each date)
                val deduplicated = sorted.groupBy { it.first }
                    .mapValues { (_, entries) -> entries.last().second }
                    .toList()
                    .sortedBy { it.first }
                sortedMap[baseQuote] = deduplicated
            }

            // Generate inverse rates for all pairs
            val finalMap = sortedMap.toMutableMap()
            for ((baseQuote, priceList) in sortedMap) {
                val (base, quote) = baseQuote
                val inverseList = priceList.mapNotNull { (date, price) ->
                    if (price.isZero()) null else Pair(date, Decimal.ONE / price)
                }
                finalMap[Pair(quote, base)] = inverseList
            }

            return PriceDatabase(finalMap)
        }

        /**
         * Build a PriceDatabase from a list of Price entries directly.
         */
        fun buildFromPrices(prices: List<Price>): PriceDatabase {
            return build(prices)
        }
    }
}

/**
 * Binary search: find the index where date should be inserted to maintain order.
 * Returns the rightmost position where all elements before are <= date.
 */
private fun bisectRight(prices: List<Pair<LocalDate, Decimal>>, date: LocalDate): Int {
    var left = 0
    var right = prices.size
    while (left < right) {
        val mid = (left + right) / 2
        if (prices[mid].first <= date) {
            left = mid + 1
        } else {
            right = mid
        }
    }
    return left
}

/**
 * Linear interpolation between two price points.
 */
private fun interpolate(
    date1: LocalDate, price1: Decimal,
    date2: LocalDate, price2: Decimal,
    targetDate: LocalDate
): Decimal {
    if (date1 == date2) return price1

    // Calculate days between dates (simplified)
    val totalDays = daysBetween(date1, date2)
    val elapsedDays = daysBetween(date1, targetDate)

    if (totalDays == 0) return price1

    val ratio = Decimal(elapsedDays.toString()) / Decimal(totalDays.toString())
    val diff = price2 - price1
    return price1 + (diff * ratio)
}

/**
 * Calculate days between two dates (simplified, doesn't handle leap years perfectly).
 */
private fun daysBetween(start: LocalDate, end: LocalDate): Int {
    return (end.year - start.year) * 365 +
           (end.monthNumber - start.monthNumber) * 30 +
           (end.dayOfMonth - start.dayOfMonth)
}

/**
 * Get the last price entries before a given date for each currency pair.
 * Useful for displaying current prices.
 */
fun getLastPriceEntries(entries: List<Directive>, date: LocalDate?): List<Price> {
    val priceEntryMap = mutableMapOf<Pair<String, String>, Price>()

    for (entry in entries) {
        if (date != null && entry.date >= date) break
        if (entry is Price) {
            val baseQuote = Pair(entry.currency, entry.amount.currency)
            priceEntryMap[baseQuote] = entry
        }
    }

    return priceEntryMap.values.sortedBy { it.date }
}
