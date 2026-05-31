package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Given a Beancount ledger, compute time intervals where we hold each commodity.
 *
 * This script computes, for each commodity, which time intervals it is required at.
 * This can then be used to identify a list of dates at which we need to fetch prices
 * in order to properly fill the price database.
 *
 * Based on beancount.ops.lifetimes
 */
object Lifetimes {

    /**
     * Given a list of directives, figure out the life of each commodity.
     *
     * @param entries A list of directives.
     * @return A map of (currency, cost-currency) commodity strings to lists of (start, end)
     *         date pairs. The dates are inclusive of the day the commodity was seen;
     *         the end/last dates are one day _after_ the last date seen.
     */
    fun getCommodityLifetimes(entries: List<Directive>): Map<Pair<Currency, Currency?>, List<Pair<LocalDate, LocalDate?>>> {
        val lifetimes = mutableMapOf<Pair<Currency, Currency?>, MutableList<Pair<LocalDate, LocalDate?>>>()

        var commodities = setOf<Pair<Currency, Currency?>>()
        val balances = mutableMapOf<Account, Inventory>()

        for (entry in entries) {
            if (entry !is Transaction) continue

            var commoditiesChanged = false
            for (posting in entry.postings) {
                val balance = balances.getOrPut(posting.account) { Inventory() }
                val commoditiesBefore = balance.currencyPairs()

                val units = posting.units ?: continue
                val cost = posting.cost?.let { spec ->
                    if (spec.numberPer != null && spec.currency != null) {
                        Cost(spec.numberPer, spec.currency, spec.date ?: entry.date, spec.label)
                    } else null
                }
                balance.addAmount(units, cost)

                val commoditiesAfter = balance.currencyPairs()
                if (commoditiesAfter != commoditiesBefore) {
                    commoditiesChanged = true
                }
            }

            if (commoditiesChanged) {
                val newCommodities = balances.values.flatMap { it.currencyPairs() }.toSet()

                if (newCommodities != commodities) {
                    for (currency in newCommodities - commodities) {
                        lifetimes.getOrPut(currency) { mutableListOf() }
                            .add(entry.date to null)
                    }

                    for (currency in commodities - newCommodities) {
                        val lifetime = lifetimes.getValue(currency)
                        val (beginDate, _) = lifetime.removeAt(lifetime.size - 1)
                        lifetime.add(beginDate to entry.date.plus(1, DateTimeUnit.DAY))
                    }

                    commodities = newCommodities
                }
            }
        }

        return lifetimes
    }

    /**
     * Compress a list of date pairs to ignore short stretches of unused days.
     */
    fun compressIntervalsDays(
        intervals: List<Pair<LocalDate, LocalDate?>>,
        numDays: Int
    ): List<Pair<LocalDate, LocalDate?>> {
        if (intervals.isEmpty()) return emptyList()

        val ignoreInterval = numDays
        val newIntervals = mutableListOf<Pair<LocalDate, LocalDate?>>()
        val iter = intervals.iterator()

        var (lastBegin, lastEnd) = iter.next()

        for ((dateBegin, dateEnd) in iter) {
            val endDate = lastEnd ?: dateBegin
            val daysBetween = dateBegin.toEpochDays() - endDate.toEpochDays()

            if (daysBetween < ignoreInterval) {
                lastEnd = dateEnd
                continue
            }
            newIntervals.add(lastBegin to lastEnd)
            lastBegin = dateBegin
            lastEnd = dateEnd
        }
        newIntervals.add(lastBegin to lastEnd)

        return newIntervals
    }

    /**
     * Trim a list of date pairs to be within a start and end date.
     */
    fun trimIntervals(
        intervals: List<Pair<LocalDate, LocalDate?>>,
        trimStart: LocalDate? = null,
        trimEnd: LocalDate? = null
    ): List<Pair<LocalDate, LocalDate?>> {
        if (trimStart != null && trimEnd != null && trimEnd < trimStart) {
            throw IllegalArgumentException("Trim end date is before start date")
        }

        return intervals.mapNotNull { (dateBegin, dateEnd) ->
            var newBegin = dateBegin
            var newEnd = dateEnd

            if (trimStart != null && trimStart > newBegin) {
                newBegin = trimStart
            }
            if (trimEnd != null) {
                if (newEnd == null || trimEnd < newEnd) {
                    newEnd = trimEnd
                }
            }

            if (newEnd == null || newBegin <= newEnd) {
                newBegin to newEnd
            } else null
        }
    }

    /**
     * Compress a lifetimes map to ignore short stretches of unused days.
     */
    fun compressLifetimesDays(
        lifetimesMap: Map<Pair<Currency, Currency?>, List<Pair<LocalDate, LocalDate?>>>,
        numDays: Int
    ): Map<Pair<Currency, Currency?>, List<Pair<LocalDate, LocalDate?>>> {
        return lifetimesMap.mapValues { (_, intervals) ->
            compressIntervalsDays(intervals, numDays)
        }
    }

    /**
     * Enumerate all the commodities and Fridays where the price is required.
     */
    fun requiredWeeklyPrices(
        lifetimesMap: Map<Pair<Currency, Currency?>, List<Pair<LocalDate, LocalDate?>>>,
        dateLast: LocalDate
    ): List<Triple<LocalDate, Currency, Currency>> {
        val results = mutableListOf<Triple<LocalDate, Currency, Currency>>()

        for ((currencyPair, intervals) in lifetimesMap) {
            val (currency, costCurrency) = currencyPair
            if (costCurrency == null) continue

            for ((dateBegin, dateEnd) in intervals) {
                val end = dateEnd ?: dateLast

                // Find first Friday before or on the minimum date
                var date = dateBegin
                val dayOfWeek = date.dayOfWeek.value // 1=Monday, 5=Friday
                var diffDays = 5 - dayOfWeek
                if (diffDays >= 1) {
                    diffDays -= 7
                }
                date = date.plus(diffDays, DateTimeUnit.DAY)

                // Iterate over all Fridays
                while (date < end) {
                    results.add(Triple(date, currency, costCurrency))
                    date = date.plus(7, DateTimeUnit.DAY)
                }
            }
        }

        return results.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
    }

    /**
     * Enumerate all the commodities and days where the price is required.
     */
    fun requiredDailyPrices(
        lifetimesMap: Map<Pair<Currency, Currency?>, List<Pair<LocalDate, LocalDate?>>>,
        dateLast: LocalDate,
        weekdaysOnly: Boolean = false
    ): List<Triple<LocalDate, Currency, Currency>> {
        val results = mutableListOf<Triple<LocalDate, Currency, Currency>>()

        for ((currencyPair, intervals) in lifetimesMap) {
            val (currency, costCurrency) = currencyPair
            if (costCurrency == null) continue

            for ((dateBegin, dateEnd) in intervals) {
                val end = dateEnd ?: dateLast

                var date = dateBegin

                if (weekdaysOnly) {
                    // Find first Friday before or on the minimum date
                    val dayOfWeek = date.dayOfWeek.value
                    val diffDays = 5 - dayOfWeek
                    if (diffDays < 0) {
                        date = date.plus(diffDays, DateTimeUnit.DAY)
                    }
                }

                while (date < end) {
                    results.add(Triple(date, currency, costCurrency))

                    if (weekdaysOnly && date.dayOfWeek.value == 5) {
                        // Friday -> skip to Monday
                        date = date.plus(3, DateTimeUnit.DAY)
                    } else {
                        date = date.plus(1, DateTimeUnit.DAY)
                    }
                }
            }
        }

        return results.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
    }

    /**
     * Extension function to get currency pairs from an inventory.
     * Returns pairs of (units.currency, cost.currency) for all positions.
     */
    private fun Inventory.currencyPairs(): Set<Pair<Currency, Currency?>> {
        return getPositions().map { position ->
            position.units.currency to position.cost?.currency
        }.toSet()
    }

}
