package io.github.tonyzhye.beancount.parser

import kotlinx.datetime.LocalDate

/**
 * Token types for beancount lexer.
 */
sealed class Token {
    abstract val text: String
    abstract val line: Int
    abstract val column: Int
    
    data class EOF(override val line: Int, override val column: Int) : Token() {
        override val text: String = ""
    }
    
    data class DATE(
        val value: LocalDate,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    data class NUMBER(
        val value: Double,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    data class STRING(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    data class ACCOUNT(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    data class CURRENCY(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    data class KEYWORD(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    data class FLAG(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    data class SYMBOL(
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    data class COMMENT(
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
}
