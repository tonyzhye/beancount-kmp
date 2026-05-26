package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.Decimal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * Tests for Lexer.
 * Based on beancount.parser.lexer_test
 */
class LexerTest {

    private fun lex(input: String): Pair<List<Token>, List<LexerError>> {
        val lexer = Lexer(input)
        val tokens = mutableListOf<Token>()
        
        while (true) {
            val token = lexer.nextToken()
            if (token is Token.EOF) {
                tokens.add(token)
                break
            }
            tokens.add(token)
        }
        
        return Pair(tokens, lexer.getErrors())
    }

    @Test
    fun `should lex basic tokens`() {
        val input = """
            2013-05-18 2014-01-02
            Assets:US:Bank:Checking
            USD HOOL TEST_D
            "Nice dinner at Mermaid Inn"
            123 123.45 -123
        """.trimIndent()
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
        assertTrue(tokens.isNotEmpty(), "Expected tokens")
        
        // Check date token
        val dateToken = tokens[0] as Token.DATE
        assertEquals(LocalDate(2013, 5, 18), dateToken.value)
        
        // Check account token
        val accountToken = tokens.find { it is Token.ACCOUNT } as Token.ACCOUNT
        assertEquals("Assets:US:Bank:Checking", accountToken.value)
        
        // Check currency token
        val currencyToken = tokens.find { it is Token.CURRENCY && it.value == "USD" } as Token.CURRENCY
        assertEquals("USD", currencyToken.value)
        
        // Check string token
        val stringToken = tokens.find { it is Token.STRING } as Token.STRING
        assertEquals("Nice dinner at Mermaid Inn", stringToken.value)
        
        // Check number tokens
        val numberTokens = tokens.filterIsInstance<Token.NUMBER>()
        assertEquals(3, numberTokens.size, "Expected 3 numbers")
    }

    @Test
    fun `should lex transaction with postings`() {
        val input = """
            2013-05-18 * "Nice dinner at Mermaid Inn"
              Expenses:Restaurant         100 USD
              Assets:US:Cash             -100 USD
        """.trimIndent()
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty(), "Expected no errors")
        
        // Verify key tokens
        val dateToken = tokens[0] as Token.DATE
        assertEquals(LocalDate(2013, 5, 18), dateToken.value)
        
        val flagToken = tokens.find { it is Token.FLAG } as Token.FLAG
        assertEquals("*", flagToken.value)
        
        val stringToken = tokens.find { it is Token.STRING } as Token.STRING
        assertEquals("Nice dinner at Mermaid Inn", stringToken.value)
        
        val accountTokens = tokens.filterIsInstance<Token.ACCOUNT>()
        assertEquals(2, accountTokens.size)
        assertEquals("Expenses:Restaurant", accountTokens[0].value)
        assertEquals("Assets:US:Cash", accountTokens[1].value)
    }

    @Test
    fun `should lex open directive`() {
        val input = "2013-05-18 open Assets:US:Cash USD"
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty())
        assertEquals(5, tokens.size) // DATE, KEYWORD(open), ACCOUNT, CURRENCY, EOF
        
        val dateToken = tokens[0] as Token.DATE
        assertEquals(LocalDate(2013, 5, 18), dateToken.value)
        
        val keywordToken = tokens[1] as Token.KEYWORD
        assertEquals("open", keywordToken.value)
        
        val accountToken = tokens[2] as Token.ACCOUNT
        assertEquals("Assets:US:Cash", accountToken.value)
        
        val currencyToken = tokens[3] as Token.CURRENCY
        assertEquals("USD", currencyToken.value)
    }

    @Test
    fun `should lex balance directive`() {
        val input = "2013-05-18 balance Assets:US:Cash 1000.00 USD"
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty())
        
        val keywordToken = tokens.find { it is Token.KEYWORD && it.value == "balance" } as Token.KEYWORD
        assertEquals("balance", keywordToken.value)
        
        val numberToken = tokens.find { it is Token.NUMBER } as Token.NUMBER
        assertEquals(1000.00, numberToken.value, 0.001)
    }

    @Test
    fun `should handle comments`() {
        val input = """
; This is a comment
2013-05-18 open Assets:US:Cash
        """.trimIndent()
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty())
        // Comment should be skipped, only date, keyword, account, eof
        // But we also have EOL tokens
        assertTrue(tokens.size >= 4, "Expected at least 4 tokens but got ${tokens.size}")
        
        // Verify we have the key tokens
        val dateToken = tokens.find { it is Token.DATE } as? Token.DATE
        assertNotNull(dateToken, "Expected DATE token")
        
        val keywordToken = tokens.find { it is Token.KEYWORD && it.value == "open" } as? Token.KEYWORD
        assertNotNull(keywordToken, "Expected 'open' keyword token")
        
        val accountToken = tokens.find { it is Token.ACCOUNT } as? Token.ACCOUNT
        assertNotNull(accountToken, "Expected ACCOUNT token")
    }

    @Test
    fun `should handle invalid date`() {
        val input = "2013-12-98 open Assets:Test"
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isNotEmpty(), "Expected error for invalid date")
        assertTrue(errors[0].message.contains("out of range") || errors[0].message.contains("Invalid date"))
    }

    @Test
    fun `should handle invalid number format`() {
        val input = "1.234.00 USD"
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isNotEmpty(), "Expected error for invalid number")
    }

    @Test
    fun `should handle empty string`() {
        val input = """
            ""
        """.trimIndent()
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty())
        val stringToken = tokens.find { it is Token.STRING } as Token.STRING
        assertEquals("", stringToken.value)
    }

    @Test
    fun `should handle escaped characters in string`() {
        val input = """
            "The Great \"Juju\""
        """.trimIndent()
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty())
        val stringToken = tokens.find { it is Token.STRING } as Token.STRING
        assertEquals("The Great \"Juju\"", stringToken.value)
    }

    @Test
    fun `should handle unicode in account names`() {
        val input = "Óthяr:Bあnk"
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty())
        val accountToken = tokens.find { it is Token.ACCOUNT } as Token.ACCOUNT
        assertEquals("Óthяr:Bあnk", accountToken.value)
    }

    @Test
    fun `should handle tags and links`() {
        val input = """
            2013-05-18 * "Test" #tag1 #tag2 ^link1
              Expenses:Food  10 USD
        """.trimIndent()
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty())
        
        // Should have tag tokens
        val tagTokens = tokens.filter { it is Token.TAG }.map { it as Token.TAG }
        assertEquals(2, tagTokens.size, "Expected 2 tags")
        assertEquals("tag1", tagTokens[0].value)
        assertEquals("tag2", tagTokens[1].value)
        
        // Should have link tokens
        val linkTokens = tokens.filter { it is Token.LINK }.map { it as Token.LINK }
        assertEquals(1, linkTokens.size, "Expected 1 link")
        assertEquals("link1", linkTokens[0].value)
    }

    @Test
    fun `should handle keywords`() {
        val keywords = listOf(
            "open", "close", "commodity", "transaction",
            "balance", "pad", "note", "event", "price",
            "document", "query", "custom", "option",
            "plugin", "include"
        )
        
        for (keyword in keywords) {
            val input = "2013-05-18 $keyword Assets:Test"
            val (tokens, errors) = lex(input)
            
            assertTrue(errors.isEmpty(), "Unexpected error for keyword '$keyword': ${errors.firstOrNull()?.message}")
            
            val keywordToken = tokens.find { it is Token.KEYWORD && it.value == keyword } as Token.KEYWORD
            assertEquals(keyword, keywordToken.value, "Failed to lex keyword: $keyword")
        }
    }

    @Test
    fun `should handle flags`() {
        val flags = listOf("*", "!")
        
        for (flag in flags) {
            val input = "2013-05-18 $flag \"Test\""
            val (tokens, errors) = lex(input)
            
            assertTrue(errors.isEmpty(), "Unexpected error for flag '$flag'")
            
            val flagToken = tokens.find { it is Token.FLAG && it.value == flag } as Token.FLAG
            assertEquals(flag, flagToken.value, "Failed to lex flag: $flag")
        }
    }

    @Test
    fun `should handle complex transaction`() {
        val input = """
            2014-01-27 * "UNION MARKET"
              Liabilities:US:Amex:BlueCash    -22.02 USD
              Expenses:Food:Grocery            22.02 USD
        """.trimIndent()
        
        val (tokens, errors) = lex(input)
        
        assertTrue(errors.isEmpty())
        
        val numberTokens = tokens.filterIsInstance<Token.NUMBER>()
        assertEquals(2, numberTokens.size)
        assertEquals(-22.02, numberTokens[0].value, 0.001)
        assertEquals(22.02, numberTokens[1].value, 0.001)
    }
}

/**
 * Error class for lexer errors.
 */
data class LexerError(
    val line: Int,
    val column: Int,
    val message: String,
    val text: String
)
