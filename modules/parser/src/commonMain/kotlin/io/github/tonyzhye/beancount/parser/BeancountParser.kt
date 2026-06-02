package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate

/**
 * Recursive descent parser for beancount input.
 * Converts a stream of tokens into a list of directives.
 */
class BeancountParser : Parser {
    
    private lateinit var lexer: Lexer
    private lateinit var tokens: List<Token>
    private var position = 0
    private val errors = mutableListOf<ParseError>()
    private val options = mutableMapOf<String, Any>()
    private var currentFilename = ""
    
    /**
     * Parse error information.
     */
    data class ParseError(
        val line: Int,
        val column: Int,
        val message: String
    )
    
    override fun parseFile(filename: String): ParseResult {
        currentFilename = filename
        val content = try {
            java.io.File(filename).readText()
        } catch (e: Exception) {
            return ParseResult(
                emptyList(),
                listOf(LoadError(
                    newMetadata(filename, 0),
                    "Cannot read file: ${e.message}"
                )),
                Options(filename = filename)
            )
        }
        return parseString(content, filename)
    }
    
    override fun parseString(content: String): ParseResult {
        return parseString(content, "")
    }
    
    private fun parseString(content: String, filename: String): ParseResult {
        currentFilename = filename
        lexer = Lexer(content)
        position = 0 // Reset position for new parse

        // Tokenize
        val tokenList = mutableListOf<Token>()
        while (true) {
            val token = lexer.nextToken()
            tokenList.add(token)
            if (token is Token.EOF) break
        }
        tokens = tokenList
        
        // Parse
        val entries = mutableListOf<Directive>()
        val parseErrors = mutableListOf<BeancountError>()
        
        // Collect lexer errors
        lexer.getErrors().forEach { error ->
            parseErrors.add(LoadError(
                newMetadata(filename, error.line),
                error.message
            ))
        }
        
        // Parse directives
        while (!isAtEnd()) {
            skipWhitespaceAndComments()
            
            if (isAtEnd()) break
            
            // Handle options without dates
            if (peek() is Token.KEYWORD && (peek() as Token.KEYWORD).value == "option") {
                parseOptionNoDate()
                continue
            }
            
            val entry = parseDirective()
            if (entry != null) {
                entries.add(entry)
            }
        }
        
        // Build options from collected options
        val optionsMap = buildOptions()
        
        return ParseResult(
            entries,
            parseErrors,
            optionsMap
        )
    }
    
    private fun parseDirective(): Directive? {
        return when (val token = peek()) {
            is Token.DATE -> parseDatedDirective()
            else -> {
                reportError("Expected date or directive, found ${token::class.simpleName}")
                advance() // skip
                null
            }
        }
    }
    
    // Tag and meta stacks for pushtag/poptag/pushmeta/popmeta
    private val tagStack = mutableListOf<String>()
    private val metaStack = mutableMapOf<String, Any>()
    
    private fun parseDatedDirective(): Directive? {
        val dateToken = consume(Token.DATE::class) as? Token.DATE
            ?: return null
        
        skipWhitespaceAndComments()
        
        return when (val token = peek()) {
            is Token.KEYWORD -> when (token.value) {
                "open" -> parseOpen(dateToken)
                "close" -> parseClose(dateToken)
                "commodity" -> parseCommodity(dateToken)
                "transaction" -> parseTransaction(dateToken)
                "balance" -> parseBalance(dateToken)
                "pad" -> parsePad(dateToken)
                "note" -> parseNote(dateToken)
                "event" -> parseEvent(dateToken)
                "price" -> parsePrice(dateToken)
                "document" -> parseDocument(dateToken)
                "query" -> parseQuery(dateToken)
                "custom" -> parseCustom(dateToken)
                "include" -> parseInclude(dateToken)
                "pushtag" -> parsePushTag(dateToken)
                "poptag" -> parsePopTag(dateToken)
                "pushmeta" -> parsePushMeta(dateToken)
                "popmeta" -> parsePopMeta(dateToken)
                "plugin" -> {
                    parsePlugin(dateToken)
                    null // Plugins don't create entries
                }
                "option" -> {
                    parseOption(dateToken)
                    null // Options don't create entries
                }
                else -> {
                    reportError("Unknown keyword: ${token.value}")
                    skipToNextDirective()
                    null
                }
            }
            is Token.FLAG -> parseTransaction(dateToken) // Transaction can start with flag directly
            is Token.STRING -> parseTransaction(dateToken) // Transaction can start with narration directly (no flag)
            else -> {
                reportError("Expected keyword after date")
                skipToNextDirective()
                null
            }
        }
    }
    
    private fun parseOpen(dateToken: Token.DATE): Open {
        consume(Token.KEYWORD::class) // consume "open"
        skipWhitespaceAndComments()
        
        val account = parseAccount()
        val currencies = parseCurrencyList()
        val booking = if (peek() is Token.KEYWORD && (peek() as Token.KEYWORD).value == "STRICT") {
            consume(Token.KEYWORD::class)
            io.github.tonyzhye.beancount.core.Booking.STRICT
        } else null
        
        val meta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Open(
            meta = meta,
            date = dateToken.value,
            account = account,
            currencies = currencies,
            booking = booking
        )
    }
    
    private fun parseClose(dateToken: Token.DATE): Close {
        consume(Token.KEYWORD::class) // consume "close"
        skipWhitespaceAndComments()
        
        val account = parseAccount()
        
        val closeMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Close(
            meta = closeMeta,
            date = dateToken.value,
            account = account
        )
    }

    private fun parseCommodity(dateToken: Token.DATE): Commodity {
        consume(Token.KEYWORD::class) // consume "commodity"
        skipWhitespaceAndComments()

        val currency = parseCurrency()
        val commodityMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Commodity(
            meta = commodityMeta,
            date = dateToken.value,
            currency = currency
        )
    }
    
    private fun parseTransaction(dateToken: Token.DATE): Transaction {
        val flag = if (peek() is Token.FLAG) {
            (consume(Token.FLAG::class) as Token.FLAG).value
        } else if (peek() is Token.KEYWORD && (peek() as Token.KEYWORD).value == "transaction") {
            consume(Token.KEYWORD::class) // consume "transaction"
            "*"
        } else {
            "*"
        }
        
        skipWhitespaceAndComments()
        
        // Parse payee and narration
        // Format: ["payee"] "narration"
        var payee: String? = null
        var narration: String? = null
        
        // Check if there are strings
        if (peek() is Token.STRING) {
            val firstString = (consume(Token.STRING::class) as Token.STRING).value
            skipWhitespaceAndComments()
            
            // If there's another string, first is payee, second is narration
            // Otherwise, first is narration
            if (peek() is Token.STRING) {
                payee = firstString
                narration = (consume(Token.STRING::class) as Token.STRING).value
            } else {
                narration = firstString
            }
        }
        
        // Parse tags and links
        val tags = mutableSetOf<String>()
        val links = mutableSetOf<String>()
        
        // Add tags from tag stack
        tags.addAll(tagStack)
        
        while (peek() is Token.TAG || peek() is Token.LINK) {
            when (val token = advance()) {
                is Token.TAG -> tags.add(token.value)
                is Token.LINK -> links.add(token.value)
                else -> {} // ignore other tokens
            }
            skipWhitespaceAndComments()
        }
        
        // Parse transaction metadata (before postings)
        // Note: Don't call skipWhitespaceAndComments() here because it would consume
        // the INDENT tokens that parseMetadata() needs to identify metadata lines.
        val txnMeta = newMetadata(currentFilename, dateToken.line) + metaStack + parseMetadata()

        // Parse postings
        val postings = mutableListOf<Posting>()

        while (peek() is Token.INDENT || peek() is Token.ACCOUNT) {
            if (peek() is Token.INDENT) {
                consume(Token.INDENT::class)
            }

            val posting = parsePosting()
            if (posting != null) {
                postings.add(posting)
            }

            skipWhitespaceAndComments()
        }

        return Transaction(
            meta = txnMeta,
            date = dateToken.value,
            flag = flag,
            payee = payee,
            narration = narration,
            tags = tags,
            links = links,
            postings = postings
        )
    }
    
    private fun parsePosting(): Posting? {
        // Parse optional posting-level flag
        var postingFlag: Flag? = null
        if (peek() is Token.FLAG) {
            postingFlag = (consume(Token.FLAG::class) as Token.FLAG).value
            skipWhitespaceAndComments()
        }

        val account = parseAccount()
        skipWhitespaceAndComments()

        // Parse amount (optional)
        var units: Amount? = null
        if (peek() is Token.NUMBER) {
            val number = (consume(Token.NUMBER::class) as Token.NUMBER).value
            skipWhitespaceAndComments()
            val currency = parseCurrency()
            units = Amount(Decimal(number.toString()), currency)
            skipWhitespaceAndComments()
        }

        // Parse cost (optional): {cost}
        var cost: CostSpec? = null
        if (peek() is Token.LCURLY) {
            cost = parseCostSpec()
            skipInlineWhitespace()
        }

        // Parse price (optional): @ price or @@ price
        var price: Amount? = null
        var isTotalPrice = false
        if (peek() is Token.AT) {
            consume(Token.AT::class)
            skipInlineWhitespace()
            val priceNumber = (consume(Token.NUMBER::class) as Token.NUMBER).value
            skipInlineWhitespace()
            val priceCurrency = parseCurrency()
            price = Amount(Decimal(priceNumber.toString()), priceCurrency)
        } else if (peek() is Token.DOUBLE_AT) {
            consume(Token.DOUBLE_AT::class)
            skipInlineWhitespace()
            val totalPriceNumber = (consume(Token.NUMBER::class) as Token.NUMBER).value
            skipInlineWhitespace()
            val totalPriceCurrency = parseCurrency()
            // @@ means total price; convert to per-unit price
            isTotalPrice = true
            if (units != null && !units.number.isZero()) {
                val perUnitPrice = Decimal(totalPriceNumber.toString()) / units.number.abs()
                price = Amount(perUnitPrice, totalPriceCurrency)
            } else {
                price = Amount(Decimal(totalPriceNumber.toString()), totalPriceCurrency)
            }
        }

        // Parse posting metadata (optional)
        val postingMeta = parseMetadata()

        return Posting(
            account = account,
            units = units,
            cost = cost,
            price = price,
            flag = postingFlag,
            meta = postingMeta.ifEmpty { null }
        )
    }

    private fun parseCostSpec(): CostSpec? {
        consume(Token.LCURLY::class) // consume "{"
        skipWhitespaceAndComments()

        var numberPer: Decimal? = null
        var numberTotal: Decimal? = null
        var currency: Currency? = null
        var date: LocalDate? = null
        var label: String? = null
        var mergeCost = false

        // Check for merge flag "*"
        if (peek() is Token.ASTERISK) {
            consume(Token.ASTERISK::class)
            mergeCost = true
            skipWhitespaceAndComments()
        }

        // Try to parse cost components
        if (peek() is Token.NUMBER) {
            val firstNumber = Decimal((consume(Token.NUMBER::class) as Token.NUMBER).value.toString())
            skipWhitespaceAndComments()

            // Check for compound cost syntax: number # total
            // In our lexer, #total is tokenized as a TAG token with value "total"
            // So we check if the next token is a TAG whose value is all digits
            if (peek() is Token.TAG) {
                val tagToken = peek() as Token.TAG
                if (tagToken.value.all { it.isDigit() }) {
                    // This is a compound cost: numberPer # numberTotal
                    numberTotal = Decimal(tagToken.value)
                    consume(Token.TAG::class)
                    skipWhitespaceAndComments()
                }
            }

            numberPer = firstNumber

            if (peek() is Token.CURRENCY) {
                currency = parseCurrency()
                skipWhitespaceAndComments()
            }
        } else if (peek() is Token.CURRENCY) {
            // Cost spec with only currency: {USD}
            currency = parseCurrency()
            skipWhitespaceAndComments()
        }

        // Skip optional comma between components
        if (peek() is Token.COMMA) {
            consume(Token.COMMA::class)
            skipWhitespaceAndComments()
        }

        // Check for date
        if (peek() is Token.DATE) {
            date = (consume(Token.DATE::class) as Token.DATE).value
            skipWhitespaceAndComments()
        }

        // Skip optional comma before label
        if (peek() is Token.COMMA) {
            consume(Token.COMMA::class)
            skipWhitespaceAndComments()
        }

        // Check for label (string)
        if (peek() is Token.STRING) {
            label = (consume(Token.STRING::class) as Token.STRING).value
            skipWhitespaceAndComments()
        }

        if (peek() is Token.RCURLY) {
            consume(Token.RCURLY::class)
        }

        return CostSpec(
            numberPer = numberPer,
            numberTotal = numberTotal,
            currency = currency,
            date = date,
            label = label,
            mergeCost = mergeCost
        )
    }
    
    private fun parseBalance(dateToken: Token.DATE): Balance {
        consume(Token.KEYWORD::class) // consume "balance"
        skipWhitespaceAndComments()
        
        val account = parseAccount()
        skipWhitespaceAndComments()
        
        val amount = parseAmount()
        skipWhitespaceAndComments()
        
        // Parse optional tolerance: ~ 0.01
        var tolerance: Decimal? = null
        if (peek() is Token.TILDE) {
            consume(Token.TILDE::class)
            skipWhitespaceAndComments()
            if (peek() is Token.NUMBER) {
                val toleranceValue = (consume(Token.NUMBER::class) as Token.NUMBER).value
                tolerance = Decimal(toleranceValue.toString())
                skipWhitespaceAndComments()
            }
        }
        
        val balanceMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Balance(
            meta = balanceMeta,
            date = dateToken.value,
            account = account,
            amount = amount,
            tolerance = tolerance
        )
    }

    private fun parsePad(dateToken: Token.DATE): Pad {
        consume(Token.KEYWORD::class) // consume "pad"
        skipWhitespaceAndComments()

        val account = parseAccount()
        skipWhitespaceAndComments()

        val sourceAccount = parseAccount()
        val padMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Pad(
            meta = padMeta,
            date = dateToken.value,
            account = account,
            sourceAccount = sourceAccount
        )
    }

    private fun parseNote(dateToken: Token.DATE): Note {
        consume(Token.KEYWORD::class) // consume "note"
        skipWhitespaceAndComments()

        val account = parseAccount()
        skipWhitespaceAndComments()

        val comment = (consume(Token.STRING::class) as Token.STRING).value
        skipWhitespaceAndComments()

        // Parse tags and links
        val tags = mutableSetOf<String>()
        val links = mutableSetOf<String>()
        while (peek() is Token.TAG || peek() is Token.LINK) {
            when (val token = advance()) {
                is Token.TAG -> tags.add(token.value)
                is Token.LINK -> links.add(token.value)
                else -> {}
            }
            skipWhitespaceAndComments()
        }

        val noteMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Note(
            meta = noteMeta,
            date = dateToken.value,
            account = account,
            comment = comment,
            tags = tags.ifEmpty { null },
            links = links.ifEmpty { null }
        )
    }

    private fun parseEvent(dateToken: Token.DATE): Event {
        consume(Token.KEYWORD::class) // consume "event"
        skipWhitespaceAndComments()

        val type = (consume(Token.STRING::class) as Token.STRING).value
        skipWhitespaceAndComments()

        val description = (consume(Token.STRING::class) as Token.STRING).value
        val eventMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Event(
            meta = eventMeta,
            date = dateToken.value,
            type = type,
            description = description
        )
    }

    private fun parsePrice(dateToken: Token.DATE): Price {
        consume(Token.KEYWORD::class) // consume "price"
        skipWhitespaceAndComments()

        val currency = parseCurrency()
        skipWhitespaceAndComments()

        val amount = parseAmount()
        val priceMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Price(
            meta = priceMeta,
            date = dateToken.value,
            currency = currency,
            amount = amount
        )
    }

    private fun parseDocument(dateToken: Token.DATE): Document {
        consume(Token.KEYWORD::class) // consume "document"
        skipWhitespaceAndComments()

        val account = parseAccount()
        skipWhitespaceAndComments()

        val filename = (consume(Token.STRING::class) as Token.STRING).value
        skipWhitespaceAndComments()

        // Parse tags and links
        val tags = mutableSetOf<String>()
        val links = mutableSetOf<String>()
        while (peek() is Token.TAG || peek() is Token.LINK) {
            when (val token = advance()) {
                is Token.TAG -> tags.add(token.value)
                is Token.LINK -> links.add(token.value)
                else -> {}
            }
            skipWhitespaceAndComments()
        }

        val documentMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Document(
            meta = documentMeta,
            date = dateToken.value,
            account = account,
            filename = filename,
            tags = tags.ifEmpty { null },
            links = links.ifEmpty { null }
        )
    }

    private fun parseQuery(dateToken: Token.DATE): Query {
        consume(Token.KEYWORD::class) // consume "query"
        skipWhitespaceAndComments()

        val name = (consume(Token.STRING::class) as Token.STRING).value
        skipWhitespaceAndComments()

        val queryString = (consume(Token.STRING::class) as Token.STRING).value
        val queryMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Query(
            meta = queryMeta,
            date = dateToken.value,
            name = name,
            queryString = queryString
        )
    }

    private fun parseCustom(dateToken: Token.DATE): Custom {
        consume(Token.KEYWORD::class) // consume "custom"
        skipWhitespaceAndComments()

        val type = (consume(Token.STRING::class) as Token.STRING).value
        val values = mutableListOf<Any>()

        skipWhitespaceAndComments()

        while (peek() is Token.STRING || peek() is Token.NUMBER || peek() is Token.DATE || peek() is Token.CURRENCY) {
            when (val token = advance()) {
                is Token.STRING -> values.add(token.value)
                is Token.NUMBER -> values.add(token.value)
                is Token.DATE -> values.add(token.value)
                is Token.CURRENCY -> values.add(token.value)
                else -> {} // ignore other tokens
            }
            skipWhitespaceAndComments()
        }
        val customMeta = newMetadata(currentFilename, dateToken.line) + parseMetadata()

        return Custom(
            meta = customMeta,
            date = dateToken.value,
            type = type,
            values = values
        )
    }

    private fun parseInclude(dateToken: Token.DATE): Include {
        consume(Token.KEYWORD::class) // consume "include"
        skipWhitespaceAndComments()

        val filename = (consume(Token.STRING::class) as Token.STRING).value
        val includeMeta = newMetadata(currentFilename, dateToken.line)

        return Include(
            meta = includeMeta,
            date = dateToken.value,
            filename = filename
        )
    }

    private fun parsePushTag(dateToken: Token.DATE): PushTag {
        consume(Token.KEYWORD::class) // consume "pushtag"
        skipWhitespaceAndComments()
        
        val tag = (consume(Token.TAG::class) as Token.TAG).value
        tagStack.add(tag)
        
        return PushTag(
            meta = newMetadata(currentFilename, dateToken.line),
            date = dateToken.value,
            tag = tag
        )
    }
    
    private fun parsePopTag(dateToken: Token.DATE): PopTag {
        consume(Token.KEYWORD::class) // consume "poptag"
        skipWhitespaceAndComments()
        
        val tag = (consume(Token.TAG::class) as Token.TAG).value
        tagStack.remove(tag)
        
        return PopTag(
            meta = newMetadata(currentFilename, dateToken.line),
            date = dateToken.value,
            tag = tag
        )
    }
    
    private fun parsePushMeta(dateToken: Token.DATE): PushMeta {
        consume(Token.KEYWORD::class) // consume "pushmeta"
        skipWhitespaceAndComments()
        
        val key = (consume(Token.KEY::class) as Token.KEY).value
        skipWhitespaceAndComments()
        
        val value = parseMetadataValue() ?: ""
        metaStack[key] = value
        
        return PushMeta(
            meta = newMetadata(currentFilename, dateToken.line),
            date = dateToken.value,
            key = key,
            value = value
        )
    }
    
    private fun parsePopMeta(dateToken: Token.DATE): PopMeta {
        consume(Token.KEYWORD::class) // consume "popmeta"
        skipWhitespaceAndComments()
        
        val key = (consume(Token.KEY::class) as Token.KEY).value
        metaStack.remove(key)
        
        return PopMeta(
            meta = newMetadata(currentFilename, dateToken.line),
            date = dateToken.value,
            key = key
        )
    }

    private fun parseOptionNoDate() {
        consume(Token.KEYWORD::class) // consume "option"
        skipWhitespaceAndComments()
        
        val key = (consume(Token.STRING::class) as Token.STRING).value
        skipWhitespaceAndComments()
        
        val value = (consume(Token.STRING::class) as Token.STRING).value
        
        // Store option
        when (key) {
            "title" -> options["title"] = value
            "operating_currency" -> {
                val currencies = options.getOrPut("operating_currencies") { mutableListOf<String>() } as MutableList<String>
                currencies.add(value)
            }
            else -> options[key] = value
        }
    }
    
    private fun parseOption(dateToken: Token.DATE) {
        consume(Token.KEYWORD::class) // consume "option"
        skipWhitespaceAndComments()
        
        val key = (consume(Token.STRING::class) as Token.STRING).value
        skipWhitespaceAndComments()
        
        val value = (consume(Token.STRING::class) as Token.STRING).value
        
        // Store option
        when (key) {
            "title" -> options["title"] = value
            "operating_currency" -> {
                val currencies = options.getOrPut("operating_currencies") { mutableListOf<String>() } as MutableList<String>
                currencies.add(value)
            }
            else -> options[key] = value
        }
    }
    
    private fun parsePlugin(dateToken: Token.DATE) {
        consume(Token.KEYWORD::class) // consume "plugin"
        skipWhitespaceAndComments()
        
        val moduleName = (consume(Token.STRING::class) as Token.STRING).value
        skipWhitespaceAndComments()
        
        // Optional configuration string
        val config = if (peek() is Token.STRING) {
            (consume(Token.STRING::class) as Token.STRING).value
        } else {
            null
        }
        
        // Store plugin spec
        val plugins = options.getOrPut("plugins") { mutableListOf<PluginSpec>() } as MutableList<PluginSpec>
        plugins.add(PluginSpec(moduleName, config))
    }
    
    // Helper methods
    
    /**
     * Parse optional metadata (indented key-value pairs).
     * Returns a map of metadata entries.
     */
    private fun parseMetadata(): Meta {
        val meta = mutableMapOf<String, Any>()

        // Metadata lines start with indentation and have key: value format
        while (true) {
            // Skip any EOLs between entries
            while (peek() is Token.EOL) {
                consume(Token.EOL::class)
            }

            // Check if next token is an indent or key (indicating a metadata line)
            // Indent may have been consumed by skipWhitespaceAndComments() elsewhere
            if (peek() is Token.INDENT) {
                consume(Token.INDENT::class)
                skipWhitespaceAndComments()
            } else if (peek() !is Token.KEY) {
                break
            }

            // Check for metadata key
            if (peek() is Token.KEY) {
                val key = (consume(Token.KEY::class) as Token.KEY).value
                skipWhitespaceAndComments()

                // Consume colon after key
                if (peek() is Token.COLON) {
                    consume(Token.COLON::class)
                    skipWhitespaceAndComments()
                }

                // Parse value
                val value = when (val token = peek()) {
                    is Token.STRING -> (consume(Token.STRING::class) as Token.STRING).value
                    is Token.NUMBER -> (consume(Token.NUMBER::class) as Token.NUMBER).value
                    is Token.DATE -> (consume(Token.DATE::class) as Token.DATE).value
                    is Token.BOOL -> (consume(Token.BOOL::class) as Token.BOOL).value
                    is Token.NONE -> {
                        consume(Token.NONE::class)
                        null
                    }
                    else -> {
                        reportError("Expected metadata value, found ${token::class.simpleName}")
                        break
                    }
                }

                if (value != null) {
                    meta[key] = value
                }
            } else {
                // Not metadata, might be next posting or directive
                // We need to 'unread' the indent, but since we can't, we just break
                break
            }
        }

        return meta
    }

    private fun parseMetadataValue(): Any? {
        return when (val token = peek()) {
            is Token.STRING -> (consume(Token.STRING::class) as Token.STRING).value
            is Token.NUMBER -> (consume(Token.NUMBER::class) as Token.NUMBER).value
            is Token.DATE -> (consume(Token.DATE::class) as Token.DATE).value
            is Token.BOOL -> (consume(Token.BOOL::class) as Token.BOOL).value
            is Token.NONE -> {
                consume(Token.NONE::class)
                null
            }
            else -> {
                reportError("Expected metadata value, found ${token::class.simpleName}")
                null
            }
        }
    }

    private fun parseAccount(): String {
        val token = peek()
        return if (token is Token.ACCOUNT) {
            (consume(Token.ACCOUNT::class) as Token.ACCOUNT).value
        } else {
            reportError("Expected account name, found ${token::class.simpleName}")
            advance() // skip the invalid token
            ""
        }
    }
    
    private fun parseCurrency(): String {
        return (consume(Token.CURRENCY::class) as Token.CURRENCY).value
    }
    
    private fun parseCurrencyList(): List<String> {
        val currencies = mutableListOf<String>()
        
        skipWhitespaceAndComments()
        
        if (peek() is Token.CURRENCY) {
            currencies.add(parseCurrency())
            
            while (peek() is Token.COMMA) {
                consume(Token.COMMA::class)
                skipWhitespaceAndComments()
                if (peek() is Token.CURRENCY) {
                    currencies.add(parseCurrency())
                }
            }
        }
        
        return currencies
    }
    
    private fun parseAmount(): Amount {
        val number = (consume(Token.NUMBER::class) as Token.NUMBER).value
        skipWhitespaceAndComments()
        val currency = parseCurrency()
        
        return Amount(Decimal(number.toString()), currency)
    }
    
    private fun parseAmountWithOptionalSign(): Amount {
        var sign = 1.0
        
        if (peek() is Token.MINUS) {
            consume(Token.MINUS::class)
            sign = -1.0
        } else if (peek() is Token.PLUS) {
            consume(Token.PLUS::class)
        }
        
        val number = (consume(Token.NUMBER::class) as Token.NUMBER).value
        skipWhitespaceAndComments()
        val currency = parseCurrency()
        
        return Amount(Decimal((number * sign).toString()), currency)
    }
    
    private fun buildOptions(): Options {
        return Options(
            title = options["title"] as? String ?: "",
            operatingCurrencies = (options["operating_currencies"] as? List<String>) ?: emptyList(),
            plugin = (options["plugins"] as? List<PluginSpec>) ?: emptyList(),
            filename = currentFilename
        )
    }
    
    private fun skipWhitespaceAndComments() {
        while (true) {
            when (peek()) {
                is Token.INDENT, is Token.EOL -> advance()
                else -> break
            }
        }
    }

    /**
     * Skip only inline whitespace (spaces), not EOL or INDENT.
     * Used within postings to avoid consuming line breaks that separate
     * postings from metadata or the next posting.
     */
    private fun skipInlineWhitespace() {
        while (true) {
            when (peek()) {
                is Token.INDENT -> advance()  // Treat indent as inline within posting
                else -> break
            }
        }
    }

    private fun skipToNextDirective() {
        while (!isAtEnd() && peek() !is Token.DATE) {
            advance()
        }
    }
    
    private fun peek(): Token {
        return if (position < tokens.size) tokens[position] else Token.EOF(0, 0)
    }
    
    private fun advance(): Token {
        return if (position < tokens.size) tokens[position++] else Token.EOF(0, 0)
    }
    
    private fun isAtEnd(): Boolean {
        return position >= tokens.size || peek() is Token.EOF
    }
    
    private fun <T : Token> consume(expectedClass: kotlin.reflect.KClass<T>): Token {
        val token = peek()
        if (expectedClass.isInstance(token)) {
            return advance()
        }
        reportError("Expected ${expectedClass.simpleName}, found ${token::class.simpleName}")
        return token
    }
    
    private fun reportError(message: String) {
        val token = peek()
        errors.add(ParseError(token.line, token.column, message))
    }
}
