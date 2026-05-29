package io.github.tonyzhye.beancount.query.parser

import kotlinx.datetime.LocalDate

/**
 * BQL Parser - handwritten recursive descent parser.
 * Supports: SELECT, FROM, WHERE, GROUP BY, ORDER BY, LIMIT, DISTINCT
 */
class BqlParser(private val input: String) {
    private var position = 0
    private val tokens = tokenize(input)
    private var currentTokenIndex = 0

    private data class Token(
        val type: TokenType,
        val value: String,
        val position: Int
    )

    private enum class TokenType {
        KEYWORD,    // SELECT, FROM, WHERE, etc.
        IDENTIFIER,
        STRING,
        INTEGER,
        DECIMAL,
        DATE,
        OPERATOR,   // +, -, *, /, =, !=, <, >, <=, >=, ~
        COMMA,
        LPAREN,
        RPAREN,
        EOF
    }

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = 0
        val length = input.length

        while (pos < length) {
            val char = input[pos]

            // Skip whitespace
            if (char.isWhitespace()) {
                pos++
                continue
            }

            // Single-line comment
            if (char == ';') {
                while (pos < length && input[pos] != '\n') pos++
                continue
            }

            // String literal
            if (char == '\'' || char == '"') {
                val quote = char
                val start = pos
                pos++
                val sb = StringBuilder()
                while (pos < length && input[pos] != quote) {
                    if (input[pos] == '\\' && pos + 1 < length) {
                        pos++
                        when (input[pos]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            '\\' -> sb.append('\\')
                            '\'' -> sb.append('\'')
                            '"' -> sb.append('"')
                            else -> sb.append(input[pos])
                        }
                    } else {
                        sb.append(input[pos])
                    }
                    pos++
                }
                pos++ // skip closing quote
                tokens.add(Token(TokenType.STRING, sb.toString(), start))
                continue
            }

            // Date: YYYY-MM-DD (must be checked before number to avoid splitting)
            if (char.isDigit() && pos + 9 < length &&
                input[pos + 4] == '-' && input[pos + 7] == '-' &&
                input.substring(pos, pos + 10).matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
            ) {
                tokens.add(Token(TokenType.DATE, input.substring(pos, pos + 10), pos))
                pos += 10
                continue
            }

            // Number (integer or decimal)
            if (char.isDigit() || (char == '.' && pos + 1 < length && input[pos + 1].isDigit())) {
                val start = pos
                val sb = StringBuilder()
                var hasDot = false

                if (char == '.') {
                    hasDot = true
                    sb.append(char)
                    pos++
                }

                while (pos < length && (input[pos].isDigit() || input[pos] == '.')) {
                    if (input[pos] == '.') {
                        if (hasDot) break
                        hasDot = true
                    }
                    sb.append(input[pos])
                    pos++
                }

                val value = sb.toString()
                if (hasDot) {
                    tokens.add(Token(TokenType.DECIMAL, value, start))
                } else {
                    tokens.add(Token(TokenType.INTEGER, value, start))
                }
                continue
            }

            // Identifier or keyword
            if (char.isLetter() || char == '_') {
                val start = pos
                val sb = StringBuilder()
                while (pos < length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
                    sb.append(input[pos])
                    pos++
                }
                val value = sb.toString()
                val type = if (isKeyword(value)) TokenType.KEYWORD else TokenType.IDENTIFIER
                tokens.add(Token(type, value, start))
                continue
            }

            // Operators and punctuation
            val start = pos
            when (char) {
                ',' -> { tokens.add(Token(TokenType.COMMA, ",", start)); pos++ }
                '(' -> { tokens.add(Token(TokenType.LPAREN, "(", start)); pos++ }
                ')' -> { tokens.add(Token(TokenType.RPAREN, ")", start)); pos++ }
                '+', '-', '*', '/', '~' -> {
                    tokens.add(Token(TokenType.OPERATOR, char.toString(), start))
                    pos++
                }
                '=', '<', '>', '!' -> {
                    if (pos + 1 < length && input[pos + 1] == '=') {
                        tokens.add(Token(TokenType.OPERATOR, input.substring(pos, pos + 2), start))
                        pos += 2
                    } else {
                        tokens.add(Token(TokenType.OPERATOR, char.toString(), start))
                        pos++
                    }
                }
                else -> {
                    // Unknown character, skip it
                    pos++
                }
            }
        }

        tokens.add(Token(TokenType.EOF, "", pos))
        return tokens
    }

    private fun isKeyword(value: String): Boolean {
        val keywords = setOf(
            "SELECT", "FROM", "WHERE", "GROUP", "BY", "ORDER", "LIMIT",
            "DISTINCT", "ASC", "DESC", "AS", "AND", "OR", "NOT",
            "TRUE", "FALSE", "NULL", "OPEN", "CLOSE", "CLEAR",
            "HAVING", "IN", "BETWEEN"
        )
        return value.uppercase() in keywords
    }

    private fun current(): Token = tokens.getOrElse(currentTokenIndex) { tokens.last() }

    private fun advance(): Token {
        val token = current()
        if (currentTokenIndex < tokens.size - 1) currentTokenIndex++
        return token
    }

    private fun expect(type: TokenType, value: String? = null): Token {
        val token = current()
        if (token.type != type || (value != null && token.value.uppercase() != value.uppercase())) {
            throw ParseException(
                "Expected ${value ?: type} but found ${token.value} at position ${token.position}"
            )
        }
        return advance()
    }

    private fun matchKeyword(keyword: String): Boolean {
        val token = current()
        return token.type == TokenType.KEYWORD && token.value.uppercase() == keyword.uppercase()
    }

    private fun match(type: TokenType): Boolean {
        return current().type == type
    }

    private fun consumeKeyword(keyword: String): Boolean {
        if (matchKeyword(keyword)) {
            advance()
            return true
        }
        return false
    }

    /**
     * Parse a complete query.
     */
    fun parseQuery(): AstQuery {
        expect(TokenType.KEYWORD, "SELECT")
        val distinct = consumeKeyword("DISTINCT")
        
        val targets = mutableListOf<AstTarget>()
        do {
            val expr = if (match(TokenType.OPERATOR) && current().value == "*") {
                advance()
                AstIdentifier("*")
            } else {
                parseExpression()
            }
            val alias = if (consumeKeyword("AS")) {
                expect(TokenType.IDENTIFIER).value
            } else null
            targets.add(AstTarget(expr, alias))
        } while (match(TokenType.COMMA).also { if (it) advance() })
        
        val from = if (matchKeyword("FROM")) parseFrom() else null
        val where = if (matchKeyword("WHERE")) parseWhere() else null
        val groupBy = if (matchKeyword("GROUP")) parseGroupBy() else emptyList()
        val having = if (matchKeyword("HAVING")) parseHaving() else null
        val orderBy = if (matchKeyword("ORDER")) parseOrderBy() else emptyList()
        val limit = if (matchKeyword("LIMIT")) parseLimit() else null

        if (!match(TokenType.EOF)) {
            throw ParseException("Unexpected token: ${current().value}")
        }

        return AstQuery(
            distinct = distinct,
            targets = targets,
            from = from,
            where = where,
            groupBy = groupBy,
            having = having,
            orderBy = orderBy,
            limit = limit
        )
    }

    private fun parseFrom(): AstFrom {
        expect(TokenType.KEYWORD, "FROM")
        val tableName = expect(TokenType.IDENTIFIER).value

        var openDate: LocalDate? = null
        var closeDate: LocalDate? = null
        var closeAll = false
        var clear = false

        if (consumeKeyword("OPEN")) {
            consumeKeyword("ON")
            openDate = parseDateLiteral()
        }

        if (consumeKeyword("CLOSE")) {
            if (consumeKeyword("ON")) {
                closeDate = parseDateLiteral()
            } else if (matchKeyword("TRUE")) {
                advance()
                closeAll = true
            } else {
                closeDate = parseDateLiteral()
            }
        }

        if (consumeKeyword("CLEAR")) {
            clear = true
        }

        return AstFrom(tableName, openDate, closeDate, closeAll, clear)
    }

    private fun parseWhere(): AstExpression {
        expect(TokenType.KEYWORD, "WHERE")
        return parseExpression()
    }

    private fun parseGroupBy(): List<AstExpression> {
        expect(TokenType.KEYWORD, "GROUP")
        expect(TokenType.KEYWORD, "BY")

        val expressions = mutableListOf<AstExpression>()
        do {
            expressions.add(parseExpression())
        } while (match(TokenType.COMMA).also { if (it) advance() })

        return expressions
    }

    private fun parseHaving(): AstExpression {
        expect(TokenType.KEYWORD, "HAVING")
        return parseExpression()
    }

    private fun parseOrderBy(): List<AstOrderBy> {
        expect(TokenType.KEYWORD, "ORDER")
        expect(TokenType.KEYWORD, "BY")

        val orders = mutableListOf<AstOrderBy>()
        do {
            val expr = parseExpression()
            val descending = when {
                consumeKeyword("DESC") -> true
                consumeKeyword("ASC") -> false
                else -> false
            }
            orders.add(AstOrderBy(expr, descending))
        } while (match(TokenType.COMMA).also { if (it) advance() })

        return orders
    }

    private fun parseLimit(): Int {
        expect(TokenType.KEYWORD, "LIMIT")
        return expect(TokenType.INTEGER).value.toInt()
    }

    /**
     * Expression parsing with operator precedence.
     * Precedence (lowest to highest):
     * OR, AND, NOT, = != < > <= >= ~, + -, * /, unary, primary
     */
    private fun parseExpression(): AstExpression {
        return parseOrExpression()
    }

    private fun parseOrExpression(): AstExpression {
        var left = parseAndExpression()
        while (consumeKeyword("OR")) {
            val right = parseAndExpression()
            left = AstBinaryOp("OR", left, right)
        }
        return left
    }

    private fun parseAndExpression(): AstExpression {
        var left = parseNotExpression()
        while (consumeKeyword("AND")) {
            val right = parseNotExpression()
            left = AstBinaryOp("AND", left, right)
        }
        return left
    }

    private fun parseNotExpression(): AstExpression {
        if (consumeKeyword("NOT")) {
            return AstUnaryOp("NOT", parseNotExpression())
        }
        return parseBetweenExpression()
    }

    private fun parseBetweenExpression(): AstExpression {
        var left = parseComparisonExpression()
        // Handle BETWEEN operator: expr BETWEEN low AND high
        if (consumeKeyword("BETWEEN")) {
            val low = parseAddExpression()  // Use lower precedence to avoid consuming AND
            expectKeyword("AND")
            val high = parseAddExpression()
            left = AstBetweenOp(left, low, high)
        }
        return left
    }

    private fun parseComparisonExpression(): AstExpression {
        var left = parseAddExpression()
        while (match(TokenType.OPERATOR) && current().value in setOf("=", "!=", "<", ">", "<=", ">=", "~")) {
            val op = advance().value
            val right = parseAddExpression()
            left = AstBinaryOp(op, left, right)
        }
        // Handle IN operator: expr IN (val1, val2, ...)
        if (consumeKeyword("IN")) {
            expect(TokenType.LPAREN)
            val values = mutableListOf<AstExpression>()
            if (!match(TokenType.RPAREN)) {
                do {
                    values.add(parseExpression())
                } while (match(TokenType.COMMA).also { if (it) advance() })
            }
            expect(TokenType.RPAREN)
            left = AstInOp(left, values)
        }
        return left
    }

    private fun expectKeyword(keyword: String): Token {
        return expect(TokenType.KEYWORD, keyword)
    }

    private fun parseAddExpression(): AstExpression {
        var left = parseMulExpression()
        while (match(TokenType.OPERATOR) && current().value in setOf("+", "-")) {
            val op = advance().value
            val right = parseMulExpression()
            left = AstBinaryOp(op, left, right)
        }
        return left
    }

    private fun parseMulExpression(): AstExpression {
        var left = parseUnaryExpression()
        while (match(TokenType.OPERATOR) && current().value in setOf("*", "/")) {
            val op = advance().value
            val right = parseUnaryExpression()
            left = AstBinaryOp(op, left, right)
        }
        return left
    }

    private fun parseUnaryExpression(): AstExpression {
        if (match(TokenType.OPERATOR) && current().value in setOf("+", "-")) {
            val op = advance().value
            return AstUnaryOp(op, parseUnaryExpression())
        }
        return parsePrimaryExpression()
    }

    private fun parsePrimaryExpression(): AstExpression {
        return when {
            match(TokenType.STRING) -> {
                AstStringLiteral(advance().value)
            }
            match(TokenType.INTEGER) -> {
                AstIntegerLiteral(advance().value.toInt())
            }
            match(TokenType.DECIMAL) -> {
                AstDecimalLiteral(advance().value)
            }
            match(TokenType.DATE) -> {
                AstDateLiteral(parseDateLiteral())
            }
            matchKeyword("TRUE") -> {
                advance()
                AstBooleanLiteral(true)
            }
            matchKeyword("FALSE") -> {
                advance()
                AstBooleanLiteral(false)
            }
            matchKeyword("NULL") -> {
                advance()
                AstNullLiteral()
            }
            match(TokenType.IDENTIFIER) || match(TokenType.KEYWORD) -> {
                val name = advance().value
                if (match(TokenType.LPAREN)) {
                    // Function call
                    advance() // consume (
                    val args = mutableListOf<AstExpression>()
                    if (!match(TokenType.RPAREN)) {
                        do {
                            if (match(TokenType.OPERATOR) && current().value == "*") {
                                advance()
                                args.add(AstIdentifier("*"))
                            } else {
                                args.add(parseExpression())
                            }
                        } while (consumeKeyword(","))
                    }
                    expect(TokenType.RPAREN)
                    AstFunctionCall(name, args)
                } else {
                    AstIdentifier(name)
                }
            }
            match(TokenType.LPAREN) -> {
                advance() // consume (
                val expr = parseExpression()
                expect(TokenType.RPAREN)
                expr
            }
            else -> {
                throw ParseException("Unexpected token: ${current().value} at position ${current().position}")
            }
        }
    }

    private fun parseDateLiteral(): LocalDate {
        val dateStr = expect(TokenType.DATE).value
        val parts = dateStr.split("-")
        return LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }
}

class ParseException(message: String) : RuntimeException(message)
