package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * Validation function type.
 * Takes entries and options, returns list of validation errors.
 * Based on beancount.ops.validation.
 */
typealias Validation = (List<Directive>, Options) -> List<ValidationError>

/**
 * Basic validations that are always run.
 * Based on beancount.ops.validation.BASIC_VALIDATIONS.
 */
val BASIC_VALIDATIONS: List<Validation> = listOf(
    ::validateOpenClose,
    ::validateActiveAccounts,
    ::validateCurrencyConstraints,
    ::validateDuplicateBalances,
    ::validateDuplicateCommodities,
    ::validateDocumentsPaths,
    ::validateCheckTransactionBalances
)

/**
 * Balance validation - checks balance assertions against computed balances.
 * This is a separate validation because it requires running after basic validations
 * and operates on a copy of entries (may modify Balance directives with diff_amount).
 */
fun validateBalanceAssertions(entries: List<Directive>, options: Options): List<ValidationError> {
    val (_, balanceErrors) = validateBalances(entries, options)
    return balanceErrors.map { error ->
        ValidationError(error.source, error.message, error.entry)
    }
}

/**
 * Hardcore validations - only enabled by bean-check.
 * Based on beancount.ops.validation.HARDCORE_VALIDATIONS.
 */
val HARDCORE_VALIDATIONS: List<Validation> = listOf(
    ::validateDataTypes
)

/**
 * Check constraints on open and close directives.
 * 1. No duplicate opens or closes for an account
 * 2. Close must come after open
 * 3. Only one open and one close per account
 */
fun validateOpenClose(entries: List<Directive>, options: Options): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    val openMap = mutableMapOf<Account, Open>()
    val closeMap = mutableMapOf<Account, Close>()
    
    for (entry in entries) {
        when (entry) {
            is Open -> {
                if (entry.account in openMap) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Duplicate open directive for ${entry.account}",
                        entry
                    ))
                } else {
                    openMap[entry.account] = entry
                }
            }
            is Close -> {
                if (entry.account in closeMap) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Duplicate close directive for ${entry.account}",
                        entry
                    ))
                } else {
                    val openEntry = openMap[entry.account]
                    if (openEntry == null) {
                        errors.add(ValidationError(
                            entry.meta,
                            "Unopened account ${entry.account} is being closed",
                            entry
                        ))
                    } else if (entry.date < openEntry.date) {
                        errors.add(ValidationError(
                            entry.meta,
                            "Closing date for ${entry.account} appears before opening date",
                            entry
                        ))
                    }
                    closeMap[entry.account] = entry
                }
            }
            else -> {}
        }
    }
    
    return errors
}

/**
 * Check that all references to accounts occur on active accounts.
 * Active means the account is open and not yet closed at the transaction date.
 */
fun validateActiveAccounts(entries: List<Directive>, options: Options): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    
    // Build a map of account -> (open date, close date)
    val accountLifecycle = mutableMapOf<Account, Pair<LocalDate, LocalDate?>>()
    
    for (entry in entries) {
        when (entry) {
            is Open -> accountLifecycle[entry.account] = Pair(entry.date, null)
            is Close -> {
                val current = accountLifecycle[entry.account]
                if (current != null) {
                    accountLifecycle[entry.account] = Pair(current.first, entry.date)
                }
            }
            else -> {}
        }
    }
    
    // Check all account references
    for (entry in entries) {
        when (entry) {
            is Transaction -> {
                for (posting in entry.postings) {
                    val account = posting.account
                    val lifecycle = accountLifecycle[account]
                    
                    if (lifecycle == null) {
                        errors.add(ValidationError(
                            entry.meta,
                            "Invalid reference to unknown account '$account'",
                            entry
                        ))
                    } else {
                        val (openDate, closeDate) = lifecycle
                        if (entry.date < openDate || (closeDate != null && entry.date > closeDate)) {
                            errors.add(ValidationError(
                                entry.meta,
                                "Invalid reference to inactive account '$account'",
                                entry
                            ))
                        }
                    }
                }
            }
            is Balance -> {
                val account = entry.account
                val lifecycle = accountLifecycle[account]
                
                if (lifecycle == null) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Invalid reference to unknown account '$account'",
                        entry
                    ))
                }
            }
            is Note -> {
                val account = entry.account
                val lifecycle = accountLifecycle[account]
                
                if (lifecycle == null) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Invalid reference to unknown account '$account'",
                        entry
                    ))
                }
            }
            is Pad -> {
                // Check both accounts
                for (acc in listOf(entry.account, entry.sourceAccount)) {
                    val lifecycle = accountLifecycle[acc]
                    if (lifecycle == null) {
                        errors.add(ValidationError(
                            entry.meta,
                            "Invalid reference to unknown account '$acc'",
                            entry
                        ))
                    }
                }
            }
            else -> {}
        }
    }
    
    return errors
}

/**
 * Check currency constraints from account open declarations.
 */
fun validateCurrencyConstraints(entries: List<Directive>, options: Options): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    
    // Get all open entries with currency constraints
    val openMap = entries.filterIsInstance<Open>()
        .filter { it.currencies.isNotEmpty() }
        .associateBy { it.account }
    
    for (entry in entries) {
        if (entry !is Transaction) continue
        
        for (posting in entry.postings) {
            val openEntry = openMap[posting.account]
            if (openEntry != null) {
                val currency = posting.units?.currency
                if (currency != null && currency !in openEntry.currencies) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Invalid currency $currency for account '${posting.account}'",
                        entry
                    ))
                }
            }
        }
    }
    
    return errors
}

/**
 * Check that balance entries occur only once per day per account.
 */
fun validateDuplicateBalances(entries: List<Directive>, options: Options): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    val balanceEntries = mutableMapOf<Triple<Account, Currency, kotlinx.datetime.LocalDate>, Balance>()
    
    for (entry in entries) {
        if (entry !is Balance) continue
        
        val key = Triple(entry.account, entry.amount.currency, entry.date)
        val previous = balanceEntries[key]
        
        if (previous != null) {
            if (entry.amount.number != previous.amount.number) {
                errors.add(ValidationError(
                    entry.meta,
                    "Duplicate balance assertion with different amounts",
                    entry
                ))
            }
        } else {
            balanceEntries[key] = entry
        }
    }
    
    return errors
}

/**
 * Check that commodity entries are unique for each commodity.
 */
fun validateDuplicateCommodities(entries: List<Directive>, options: Options): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    val commodityEntries = mutableMapOf<Currency, Commodity>()
    
    for (entry in entries) {
        if (entry !is Commodity) continue
        
        val previous = commodityEntries[entry.currency]
        if (previous != null) {
            errors.add(ValidationError(
                entry.meta,
                "Duplicate commodity directives for '${entry.currency}'",
                entry
            ))
        } else {
            commodityEntries[entry.currency] = entry
        }
    }
    
    return errors
}

/**
 * Check that all transaction postings balance.
 * Uses posting weights (considering cost/price conversions) and tolerances.
 * Based on beancount.ops.validation.validate_check_transaction_balances.
 */
fun validateCheckTransactionBalances(entries: List<Directive>, options: Options): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()

    for (entry in entries) {
        if (entry !is Transaction) continue

        // Compute residual using weights (cost/price conversions applied)
        val residual = Inventory()
        for (posting in entry.postings) {
            val weight = getWeight(posting)
            residual.addAmount(weight)
        }

        // Check if residual is small enough (within tolerance)
        // Use default tolerance of 0.005 if no tolerance map is provided.
        val tolerances = options.toleranceMap.ifEmpty {
            mapOf("*" to Decimal("0.005"))
        }
        if (!residual.isSmall(tolerances)) {
            // Build error message showing non-small positions
            val residualStr = residual.getPositions()
                .filter { !it.units.number.isZero() }
                .joinToString(", ") { "${it.units.number.toPlainString()} ${it.units.currency}" }

            errors.add(ValidationError(
                entry.meta,
                "Transaction does not balance: $residualStr",
                entry
            ))
        }
    }

    return errors
}

/**
 * Validate that no account has mixed lots (different costs for the same currency).
 * Based on beancount.parser.booking.validate_inventory_booking.
 *
 * This simulates a simple STRICT-like inventory booking and checks that
 * for each account+currency, all positions share the same cost basis.
 */
fun validateInventoryBooking(
    entries: List<Directive>,
    options: Options
): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    val accountInventories = mutableMapOf<Account, Inventory>()

    for (entry in entries) {
        if (entry !is Transaction) continue

        for (posting in entry.postings) {
            val units = posting.units ?: continue
            val costSpec = posting.cost ?: continue

            val inventory = accountInventories.getOrPut(posting.account) { Inventory() }
            val cost = costSpec.toCost(entry.date, units.number)
                ?: continue

            if (units.number.isPositive()) {
                // Augmentation - check for mixed lots (compare by number+currency only)
                val existingPositions = inventory.getPositions(units.currency)
                if (existingPositions.isNotEmpty()) {
                    val existingCost = existingPositions[0].cost
                    if (existingCost != null &&
                        (existingCost.number != cost.number || existingCost.currency != cost.currency)) {
                        errors.add(ValidationError(
                            entry.meta,
                            "Mixed lot detected for ${posting.account}: " +
                            "existing ${existingCost.number} ${existingCost.currency} " +
                            "vs new ${cost.number} ${cost.currency}",
                            entry
                        ))
                    }
                }
                inventory.addAmount(units, cost)
            } else if (units.number.isNegative()) {
                // Reduction - check if matching cost exists when inventory is non-empty
                val existingPositions = inventory.getPositions(units.currency)
                if (existingPositions.isNotEmpty()) {
                    val matches = inventory.findMatches(units.currency, costSpec)
                    if (matches.isEmpty()) {
                        errors.add(ValidationError(
                            entry.meta,
                            "Mixed lot detected for ${posting.account}: " +
                            "reduction cost does not match existing lots",
                            entry
                        ))
                    }
                }
                // Apply reduction to inventory (even if error, to track state)
                inventory.addAmount(units, cost)
            }
        }
    }

    return errors
}

/**
 * Perform a sanity check on the types of all entries and their fields.
 * Based on beancount.core.data.sanity_check_types.
 *
 * Validates that:
 * - All entries have required fields (meta, date)
 * - All dates are valid (year >= 0, month in 1..12, day in 1..31)
 * - All accounts are non-empty strings with valid format
 * - All currencies are non-empty strings
 * - All amounts have non-null numbers
 * - Transaction postings have valid accounts
 * - CostSpec fields are valid
 *
 * @param entries A list of directive instances.
 * @return A list of validation errors.
 */
fun sanityCheckTypes(entries: List<Directive>): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()

    for (entry in entries) {
        // Check meta is not empty (should contain at least filename and lineno)
        if (entry.meta.isEmpty()) {
            errors.add(ValidationError(
                entry.meta,
                "Entry has empty metadata",
                entry
            ))
        }

        // Validate date
        try {
            val date = entry.date
            require(date.year >= 0) { "Invalid year: ${date.year}" }
            require(date.monthNumber in 1..12) { "Invalid month: ${date.monthNumber}" }
            require(date.dayOfMonth in 1..31) { "Invalid day: ${date.dayOfMonth}" }
        } catch (e: Exception) {
            errors.add(ValidationError(
                entry.meta,
                "Invalid date for entry: ${e.message}",
                entry
            ))
        }

        // Entry-specific validation
        when (entry) {
            is Open -> {
                if (entry.account.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Open directive has empty account",
                        entry
                    ))
                }
                if (entry.currencies.any { it.isBlank() }) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Open directive has empty currency",
                        entry
                    ))
                }
            }
            is Close -> {
                if (entry.account.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Close directive has empty account",
                        entry
                    ))
                }
            }
            is Commodity -> {
                if (entry.currency.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Commodity directive has empty currency",
                        entry
                    ))
                }
            }
            is Transaction -> {
                if (entry.postings.isEmpty()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Transaction has no postings",
                        entry
                    ))
                }
                for (posting in entry.postings) {
                    if (posting.account.isBlank()) {
                        errors.add(ValidationError(
                            entry.meta,
                            "Posting has empty account",
                            entry
                        ))
                    }
                    posting.units?.let { amount ->
                        if (amount.currency.isBlank()) {
                            errors.add(ValidationError(
                                entry.meta,
                                "Posting has empty currency",
                                entry
                            ))
                        }
                    }
                    posting.cost?.let { cost ->
                        if (cost.currency != null && cost.currency.isBlank()) {
                            errors.add(ValidationError(
                                entry.meta,
                                "Posting cost has empty currency",
                                entry
                            ))
                        }
                    }
                }
            }
            is Balance -> {
                if (entry.account.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Balance directive has empty account",
                        entry
                    ))
                }
                if (entry.amount.currency.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Balance directive has empty currency",
                        entry
                    ))
                }
            }
            is Pad -> {
                if (entry.account.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Pad directive has empty account",
                        entry
                    ))
                }
                if (entry.sourceAccount.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Pad directive has empty source account",
                        entry
                    ))
                }
            }
            is Note -> {
                if (entry.account.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Note directive has empty account",
                        entry
                    ))
                }
            }
            is Document -> {
                if (entry.account.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Document directive has empty account",
                        entry
                    ))
                }
                if (entry.filename.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Document directive has empty filename",
                        entry
                    ))
                }
            }
            is Price -> {
                if (entry.currency.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Price directive has empty currency",
                        entry
                    ))
                }
                if (entry.amount.currency.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Price directive has empty amount currency",
                        entry
                    ))
                }
            }
            is Event -> {
                if (entry.type.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Event directive has empty type",
                        entry
                    ))
                }
            }
            is Query -> {
                if (entry.name.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Query directive has empty name",
                        entry
                    ))
                }
            }
            is Custom -> {
                if (entry.type.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Custom directive has empty type",
                        entry
                    ))
                }
            }
            is Include -> {
                if (entry.filename.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Include directive has empty filename",
                        entry
                    ))
                }
            }
            is PushTag -> {
                if (entry.tag.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "PushTag directive has empty tag",
                        entry
                    ))
                }
            }
            is PopTag -> {
                if (entry.tag.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "PopTag directive has empty tag",
                        entry
                    ))
                }
            }
            is PushMeta -> {
                if (entry.key.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "PushMeta directive has empty key",
                        entry
                    ))
                }
            }
            is PopMeta -> {
                if (entry.key.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "PopMeta directive has empty key",
                        entry
                    ))
                }
            }
        }
    }

    return errors
}

/**
 * Check that all data types are correct.
 * This is a strict validation enabled by bean-check.
 */
/**
 * Check that all filenames in Document entries are absolute paths.
 * Based on beancount.ops.validation.validate_documents_paths.
 */
fun validateDocumentsPaths(entries: List<Directive>, options: Options): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    for (entry in entries) {
        if (entry is Document) {
            val filename = entry.filename
            // Check if path is absolute (Unix-style / or Windows-style X:/ or \\)
            if (!filename.startsWith("/") && !filename.matches(Regex("^[A-Za-z]:[/\\\\].*")) && !filename.startsWith("\\\\")) {
                errors.add(ValidationError(
                    entry.meta,
                    "Invalid relative path for document: $filename",
                    entry
                ))
            }
        }
    }
    return errors
}

/**
 * Perform all standard validations on parsed contents.
 * Based on beancount.ops.validation.validate.
 */
fun validate(
    entries: List<Directive>,
    options: Options,
    extraValidations: List<Validation> = emptyList()
): List<ValidationError> {
    val validations = BASIC_VALIDATIONS + extraValidations
    val errors = mutableListOf<ValidationError>()
    for (validation in validations) {
        errors.addAll(validation(entries, options))
    }
    return errors
}

fun validateDataTypes(entries: List<Directive>, options: Options): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    
    for (entry in entries) {
        // Check metadata
        if (!entry.meta.containsKey("filename")) {
            errors.add(ValidationError(
                entry.meta,
                "Missing filename in metadata",
                entry
            ))
        }
        
        if (!entry.meta.containsKey("lineno")) {
            errors.add(ValidationError(
                entry.meta,
                "Missing line number in metadata",
                entry
            ))
        }
        
        // Check transaction-specific fields
        if (entry is Transaction) {
            // Check postings
            for (posting in entry.postings) {
                if (posting.account.isBlank()) {
                    errors.add(ValidationError(
                        entry.meta,
                        "Empty account in posting",
                        entry
                    ))
                }
            }
            
            // Tags and links are non-nullable in Kotlin data class
        }
    }
    
    return errors
}
