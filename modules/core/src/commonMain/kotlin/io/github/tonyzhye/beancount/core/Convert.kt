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
 * Conversions from Position (or Posting) to units, cost, weight, market value.
 *
 * - Units: Just the primary amount of the position.
 * - Cost: The cost basis of the position, if available.
 * - Weight: The cost basis or price of the position (used for balancing).
 * - Market Value: The units converted to a value via a price database.
 *
 * Based on beancount.core.convert.
 */

/**
 * Return the units of a Position.
 */
fun getUnits(pos: Position): Amount = pos.units

/**
 * Return the units of a Posting.
 */
fun getUnits(posting: Posting): Amount = posting.units!!

/**
 * Return the total cost of a Position.
 *
 * If the position has a cost basis, returns (cost.number * units.number, cost.currency).
 * Otherwise returns the units unchanged.
 */
fun getCost(pos: Position): Amount {
    val cost = pos.cost
    return if (cost != null) {
        Amount(cost.number * pos.units.number, cost.currency)
    } else {
        pos.units
    }
}

/**
 * Return the total cost of a Posting.
 *
 * Note: Posting cost is CostSpec (may be incomplete). Only returns cost if
 * all required fields (numberPer and currency) are present.
 */
fun getCost(posting: Posting): Amount {
    val units = posting.units!!
    val costSpec = posting.cost
    return if (costSpec != null && costSpec.numberPer != null && costSpec.currency != null) {
        Amount(costSpec.numberPer * units.number, costSpec.currency)
    } else {
        units
    }
}

/**
 * Return the weight of a Position.
 *
 * The weight is the amount used to check transaction balance:
 * - If position has cost, weight = cost basis (cost.number * units.number, cost.currency)
 * - Otherwise weight = units
 */
fun getWeight(pos: Position): Amount {
    val cost = pos.cost
    return if (cost != null) {
        Amount(cost.number * pos.units.number, cost.currency)
    } else {
        pos.units
    }
}

/**
 * Return the weight of a Posting.
 *
 * This is a *key* element of transaction semantics:
 * - Assets:Account  5234.50 USD                             ->  5234.50 USD
 * - Assets:Account  3877.41 EUR @ 1.35 USD                  ->  5234.50 USD
 * - Assets:Account       10 HOOL {523.45 USD}               ->  5234.50 USD
 * - Assets:Account       10 HOOL {523.45 USD} @ 545.60 CAD  ->  5234.50 USD
 */
fun getWeight(posting: Posting): Amount {
    val units = posting.units ?: return Amount(Decimal.ZERO, "XXX")
    val costSpec = posting.cost

    // If the posting has a cost, use that as the weight.
    if (costSpec != null && costSpec.numberPer != null && costSpec.currency != null) {
        return Amount(costSpec.numberPer * units.number, costSpec.currency)
    }

    // Otherwise use the units.
    var weight = units

    // Unless there is a price available; use that if present.
    val price = posting.price
    if (price != null) {
        weight = Amount(price.number * units.number, price.currency)
    }

    return weight
}

/**
 * Return the market value of a Position.
 *
 * Looks up the price of the position's currency in its cost currency
 * (or the price currency for postings).
 *
 * @param pos The position to value.
 * @param priceMap The price database.
 * @param date The date to evaluate at, or null for latest.
 * @return The converted amount, or the original units if no price found.
 */
fun getValue(pos: Position, priceMap: PriceDatabase, date: LocalDate? = null): Amount {
    val units = pos.units
    val cost = pos.cost

    // Try to infer what the cost/price currency should be.
    val valueCurrency = cost?.currency

    return if (valueCurrency != null) {
        val (_, rate) = priceMap.getPrice(units.currency, valueCurrency, date)
        if (rate != null) {
            Amount(units.number * rate, valueCurrency)
        } else {
            units
        }
    } else {
        units
    }
}

/**
 * Return the market value of a Posting.
 *
 * Looks up the price of the posting's currency in its cost currency
 * or price currency.
 */
fun getValue(posting: Posting, priceMap: PriceDatabase, date: LocalDate? = null): Amount {
    val units = posting.units!!
    val costSpec = posting.cost
    val price = posting.price

    // Try to infer what the cost/price currency should be.
    val valueCurrency = costSpec?.currency ?: price?.currency

    return if (valueCurrency != null) {
        val (_, rate) = priceMap.getPrice(units.currency, valueCurrency, date)
        if (rate != null) {
            Amount(units.number * rate, valueCurrency)
        } else {
            units
        }
    } else {
        units
    }
}

/**
 * Convert an Amount to a target currency using a price map.
 *
 * @param amount The amount to convert.
 * @param targetCurrency The target currency.
 * @param priceMap The price database.
 * @param date The date to evaluate at, or null for latest.
 * @param via Optional list of intermediate currencies to attempt indirect conversion.
 * @return The converted amount, or the original amount if conversion fails.
 */
fun convertAmount(
    amount: Amount,
    targetCurrency: Currency,
    priceMap: PriceDatabase,
    date: LocalDate? = null,
    via: List<Currency>? = null
): Amount {
    if (amount.currency == targetCurrency) {
        return amount
    }

    // First, attempt direct conversion.
    val (_, rate) = priceMap.getPrice(amount.currency, targetCurrency, date)
    if (rate != null) {
        return Amount(amount.number * rate, targetCurrency)
    }

    // Try indirect conversion via intermediate currencies.
    if (via != null) {
        for (impliedCurrency in via) {
            if (impliedCurrency == targetCurrency) continue

            val (_, rate1) = priceMap.getPrice(amount.currency, impliedCurrency, date)
            if (rate1 != null) {
                val (_, rate2) = priceMap.getPrice(impliedCurrency, targetCurrency, date)
                if (rate2 != null) {
                    return Amount(amount.number * rate1 * rate2, targetCurrency)
                }
            }
        }
    }

    // Conversion failed; return original amount.
    return amount
}

/**
 * Convert a Position to a target currency.
 *
 * Attempts to convert via the position's cost currency if direct conversion fails.
 */
fun convertPosition(
    pos: Position,
    targetCurrency: Currency,
    priceMap: PriceDatabase,
    date: LocalDate? = null
): Amount {
    val valueCurrency = pos.cost?.currency
    return convertAmount(
        pos.units, targetCurrency, priceMap, date,
        via = if (valueCurrency != null) listOf(valueCurrency) else null
    )
}

/**
 * Convert a Posting to a target currency.
 *
 * Attempts to convert via the posting's cost or price currency if direct conversion fails.
 */
fun convertPosting(
    posting: Posting,
    targetCurrency: Currency,
    priceMap: PriceDatabase,
    date: LocalDate? = null
): Amount {
    val valueCurrency = posting.cost?.currency ?: posting.price?.currency
    return convertAmount(
        posting.units!!, targetCurrency, priceMap, date,
        via = if (valueCurrency != null) listOf(valueCurrency) else null
    )
}

/**
 * Convert an Inventory to a target currency.
 */
fun convertInventory(
    inventory: Inventory,
    targetCurrency: Currency,
    priceMap: PriceDatabase,
    date: LocalDate? = null
): Inventory {
    val result = Inventory()
    for (position in inventory) {
        val converted = convertPosition(position, targetCurrency, priceMap, date)
        result.addAmount(converted)
    }
    return result
}

/**
 * Get the market value of an Inventory.
 */
fun getValue(inventory: Inventory, priceMap: PriceDatabase, date: LocalDate? = null): Inventory {
    val result = Inventory()
    for (position in inventory) {
        val valued = getValue(position, priceMap, date)
        result.addAmount(valued)
    }
    return result
}

/**
 * Return the units of an Inventory (stripping cost).
 */
fun getUnits(inventory: Inventory): Inventory {
    val result = Inventory()
    for (position in inventory) {
        result.addAmount(position.units)
    }
    return result
}

/**
 * Return the cost of an Inventory.
 */
fun getCost(inventory: Inventory): Inventory {
    val result = Inventory()
    for (position in inventory) {
        val costAmount = getCost(position)
        result.addAmount(costAmount)
    }
    return result
}
