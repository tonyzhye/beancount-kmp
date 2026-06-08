/*
 * Beancount JVM - A JVM implementation of Beancount
 * Copyright (C) 2026  Beancount JVM Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Beancount by Martin Blais
 * Original project: https://github.com/beancount/beancount
 */

package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * A distribution accumulator that maintains counts for discrete values.
 * Based on beancount.core.distribution.Distribution.
 *
 * This is useful for computing statistics over a set of numbers,
 * such as the distribution of fractional digits in amounts.
 *
 * Example:
 * ```kotlin
 * val dist = Distribution()
 * dist.add(2)  // count of 2
 * dist.add(2)  // count of 2 again
 * dist.add(5)  // count of 5
 * println(dist.mode()) // 2 (most frequent)
 * ```
 */
class Distribution {
    private val counts = mutableMapOf<Int, Int>()
    private var totalCount = 0

    /** Add a value to the distribution */
    fun add(value: Int) {
        counts[value] = (counts[value] ?: 0) + 1
        totalCount++
    }

    /** Add multiple values to the distribution */
    fun addAll(values: List<Int>) {
        values.forEach { add(it) }
    }

    /** Get the count for a specific value */
    fun count(value: Int): Int = counts[value] ?: 0

    /** Get the frequency (proportion) for a specific value */
    fun frequency(value: Int): Double {
        if (totalCount == 0) return 0.0
        return count(value).toDouble() / totalCount
    }

    /** Get the minimum value seen */
    fun min(): Int? = counts.keys.minOrNull()

    /** Get the maximum value seen */
    fun max(): Int? = counts.keys.maxOrNull()

    /** Get the mode (most frequent value) */
    fun mode(): Int? {
        if (counts.isEmpty()) return null
        return counts.maxByOrNull { it.value }?.key
    }

    /** Get all values seen */
    fun values(): Set<Int> = counts.keys.toSet()

    /** Get the total number of samples */
    fun total(): Int = totalCount

    /** Get the number of unique values */
    fun unique(): Int = counts.size

    /** Check if the distribution is empty */
    fun isEmpty(): Boolean = counts.isEmpty()

    /** Get a sorted list of (value, count) pairs */
    fun sortedPairs(): List<Pair<Int, Int>> {
        return counts.toList().sortedBy { it.first }
    }

    /** Get the mean of the distribution */
    fun mean(): Double {
        if (totalCount == 0) return 0.0
        val sum = counts.entries.sumOf { it.key * it.value }
        return sum.toDouble() / totalCount
    }

    /**
     * Update this distribution from another distribution.
     */
    fun updateFrom(other: Distribution) {
        for ((value, count) in other.counts) {
            this.counts[value] = (this.counts[value] ?: 0) + count
            this.totalCount += count
        }
    }

    override fun toString(): String {
        if (counts.isEmpty()) return "Distribution(empty)"
        val pairs = sortedPairs().joinToString(", ") { "${it.first}:${it.second}" }
        return "Distribution($pairs, total=$totalCount)"
    }
}

/**
 * Build a distribution of fractional digit counts from a list of Decimals.
 *
 * @param numbers List of Decimal values.
 * @return A Distribution of fractional digit counts.
 */
fun buildFractionalDistribution(numbers: List<Decimal>): Distribution {
    val dist = Distribution()
    for (number in numbers) {
        val plainStr = number.toPlainString()
        val dotIndex = plainStr.indexOf('.')
        val fractionalDigits = if (dotIndex >= 0) {
            plainStr.length - dotIndex - 1
        } else {
            0
        }
        dist.add(fractionalDigits)
    }
    return dist
}

/**
 * Build a distribution of transaction amounts by currency.
 *
 * @param entries List of entries.
 * @return A map from currency to Distribution of amount magnitudes.
 */
fun buildAmountDistribution(entries: List<Directive>): Map<String, Distribution> {
    val distributions = mutableMapOf<String, MutableList<Int>>()

    for (entry in entries.filterIsInstance<Transaction>()) {
        for (posting in entry.postings) {
            val units = posting.units ?: continue
            val currency = units.currency
            val magnitude = units.number.abs().toPlainString().length
            distributions.getOrPut(currency) { mutableListOf() }.add(magnitude)
        }
    }

    return distributions.mapValues { (_, values) ->
        Distribution().apply { addAll(values) }
    }
}

/**
 * Build a distribution of posting counts per transaction.
 *
 * @param entries List of entries.
 * @return A Distribution of posting counts.
 */
fun buildPostingCountDistribution(entries: List<Directive>): Distribution {
    val dist = Distribution()
    for (entry in entries.filterIsInstance<Transaction>()) {
        dist.add(entry.postings.size)
    }
    return dist
}

/**
 * Build a monthly transaction frequency distribution.
 *
 * @param entries List of entries.
 * @return A Distribution of monthly transaction counts.
 */
fun buildMonthlyFrequencyDistribution(entries: List<Directive>): Distribution {
    val monthlyCounts = mutableMapOf<String, Int>()

    for (entry in entries.filterIsInstance<Transaction>()) {
        val monthKey = "${entry.date.year}-${entry.date.monthNumber.toString().padStart(2, '0')}"
        monthlyCounts[monthKey] = (monthlyCounts[monthKey] ?: 0) + 1
    }

    val dist = Distribution()
    for (count in monthlyCounts.values) {
        dist.add(count)
    }
    return dist
}
