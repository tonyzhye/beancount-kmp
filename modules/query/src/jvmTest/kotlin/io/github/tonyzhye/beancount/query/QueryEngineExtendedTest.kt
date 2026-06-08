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
 * Extended tests for QueryEngine to improve coverage.
 */
class QueryEngineExtendedTest {

    private val testEntries = listOf<Directive>(
        Open(emptyMap(), LocalDate(2023, 1, 1), "Assets:Bank", listOf("USD")),
        Open(emptyMap(), LocalDate(2023, 2, 1), "Assets:Investment", listOf("AAPL")),
        Transaction(
            emptyMap(), LocalDate(2023, 1, 15), "*", narration = "January tx",
            postings = listOf(
                Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
            )
        ),
        Transaction(
            emptyMap(), LocalDate(2023, 2, 15), "*", narration = "February tx",
            postings = listOf(
                Posting("Assets:Bank", Amount(Decimal("200"), "USD")),
                Posting("Income:Salary", Amount(Decimal("-200"), "USD"))
            )
        ),
        Transaction(
            emptyMap(), LocalDate(2023, 3, 15), "*", narration = "March tx",
            postings = listOf(
                Posting("Assets:Investment", Amount(Decimal("10"), "AAPL")),
                Posting("Assets:Bank", Amount(Decimal("-1000"), "USD"))
            )
        ),
        Close(emptyMap(), LocalDate(2023, 4, 1), "Assets:Bank"),
        Close(emptyMap(), LocalDate(2023, 5, 1), "Assets:Investment")
    )

    private val engine = QueryEngine(testEntries)

    @Test
    fun `should query accounts table`() {
        val result = engine.execute("SELECT account, open_date FROM accounts")
        assertEquals(2, result.rows.size)
        assertTrue(result.rows.any { it[0].asString() == "Assets:Bank" })
        assertTrue(result.rows.any { it[0].asString() == "Assets:Investment" })
    }

    @Test
    fun `should query commodities table`() {
        val entries = testEntries + listOf(
            Commodity(emptyMap(), LocalDate(2023, 1, 1), "USD"),
            Commodity(emptyMap(), LocalDate(2023, 1, 1), "AAPL")
        )
        val eng = QueryEngine(entries)
        val result = eng.execute("SELECT currency FROM commodities")
        assertEquals(2, result.rows.size)
    }

    @Test
    fun `should query prices table`() {
        val entries = testEntries + listOf(
            Price(emptyMap(), LocalDate(2023, 1, 1), "AAPL", Amount(Decimal("150"), "USD"))
        )
        val eng = QueryEngine(entries)
        val result = eng.execute("SELECT currency, amount FROM prices")
        assertEquals(1, result.rows.size)
        assertEquals("AAPL", result.rows[0][0].asString())
    }

    @Test
    fun `should query balances table`() {
        val entries = testEntries + listOf(
            Balance(emptyMap(), LocalDate(2023, 1, 31), "Assets:Bank", Amount(Decimal("100"), "USD"))
        )
        val eng = QueryEngine(entries)
        val result = eng.execute("SELECT account, amount FROM balances")
        assertEquals(1, result.rows.size)
        assertEquals("Assets:Bank", result.rows[0][0].asString())
    }

    @Test
    fun `should query notes table`() {
        val entries = testEntries + listOf(
            Note(emptyMap(), LocalDate(2023, 1, 15), "Assets:Bank", "Test note")
        )
        val eng = QueryEngine(entries)
        val result = eng.execute("SELECT account, comment FROM notes")
        assertEquals(1, result.rows.size)
        assertEquals("Test note", result.rows[0][1].asString())
    }

    @Test
    fun `should query events table`() {
        val entries = testEntries + listOf(
            Event(emptyMap(), LocalDate(2023, 1, 1), "employer", "Company")
        )
        val eng = QueryEngine(entries)
        val result = eng.execute("SELECT type, description FROM events")
        assertEquals(1, result.rows.size)
    }

    @Test
    fun `should query documents table`() {
        val entries = testEntries + listOf(
            Document(emptyMap(), LocalDate(2023, 1, 15), "Assets:Bank", "test.pdf")
        )
        val eng = QueryEngine(entries)
        val result = eng.execute("SELECT account, filename FROM documents")
        assertEquals(1, result.rows.size)
        assertEquals("test.pdf", result.rows[0][1].asString())
    }

    @Test
    fun `should query transactions table`() {
        val result = engine.execute("SELECT narration FROM transactions")
        assertEquals(3, result.rows.size)
    }

    @Test
    fun `should apply OPEN ON time slicing`() {
        val result = engine.execute("SELECT narration FROM postings OPEN ON 2023-02-01")
        // Should only include entries from Feb 1 onwards
        val narrations = result.rows.map { it[0].asString() }.toSet()
        assertFalse(narrations.contains("January tx"))
        assertTrue(narrations.contains("February tx"))
        assertTrue(narrations.contains("March tx"))
    }

    @Test
    fun `should apply CLOSE ON time slicing`() {
        val result = engine.execute("SELECT narration FROM postings CLOSE ON 2023-03-01")
        // Should only include entries before March 1
        val narrations = result.rows.map { it[0].asString() }.toSet()
        assertTrue(narrations.contains("January tx"))
        assertTrue(narrations.contains("February tx"))
        assertFalse(narrations.contains("March tx"))
    }

    @Test
    fun `should apply OPEN ON and CLOSE ON together`() {
        val result = engine.execute(
            "SELECT narration FROM postings OPEN ON 2023-02-01 CLOSE ON 2023-03-01"
        )
        val narrations = result.rows.map { it[0].asString() }.toSet()
        assertFalse(narrations.contains("January tx"))
        assertTrue(narrations.contains("February tx"))
        assertFalse(narrations.contains("March tx"))
    }

    @Test
    fun `should apply CLOSE ON TRUE for open accounts`() {
        val result = engine.execute("SELECT account FROM postings CLOSE ON TRUE")
        // Assets:Bank is closed on 2023-04-01, so entries after that should be excluded
        val accounts = result.rows.map { it[0].asString() }.toSet()
        assertTrue(accounts.contains("Assets:Bank")) // Entries before close date
    }

    @Test
    fun `should handle unknown table`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.execute("SELECT * FROM unknown_table")
        }
    }

    @Test
    fun `should get column names for all tables`() {
        assertTrue(engine.getColumnNames("postings").isNotEmpty())
        assertTrue(engine.getColumnNames("entries").isNotEmpty())
        assertTrue(engine.getColumnNames("accounts").isNotEmpty())
        assertTrue(engine.getColumnNames("commodities").isNotEmpty())
        assertTrue(engine.getColumnNames("prices").isNotEmpty())
        assertTrue(engine.getColumnNames("balances").isNotEmpty())
        assertTrue(engine.getColumnNames("notes").isNotEmpty())
        assertTrue(engine.getColumnNames("events").isNotEmpty())
        assertTrue(engine.getColumnNames("documents").isNotEmpty())
    }

    @Test
    fun `should get all table names`() {
        val names = engine.getTableNames()
        assertTrue(names.contains("postings"))
        assertTrue(names.contains("entries"))
        assertTrue(names.contains("transactions"))
        assertTrue(names.contains("accounts"))
        assertTrue(names.contains("commodities"))
        assertTrue(names.contains("prices"))
        assertTrue(names.contains("balances"))
        assertTrue(names.contains("notes"))
        assertTrue(names.contains("events"))
        assertTrue(names.contains("documents"))
    }

    @Test
    fun `should handle empty entries for accounts table`() {
        val eng = QueryEngine(emptyList())
        val result = eng.execute("SELECT account FROM accounts")
        assertEquals(0, result.rows.size)
    }

    @Test
    fun `should handle entries table with no filename meta`() {
        val entries = listOf<Directive>(
            Open(emptyMap(), LocalDate(2023, 1, 1), "Assets:Bank", listOf("USD"))
        )
        val eng = QueryEngine(entries)
        val result = eng.execute("SELECT filename FROM entries")
        assertEquals(1, result.rows.size)
        assertTrue(result.rows[0][0] is BqlNullValue)
    }

    @Test
    fun `should handle entries table with no lineno meta`() {
        val entries = listOf<Directive>(
            Open(emptyMap(), LocalDate(2023, 1, 1), "Assets:Bank", listOf("USD"))
        )
        val eng = QueryEngine(entries)
        val result = eng.execute("SELECT lineno FROM entries")
        assertEquals(1, result.rows.size)
        assertTrue(result.rows[0][0] is BqlNullValue)
    }
}
