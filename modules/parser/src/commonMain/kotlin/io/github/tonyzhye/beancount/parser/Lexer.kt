package io.github.tonyzhye.beancount.parser

/**
 * Lexer for beancount input.
 * Converts raw text into a stream of tokens.
 */
class Lexer(private val input: String) {
    private var position = 0
    private var line = 1
    private var column = 1
    
    private val keywords = setOf(
        "open", "close", "commodity", "transaction",
        "balance", "pad", "note", "event", "price",
        "document", "query", "custom", "option",
        "plugin", "include", "pushtag", "poptag",
        "pushmeta", "popmeta"
    )
    
    /**
     * Get the next token from input.
     */
    fun nextToken(): Token {
        skipWhitespace()
        skipComments()
        skipWhitespace()
        
        if (isAtEnd()) {
            return Token.EOF(line, column)
        }
        
        val startLine = line
        val startColumn = column
        val char = advance()
        
        return when {
            char == '"' -> readString(startLine, startColumn)
            char.isDigit() || (char == '-' && peek().isDigit()) -> readNumberOrDate(char, startLine, startColumn)
            char.isLetter() || char == ':' -> readIdentifier(char, startLine, startColumn)
            char == '*' || char == '!' || (char.isLetter() && char.isUpperCase()) -> readFlagOrKeyword(char, startLine, startColumn)
            else -> Token.SYMBOL(char.toString(), startLine, startColumn)
        }
    }
    
    private fun readString(startLine: Int, startColumn: Int): Token {
        val builder = StringBuilder()
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\\') {
                advance()
                when (peek()) {
                    '"', '\\', 'n', 't', 'r' -> builder.append(advance())
                    else -> builder.append(advance())
                }
            } else {
                builder.append(advance())
            }
        }
        if (!isAtEnd() && peek() == '"') {
            advance() // closing quote
        }
        return Token.STRING(builder.toString(), "\"$builder\"", startLine, startColumn)
    }
    
    private fun readNumberOrDate(firstChar: Char, startLine: Int, startColumn: Int): Token {
        val builder = StringBuilder()
        builder.append(firstChar)
        
        while (!isAtEnd() && (peek().isDigit() || peek() == '.' || peek() == '-')) {
            builder.append(advance())
        }
        
        val text = builder.toString()
        
        // Try date format: YYYY-MM-DD
        if (text.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val parts = text.split("-")
            return Token.DATE(
                kotlinx.datetime.LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()),
                text, startLine, startColumn
            )
        }
        
        // Number
        return Token.NUMBER(text.toDoubleOrNull() ?: 0.0, text, startLine, startColumn)
    }
    
    private fun readIdentifier(firstChar: Char, startLine: Int, startColumn: Int): Token {
        val builder = StringBuilder()
        builder.append(firstChar)
        
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_' || peek() == '-' || peek() == ':' || peek() == '.')) {
            builder.append(advance())
        }
        
        val text = builder.toString()
        
        return when {
            text.startsWith(":") -> Token.ACCOUNT(text, text, startLine, startColumn)
            text in keywords -> Token.KEYWORD(text, text, startLine, startColumn)
            text.all { it.isUpperCase() || it.isDigit() || it == '_' } -> Token.CURRENCY(text, text, startLine, startColumn)
            else -> Token.KEYWORD(text, text, startLine, startColumn)
        }
    }
    
    private fun readFlagOrKeyword(char: Char, startLine: Int, startColumn: Int): Token {
        val text = char.toString()
        return if (text in setOf("*", "!")) {
            Token.FLAG(text, text, startLine, startColumn)
        } else {
            Token.KEYWORD(text, text, startLine, startColumn)
        }
    }
    
    private fun skipWhitespace() {
        while (!isAtEnd() && peek().isWhitespace()) {
            advance()
        }
    }
    
    private fun skipComments() {
        if (peek() == ';') {
            while (!isAtEnd() && peek() != '\n') {
                advance()
            }
        }
    }
    
    private fun isAtEnd(): Boolean = position >= input.length
    
    private fun peek(): Char = if (isAtEnd()) '\u0000' else input[position]
    
    private fun advance(): Char {
        val char = input[position++]
        if (char == '\n') {
            line++
            column = 1
        } else {
            column++
        }
        return char
    }
}
