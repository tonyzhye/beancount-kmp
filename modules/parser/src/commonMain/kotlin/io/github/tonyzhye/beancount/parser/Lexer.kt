package io.github.tonyzhye.beancount.parser

import kotlinx.datetime.LocalDate

/**
 * Lexer error information.
 */
data class LexerError(
    val line: Int,
    val column: Int,
    val message: String,
    val text: String
)

/**
 * Lexer for beancount input.
 * Converts raw text into a stream of tokens.
 * Based on beancount.parser.lexer.
 */
class Lexer(private val input: String) {
    private var position = 0
    private var line = 1
    private var column = 1
    private val errors = mutableListOf<LexerError>()
    
    /** Get all errors collected during lexing. */
    fun getErrors(): List<LexerError> = errors.toList()
    
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
        // Handle end of file
        if (isAtEnd()) {
            return Token.EOF(line, column)
        }
        
        // Handle newlines
        if (peek() == '\n' || peek() == '\r') {
            return readEOL()
        }
        
        // Handle indentation at start of line
        if (column == 1 && peek() == ' ') {
            return readIndent()
        }
        
        // Skip whitespace (not at start of line, not newlines)
        skipWhitespace()
        skipComments()
        skipWhitespace()
        
        if (isAtEnd()) {
            return Token.EOF(line, column)
        }
        
        // Handle newlines after comments
        if (peek() == '\n' || peek() == '\r') {
            return readEOL()
        }
        
        val startLine = line
        val startColumn = column
        val char = peek()
        
        return when {
            // String literal
            char == '"' -> {
                advance() // consume opening quote
                readString(startLine, startColumn)
            }
            
            // Date or Number (starts with digit or +/- followed by digit)
            char.isDigit() || 
            ((char == '-' || char == '+') && position + 1 < input.length && input[position + 1].isDigit()) -> {
                readNumberOrDate(startLine, startColumn)
            }
            
            // Tag: #tag
            char == '#' -> {
                advance()
                readTag(startLine, startColumn)
            }
            
            // Link: ^link
            char == '^' -> {
                advance()
                readLink(startLine, startColumn)
            }
            
            // Account or identifier (starts with letter or :)
            char.isLetter() || char == ':' -> {
                readIdentifier(startLine, startColumn)
            }
            
            // Single character tokens
            // Note: * can be a flag (in transaction) or asterisk
            char == '*' -> {
                advance()
                Token.FLAG("*", "*", startLine, startColumn)
            }
            
            char == '!' -> {
                advance()
                Token.FLAG("!", "!", startLine, startColumn)
            }
            
            char == '@' -> {
                advance()
                Token.AT(startLine, startColumn)
            }
            
            char == '{' -> {
                advance()
                Token.LCURLY(startLine, startColumn)
            }
            
            char == '}' -> {
                advance()
                Token.RCURLY(startLine, startColumn)
            }
            
            char == '(' -> {
                advance()
                Token.LPAREN(startLine, startColumn)
            }
            
            char == ')' -> {
                advance()
                Token.RPAREN(startLine, startColumn)
            }
            
            char == ':' -> {
                advance()
                Token.COLON(startLine, startColumn)
            }
            
            char == ',' -> {
                advance()
                Token.COMMA(startLine, startColumn)
            }
            
            char == '/' -> {
                advance()
                Token.SLASH(startLine, startColumn)
            }
            
            char == '-' -> {
                advance()
                Token.MINUS(startLine, startColumn)
            }
            
            char == '+' -> {
                advance()
                Token.PLUS(startLine, startColumn)
            }
            
            // Unknown character - error token
            else -> {
                advance()
                reportError("Unexpected character: '$char'", startLine, startColumn, char.toString())
                Token.ERROR("Unexpected character: '$char'", char.toString(), startLine, startColumn)
            }
        }
    }
    
    private fun readEOL(): Token {
        val startLine = line
        val startColumn = column
        
        if (peek() == '\r') {
            advance()
        }
        if (peek() == '\n') {
            advance()
        }
        
        return Token.EOL(startLine, startColumn)
    }
    
    private fun readIndent(): Token {
        val startLine = line
        val startColumn = column
        val builder = StringBuilder()
        var spaces = 0
        
        while (!isAtEnd() && peek() == ' ') {
            builder.append(advance())
            spaces++
        }
        
        return Token.INDENT(spaces, builder.toString(), startLine, startColumn)
    }
    
    private fun readString(startLine: Int, startColumn: Int): Token {
        val builder = StringBuilder()
        val rawBuilder = StringBuilder("\"")
        
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\\') {
                rawBuilder.append(advance()) // backslash
                when (peek()) {
                    '"' -> {
                        rawBuilder.append(advance())
                        builder.append('"')
                    }
                    '\\' -> {
                        rawBuilder.append(advance())
                        builder.append('\\')
                    }
                    'n' -> {
                        rawBuilder.append(advance())
                        builder.append('\n')
                    }
                    't' -> {
                        rawBuilder.append(advance())
                        builder.append('\t')
                    }
                    'r' -> {
                        rawBuilder.append(advance())
                        builder.append('\r')
                    }
                    else -> {
                        // Unknown escape sequence, just append the character
                        builder.append(advance())
                    }
                }
            } else {
                val char = advance()
                rawBuilder.append(char)
                builder.append(char)
            }
        }
        
        if (!isAtEnd() && peek() == '"') {
            rawBuilder.append(advance()) // closing quote
        }
        
        return Token.STRING(builder.toString(), rawBuilder.toString(), startLine, startColumn)
    }
    
    private fun readNumberOrDate(startLine: Int, startColumn: Int): Token {
        val builder = StringBuilder()
        
        // Handle sign
        if (peek() == '-' || peek() == '+') {
            builder.append(advance())
        }
        
        // Read digits and separators
        while (!isAtEnd() && (peek().isDigit() || peek() == '.' || peek() == ',' || peek() == '-')) {
            builder.append(advance())
        }
        
        val text = builder.toString()
        
        // Try date format: YYYY-MM-DD or YYYY/MM/DD
        val dateRegex = Regex("""^(\d{4})[-/](\d{2})[-/](\d{2})$""")
        val dateMatch = dateRegex.matchEntire(text)
        if (dateMatch != null) {
            val year = dateMatch.groupValues[1].toInt()
            val month = dateMatch.groupValues[2].toInt()
            val day = dateMatch.groupValues[3].toInt()
            
            return try {
                Token.DATE(LocalDate(year, month, day), text, startLine, startColumn)
            } catch (e: IllegalArgumentException) {
                reportError("Invalid date: $text", startLine, startColumn, text)
                Token.ERROR("Invalid date: $text", text, startLine, startColumn)
            }
        }
        
        // Remove commas for number parsing
        val numberText = text.replace(",", "")
        
        // Check for invalid number format (multiple dots)
        if (numberText.count { it == '.' } > 1) {
            reportError("Invalid number format: $text", startLine, startColumn, text)
            return Token.ERROR("Invalid number format: $text", text, startLine, startColumn)
        }
        
        // Parse number
        return try {
            val value = numberText.toDouble()
            Token.NUMBER(value, text, startLine, startColumn)
        } catch (e: NumberFormatException) {
            reportError("Invalid number: $text", startLine, startColumn, text)
            Token.ERROR("Invalid number: $text", text, startLine, startColumn)
        }
    }
    
    private fun readTag(startLine: Int, startColumn: Int): Token {
        val builder = StringBuilder()
        
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_' || peek() == '-')) {
            builder.append(advance())
        }
        
        val text = builder.toString()
        return Token.TAG(text, "#$text", startLine, startColumn)
    }
    
    private fun readLink(startLine: Int, startColumn: Int): Token {
        val builder = StringBuilder()
        
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_' || peek() == '-')) {
            builder.append(advance())
        }
        
        val text = builder.toString()
        return Token.LINK(text, "^$text", startLine, startColumn)
    }
    
    private fun readIdentifier(startLine: Int, startColumn: Int): Token {
        val builder = StringBuilder()
        
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_' || peek() == '-' || peek() == ':' || peek() == '.')) {
            builder.append(advance())
        }
        
        val text = builder.toString()
        
        // Check if it's an account (contains :)
        if (text.contains(':')) {
            return Token.ACCOUNT(text, text, startLine, startColumn)
        }
        
        // Check if it's a keyword
        if (text.lowercase() in keywords) {
            return Token.KEYWORD(text.lowercase(), text, startLine, startColumn)
        }
        
        // Check if it's a boolean
        if (text == "TRUE") {
            return Token.BOOL(true, text, startLine, startColumn)
        }
        if (text == "FALSE") {
            return Token.BOOL(false, text, startLine, startColumn)
        }
        
        // Check if it's NULL
        if (text == "NULL") {
            return Token.NONE(startLine, startColumn)
        }
        
        // Check if it's a currency (all uppercase with digits and underscores)
        if (text.all { it.isUpperCase() || it.isDigit() || it == '_' || it == '/' || it == '.' }) {
            return Token.CURRENCY(text, text, startLine, startColumn)
        }
        
        // Check if it's a flag (single uppercase letter)
        if (text.length == 1 && text[0].isUpperCase()) {
            return Token.FLAG(text, text, startLine, startColumn)
        }
        
        // Check if it could be a key (for metadata)
        if (peek() == ':') {
            return Token.KEY(text, text, startLine, startColumn)
        }
        
        // Otherwise, it's a keyword or identifier
        return Token.KEYWORD(text, text, startLine, startColumn)
    }
    
    private fun skipWhitespace() {
        while (!isAtEnd() && peek().isWhitespace() && peek() != '\n' && peek() != '\r') {
            advance()
        }
    }
    
    private fun skipComments() {
        if (peek() == ';') {
            while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
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
    
    private fun reportError(message: String, line: Int, column: Int, text: String) {
        errors.add(LexerError(line, column, message, text))
    }
}
