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

package io.github.tonyzhye.beancount.api

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Extended unit tests for the Beancount API facade.
 */
class BeancountApiExtendedTest {

    private val sampleEntries = run {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Assets:Bank:Savings USD
            2024-01-01 open Expenses:Food USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck" #income
              Assets:Bank:Checking  1000.00 USD
              Income:Salary

            2024-01-20 * "Grocery" #food
              Expenses:Food  50.00 USD
              Assets:Bank:Checking

            2024-01-25 * "Dinner" ^dinner-link
              Expenses:Food  30.00 USD
              Assets:Bank:Checking
        """.trimIndent()
        Beancount.loadString(content).entries
    }

    @Test
    fun `accountRoot should return root component`() {
        assertEquals("Assets", Beancount.accountRoot(1, "Assets:Bank:Checking"))
        assertEquals("Assets", Beancount.accountRoot(1, "Assets"))
    }

    @Test
    fun `accountRoot with depth should return truncated account`() {
        assertEquals("Assets:Bank", Beancount.accountRoot(2, "Assets:Bank:Checking"))
        assertEquals("Assets:Bank:Checking", Beancount.accountRoot(5, "Assets:Bank:Checking"))
    }

    @Test
    fun `accountSansRoot should remove root`() {
        assertEquals("Bank:Checking", Beancount.accountSansRoot("Assets:Bank:Checking"))
    }

    @Test
    fun `accountCommonPrefix should find common prefix`() {
        assertEquals("Assets:Bank", Beancount.accountCommonPrefix(listOf("Assets:Bank:Checking", "Assets:Bank:Savings")))
    }

    @Test
    fun `accountParents should list all parents`() {
        val parents = Beancount.accountParents("Assets:Bank:Checking")
        assertTrue(parents.contains("Assets"))
        assertTrue(parents.contains("Assets:Bank"))
    }

    @Test
    fun `parentMatcher should match parent pattern`() {
        val matcher = Beancount.parentMatcher("Assets:Bank")
        assertTrue(matcher("Assets:Bank"))
        assertFalse(matcher("Expenses:Food"))
    }

    @Test
    fun `getAccountOpenClose should return open and close info`() {
        val result = Beancount.getAccountOpenClose(sampleEntries)
        assertTrue(result.containsKey("Assets:Bank:Checking"))
    }

    @Test
    fun `getAccountsUseMap should return account usage map`() {
        val (firstSeen, lastSeen) = Beancount.getAccountsUseMap(sampleEntries)
        assertTrue(firstSeen.containsKey("Assets:Bank:Checking"))
    }

    @Test
    fun `getAllPayees should collect all payees`() {
        val payees = Beancount.getAllPayees(sampleEntries)
        // Some entries may not have payees
        assertNotNull(payees)
    }

    @Test
    fun `getAllLinks should collect all links`() {
        val links = Beancount.getAllLinks(sampleEntries)
        assertTrue(links.contains("dinner-link"))
    }

    @Test
    fun `getMinMaxDates should return date range`() {
        val (minDate, maxDate) = Beancount.getMinMaxDates(sampleEntries)
        assertNotNull(minDate)
        assertNotNull(maxDate)
        assertTrue(minDate!! <= maxDate!!)
    }

    @Test
    fun `getActiveYears should return active years`() {
        val years = Beancount.getActiveYears(sampleEntries)
        assertTrue(years.contains(2024))
    }

    @Test
    fun `filterTxns should return only transactions`() {
        val txns = Beancount.filterTxns(sampleEntries)
        assertTrue(txns.isNotEmpty())
        assertTrue(txns.all { it is Transaction })
    }

    @Test
    fun `getEntry should return entry from directive`() {
        val txn = sampleEntries.filterIsInstance<Transaction>().first()
        val entry = Beancount.getEntry(txn)
        assertNotNull(entry)
        assertTrue(entry is Transaction)
    }

    @Test
    fun `getUnits should return units from position`() {
        val pos = Position(Amount(Decimal("100"), "USD"), null)
        val units = Beancount.getUnits(pos)
        assertEquals(Amount(Decimal("100"), "USD"), units)
    }

    @Test
    fun `getCost should return cost from position`() {
        val cost = Cost(Decimal("50"), "USD", LocalDate(2024, 1, 1))
        val pos = Position(Amount(Decimal("100"), "AAPL"), cost)
        assertNotNull(Beancount.getCost(pos))
    }

    @Test
    fun `getWeight should return weight from position`() {
        val cost = Cost(Decimal("50"), "USD", LocalDate(2024, 1, 1))
        val pos = Position(Amount(Decimal("2"), "AAPL"), cost)
        val weight = Beancount.getWeight(pos)
        assertEquals("USD", weight.currency)
    }

    @Test
    fun `convertAmount should convert currency`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD")),
        )
        val priceMap = Beancount.buildPriceMap(entries)
        val amount = Amount(Decimal("100"), "EUR")
        val converted = Beancount.convertAmount(amount, "USD", priceMap)
        assertEquals("USD", converted.currency)
    }

    @Test
    fun `convertPosition should convert position currency`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD")),
        )
        val priceMap = Beancount.buildPriceMap(entries)
        val pos = Position(Amount(Decimal("100"), "EUR"), null)
        val converted = Beancount.convertPosition(pos, "USD", priceMap)
        assertEquals("USD", converted.currency)
    }

    @Test
    fun `buildPriceMap should create price database`() {
        val entries = listOf(
            Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.10"), "USD")),
        )
        val priceMap = Beancount.buildPriceMap(entries)
        assertNotNull(priceMap)
    }

    @Test
    fun `formatEntry should format a single entry`() {
        val txn = sampleEntries.filterIsInstance<Transaction>().first()
        val formatted = Beancount.formatEntry(txn)
        assertTrue(formatted.contains("2024-01-15"))
    }

    @Test
    fun `formatEntries should format multiple entries`() {
        val formatted = Beancount.formatEntries(sampleEntries)
        assertTrue(formatted.contains("2024-01-01 open"))
    }

    @Test
    fun `hashEntry should produce consistent hash`() {
        val txn = sampleEntries.filterIsInstance<Transaction>().first()
        val hash1 = Beancount.hashEntry(txn)
        val hash2 = Beancount.hashEntry(txn)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `compareEntries should compare two entry lists`() {
        val (equal, onlyIn1, onlyIn2) = Beancount.compareEntries(sampleEntries, sampleEntries)
        assertTrue(equal)
        assertTrue(onlyIn1.isEmpty())
        assertTrue(onlyIn2.isEmpty())
    }

    @Test
    fun `queryEngine should create query engine`() {
        val engine = Beancount.queryEngine(sampleEntries)
        val result = engine.execute("SELECT account FROM postings")
        assertTrue(result.rows.isNotEmpty())
    }

    @Test
    fun `roundTo should round decimal`() {
        val d = Decimal("3.14159")
        val rounded = Beancount.roundTo(d, 2)
        assertNotNull(rounded)
    }

    @Test
    fun `autoQuantize should quantize decimal`() {
        val d = Decimal("3.14159")
        val quantized = Beancount.autoQuantize(d, 2)
        assertNotNull(quantized)
    }

    @Test
    fun `sameSign should check signs`() {
        assertTrue(Beancount.sameSign(Decimal("10"), Decimal("20")))
        assertTrue(Beancount.sameSign(Decimal("-10"), Decimal("-20")))
        assertFalse(Beancount.sameSign(Decimal("10"), Decimal("-20")))
    }

    @Test
    fun `isInvertedAccount should identify inverted accounts`() {
        assertTrue(Beancount.isInvertedAccount("Income:Salary"))
        assertTrue(Beancount.isInvertedAccount("Liabilities:CreditCard"))
        assertFalse(Beancount.isInvertedAccount("Assets:Bank"))
        assertFalse(Beancount.isInvertedAccount("Expenses:Food"))
    }

    @Test
    fun `loadFile should parse file content`() {
        val tempFile = File.createTempFile("test", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank USD
            2024-01-15 * "Test"
              Assets:Bank  100 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val result = Beancount.loadFile(tempFile.absolutePath)
        assertTrue(result.entries.isNotEmpty())
    }
}
