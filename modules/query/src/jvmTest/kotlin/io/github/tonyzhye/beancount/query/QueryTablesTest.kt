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
import io.github.tonyzhye.beancount.query.tables.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for query tables to improve coverage.
 */
class QueryTablesTest {

    private val testEntries = listOf<Directive>(
        Open(emptyMap(), LocalDate(2023, 1, 1), "Assets:Bank", listOf("USD"), booking = Booking.STRICT),
        Open(emptyMap(), LocalDate(2023, 1, 1), "Expenses:Food", listOf("USD")),
        Commodity(emptyMap(), LocalDate(2023, 1, 1), "USD"),
        Commodity(emptyMap(), LocalDate(2023, 1, 1), "EUR"),
        Price(emptyMap(), LocalDate(2023, 1, 15), "EUR", Amount(Decimal("1.10"), "USD")),
        Balance(emptyMap(), LocalDate(2023, 1, 31), "Assets:Bank", Amount(Decimal("1000"), "USD")),
        Note(emptyMap(), LocalDate(2023, 1, 20), "Assets:Bank", "Test note", tags = setOf("test"), links = setOf("link1")),
        Event(emptyMap(), LocalDate(2023, 1, 1), "employer", "Company A"),
        Document(emptyMap(), LocalDate(2023, 1, 25), "Assets:Bank", "statement.pdf", tags = setOf("doc"), links = emptySet()),
        Transaction(
            emptyMap(), LocalDate(2023, 1, 15), "*", narration = "Test tx",
            postings = listOf(
                Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                Posting("Expenses:Food", Amount(Decimal("-100"), "USD"))
            )
        ),
        Close(emptyMap(), LocalDate(2023, 12, 31), "Expenses:Food")
    )

    @Test
    fun `AccountsTable should list all accounts`() {
        val table = AccountsTable(testEntries)
        assertEquals("accounts", table.name)
        assertTrue(table.wildcardColumns.contains("account"))

        val rows = table.iterator().asSequence().toList()
        assertTrue(rows.isNotEmpty())

        val row = rows.first { (it as AccountRowContext).accountInfo.account == "Assets:Bank" }
        val info = (row as AccountRowContext).accountInfo
        assertEquals(LocalDate(2023, 1, 1), info.openDate)
        assertNull(info.closeDate)
        assertTrue(info.currencies.contains("USD"))
        assertEquals("STRICT", info.booking)
    }

    @Test
    fun `AccountsTable should handle closed accounts`() {
        val table = AccountsTable(testEntries)
        val rows = table.iterator().asSequence().toList()

        val row = rows.first { (it as AccountRowContext).accountInfo.account == "Expenses:Food" }
        val info = (row as AccountRowContext).accountInfo
        assertEquals(LocalDate(2023, 1, 1), info.openDate)
        assertEquals(LocalDate(2023, 12, 31), info.closeDate)
    }

    @Test
    fun `AccountsTable should handle Close without Open`() {
        val entries = listOf<Directive>(
            Close(emptyMap(), LocalDate(2023, 6, 1), "Assets:Test")
        )
        val table = AccountsTable(entries)
        val rows = table.iterator().asSequence().toList()

        assertEquals(1, rows.size)
        val info = (rows[0] as AccountRowContext).accountInfo
        assertNull(info.openDate)
        assertEquals(LocalDate(2023, 6, 1), info.closeDate)
    }

    @Test
    fun `AccountsTable columns should return correct values`() {
        val table = AccountsTable(testEntries)
        val rows = table.iterator().asSequence().toList()
        val row = rows.first { (it as AccountRowContext).accountInfo.account == "Assets:Bank" }

        assertEquals(BqlStringValue("Assets:Bank"), table.columns["account"]!!.evaluate(row))
        assertEquals(BqlDateValue(LocalDate(2023, 1, 1)), table.columns["open_date"]!!.evaluate(row))
        assertTrue(table.columns["close_date"]!!.evaluate(row) is BqlNullValue)
        assertEquals(BqlSetValue(setOf("USD")), table.columns["currencies"]!!.evaluate(row))
        assertEquals(BqlStringValue("STRICT"), table.columns["booking"]!!.evaluate(row))
    }

    @Test
    fun `CommoditiesTable should list all commodities`() {
        val table = CommoditiesTable(testEntries)
        assertEquals("commodities", table.name)

        val rows = table.iterator().asSequence().toList()
        assertEquals(2, rows.size)

        val row = rows.first()
        assertEquals(BqlStringValue("USD"), table.columns["currency"]!!.evaluate(row))
        assertEquals(BqlDateValue(LocalDate(2023, 1, 1)), table.columns["date"]!!.evaluate(row))
        assertEquals(BqlIntegerValue(2023), table.columns["year"]!!.evaluate(row))
        assertEquals(BqlIntegerValue(1), table.columns["month"]!!.evaluate(row))
        assertEquals(BqlIntegerValue(1), table.columns["day"]!!.evaluate(row))
    }

    @Test
    fun `PricesTable should list all prices`() {
        val table = PricesTable(testEntries)
        assertEquals("prices", table.name)

        val rows = table.iterator().asSequence().toList()
        assertEquals(1, rows.size)

        val row = rows[0]
        assertEquals(BqlStringValue("EUR"), table.columns["currency"]!!.evaluate(row))
        assertEquals(BqlAmountValue(Amount(Decimal("1.10"), "USD")), table.columns["amount"]!!.evaluate(row))
        assertEquals(BqlDateValue(LocalDate(2023, 1, 15)), table.columns["date"]!!.evaluate(row))
    }

    @Test
    fun `BalancesTable should list all balances`() {
        val table = BalancesTable(testEntries)
        assertEquals("balances", table.name)

        val rows = table.iterator().asSequence().toList()
        assertEquals(1, rows.size)

        val row = rows[0]
        assertEquals(BqlStringValue("Assets:Bank"), table.columns["account"]!!.evaluate(row))
        assertEquals(BqlAmountValue(Amount(Decimal("1000"), "USD")), table.columns["amount"]!!.evaluate(row))
        assertTrue(table.columns["tolerance"]!!.evaluate(row) is BqlNullValue)
        assertTrue(table.columns["diff_amount"]!!.evaluate(row) is BqlNullValue)
    }

    @Test
    fun `NotesTable should list all notes`() {
        val table = NotesTable(testEntries)
        assertEquals("notes", table.name)

        val rows = table.iterator().asSequence().toList()
        assertEquals(1, rows.size)

        val row = rows[0]
        assertEquals(BqlStringValue("Assets:Bank"), table.columns["account"]!!.evaluate(row))
        assertEquals(BqlStringValue("Test note"), table.columns["comment"]!!.evaluate(row))
        assertEquals(BqlSetValue(setOf("test")), table.columns["tags"]!!.evaluate(row))
        assertEquals(BqlSetValue(setOf("link1")), table.columns["links"]!!.evaluate(row))
    }

    @Test
    fun `EventsTable should list all events`() {
        val table = EventsTable(testEntries)
        assertEquals("events", table.name)

        val rows = table.iterator().asSequence().toList()
        assertEquals(1, rows.size)

        val row = rows[0]
        assertEquals(BqlStringValue("employer"), table.columns["type"]!!.evaluate(row))
        assertEquals(BqlStringValue("Company A"), table.columns["description"]!!.evaluate(row))
        assertEquals(BqlDateValue(LocalDate(2023, 1, 1)), table.columns["date"]!!.evaluate(row))
    }

    @Test
    fun `DocumentsTable should list all documents`() {
        val table = DocumentsTable(testEntries)
        assertEquals("documents", table.name)

        val rows = table.iterator().asSequence().toList()
        assertEquals(1, rows.size)

        val row = rows[0]
        assertEquals(BqlStringValue("Assets:Bank"), table.columns["account"]!!.evaluate(row))
        assertEquals(BqlStringValue("statement.pdf"), table.columns["filename"]!!.evaluate(row))
        assertEquals(BqlSetValue(setOf("doc")), table.columns["tags"]!!.evaluate(row))
    }

    @Test
    fun `EntriesTable should list all entries`() {
        val table = EntriesTable(testEntries)
        assertEquals("entries", table.name)

        val rows = table.iterator().asSequence().toList()
        assertEquals(testEntries.size, rows.size)

        val row = rows[0]
        assertEquals(BqlStringValue("open"), table.columns["type"]!!.evaluate(row))
        assertEquals(BqlDateValue(LocalDate(2023, 1, 1)), table.columns["date"]!!.evaluate(row))
        assertEquals(BqlIntegerValue(2023), table.columns["year"]!!.evaluate(row))
    }

    @Test
    fun `EntriesTable should handle transaction-specific columns`() {
        val table = EntriesTable(testEntries)
        val rows = table.iterator().asSequence().toList()

        val txRow = rows.first { it.entry is Transaction }
        assertEquals(BqlStringValue("*"), table.columns["flag"]!!.evaluate(txRow))
        assertTrue(table.columns["payee"]!!.evaluate(txRow) is BqlNullValue)
        assertEquals(BqlStringValue("Test tx"), table.columns["narration"]!!.evaluate(txRow))
        assertEquals(BqlStringValue("Test tx"), table.columns["description"]!!.evaluate(txRow))
        assertTrue(table.columns["tags"]!!.evaluate(txRow) is BqlSetValue)
        assertTrue(table.columns["links"]!!.evaluate(txRow) is BqlSetValue)
    }

    @Test
    fun `EntriesTable accounts column should return correct accounts`() {
        val table = EntriesTable(testEntries)
        val rows = table.iterator().asSequence().toList()

        val txRow = rows.first { it.entry is Transaction }
        val accounts = table.columns["accounts"]!!.evaluate(txRow).asSet()
        assertTrue(accounts.contains("Assets:Bank"))
        assertTrue(accounts.contains("Expenses:Food"))

        val openRow = rows.first { it.entry is Open }
        assertEquals(setOf("Assets:Bank"), table.columns["accounts"]!!.evaluate(openRow).asSet())
    }

    @Test
    fun `TransactionsTable should filter only transactions`() {
        val table = TransactionsTable(testEntries)
        assertEquals("transactions", table.name)

        val rows = table.iterator().asSequence().toList()
        assertEquals(1, rows.size)
        assertTrue(rows[0].entry is Transaction)
    }

    @Test
    fun `PostingsTable should list all postings`() {
        val table = PostingsTable(testEntries)
        assertEquals("postings", table.name)

        val rows = table.iterator().asSequence().toList()
        assertEquals(2, rows.size) // 1 transaction * 2 postings

        val row = rows[0]
        assertEquals(BqlStringValue("Assets:Bank"), table.columns["account"]!!.evaluate(row))
        assertEquals(BqlDecimalValue(Decimal("100")), table.columns["number"]!!.evaluate(row))
        assertEquals(BqlStringValue("USD"), table.columns["currency"]!!.evaluate(row))
    }

    @Test
    fun `PostingsTable should handle optional columns`() {
        val table = PostingsTable(testEntries)
        val rows = table.iterator().asSequence().toList()

        val row = rows[0]
        assertTrue(table.columns["payee"]!!.evaluate(row) is BqlNullValue)
        assertTrue(table.columns["posting_flag"]!!.evaluate(row) is BqlNullValue)
        assertTrue(table.columns["price"]!!.evaluate(row) is BqlNullValue)
        assertTrue(table.columns["cost_number"]!!.evaluate(row) is BqlNullValue)
    }

    @Test
    fun `PostingsTable balance column should track running balance`() {
        val table = PostingsTable(testEntries)
        val rows = table.iterator().asSequence().toList()

        // Each row should have a balance
        rows.forEachIndexed { index, row ->
            val balance = table.columns["balance"]!!.evaluate(row)
            assertTrue(balance is BqlInventoryValue, "Row $index should have balance")
        }
    }

    @Test
    fun `PostingsTable should calculate weight with price`() {
        val entries = listOf<Directive>(
            Transaction(
                emptyMap(), LocalDate(2023, 1, 15), "*", narration = "Exchange",
                postings = listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD"), price = Amount(Decimal("0.9"), "EUR")),
                    Posting("Assets:Forex", Amount(Decimal("-90"), "EUR"))
                )
            )
        )

        val table = PostingsTable(entries)
        val rows = table.iterator().asSequence().toList()

        val row = rows.first { it.posting?.account == "Assets:Bank" }
        val weight = table.columns["weight"]!!.evaluate(row).asAmount()
        assertEquals(Decimal("90"), weight.number)
        assertEquals("EUR", weight.currency)
    }

    @Test
    fun `PostingsTable should generate id`() {
        val table = PostingsTable(testEntries)
        val rows = table.iterator().asSequence().toList()

        val row = rows[0]
        val id = table.columns["id"]!!.evaluate(row).asString()
        assertTrue(id.isNotEmpty())
    }

    @Test
    fun `PostingsTable should return location`() {
        val table = PostingsTable(testEntries)
        val rows = table.iterator().asSequence().toList()

        val row = rows[0]
        val location = table.columns["location"]!!.evaluate(row).asString()
        assertTrue(location.isNotEmpty())
    }
}
