package io.github.tonyzhye.beancount.parser

/**
 * Test utilities for parser tests.
 */
object ParserTestUtils {
    
    /**
     * Create a lexer and return all tokens.
     */
    fun tokenize(input: String): List<Token> {
        val lexer = Lexer(input)
        val tokens = mutableListOf<Token>()
        
        while (true) {
            val token = lexer.nextToken()
            tokens.add(token)
            if (token is Token.EOF) break
        }
        
        return tokens
    }
    
    /**
     * Filter tokens by type.
     */
    inline fun <reified T : Token> List<Token>.filterByType(): List<T> {
        return this.filterIsInstance<T>()
    }
    
    /**
     * Find first token by type.
     */
    inline fun <reified T : Token> List<Token>.findByType(): T? {
        return this.find { it is T } as? T
    }
}
