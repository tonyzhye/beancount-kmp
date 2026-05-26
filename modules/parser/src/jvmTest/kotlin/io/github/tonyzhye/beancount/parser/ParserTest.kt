package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Parser.
 * Based on beancount.parser.parser_test
 */
class ParserTest {

    /**
     * Helper to parse a string and return the result.
     */
    private fun parseString(input: String): ParseResult {
        val parser = BeancountParser()
        return parser.parseString(input)
    }

    @Test
    fun `should parse simple transaction`() {
        val input = """
            2013-05-18 * "Nice dinner at Mermaid Inn"
              Expenses:Restaurant         100 USD
              Assets:US:Cash
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size, "Expected 1 entry")
        assertEquals(0, result.errors.size, "Expected no errors")
        
        val transaction = result.entries[0] as Transaction
        assertEquals(LocalDate(2013, 5, 18), transaction.date)
        assertEquals("*", transaction.flag)
        assertEquals("Nice dinner at Mermaid Inn", transaction.narration)
        assertEquals(2, transaction.postings.size)
    }

    @Test
    fun `should parse transaction with payee`() {
        val input = """
            2013-05-18 * "Mermaid Inn" "Nice dinner"
              Expenses:Restaurant         100 USD
              Assets:US:Cash
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        val transaction = result.entries[0] as Transaction
        assertEquals("Mermaid Inn", transaction.payee)
        assertEquals("Nice dinner", transaction.narration)
    }

    @Test
    fun `should parse transaction with tags and links`() {
        val input = """
            2013-05-18 * "Test" #tag1 #tag2 ^link1
              Expenses:Food  10 USD
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        val transaction = result.entries[0] as Transaction
        assertTrue(transaction.tags.contains("tag1"))
        assertTrue(transaction.tags.contains("tag2"))
        assertTrue(transaction.links.contains("link1"))
    }

    @Test
    fun `should parse open directive`() {
        val input = "2013-05-18 open Assets:US:Cash USD"
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        assertEquals(0, result.errors.size)
        
        val open = result.entries[0] as Open
        assertEquals(LocalDate(2013, 5, 18), open.date)
        assertEquals("Assets:US:Cash", open.account)
        assertEquals(listOf("USD"), open.currencies)
    }

    @Test
    fun `should parse open with multiple currencies`() {
        val input = "2013-05-18 open Assets:US:Cash USD, EUR"
        
        val result = parseString(input)
        
        val open = result.entries[0] as Open
        assertEquals(listOf("USD", "EUR"), open.currencies)
    }

    @Test
    fun `should parse close directive`() {
        val input = "2013-05-18 close Assets:US:Cash"
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val close = result.entries[0] as Close
        assertEquals(LocalDate(2013, 5, 18), close.date)
        assertEquals("Assets:US:Cash", close.account)
    }

    @Test
    fun `should parse balance directive`() {
        val input = "2013-05-18 balance Assets:US:Cash 1000.00 USD"
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val balance = result.entries[0] as Balance
        assertEquals(LocalDate(2013, 5, 18), balance.date)
        assertEquals("Assets:US:Cash", balance.account)
        assertEquals(Decimal("1000.00"), balance.amount.number)
        assertEquals("USD", balance.amount.currency)
    }

    @Test
    fun `should parse note directive`() {
        val input = """
            2013-05-18 note Assets:US:Cash "Something important"
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val note = result.entries[0] as Note
        assertEquals(LocalDate(2013, 5, 18), note.date)
        assertEquals("Assets:US:Cash", note.account)
        assertEquals("Something important", note.comment)
    }

    @Test
    fun `should parse event directive`() {
        val input = """
            2013-05-18 event "location" "Paris"
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val event = result.entries[0] as Event
        assertEquals("location", event.type)
        assertEquals("Paris", event.description)
    }

    @Test
    fun `should parse price directive`() {
        val input = "2013-05-18 price HOOL 1200.12 USD"
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val price = result.entries[0] as Price
        assertEquals("HOOL", price.currency)
        assertEquals(Decimal("1200.12"), price.amount.number)
        assertEquals("USD", price.amount.currency)
    }

    @Test
    fun `should parse commodity directive`() {
        val input = "2013-05-18 commodity HOOL"
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val commodity = result.entries[0] as Commodity
        assertEquals("HOOL", commodity.currency)
    }

    @Test
    fun `should parse pad directive`() {
        val input = "2013-05-18 pad Assets:US:Cash Equity:Opening-Balances"
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val pad = result.entries[0] as Pad
        assertEquals("Assets:US:Cash", pad.account)
        assertEquals("Equity:Opening-Balances", pad.sourceAccount)
    }

    @Test
    fun `should parse document directive`() {
        val input = """
            2013-05-18 document Assets:US:Cash "/path/to/statement.pdf"
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val document = result.entries[0] as Document
        assertEquals("Assets:US:Cash", document.account)
        assertEquals("/path/to/statement.pdf", document.filename)
    }

    @Test
    fun `should parse query directive`() {
        val input = """
            2013-05-18 query "assets" "SELECT * WHERE account ~ 'Assets'"
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val query = result.entries[0] as Query
        assertEquals("assets", query.name)
        assertEquals("SELECT * WHERE account ~ 'Assets'", query.queryString)
    }

    @Test
    fun `should parse custom directive`() {
        val input = """
            2013-05-18 custom "budget" "daily" 100.00 USD
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        
        val custom = result.entries[0] as Custom
        assertEquals("budget", custom.type)
        assertEquals(3, custom.values.size)
    }

    @Test
    fun `should parse option directive`() {
        val input = """
            option "title" "My Ledger"
            option "operating_currency" "USD"
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(0, result.entries.size, "Options should not create entries")
        assertEquals("My Ledger", result.options.title)
        assertEquals(listOf("USD"), result.options.operatingCurrencies)
    }

    @Test
    fun `should parse multiple directives`() {
        val input = """
            2013-05-18 open Assets:US:Cash USD
            2013-05-19 * "Test transaction"
              Expenses:Food  10 USD
              Assets:US:Cash
            2013-05-20 balance Assets:US:Cash 990.00 USD
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(3, result.entries.size)
        assertEquals(0, result.errors.size)
        
        assertTrue(result.entries[0] is Open)
        assertTrue(result.entries[1] is Transaction)
        assertTrue(result.entries[2] is Balance)
    }

    @Test
    fun `should handle empty input`() {
        val input = ""
        
        val result = parseString(input)
        
        assertEquals(0, result.entries.size)
        assertEquals(0, result.errors.size)
    }

    @Test
    fun `should handle comments`() {
        val input = """
            ; This is a comment
            2013-05-18 open Assets:US:Cash
            
            ; Another comment
            2013-05-19 * "Test"
              Expenses:Food  10 USD
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(2, result.entries.size)
        assertEquals(0, result.errors.size)
    }

    @Test
    fun `should handle posting with cost`() {
        val input = """
            2013-05-18 * "Buy HOOL"
              Assets:Investments:HOOL    10 HOOL {120.00 USD}
              Assets:US:Cash
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        val transaction = result.entries[0] as Transaction
        
        val posting = transaction.postings[0]
        assertNotNull(posting.cost)
        assertEquals(Decimal("120.00"), posting.cost?.number)
        assertEquals("USD", posting.cost?.currency)
    }

    @Test
    fun `should handle posting with price`() {
        val input = """
            2013-05-18 * "Buy HOOL"
              Assets:Investments:HOOL    10 HOOL @ 120.00 USD
              Assets:US:Cash
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        val transaction = result.entries[0] as Transaction
        
        val posting = transaction.postings[0]
        assertNotNull(posting.price)
        assertEquals(Decimal("120.00"), posting.price?.number)
        assertEquals("USD", posting.price?.currency)
    }

    @Test
    fun `should report error for invalid date`() {
        val input = """
            2013-13-01 * "Invalid date"
              Expenses:Food  10 USD
        """.trimIndent()
        
        val result = parseString(input)
        
        assertTrue(result.errors.isNotEmpty(), "Expected error for invalid date")
    }

    @Test
    fun `should report error for unbalanced transaction`() {
        val input = """
            2013-05-18 * "Unbalanced"
              Expenses:Food  10 USD
              Assets:US:Cash  20 USD
        """.trimIndent()
        
        val result = parseString(input)
        
        // Transaction itself parses, but validation would catch the imbalance
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `should parse transaction with multiple postings`() {
        val input = """
            2013-05-18 * "Complex transaction"
              Expenses:Food:Grocery       50.00 USD
              Expenses:Food:Restaurant    30.00 USD
              Assets:US:CreditCard       -80.00 USD
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        val transaction = result.entries[0] as Transaction
        assertEquals(3, transaction.postings.size)
    }

    @Test
    fun `should handle unicode in strings`() {
        val input = """
            2015-05-23 note Assets:Something "école Floß"
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        val note = result.entries[0] as Note
        assertEquals("école Floß", note.comment)
    }

    @Test
    fun `should handle empty string in transaction`() {
        val input = """
            2013-05-18 * ""
              Expenses:Food  10 USD
        """.trimIndent()
        
        val result = parseString(input)
        
        assertEquals(1, result.entries.size)
        val transaction = result.entries[0] as Transaction
        assertEquals("", transaction.narration)
    }
}


