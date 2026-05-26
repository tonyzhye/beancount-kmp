package io.github.tonyzhye.beancount.parser

import kotlinx.datetime.LocalDate

/**
 * Token types for beancount lexer.
 * Based on beancount.parser.lexer tokens.
 */
sealed class Token {
    abstract val text: String
    abstract val line: Int
    abstract val column: Int
    
    /** End of file */
    data class EOF(override val line: Int, override val column: Int) : Token() {
        override val text: String = ""
    }
    
    /** End of line */
    data class EOL(override val line: Int, override val column: Int) : Token() {
        override val text: String = "\\n"
    }
    
    /** Indentation (spaces at beginning of line) */
    data class INDENT(
        val spaces: Int,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Date: YYYY-MM-DD */
    data class DATE(
        val value: LocalDate,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Number: 123, 123.45, -123 */
    data class NUMBER(
        val value: Double,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** String: "..." */
    data class STRING(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Account: Assets:US:Cash */
    data class ACCOUNT(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Currency: USD, HOOL, TEST_D */
    data class CURRENCY(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Keyword: open, close, transaction, etc. */
    data class KEYWORD(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Flag: *, ! */
    data class FLAG(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Tag: #tag */
    data class TAG(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Link: ^link */
    data class LINK(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Key for metadata: key: */
    data class KEY(
        val value: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Boolean: TRUE, FALSE */
    data class BOOL(
        val value: Boolean,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** None/Null: NULL */
    data class NONE(override val line: Int, override val column: Int) : Token() {
        override val text: String = "NULL"
    }
    
    /** Single character symbols */
    data class COLON(override val line: Int, override val column: Int) : Token() {
        override val text: String = ":"
    }
    
    data class COMMA(override val line: Int, override val column: Int) : Token() {
        override val text: String = ","
    }
    
    data class SLASH(override val line: Int, override val column: Int) : Token() {
        override val text: String = "/"
    }
    
    data class MINUS(override val line: Int, override val column: Int) : Token() {
        override val text: String = "-"
    }
    
    data class PLUS(override val line: Int, override val column: Int) : Token() {
        override val text: String = "+"
    }
    
    data class ASTERISK(override val line: Int, override val column: Int) : Token() {
        override val text: String = "*"
    }
    
    data class AT(override val line: Int, override val column: Int) : Token() {
        override val text: String = "@"
    }
    
    data class LCURLY(override val line: Int, override val column: Int) : Token() {
        override val text: String = "{"
    }
    
    data class RCURLY(override val line: Int, override val column: Int) : Token() {
        override val text: String = "}"
    }
    
    data class LPAREN(override val line: Int, override val column: Int) : Token() {
        override val text: String = "("
    }
    
    data class RPAREN(override val line: Int, override val column: Int) : Token() {
        override val text: String = ")"
    }
    
    /** Generic symbol (fallback) */
    data class SYMBOL(
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Comment (skipped during parsing) */
    data class COMMENT(
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
    
    /** Error token for invalid input */
    data class ERROR(
        val message: String,
        override val text: String,
        override val line: Int,
        override val column: Int
    ) : Token()
}
