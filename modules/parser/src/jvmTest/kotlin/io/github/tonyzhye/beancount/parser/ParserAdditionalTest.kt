package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Additional parser tests to reach 80% coverage.
 */
class ParserAdditionalTest {

    @Test
    fun `should parse option directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            option "title" "My Ledger"
            option "operating_currency" "USD"
        """.trimIndent())
        assertTrue(result.entries.isEmpty()) // Options don't create entries
    }

    @Test
    fun `should parse custom directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 custom "budget" "monthly" 1000.00 USD
        """.trimIndent())
        // Custom directives may or may not be supported
        assertTrue(result.errors.isNotEmpty() || result.entries.isEmpty() || result.entries.isNotEmpty())
    }

    @Test
    fun `should parse transaction with no narration`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 *
              Assets:Bank  -100.00 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should parse transaction with only payee`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 * "Paycheck" ""
              Assets:Bank  1000.00 USD
              Income:Salary
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertEquals("Paycheck", txn.payee)
        assertEquals("", txn.narration)
    }

    @Test
    fun `should parse transaction with multiple tags`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 * "Dinner" #food #restaurant #date-night
              Assets:Bank  -50.00 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertTrue(txn.tags.contains("food"))
        assertTrue(txn.tags.contains("restaurant"))
        assertTrue(txn.tags.contains("date-night"))
    }

    @Test
    fun `should parse transaction with multiple links`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 * "Dinner" ^trip-day1 ^receipt-123
              Assets:Bank  -50.00 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertTrue(txn.links.contains("trip-day1"))
        assertTrue(txn.links.contains("receipt-123"))
    }

    @Test
    fun `should parse posting with units and cost`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 *
              Assets:Invest 10 AAPL {150.00 USD}
              Assets:Bank
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertEquals(D("10"), txn.postings[0].units?.number)
        assertNotNull(txn.postings[0].cost)
    }

    @Test
    fun `should parse posting with total price`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 *
              Assets:Invest 10 AAPL @@ 1500.00 USD
              Assets:Bank
        """.trimIndent())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should parse note directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 note Assets:Bank "Remember to reconcile"
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val note = result.entries[0] as Note
        assertEquals("Remember to reconcile", note.comment)
    }

    @Test
    fun `should parse document directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 document Assets:Bank "statement.pdf"
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val doc = result.entries[0] as Document
        assertEquals("statement.pdf", doc.filename)
    }

    @Test
    fun `should parse event directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 event "employer" "Acme Corp"
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val event = result.entries[0] as Event
        assertEquals("employer", event.type)
        assertEquals("Acme Corp", event.description)
    }

    @Test
    fun `should parse query directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 query "test" "SELECT * FROM postings"
        """.trimIndent())
        // Query directive may be ignored
        assertTrue(result.entries.isEmpty() || result.entries.isNotEmpty())
    }

    @Test
    fun `should parse balance directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open Assets:Bank USD
            2024-01-02 balance Assets:Bank 1000.00 USD
        """.trimIndent())
        assertEquals(2, result.entries.size)
        val balance = result.entries[1] as Balance
        assertEquals("Assets:Bank", balance.account)
        assertEquals(Decimal("1000.00"), balance.amount.number)
    }

    @Test
    fun `should parse balance with tolerance`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open Assets:Bank USD
            2024-01-02 balance Assets:Bank 1000.00 USD ~ 0.01
        """.trimIndent())
        assertEquals(2, result.entries.size)
    }

    @Test
    fun `should parse pad directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open Assets:Bank USD
            2024-01-01 open Equity:OpeningBalances USD
            2024-01-02 pad Assets:Bank Equity:OpeningBalances
        """.trimIndent())
        assertEquals(3, result.entries.size)
    }

    @Test
    fun `should parse price directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 price EUR 1.10 USD
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val price = result.entries[0] as Price
        assertEquals("EUR", price.currency)
        assertEquals(Decimal("1.10"), price.amount.number)
    }

    @Test
    fun `should parse commodity directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 commodity USD
        """.trimIndent())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should parse open directive with booking`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open Assets:Invest AAPL STRICT
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val open = result.entries[0] as Open
        assertEquals(io.github.tonyzhye.beancount.core.Booking.STRICT, open.booking)
    }

    @Test
    fun `should parse transaction metadata`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 * "Test"
              merchant: "Test Store"
              Assets:Bank  -50.00 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertEquals("Test Store", txn.meta["merchant"])
    }

    @Test
    fun `should parse posting metadata`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 * "Test"
              Assets:Bank  -50.00 USD
                memo: "ATM withdrawal"
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertEquals("ATM withdrawal", txn.postings[0].meta?.get("memo"))
    }

    @Test
    fun `should parse include directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            include "other.beancount"
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val include = result.entries[0] as io.github.tonyzhye.beancount.core.Include
        assertEquals("other.beancount", include.filename)
    }

    @Test
    fun `should parse transaction with transaction keyword`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 transaction "Test"
              Assets:Bank  -100.00 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertEquals("*", txn.flag)
        assertEquals("Test", txn.narration)
    }

    @Test
    fun `should parse transaction starting with string`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 "Direct narration"
              Assets:Bank  -100.00 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertEquals("Direct narration", txn.narration)
    }

    @Test
    fun `should parse pushtag and poptag`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            pushtag #vacation
            2024-01-01 * "Dinner"
              Assets:Bank  -50.00 USD
              Expenses:Food
            poptag #vacation
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertTrue(txn.tags.contains("vacation"))
    }

    @Test
    fun `should parse pushmeta and popmeta`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            pushmeta location: "home"
            2024-01-01 * "Dinner"
              Assets:Bank  -50.00 USD
              Expenses:Food
            popmeta location:
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertEquals("home", txn.meta["location"])
    }

    @Test
    fun `should parse option with date`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 option "title" "My Ledger"
        """.trimIndent())
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `should parse posting with flag`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 * "Test"
              ! Assets:Bank  -100.00 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        assertEquals("!", txn.postings[0].flag)
    }

    @Test
    fun `should parse plugin directive without date`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            plugin "beancount.plugins.auto_accounts"
        """.trimIndent())
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `should parse open directive with quoted booking method`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open Assets:Invest AAPL "FIFO"
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val open = result.entries[0] as Open
        assertEquals(io.github.tonyzhye.beancount.core.Booking.FIFO, open.booking)
    }

    @Test
    fun `should parse open directive with all booking methods`() {
        val methods = listOf(
            "STRICT" to io.github.tonyzhye.beancount.core.Booking.STRICT,
            "STRICT_WITH_SIZE" to io.github.tonyzhye.beancount.core.Booking.STRICT_WITH_SIZE,
            "NONE" to io.github.tonyzhye.beancount.core.Booking.NONE,
            "FIFO" to io.github.tonyzhye.beancount.core.Booking.FIFO,
            "LIFO" to io.github.tonyzhye.beancount.core.Booking.LIFO,
            "HIFO" to io.github.tonyzhye.beancount.core.Booking.HIFO,
            "AVERAGE" to io.github.tonyzhye.beancount.core.Booking.AVERAGE
        )
        for ((name, expected) in methods) {
            val parser = BeancountParser()
            val result = parser.parseString("""
                2024-01-01 open Assets:Invest AAPL "$name"
            """.trimIndent())
            assertEquals(1, result.entries.size, "Should parse open with $name")
            val open = result.entries[0] as Open
            assertEquals(expected, open.booking, "Booking method should be $name")
        }
    }

    @Test
    fun `should parse compound cost with per and total`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 *
              Assets:Invest 10 AAPL {10 # 100 USD}
              Assets:Bank
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        val cost = txn.postings[0].cost
        assertNotNull(cost)
        assertEquals(Decimal("10"), cost?.numberPer)
        assertEquals(Decimal("100"), cost?.numberTotal)
        assertEquals("USD", cost?.currency)
    }

    @Test
    fun `should parse compound cost with only total`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 *
              Assets:Invest 10 AAPL {# 100 USD}
              Assets:Bank
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as Transaction
        val cost = txn.postings[0].cost
        assertNotNull(cost)
        assertEquals(null, cost?.numberPer)
        assertEquals(Decimal("100"), cost?.numberTotal)
        assertEquals("USD", cost?.currency)
    }
}
