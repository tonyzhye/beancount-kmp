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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ConvertTest {

    // ===== getUnits =====

    @Test
    fun `getUnits should return position units`() {
        val pos = Position(Amount(Decimal("100"), "USD"))
        assertEquals(Amount(Decimal("100"), "USD"), getUnits(pos))
    }

    @Test
    fun `getUnits should return posting units`() {
        val posting = Posting("Assets:Bank", Amount(Decimal("100"), "USD"))
        assertEquals(Amount(Decimal("100"), "USD"), getUnits(posting))
    }

    // ===== getCost =====

    @Test
    fun `getCost should return cost basis for position with cost`() {
        val pos = Position(
            units = Amount(Decimal("10"), "AAPL"),
            cost = Cost(Decimal("150"), "USD", LocalDate(2024, 1, 1))
        )
        assertEquals(Amount(Decimal("1500"), "USD"), getCost(pos))
    }

    @Test
    fun `getCost should return units for position without cost`() {
        val pos = Position(Amount(Decimal("100"), "USD"))
        assertEquals(Amount(Decimal("100"), "USD"), getCost(pos))
    }

    @Test
    fun `getCost should return cost basis for posting with cost`() {
        val posting = Posting(
            "Assets:Invest",
            Amount(Decimal("10"), "AAPL"),
            cost = CostSpec(numberPer = Decimal("150"), currency = "USD")
        )
        assertEquals(Amount(Decimal("1500"), "USD"), getCost(posting))
    }

    @Test
    fun `getCost should return units for posting without cost`() {
        val posting = Posting("Assets:Bank", Amount(Decimal("100"), "USD"))
        assertEquals(Amount(Decimal("100"), "USD"), getCost(posting))
    }

    // ===== getWeight =====

    @Test
    fun `getWeight should return cost basis for position with cost`() {
        val pos = Position(
            units = Amount(Decimal("10"), "AAPL"),
            cost = Cost(Decimal("150"), "USD", LocalDate(2024, 1, 1))
        )
        assertEquals(Amount(Decimal("1500"), "USD"), getWeight(pos))
    }

    @Test
    fun `getWeight should return units for position without cost`() {
        val pos = Position(Amount(Decimal("100"), "USD"))
        assertEquals(Amount(Decimal("100"), "USD"), getWeight(pos))
    }

    @Test
    fun `getWeight should use price for posting with price but no cost`() {
        val posting = Posting(
            "Assets:Bank",
            Amount(Decimal("100"), "EUR"),
            price = Amount(Decimal("1.10"), "USD")
        )
        assertEquals(Amount(Decimal("110"), "USD"), getWeight(posting))
    }

    @Test
    fun `getWeight should return units for posting without cost or price`() {
        val posting = Posting("Assets:Bank", Amount(Decimal("100"), "USD"))
        assertEquals(Amount(Decimal("100"), "USD"), getWeight(posting))
    }

    // ===== getValue =====

    @Test
    fun `getValue should convert position using price map`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "AAPL", Amount(Decimal("160"), "USD"))
        )
        val priceMap = PriceDatabase.build(entries)
        val pos = Position(
            units = Amount(Decimal("10"), "AAPL"),
            cost = Cost(Decimal("150"), "USD", LocalDate(2024, 1, 1))
        )
        assertEquals(Amount(Decimal("1600"), "USD"), getValue(pos, priceMap))
    }

    @Test
    fun `getValue should return units when no price found`() {
        val entries = emptyList<Directive>()
        val priceMap = PriceDatabase.build(entries)
        val pos = Position(
            units = Amount(Decimal("10"), "AAPL"),
            cost = Cost(Decimal("150"), "USD", LocalDate(2024, 1, 1))
        )
        assertEquals(Amount(Decimal("10"), "AAPL"), getValue(pos, priceMap))
    }

    @Test
    fun `getValue should convert posting using price map`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.build(entries)
        val posting = Posting(
            "Assets:Bank",
            Amount(Decimal("100"), "EUR"),
            price = Amount(Decimal("1.10"), "USD")
        )
        assertEquals(Amount(Decimal("110"), "USD"), getValue(posting, priceMap))
    }

    // ===== convertAmount =====

    @Test
    fun `convertAmount should convert directly when rate exists`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.build(entries)
        val result = convertAmount(Amount(Decimal("100"), "EUR"), "USD", priceMap)
        assertEquals(Amount(Decimal("110"), "USD"), result)
    }

    @Test
    fun `convertAmount should return original when no rate exists`() {
        val priceMap = PriceDatabase.build(emptyList())
        val result = convertAmount(Amount(Decimal("100"), "EUR"), "USD", priceMap)
        assertEquals(Amount(Decimal("100"), "EUR"), result)
    }

    @Test
    fun `convertAmount should convert same currency to itself`() {
        val priceMap = PriceDatabase.build(emptyList())
        val result = convertAmount(Amount(Decimal("100"), "USD"), "USD", priceMap)
        assertEquals(Amount(Decimal("100"), "USD"), result)
    }

    @Test
    fun `convertAmount should convert via intermediate currency`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "AAPL", Amount(Decimal("150"), "USD")),
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("0.90"), "EUR"))
        )
        val priceMap = PriceDatabase.build(entries)
        val result = convertAmount(
            Amount(Decimal("10"), "AAPL"), "EUR", priceMap,
            via = listOf("USD")
        )
        assertEquals(Amount(Decimal("1350"), "EUR"), result)
    }

    // ===== convertPosition =====

    @Test
    fun `convertPosition should convert via cost currency`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "AAPL", Amount(Decimal("160"), "USD")),
            Price(emptyMap(), LocalDate(2024, 1, 1), "USD", Amount(Decimal("0.90"), "EUR"))
        )
        val priceMap = PriceDatabase.build(entries)
        val pos = Position(
            units = Amount(Decimal("10"), "AAPL"),
            cost = Cost(Decimal("150"), "USD", LocalDate(2024, 1, 1))
        )
        val result = convertPosition(pos, "EUR", priceMap)
        assertEquals(Amount(Decimal("1440"), "EUR"), result)
    }

    @Test
    fun `convertPosition should return original when no conversion possible`() {
        val priceMap = PriceDatabase.build(emptyList())
        val pos = Position(Amount(Decimal("10"), "AAPL"))
        val result = convertPosition(pos, "EUR", priceMap)
        assertEquals(Amount(Decimal("10"), "AAPL"), result)
    }

    // ===== buildPriceMap =====

    @Test
    fun `buildPriceMap should build from entries`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD")),
            Price(emptyMap(), LocalDate(2024, 1, 2), "EUR", Amount(Decimal("1.12"), "USD"))
        )
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val (date, rate) = priceMap.getPrice("EUR", "USD")
        assertEquals(LocalDate(2024, 1, 2), date)
        assertEquals(Decimal("1.12"), rate)
    }
}
