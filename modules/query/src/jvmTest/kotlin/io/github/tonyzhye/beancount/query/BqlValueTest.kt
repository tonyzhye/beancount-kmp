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

package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for BqlValue types and conversions.
 */
class BqlValueTest {

    @Test
    fun `BqlNullValue should be null`() {
        val value = BqlNullValue()
        assertTrue(value.isNull())
        assertEquals(BqlType.Null, value.type)
    }

    @Test
    fun `BqlStringValue should convert to string`() {
        val value = BqlStringValue("test")
        assertEquals("test", value.asString())
        assertEquals(BqlType.String, value.type)
    }

    @Test
    fun `BqlDecimalValue should convert to decimal`() {
        val decimal = Decimal("123.45")
        val value = BqlDecimalValue(decimal)
        assertEquals(decimal, value.asDecimal())
        assertEquals(BqlType.Decimal, value.type)
    }

    @Test
    fun `BqlDateValue should convert to date`() {
        val date = LocalDate(2023, 1, 15)
        val value = BqlDateValue(date)
        assertEquals(date, value.asDate())
        assertEquals(BqlType.Date, value.type)
    }

    @Test
    fun `BqlBooleanValue should convert to boolean`() {
        val value = BqlBooleanValue(true)
        assertTrue(value.asBoolean())
        assertEquals(BqlType.Boolean, value.type)
    }

    @Test
    fun `BqlIntegerValue should convert to integer`() {
        val value = BqlIntegerValue(42)
        assertEquals(42, value.asInteger())
        assertEquals(BqlType.Integer, value.type)
    }

    @Test
    fun `BqlSetValue should convert to set`() {
        val set = setOf("a", "b", "c")
        val value = BqlSetValue(set)
        assertEquals(set, value.asSet())
        assertEquals(BqlType.Set, value.type)
    }

    @Test
    fun `BqlAmountValue should convert to amount`() {
        val amount = Amount(Decimal("100"), "USD")
        val value = BqlAmountValue(amount)
        assertEquals(amount, value.asAmount())
        assertEquals(BqlType.Amount, value.type)
    }

    @Test
    fun `BqlPositionValue should convert to position`() {
        val position = Position(Amount(Decimal("10"), "AAPL"))
        val value = BqlPositionValue(position)
        assertEquals(position, value.asPosition())
        assertEquals(BqlType.Position, value.type)
    }

    @Test
    fun `BqlInventoryValue should convert to inventory`() {
        val inventory = Inventory()
        val value = BqlInventoryValue(inventory)
        assertEquals(inventory, value.asInventory())
        assertEquals(BqlType.Inventory, value.type)
    }

    @Test
    fun `BqlCostValue should have correct type`() {
        val cost = Cost(Decimal("100"), "USD", LocalDate(2023, 1, 15))
        val value = BqlCostValue(cost)
        assertEquals(BqlType.Cost, value.type)
    }

    @Test
    fun `BqlTransactionValue should have correct type`() {
        val tx = Transaction(emptyMap(), LocalDate(2023, 1, 15), "*")
        val value = BqlTransactionValue(tx)
        assertEquals(BqlType.Transaction, value.type)
    }

    @Test
    fun `BqlPriceMapValue should convert to price map`() {
        val entries = listOf<Directive>(
            Price(emptyMap(), LocalDate(2023, 1, 15), "EUR", Amount(Decimal("1.10"), "USD"))
        )
        val priceMap = PriceDatabase.buildPriceMap(entries)
        val value = BqlPriceMapValue(priceMap, entries)
        assertEquals(priceMap, value.asPriceMap())
        assertEquals(priceMap, value.priceMap)
        assertEquals(BqlType.PriceMap, value.type)
    }

    @Test
    fun `type cast from wrong type should throw TypeCastException`() {
        val stringValue = BqlStringValue("test")

        assertThrows(TypeCastException::class.java) { stringValue.asDecimal() }
        assertThrows(TypeCastException::class.java) { stringValue.asDate() }
        assertThrows(TypeCastException::class.java) { stringValue.asBoolean() }
        assertThrows(TypeCastException::class.java) { stringValue.asInteger() }
        assertThrows(TypeCastException::class.java) { stringValue.asSet() }
        assertThrows(TypeCastException::class.java) { stringValue.asAmount() }
        assertThrows(TypeCastException::class.java) { stringValue.asPosition() }
        assertThrows(TypeCastException::class.java) { stringValue.asInventory() }
        assertThrows(TypeCastException::class.java) { stringValue.asPriceMap() }
    }

    @Test
    fun `toBqlValue should convert various types`() {
        assertTrue(toBqlValue(null) is BqlNullValue)
        assertEquals(BqlStringValue("hello"), toBqlValue("hello"))
        assertEquals(BqlDecimalValue(Decimal("1.5")), toBqlValue(Decimal("1.5")))
        assertEquals(BqlDateValue(LocalDate(2023, 1, 1)), toBqlValue(LocalDate(2023, 1, 1)))
        assertEquals(BqlBooleanValue(true), toBqlValue(true))
        assertEquals(BqlIntegerValue(42), toBqlValue(42))
        assertEquals(BqlSetValue(setOf("a", "b")), toBqlValue(setOf("a", "b")))
    }

    @Test
    fun `toBqlValue should convert core types`() {
        assertEquals(BqlAmountValue(Amount(Decimal("100"), "USD")), toBqlValue(Amount(Decimal("100"), "USD")))
        assertEquals(BqlPositionValue(Position(Amount(Decimal("10"), "AAPL"))), toBqlValue(Position(Amount(Decimal("10"), "AAPL"))))
        assertEquals(BqlInventoryValue(Inventory()), toBqlValue(Inventory()))
        assertEquals(BqlCostValue(Cost(Decimal("100"), "USD", LocalDate(2023, 1, 1))), toBqlValue(Cost(Decimal("100"), "USD", LocalDate(2023, 1, 1))))
    }

    @Test
    fun `toBqlValue should throw for unsupported type`() {
        assertThrows(IllegalArgumentException::class.java) {
            toBqlValue(3.14) // Double is not supported
        }
    }

    @Test
    fun `toBqlValue should convert Transaction`() {
        val tx = Transaction(emptyMap(), LocalDate(2023, 1, 15), "*")
        val result = toBqlValue(tx)
        assertTrue(result is BqlTransactionValue)
    }

    @Test
    fun `toBqlValue should filter non-string sets`() {
        val mixedSet = setOf("a", "b", 123)
        val result = toBqlValue(mixedSet)
        assertTrue(result is BqlSetValue)
        assertEquals(setOf("a", "b"), (result as BqlSetValue).value)
    }
}
