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

class GettersTest {

    private val testEntries = listOf(
        Open(
            meta = emptyMap(),
            date = LocalDate(2024, 1, 1),
            account = "Assets:Bank:Checking",
            currencies = listOf("USD")
        ),
        Open(
            meta = emptyMap(),
            date = LocalDate(2024, 1, 1),
            account = "Income:Salary",
            currencies = listOf("USD")
        ),
        Transaction(
            meta = emptyMap(),
            date = LocalDate(2024, 1, 15),
            flag = "*",
            payee = "Employer",
            narration = "Paycheck",
            tags = setOf("income", "monthly"),
            links = setOf("paycheck-jan"),
            postings = listOf(
                Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
            )
        ),
        Transaction(
            meta = emptyMap(),
            date = LocalDate(2024, 2, 15),
            flag = "*",
            payee = "Employer",
            narration = "Paycheck",
            tags = setOf("income"),
            postings = listOf(
                Posting("Assets:Bank:Checking", Amount(Decimal("100.00"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-100.00"), "USD"))
            )
        ),
        Balance(
            meta = emptyMap(),
            date = LocalDate(2024, 1, 31),
            account = "Assets:Bank:Checking",
            amount = Amount(Decimal("100.00"), "USD")
        ),
        Close(
            meta = emptyMap(),
            date = LocalDate(2024, 12, 31),
            account = "Income:Salary"
        )
    )

    @Test
    fun `getTransactions should return only Transaction entries`() {
        val transactions = getTransactions(testEntries)
        assertEquals(2, transactions.size)
        assertTrue(transactions.all { it is Transaction })
    }

    @Test
    fun `getEntryAccounts should return accounts for a single directive`() {
        val txn = testEntries[2] as Transaction
        val accounts = getEntryAccounts(txn)
        assertEquals(2, accounts.size)
        assertTrue(accounts.contains("Assets:Bank:Checking"))
        assertTrue(accounts.contains("Income:Salary"))
    }

    @Test
    fun `getEntryAccounts should return single account for Open`() {
        val open = testEntries[0] as Open
        val accounts = getEntryAccounts(open)
        assertEquals(1, accounts.size)
        assertTrue(accounts.contains("Assets:Bank:Checking"))
    }

    @Test
    fun `getAccounts should return all unique accounts`() {
        val accounts = getAccounts(testEntries)
        assertEquals(2, accounts.size)
        assertTrue(accounts.contains("Assets:Bank:Checking"))
        assertTrue(accounts.contains("Income:Salary"))
    }

    @Test
    fun `getAccountsUseMap should track first and last use dates`() {
        val (first, last) = getAccountsUseMap(testEntries)

        assertEquals(LocalDate(2024, 1, 1), first["Assets:Bank:Checking"])
        assertEquals(LocalDate(2024, 1, 1), first["Income:Salary"])

        assertEquals(LocalDate(2024, 12, 31), last["Income:Salary"])
        assertEquals(LocalDate(2024, 1, 31), last["Assets:Bank:Checking"])
    }

    @Test
    fun `getAccountOpenClose should return open and close entries`() {
        val openCloseMap = getAccountOpenClose(testEntries)

        val (checkingOpen, checkingClose) = openCloseMap["Assets:Bank:Checking"]!!
        assertNotNull(checkingOpen)
        assertEquals(LocalDate(2024, 1, 1), checkingOpen!!.date)
        assertNull(checkingClose)

        val (salaryOpen, salaryClose) = openCloseMap["Income:Salary"]!!
        assertNotNull(salaryOpen)
        assertNotNull(salaryClose)
        assertEquals(LocalDate(2024, 12, 31), salaryClose!!.date)
    }

    @Test
    fun `getAllTags should return sorted unique tags`() {
        val tags = getAllTags(testEntries)
        assertEquals(listOf("income", "monthly"), tags)
    }

    @Test
    fun `getAllPayees should return sorted unique payees`() {
        val payees = getAllPayees(testEntries)
        assertEquals(listOf("Employer"), payees)
    }

    @Test
    fun `getAllLinks should return sorted unique links`() {
        val links = getAllLinks(testEntries)
        assertEquals(listOf("paycheck-jan"), links)
    }

    @Test
    fun `getMinMaxDates should return date range`() {
        val (minDate, maxDate) = getMinMaxDates(testEntries)
        assertEquals(LocalDate(2024, 1, 1), minDate)
        assertEquals(LocalDate(2024, 12, 31), maxDate)
    }

    @Test
    fun `getMinMaxDates with type filter should restrict entries`() {
        val (minDate, maxDate) = getMinMaxDates(
            testEntries,
            types = setOf(Transaction::class.java)
        )
        assertEquals(LocalDate(2024, 1, 15), minDate)
        assertEquals(LocalDate(2024, 2, 15), maxDate)
    }

    @Test
    fun `getActiveYears should return unique years`() {
        val years = getActiveYears(testEntries)
        assertEquals(listOf(2024), years)
    }

    @Test
    fun `getAccountComponents should return all unique components`() {
        val components = getAccountComponents(testEntries)
        assertTrue(components.contains("Assets"))
        assertTrue(components.contains("Bank"))
        assertTrue(components.contains("Checking"))
        assertTrue(components.contains("Income"))
        assertTrue(components.contains("Salary"))
    }

    @Test
    fun `getLevelNParentAccounts should return parents at given level`() {
        val accounts = listOf(
            "Assets:Bank:Checking",
            "Assets:Bank:Savings",
            "Assets:Investments:Stocks",
            "Income:Salary"
        )
        val level1 = getLevelNParentAccounts(accounts, 1)
        assertEquals(listOf("Assets", "Income"), level1.sorted())

        val level2 = getLevelNParentAccounts(accounts, 2)
        assertEquals(listOf("Assets:Bank", "Assets:Investments", "Income:Salary"), level2.sorted())
    }

    @Test
    fun `getLeafAccounts should return only leaf accounts`() {
        val accounts = listOf(
            "Assets",
            "Assets:Bank",
            "Assets:Bank:Checking",
            "Assets:Bank:Savings",
            "Income:Salary"
        )
        val leaves = getLeafAccounts(accounts)
        assertEquals(3, leaves.size)
        assertTrue(leaves.contains("Assets:Bank:Checking"))
        assertTrue(leaves.contains("Assets:Bank:Savings"))
        assertTrue(leaves.contains("Income:Salary"))
        assertFalse(leaves.contains("Assets"))
        assertFalse(leaves.contains("Assets:Bank"))
    }
}
