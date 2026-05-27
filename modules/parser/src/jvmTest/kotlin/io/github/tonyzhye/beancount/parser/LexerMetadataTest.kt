package io.github.tonyzhye.beancount.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test Lexer token generation for metadata.
 */
class LexerMetadataTest {

    @Test
    fun `should tokenize metadata key`() {
        val lexer = Lexer("  bank: \"Chase\"")
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = lexer.nextToken()
            tokens.add(token)
            if (token is Token.EOF) break
        }

        println("Tokens: ${tokens.map { it::class.simpleName }}")

        assertTrue(tokens.any { it is Token.INDENT })
        assertTrue(tokens.any { it is Token.KEY && (it as Token.KEY).value == "bank" })
        assertTrue(tokens.any { it is Token.COLON })
        assertTrue(tokens.any { it is Token.STRING && (it as Token.STRING).value == "Chase" })
    }

    @Test
    fun `should tokenize account with metadata`() {
        val input = """
            Assets:Account  100.00 USD
              memo: "test"
        """.trimIndent()
        val lexer = Lexer(input)
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = lexer.nextToken()
            tokens.add(token)
            if (token is Token.EOF) break
        }

        println("Tokens: ${tokens.map { it::class.simpleName + "(" + (it as? Token.ACCOUNT)?.value + (it as? Token.KEY)?.value + ")" }}")

        assertTrue(tokens.any { it is Token.ACCOUNT && (it as Token.ACCOUNT).value == "Assets:Account" })
        assertTrue(tokens.any { it is Token.KEY && (it as Token.KEY).value == "memo" })
    }
}
