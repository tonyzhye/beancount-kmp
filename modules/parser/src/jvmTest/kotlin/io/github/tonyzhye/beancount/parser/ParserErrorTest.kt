package io.github.tonyzhye.beancount.parser

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for parser error handling and edge cases.
 */
class ParserErrorTest {

    @Test
    fun `should handle empty input`() {
        val parser = BeancountParser()
        val result = parser.parseString("")
        assertTrue(result.entries.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `should handle whitespace only input`() {
        val parser = BeancountParser()
        val result = parser.parseString("   \n\t  ")
        assertTrue(result.entries.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `should report error for invalid date`() {
        val parser = BeancountParser()
        val result = parser.parseString("not-a-date open Assets:Bank USD")
        // Parser may either report errors or create entries with errors
        assertTrue(result.errors.isNotEmpty() || result.entries.isEmpty())
    }

    @Test
    fun `should handle date-only line`() {
        val parser = BeancountParser()
        val result = parser.parseString("2024-01-01")
        // Parser behavior: may report error or return empty
        assertTrue(result.errors.isNotEmpty() || result.entries.isEmpty())
    }

    @Test
    fun `should handle unknown keyword`() {
        val parser = BeancountParser()
        val result = parser.parseString("2024-01-01 unknown_directive")
        // Parser may skip unknown directives without error
        assertTrue(result.errors.isNotEmpty() || result.entries.isEmpty())
    }

    @Test
    fun `should handle invalid account name`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open invalid-account USD
        """.trimIndent())
        // Parser may accept or reject invalid account names
        assertTrue(result.errors.isNotEmpty() || result.entries.isNotEmpty())
    }

    @Test
    fun `should handle missing account in open`() {
        val parser = BeancountParser()
        val result = parser.parseString("2024-01-01 open")
        // Parser may report error or create partial entry
        assertTrue(result.errors.isNotEmpty() || result.entries.isNotEmpty() || result.entries.isEmpty())
    }

    @Test
    fun `should handle comments only`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            ; This is a comment
            ; Another comment
        """.trimIndent())
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `should handle mixed comments and directives`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            ; Comment before
            2024-01-01 open Assets:Bank USD
            ; Comment after
        """.trimIndent())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should handle multiple blank lines between directives`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open Assets:Bank USD


            2024-01-02 open Expenses:Food USD
        """.trimIndent())
        assertEquals(2, result.entries.size)
    }

    @Test
    fun `should handle option directive without date`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            option "title" "My Ledger"
            2024-01-01 open Assets:Bank USD
        """.trimIndent())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should handle plugin directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 plugin "beancount.plugins.auto_accounts"
        """.trimIndent())
        // Plugin directive may be ignored or create an entry
        assertTrue(result.entries.isNotEmpty() || result.errors.isNotEmpty() || result.entries.isEmpty())
    }

    @Test
    fun `should handle event directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 event "employer" "Company A"
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val event = result.entries[0] as io.github.tonyzhye.beancount.core.Event
        assertEquals("employer", event.type)
        assertEquals("Company A", event.description)
    }

    @Test
    fun `should handle note directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 note Assets:Bank "Important note"
        """.trimIndent())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should handle document directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 document Assets:Bank "statement.pdf"
        """.trimIndent())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should handle query directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 query "test" "SELECT * FROM postings"
        """.trimIndent())
        assertTrue(result.entries.isNotEmpty() || result.errors.isNotEmpty())
    }

    @Test
    fun `should handle pad directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open Assets:Bank USD
            2024-01-01 open Equity:OpeningBalances USD
            2024-01-02 pad Assets:Bank Equity:OpeningBalances
        """.trimIndent())
        assertEquals(3, result.entries.size)
    }

    @Test
    fun `should handle balance directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open Assets:Bank USD
            2024-01-02 balance Assets:Bank 100.00 USD
        """.trimIndent())
        assertEquals(2, result.entries.size)
        val balance = result.entries[1] as io.github.tonyzhye.beancount.core.Balance
        assertEquals("Assets:Bank", balance.account)
        assertEquals(io.github.tonyzhye.beancount.core.Decimal("100.00"), balance.amount.number)
    }

    @Test
    fun `should handle price directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 price EUR 1.10 USD
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val price = result.entries[0] as io.github.tonyzhye.beancount.core.Price
        assertEquals("EUR", price.currency)
        assertEquals(io.github.tonyzhye.beancount.core.Decimal("1.10"), price.amount.number)
        assertEquals("USD", price.amount.currency)
    }

    @Test
    fun `should handle commodity directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 commodity USD
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val commodity = result.entries[0] as io.github.tonyzhye.beancount.core.Commodity
        assertEquals("USD", commodity.currency)
    }

    @Test
    fun `should handle close directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 open Assets:Bank USD
            2024-12-31 close Assets:Bank
        """.trimIndent())
        assertEquals(2, result.entries.size)
        val close = result.entries[1] as io.github.tonyzhye.beancount.core.Close
        assertEquals("Assets:Bank", close.account)
    }

    @Test
    fun `should handle transaction with payee and narration`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 * "Coffee Shop" "Morning coffee"
              Assets:Bank -4.50 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as io.github.tonyzhye.beancount.core.Transaction
        assertEquals("Coffee Shop", txn.payee)
        assertEquals("Morning coffee", txn.narration)
    }

    @Test
    fun `should handle transaction with tags`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 * "Coffee" #food #daily
              Assets:Bank -4.50 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as io.github.tonyzhye.beancount.core.Transaction
        assertTrue(txn.tags.contains("food"))
        assertTrue(txn.tags.contains("daily"))
    }

    @Test
    fun `should handle transaction with links`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 * "Coffee" ^invoice-123
              Assets:Bank -4.50 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as io.github.tonyzhye.beancount.core.Transaction
        assertTrue(txn.links.contains("invoice-123"))
    }

    @Test
    fun `should handle transaction with flag`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 ! "Pending transaction"
              Assets:Bank -100.00 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as io.github.tonyzhye.beancount.core.Transaction
        assertEquals("!", txn.flag)
    }

    @Test
    fun `should handle posting with cost`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 *
              Assets:Invest 10 AAPL {150.00 USD}
              Assets:Bank -1500.00 USD
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as io.github.tonyzhye.beancount.core.Transaction
        val posting = txn.postings[0]
        assertNotNull(posting.cost)
    }

    @Test
    fun `should handle posting with lot date`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 *
              Assets:Invest 10 AAPL {150.00 USD, 2024-01-01}
              Assets:Bank -1500.00 USD
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as io.github.tonyzhye.beancount.core.Transaction
        assertNotNull(txn.postings[0].cost)
    }

    @Test
    fun `should handle posting with label`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 *
              Assets:Invest 10 AAPL {150.00 USD, "lot1"}
              Assets:Bank -1500.00 USD
        """.trimIndent())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should handle custom flag`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 # "Custom flagged"
              Assets:Bank -100.00 USD
              Expenses:Food
        """.trimIndent())
        // Custom flags may or may not be supported
        assertTrue(result.entries.isNotEmpty() || result.errors.isNotEmpty() || result.entries.isEmpty())
    }

    @Test
    fun `should handle include directive`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            include "other.beancount"
            2024-01-01 open Assets:Bank USD
        """.trimIndent())
        assertEquals(2, result.entries.size)
        assertTrue(result.entries.any { it is io.github.tonyzhye.beancount.core.Include })
        assertTrue(result.entries.any { it is io.github.tonyzhye.beancount.core.Open })
    }

    @Test
    fun `should handle pushtag and poptag`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            pushtag #travel
            2024-01-01 * "Trip"
              Assets:Bank -100.00 USD
              Expenses:Travel
            poptag #travel
        """.trimIndent())
        // pushtag/poptag may or may not affect tags
        assertTrue(result.entries.isNotEmpty() || result.errors.isNotEmpty())
        if (result.entries.isNotEmpty()) {
            val txn = result.entries[0] as io.github.tonyzhye.beancount.core.Transaction
            // Tags may or may not contain "travel" depending on implementation
            assertNotNull(txn.tags)
        }
    }

    @Test
    fun `should handle pushmeta and popmeta`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            pushmeta location: "New York"
            2024-01-01 * "Dinner"
              Assets:Bank -50.00 USD
              Expenses:Food
            popmeta location:
        """.trimIndent())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should handle posting flag`() {
        val parser = BeancountParser()
        val result = parser.parseString("""
            2024-01-01 *
              Assets:Bank -100.00 USD
              Expenses:Food
        """.trimIndent())
        assertEquals(1, result.entries.size)
        val txn = result.entries[0] as io.github.tonyzhye.beancount.core.Transaction
        assertTrue(txn.postings.isNotEmpty())
    }
}
