package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*

/**
 * Booking logic to complete incomplete postings.
 * Based on beancount.parser.booking.
 * 
 * Simplified implementation that handles the most common case:
 * - Single missing posting in a transaction is inferred from others
 */
object Booking {
    
    /**
     * Book incomplete entries - fill in missing posting amounts.
     */
    fun book(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val result = entries.map { entry ->
            when (entry) {
                is Transaction -> {
                    val (completed, error) = completeTransaction(entry)
                    if (error != null) {
                        errors.add(error)
                    }
                    completed
                }
                else -> entry
            }
        }
        
        return Pair(result, errors)
    }
    
    /**
     * Complete a transaction by filling in missing posting amounts.
     * 
     * Logic:
     * 1. If all postings have amounts, do nothing
     * 2. If exactly one posting is missing units, infer it from the sum of others
     * 3. Otherwise, report an error
     */
    private fun completeTransaction(transaction: Transaction): Pair<Transaction, BeancountError?> {
        val postings = transaction.postings
        
        // Count postings with missing units
        val missingIndices = postings.mapIndexed { index, posting ->
            if (posting.units == null) index else null
        }.filterNotNull()
        
        // If all postings have units, nothing to do
        if (missingIndices.isEmpty()) {
            return Pair(transaction, null)
        }
        
        // If more than one posting is missing, we can't infer
        if (missingIndices.size > 1) {
            return Pair(
                transaction,
                LoadError(
                    transaction.meta,
                    "Transaction has ${missingIndices.size} postings with missing amounts; only one can be inferred",
                    transaction
                )
            )
        }
        
        // Calculate the sum of all complete postings by currency
        val balances = mutableMapOf<Currency, Decimal>()
        
        for (posting in postings) {
            val units = posting.units ?: continue
            val current = balances[units.currency] ?: Decimal.ZERO
            balances[units.currency] = current + units.number
        }
        
        // The missing posting should balance each currency to zero
        val missingIndex = missingIndices[0]
        val missingPosting = postings[missingIndex]
        
        // If there's only one currency, create the balancing posting
        if (balances.size == 1) {
            val (currency, balance) = balances.entries.first()
            val newPosting = missingPosting.copy(
                units = Amount(-balance, currency)
            )
            
            val newPostings = postings.toMutableList()
            newPostings[missingIndex] = newPosting
            
            return Pair(
                transaction.copy(postings = newPostings),
                null
            )
        }
        
        // Multiple currencies - this is more complex and may require proper inventory tracking
        return Pair(
            transaction,
            LoadError(
                transaction.meta,
                "Cannot infer missing posting with multiple currencies",
                transaction
            )
        )
    }
}
