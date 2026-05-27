package io.github.tonyzhye.beancount.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Simple test for Lexer.
 */
class LexerSimpleTest {

    @Test
    fun `should tokenize bank keyword`() {
        val lexer = Lexer("bank:")
        val tokens = mutableListOf<Token>()
        var count = 0
        while (true) {
            val token = lexer.nextToken()
            tokens.add(token)
            if (token is Token.EOF) break
            count++
            if (count > 10) {
                println("Too many tokens!")
                break
            }
        }

        println("Tokens: ${tokens.map { it::class.simpleName }}")
        assertTrue(tokens.any { it is Token.KEY })
        assertTrue(tokens.any { it is Token.COLON })
    }
}
