package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate

/**
 * Booking logic to complete incomplete postings and match lots.
 * Based on beancount.parser.booking_full and booking_method.
 *
 * Phase 2 implementation (complete):
 * - Completes missing posting amounts (existing functionality)
 * - CostSpec interpolation (fills missing date from transaction date)
 * - All booking methods:
 *   - STRICT: exact lot matching, error on ambiguous matches
 *   - STRICT_WITH_SIZE: STRICT + auto-resolve by lot size
 *   - NONE: no lot matching, allows negative positions
 *   - FIFO: First-In-First-Out
 *   - LIFO: Last-In-First-Out
 *   - HIFO: Highest-Cost-First-Out
 */
object Booking {

    /**
     * Result of booking a single posting against an inventory.
     */
    private data class BookResult(
        val postings: List<Posting> = emptyList(),
        val errors: List<BeancountError> = emptyList(),
        val insufficient: Boolean = false
    )

    /**
     * Book entries - complete incomplete postings and resolve cost specs.
     */
    fun book(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val result = mutableListOf<Directive>()

        // Track account inventories and booking methods
        val accountInventories = mutableMapOf<Account, Inventory>()
        val accountBookingMethods = mutableMapOf<Account, io.github.tonyzhye.beancount.core.Booking>()

        for (entry in entries) {
            when (entry) {
                is Open -> {
                    // Record booking method for this account
                    entry.booking?.let { accountBookingMethods[entry.account] = it }
                    result.add(entry)
                }
                is Transaction -> {
                    val (bookedTxn, txnErrors) = bookTransaction(
                        entry,
                        accountInventories,
                        accountBookingMethods
                    )
                    result.add(bookedTxn)
                    errors.addAll(txnErrors)
                }
                else -> {
                    result.add(entry)
                }
            }
        }

        return Pair(result, errors)
    }

    /**
     * Book a single transaction.
     */
    private fun bookTransaction(
        transaction: Transaction,
        accountInventories: MutableMap<Account, Inventory>,
        accountBookingMethods: Map<Account, io.github.tonyzhye.beancount.core.Booking>
    ): Pair<Transaction, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()

        // Step 1: Complete missing posting units
        val (completedPostings, completionError) = completeMissingUnits(transaction.postings)
        if (completionError != null) {
            errors.add(completionError)
        }

        // Step 2: Process each posting (interpolation + booking)
        val finalPostings = mutableListOf<Posting>()

        for (posting in completedPostings) {
            val units = posting.units
            if (units == null) {
                // Posting without units - skip booking
                finalPostings.add(posting)
                continue
            }

            val costSpec = posting.cost
            val bookingMethod = accountBookingMethods[posting.account]
                ?: io.github.tonyzhye.beancount.core.Booking.STRICT

            if (costSpec == null) {
                // No cost - simple currency posting
                if (!units.number.isZero()) {
                    val inventory = accountInventories.getOrPut(posting.account) { Inventory() }
                    inventory.addAmount(units)
                }
                finalPostings.add(posting)
                continue
            }

            // Resolve CostSpec (interpolation)
            val resolvedCostSpec = interpolateCostSpec(costSpec, units, transaction.date)

            // Handle zero units specially (no inventory change needed)
            if (units.number.isZero()) {
                finalPostings.add(posting.copy(cost = resolvedCostSpec))
                continue
            }

            // Determine if this is an augmentation or reduction
            val isReduction = units.number.isNegative()

            if (isReduction) {
                // Reduction - use booking method to match lots
                // Use original costSpec for matching (don't interpolate defaults)
                val inventory = accountInventories.getOrPut(posting.account) { Inventory() }
                val matches = inventory.findMatches(units.currency, costSpec)

                val bookResult = when (bookingMethod) {
                    io.github.tonyzhye.beancount.core.Booking.STRICT ->
                        bookStrict(transaction, posting, costSpec, inventory, matches)
                    io.github.tonyzhye.beancount.core.Booking.STRICT_WITH_SIZE ->
                        bookStrictWithSize(transaction, posting, costSpec, inventory, matches)
                    io.github.tonyzhye.beancount.core.Booking.NONE ->
                        bookNone(posting, costSpec, inventory)
                    io.github.tonyzhye.beancount.core.Booking.FIFO ->
                        bookXifo(transaction, posting, costSpec, inventory, matches,
                                 sortBy = { it.cost?.date }, reverse = false)
                    io.github.tonyzhye.beancount.core.Booking.LIFO ->
                        bookXifo(transaction, posting, costSpec, inventory, matches,
                                 sortBy = { it.cost?.date }, reverse = true)
                    io.github.tonyzhye.beancount.core.Booking.HIFO ->
                        bookXifo(transaction, posting, costSpec, inventory, matches,
                                 sortBy = { it.cost?.number }, reverse = true)
                    io.github.tonyzhye.beancount.core.Booking.AVERAGE -> {
                        // AVERAGE is not supported (same as Python)
                        BookResult(
                            errors = listOf(LoadError(
                                transaction.meta,
                                "AVERAGE method is not supported",
                                transaction
                            ))
                        )
                    }
                    else -> {
                        // Unknown booking method - fall back to STRICT
                        errors.add(LoadError(
                            transaction.meta,
                            "Booking method $bookingMethod is not yet supported, falling back to STRICT",
                            transaction
                        ))
                        bookStrict(transaction, posting, costSpec, inventory, matches)
                    }
                }

                finalPostings.addAll(bookResult.postings)
                errors.addAll(bookResult.errors)
            } else {
                // Augmentation - add to inventory
                // Use resolvedCostSpec for augmentation (fill in defaults like transaction date)
                val inventory = accountInventories.getOrPut(posting.account) { Inventory() }
                val cost = resolvedCostSpec.toCost(transaction.date)
                if (cost != null) {
                    inventory.addAmount(units, cost)
                } else {
                    errors.add(LoadError(
                        transaction.meta,
                        "Cannot resolve cost spec for posting: ${posting.account}",
                        transaction
                    ))
                }
                finalPostings.add(posting.copy(cost = resolvedCostSpec))
            }
        }

        return Pair(transaction.copy(postings = finalPostings), errors)
    }

    /**
     * STRICT booking method.
     * Requires exactly one matching lot. If multiple lots match, reports an error.
     */
    private fun bookStrict(
        transaction: Transaction,
        posting: Posting,
        costSpec: CostSpec,
        inventory: Inventory,
        matches: List<Position>
    ): BookResult {
        val units = posting.units!!
        val requiredAmount = units.number.abs()
        val sign = if (units.number.isNegative()) -Decimal.ONE else Decimal.ONE

        if (matches.isEmpty()) {
            // No matching lots - create a new negative position
            // This is allowed in some cases (short selling with STRICT)
            val cost = costSpec.toCost(transaction.date)
            if (cost != null) {
                inventory.addAmount(units, cost)
            }
            return BookResult(postings = listOf(posting.copy(cost = costSpec)))
        }

        if (matches.size > 1) {
            // Check if total of all matches equals the required amount
            val totalAvailable = matches.fold(Decimal.ZERO) { acc, pos ->
                acc + pos.units.number.abs()
            }

            if (totalAvailable == requiredAmount) {
                // Special case: consume all matching lots exactly
                val newPostings = matches.map { match ->
                    val reductionUnits = Amount(-match.units.number, match.units.currency)
                    posting.copy(units = reductionUnits, cost = costSpecFromCost(match.cost))
                }
                // Remove all matched positions from inventory
                for (match in matches) {
                    inventory.addAmount(Amount(-match.units.number, match.units.currency), match.cost)
                }
                return BookResult(postings = newPostings)
            }

            return BookResult(
                errors = listOf(LoadError(
                    transaction.meta,
                    "Ambiguous matches for ${formatPosition(units, costSpec)}: " +
                    "found ${matches.size} matching lots in ${posting.account}",
                    transaction
                ))
            )
        }

        // Exactly one match
        val match = matches[0]
        val availableAmount = match.units.number.abs()
        val reduceAmount = if (requiredAmount <= availableAmount) requiredAmount else availableAmount

        val reductionUnits = Amount(reduceAmount * sign, match.units.currency)
        val newPosting = posting.copy(
            units = reductionUnits,
            cost = costSpecFromCost(match.cost)
        )

        // Update inventory
        inventory.addAmount(Amount(-reduceAmount, match.units.currency), match.cost)

        val insufficient = reduceAmount < requiredAmount
        val error = if (insufficient) {
            listOf(LoadError(
                transaction.meta,
                "Insufficient lots for ${formatPosition(units, costSpec)} in ${posting.account}: " +
                "needed $requiredAmount, found $availableAmount",
                transaction
            ))
        } else emptyList()

        return BookResult(
            postings = listOf(newPosting),
            errors = error,
            insufficient = insufficient
        )
    }

    /**
     * STRICT_WITH_SIZE booking method.
     * Like STRICT, but if ambiguous, try to find a lot whose size exactly matches.
     * If found, select the oldest such lot.
     */
    private fun bookStrictWithSize(
        transaction: Transaction,
        posting: Posting,
        costSpec: CostSpec,
        inventory: Inventory,
        matches: List<Position>
    ): BookResult {
        // First try STRICT
        val strictResult = bookStrict(transaction, posting, costSpec, inventory, matches)

        // If STRICT failed with ambiguous match, try size-based resolution
        if (strictResult.errors.isNotEmpty() && matches.size > 1) {
            val units = posting.units!!
            val requiredAmount = units.number.abs()

            // Find lots whose size exactly matches the required amount
            val sizeMatches = matches.filter { match ->
                match.units.number.abs() == requiredAmount
            }

            if (sizeMatches.isNotEmpty()) {
                // Sort by cost date, select the oldest
                val match = sizeMatches.sortedBy { it.cost?.date }.first()
                val sign = if (units.number.isNegative()) -Decimal.ONE else Decimal.ONE
                val reductionUnits = Amount(requiredAmount * sign, match.units.currency)

                // Remove from inventory
                inventory.addAmount(Amount(-requiredAmount, match.units.currency), match.cost)

                return BookResult(
                    postings = listOf(
                        posting.copy(units = reductionUnits, cost = costSpecFromCost(match.cost))
                    )
                )
            }
        }

        return strictResult
    }

    /**
     * FIFO/LIFO/HIFO generic booking method.
     * Consumes matching lots in the specified order until the required amount is met.
     */
    private fun <T : Comparable<T>> bookXifo(
        transaction: Transaction,
        posting: Posting,
        costSpec: CostSpec,
        inventory: Inventory,
        matches: List<Position>,
        sortBy: (Position) -> T?,
        reverse: Boolean
    ): BookResult {
        val units = posting.units!!
        val requiredAmount = units.number.abs()
        val sign = if (units.number.isNegative()) -Decimal.ONE else Decimal.ONE

        if (matches.isEmpty()) {
            // No matching lots - create a new negative position
            val cost = costSpec.toCost(transaction.date)
            if (cost != null) {
                inventory.addAmount(units, cost)
            }
            return BookResult(postings = listOf(posting.copy(cost = costSpec)))
        }

        // Sort matches by the specified attribute
        val sortedMatches = if (reverse) {
            matches.sortedWith(compareByDescending { sortBy(it) })
        } else {
            matches.sortedWith(compareBy { sortBy(it) })
        }

        val newPostings = mutableListOf<Posting>()
        var remaining = requiredAmount

        for (match in sortedMatches) {
            if (remaining.isZero()) break

            // Skip lots with inconsistent sign (mixed inventory)
            if (match.units.number * sign > Decimal.ZERO) continue

            val availableAmount = match.units.number.abs()
            val consumeAmount = if (remaining <= availableAmount) remaining else availableAmount

            val reductionUnits = Amount(consumeAmount * sign, match.units.currency)
            newPostings.add(
                posting.copy(units = reductionUnits, cost = costSpecFromCost(match.cost))
            )

            // Update inventory
            inventory.addAmount(Amount(-consumeAmount, match.units.currency), match.cost)
            remaining -= consumeAmount
        }

        val insufficient = remaining > Decimal.ZERO
        val errors = if (insufficient) {
            listOf(LoadError(
                transaction.meta,
                "Insufficient lots for ${formatPosition(units, costSpec)} in ${posting.account}: " +
                "needed $requiredAmount, found ${requiredAmount - remaining}",
                transaction
            ))
        } else emptyList()

        return BookResult(
            postings = newPostings,
            errors = errors,
            insufficient = insufficient
        )
    }

    /**
     * NONE booking method.
     * No lot matching - simply add the posting as-is to inventory.
     */
    private fun bookNone(
        posting: Posting,
        costSpec: CostSpec,
        inventory: Inventory
    ): BookResult {
        val units = posting.units!!
        val cost = costSpec.toCost(LocalDate(1970, 1, 1))
        if (cost != null) {
            inventory.addAmount(units, cost)
        }
        return BookResult(postings = listOf(posting.copy(cost = costSpec)))
    }

    /**
     * Interpolate a CostSpec, filling in missing fields.
     * - date defaults to transaction date
     * - numberPer is computed from numberTotal if provided
     */
    private fun interpolateCostSpec(
        spec: CostSpec,
        units: Amount,
        transactionDate: LocalDate
    ): CostSpec {
        var numberPer = spec.numberPer

        // If numberTotal is specified but numberPer is not, compute numberPer
        val numberTotal = spec.numberTotal
        if (numberPer == null && numberTotal != null && !units.number.isZero()) {
            numberPer = numberTotal / units.number.abs()
        }

        return spec.copy(
            numberPer = numberPer,
            date = spec.date ?: transactionDate
        )
    }

    /**
     * Convert a Cost back to a CostSpec for storing in Posting.
     */
    private fun costSpecFromCost(cost: Cost?): CostSpec? {
        return cost?.let {
            CostSpec(
                numberPer = it.number,
                currency = it.currency,
                date = it.date,
                label = it.label
            )
        }
    }

    /**
     * Format a position for error messages.
     */
    private fun formatPosition(units: Amount, costSpec: CostSpec): String {
        return "${units.number.toPlainString()} ${units.currency} " +
               "{${costSpec.numberPer?.toPlainString() ?: ""} ${costSpec.currency ?: ""}}"
    }

    /**
     * Complete missing posting units in a transaction.
     * If exactly one posting has no units, infer it from the sum of others.
     */
    private fun completeMissingUnits(
        postings: List<Posting>
    ): Pair<List<Posting>, BeancountError?> {
        val missingIndices = postings.mapIndexed { index, posting ->
            if (posting.units == null) index else null
        }.filterNotNull()

        if (missingIndices.isEmpty()) {
            return Pair(postings, null)
        }

        if (missingIndices.size > 1) {
            return Pair(
                postings,
                LoadError(
                    postings[0].meta ?: emptyMap(),
                    "Transaction has ${missingIndices.size} postings with missing amounts; only one can be inferred"
                )
            )
        }

        // Calculate the sum of all complete postings by currency
        // Use weight (considering price conversions and cost basis) instead of raw units
        val balances = mutableMapOf<Currency, Decimal>()
        for (posting in postings) {
            val units = posting.units ?: continue
            
            // Check for cost basis first (for investment transactions)
            val cost = posting.cost
            if (cost != null) {
                val costNumber = cost.numberPer
                val costCurrency = cost.currency
                if (costNumber != null && costCurrency != null) {
                    val weight = costNumber * units.number
                    val current = balances[costCurrency] ?: Decimal.ZERO
                    balances[costCurrency] = current + weight
                    continue
                }
            }
            
            // Check for price conversion
            val price = posting.price
            if (price != null) {
                val weight = price.number * units.number
                val current = balances[price.currency] ?: Decimal.ZERO
                balances[price.currency] = current + weight
                continue
            }
            
            // Otherwise use raw units
            val current = balances[units.currency] ?: Decimal.ZERO
            balances[units.currency] = current + units.number
        }

        val missingIndex = missingIndices[0]
        val missingPosting = postings[missingIndex]

        return if (balances.size == 1) {
            val (currency, balance) = balances.entries.first()
            val newPosting = missingPosting.copy(units = Amount(-balance, currency))
            val newPostings = postings.toMutableList()
            newPostings[missingIndex] = newPosting
            Pair(newPostings, null)
        } else {
            Pair(
                postings,
                LoadError(
                    missingPosting.meta ?: emptyMap(),
                    "Cannot infer missing posting with multiple currencies"
                )
            )
        }
    }
}
