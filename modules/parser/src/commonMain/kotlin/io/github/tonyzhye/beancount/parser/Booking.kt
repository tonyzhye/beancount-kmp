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
    internal data class BookResult(
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
                        accountBookingMethods,
                        options
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
     *
     * Matches Python beancount v3 booking behavior:
     * - Groups postings by weight currency before booking
     * - Booking is performed per group, then interpolation
     * - Keeps CostSpec during augmentation (converts after interpolation)
     */
    internal fun bookTransaction(
        transaction: Transaction,
        accountInventories: MutableMap<Account, Inventory>,
        accountBookingMethods: Map<Account, io.github.tonyzhye.beancount.core.Booking>,
        options: Options
    ): Pair<Transaction, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()

        // Step 1: Check for self-reduction (skip Booking.NONE accounts)
        val selfReductionAccounts = detectSelfReduction(transaction.postings, accountBookingMethods)
        if (selfReductionAccounts.isNotEmpty()) {
            errors.add(LoadError(
                transaction.meta,
                "Self-reduction detected for accounts: ${selfReductionAccounts.joinToString(", ")}",
                transaction
            ))
        }

        // Step 2: Create local inventory copies
        val localInventories = accountInventories.mapValues { (_, inventory) ->
            inventory.copy()
        }.toMutableMap()

        // Step 3: Group postings by weight currency (using ante-inventory for inference)
        val (referGroups, catErrors) = categorizeByCurrency(transaction, accountInventories)
        errors.addAll(catErrors)
        val postingGroups = replaceCurrencies(transaction.postings, referGroups)

        // If no groups were formed but there are postings, all postings have missing units
        if (postingGroups.isEmpty() && transaction.postings.isNotEmpty()) {
            val missingCount = transaction.postings.count { it.units == null }
            if (missingCount > 1) {
                errors.add(
                    LoadError(
                        transaction.meta,
                        "Transaction has $missingCount postings with missing amounts; only one can be inferred"
                    )
                )
            }
            return Pair(transaction, errors)
        }

        // Step 4: Infer tolerances
        // Python computes two tolerance sets:
        // - tolerances_max (mode="max") for storing in transaction meta
        // - tolerances_interp (mode="min" if precise else max) for interpolation
        val tolerancesMax = inferTolerances(transaction.postings, options, mode = "max")
        val tolerancesInterp = if (options.usePreciseInterpolation) {
            inferTolerances(transaction.postings, options, mode = "min")
        } else {
            tolerancesMax
        }

        // Step 5: Process each currency group
        val replPostings = mutableListOf<Posting>()
        for ((currency, groupPostings) in postingGroups) {
            // 5a: Book reductions/augmentations within this group
            val (bookedPostings, bookingErrors) = bookGroup(
                transaction, groupPostings, localInventories, accountBookingMethods
            )
            errors.addAll(bookingErrors)

            // 5b: Interpolate missing values within this group
            val (interPostings, interpErrors) = interpolateGroup(bookedPostings, tolerancesInterp, currency)
            errors.addAll(interpErrors)
            replPostings.addAll(interPostings)
        }

        // Step 6: Update global inventories with final postings
        for (posting in replPostings) {
            val units = posting.units ?: continue
            if (units.number.isZero()) continue
            val balance = accountInventories.getOrPut(posting.account) { Inventory() }
            // Convert CostSpec to Cost for inventory update
            val costSpec = posting.cost
            val cost = costSpec?.toCost(costSpec.date ?: transaction.date, units.number)
            if (cost != null) {
                balance.addAmount(units, cost)
            } else {
                balance.addAmount(units)
            }
        }

        val newMeta = transaction.meta.toMutableMap()
        newMeta[AUTOMATIC_TOLERANCES] = tolerancesMax
        return Pair(transaction.copy(postings = replPostings, meta = newMeta), errors)
    }

    /**
     * Group postings by their weight currency.
     * Weight currency priority: cost.currency > price.currency > units.currency
     * Postings with no determinable currency are assigned to existing groups
     * (if only one group exists) or to the first group as fallback.
     *
     * Simple version without ante-inventory inference. Used for scenarios
     * where balances are not available.
     */
    internal fun categorizeByCurrency(postings: List<Posting>): List<Pair<Currency, List<Posting>>> {
        val groups = mutableMapOf<Currency, MutableList<Posting>>()
        val order = mutableMapOf<Currency, Int>() // preserve first-encountered order
        val unknown = mutableListOf<Posting>()

        for ((index, posting) in postings.withIndex()) {
            val costSpec = posting.cost
            val price = posting.price
            val units = posting.units
            val costCurrency = costSpec?.currency
            val currency: String? = when {
                costCurrency != null -> costCurrency
                price != null -> price.currency
                units != null -> units.currency
                else -> null
            }
            if (currency != null) {
                groups.getOrPut(currency) { mutableListOf() }.add(posting)
                if (currency !in order) {
                    order[currency] = index
                }
            } else {
                unknown.add(posting)
            }
        }

        // Assign unknown postings to groups
        for (posting in unknown) {
            when {
                groups.size == 1 -> {
                    val currency = groups.keys.first()
                    groups[currency]!!.add(posting)
                }
                groups.isEmpty() -> {
                    // No known postings - skip (interpolation will report error)
                }
                else -> {
                    // Multiple groups - fallback to first group encountered
                    val currency = groups.keys.minByOrNull { order[it]!! } ?: continue
                    groups[currency]!!.add(posting)
                }
            }
        }

        return groups.entries.sortedBy { order[it.key]!! }.map { it.key to it.value }
    }

    /**
     * Currency reference for a posting, used during currency categorization.
     * Tracks inferred currencies for units, cost, and price.
     * Equivalent to Python's Refer NamedTuple.
     */
    internal data class CurrencyRefer(
        val index: Int,
        val unitsCurrency: Currency?,
        val costCurrency: Currency?,
        val priceCurrency: Currency?
    )

    /**
     * Get the bucket currency for a currency reference.
     * Priority: cost.currency > price.currency > units.currency
     */
    private fun getBucketCurrency(refer: CurrencyRefer): Currency? {
        return when {
            refer.costCurrency != null -> refer.costCurrency
            refer.priceCurrency != null -> refer.priceCurrency
            refer.unitsCurrency != null -> refer.unitsCurrency
            else -> null
        }
    }

    /**
     * Group postings by their weight currency, using ante-inventory balances
     * to infer missing currencies.
     *
     * Matches Python's categorize_by_currency behavior.
     */
    internal fun categorizeByCurrency(
        entry: Transaction,
        balances: Map<Account, Inventory>
    ): Pair<List<Pair<Currency, List<CurrencyRefer>>>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val groups = mutableMapOf<Currency, MutableList<CurrencyRefer>>()
        val sortdict = mutableMapOf<Currency, Int>()
        val autoPostings = mutableListOf<CurrencyRefer>()
        val unknown = mutableListOf<CurrencyRefer>()

        for ((index, posting) in entry.postings.withIndex()) {
            val units = posting.units
            val cost = posting.cost
            val price = posting.price

            var unitsCurrency = units?.currency
            var costCurrency = cost?.currency
            var priceCurrency = price?.currency

            // Enforce cost/price currency consistency: if one is known and the other
            // is not, copy the known one over.
            if (costCurrency == null && priceCurrency != null) {
                costCurrency = priceCurrency
            }
            if (priceCurrency == null && costCurrency != null) {
                priceCurrency = costCurrency
            }

            val refer = CurrencyRefer(index, unitsCurrency, costCurrency, priceCurrency)

            if (units == null && priceCurrency == null) {
                // Auto-posting: no units, no price currency
                autoPostings.add(refer)
            } else {
                val currency = getBucketCurrency(refer)
                if (currency != null) {
                    sortdict.putIfAbsent(currency, index)
                    groups.getOrPut(currency) { mutableListOf() }.add(refer)
                } else {
                    unknown.add(refer)
                }
            }
        }

        // Single unknown + single group inference
        if (unknown.isNotEmpty() && unknown.size == 1 && groups.size == 1) {
            val refer = unknown.removeAt(0)
            val otherCurrency = groups.keys.first()

            val newUnitsCurrency = if (refer.priceCurrency == null && refer.costCurrency == null) {
                otherCurrency
            } else {
                refer.unitsCurrency
            }
            val newPriceCurrency = refer.priceCurrency ?: otherCurrency
            val newCostCurrency = refer.costCurrency ?: otherCurrency

            val newRefer = CurrencyRefer(
                refer.index, newUnitsCurrency, newCostCurrency, newPriceCurrency
            )
            val currency = getBucketCurrency(newRefer)
            if (currency != null) {
                sortdict.putIfAbsent(currency, refer.index)
                groups.getOrPut(currency) { mutableListOf() }.add(newRefer)
            }
        }

        // Ante-inventory inference for still-unknown postings
        for (refer in unknown.toList()) {
            val posting = entry.postings[refer.index]
            val balance = balances[posting.account] ?: Inventory()

            var unitsCurrency = refer.unitsCurrency
            var costCurrency = refer.costCurrency
            var priceCurrency = refer.priceCurrency

            if (unitsCurrency == null) {
                val balanceCurrencies = balance.currencies()
                if (balanceCurrencies.size == 1) {
                    unitsCurrency = balanceCurrencies.first()
                }
            }

            if (costCurrency == null || priceCurrency == null) {
                val balanceCostCurrencies = balance.costCurrencies()
                if (balanceCostCurrencies.size == 1) {
                    val bcCurrency = balanceCostCurrencies.first()
                    if (priceCurrency == null) priceCurrency = bcCurrency
                    if (costCurrency == null) costCurrency = bcCurrency
                }
            }

            val newRefer = CurrencyRefer(refer.index, unitsCurrency, costCurrency, priceCurrency)
            val currency = getBucketCurrency(newRefer)
            if (currency != null) {
                sortdict.putIfAbsent(currency, refer.index)
                groups.getOrPut(currency) { mutableListOf() }.add(newRefer)
                unknown.remove(refer)
            }
        }

        // Fill remaining missing units currencies within groups
        for ((currency, refers) in groups) {
            for ((rindex, refer) in refers.withIndex()) {
                if (refer.unitsCurrency == null) {
                    val posting = entry.postings[refer.index]
                    val balance = balances[posting.account] ?: Inventory()
                    val balanceCurrencies = balance.currencies()
                    if (balanceCurrencies.size == 1) {
                        refers[rindex] = refer.copy(unitsCurrency = balanceCurrencies.first())
                    }
                }
            }
        }

        // Deal with auto-postings
        // Only report/process auto-postings if there are known groups to copy them into.
        // If groups is empty, the caller (bookTransaction) will handle the empty-group case.
        if (groups.isNotEmpty()) {
            if (autoPostings.size > 1) {
                val lastRefer = autoPostings.last()
                val posting = entry.postings[lastRefer.index]
                errors.add(
                    LoadError(
                        posting.meta ?: emptyMap(),
                        "You may not have more than one auto-posting per currency",
                        entry
                    )
                )
                // Keep only the first auto-posting
                val first = autoPostings.first()
                autoPostings.clear()
                autoPostings.add(first)
            }

            for (refer in autoPostings) {
                for ((currency, glist) in groups) {
                    sortdict.putIfAbsent(currency, refer.index)
                    glist.add(CurrencyRefer(refer.index, currency, null, null))
                }
            }
        }

        val sortedGroups = groups.entries.sortedBy { sortdict[it.key] ?: 0 }
            .map { it.key to it.value }
        return Pair(sortedGroups, errors)
    }

    /**
     * Replace resolved currencies in postings based on categorizeByCurrency results.
     * Equivalent to Python's replace_currencies.
     */
    internal fun replaceCurrencies(
        postings: List<Posting>,
        referGroups: List<Pair<Currency, List<CurrencyRefer>>>
    ): List<Pair<Currency, List<Posting>>> {
        return referGroups.map { (currency, refers) ->
            val newPostings = refers.sortedBy { it.index }.map { refer ->
                val posting = postings[refer.index]
                val units = posting.units

                if (units == null) {
                    // Missing units: keep as null for interpolation to handle.
                    // The currency is determined by the group, not the posting itself.
                    posting
                } else {
                    var newUnits = units
                    var newCost = posting.cost
                    var newPrice = posting.price
                    var replace = false

                    if (refer.unitsCurrency != null && units.currency != refer.unitsCurrency) {
                        newUnits = Amount(units.number, refer.unitsCurrency)
                        replace = true
                    }

                    val cost = posting.cost
                    if (cost != null && refer.costCurrency != null && cost.currency != refer.costCurrency) {
                        newCost = cost.copy(currency = refer.costCurrency)
                        replace = true
                    }

                    val price = posting.price
                    if (price != null && refer.priceCurrency != null && price.currency != refer.priceCurrency) {
                        newPrice = Amount(price.number, refer.priceCurrency)
                        replace = true
                    }

                    if (replace) {
                        posting.copy(units = newUnits, cost = newCost, price = newPrice)
                    } else {
                        posting
                    }
                }
            }
            currency to newPostings
        }
    }

    /**
     * Book a group of postings (all in the same weight currency).
     * Processes reductions and augmentations, updating local inventories.
     */
    internal fun bookGroup(
        transaction: Transaction,
        groupPostings: List<Posting>,
        localInventories: MutableMap<Account, Inventory>,
        accountBookingMethods: Map<Account, io.github.tonyzhye.beancount.core.Booking>
    ): Pair<List<Posting>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val result = mutableListOf<Posting>()

        for (posting in groupPostings) {
            val units = posting.units
            if (units == null) {
                result.add(posting)
                continue
            }

            val costSpec = posting.cost
            val bookingMethod = accountBookingMethods[posting.account]
                ?: io.github.tonyzhye.beancount.core.Booking.STRICT

            if (costSpec == null) {
                // No cost - simple currency posting
                if (!units.number.isZero()) {
                    val inventory = localInventories.getOrPut(posting.account) { Inventory() }
                    inventory.addAmount(units)
                }
                result.add(posting)
                continue
            }

            // Resolve CostSpec (interpolation)
            val resolvedCostSpec = interpolateCostSpec(costSpec, units, transaction.date)

            // Validate: zero amount with explicit cost is not allowed
            // Only error when numberPer or numberTotal is explicitly specified in the original;
            // empty cost spec {} or currency-only spec {USD} is allowed (no-op reduction).
            if (units.number.isZero() &&
                (costSpec.numberPer != null || costSpec.numberTotal != null)) {
                errors.add(LoadError(
                    transaction.meta,
                    "Amount is zero for posting with cost: ${posting.account}",
                    transaction
                ))
                result.add(posting.copy(cost = resolvedCostSpec))
                continue
            }

            if (units.number.isZero()) {
                result.add(posting.copy(cost = resolvedCostSpec))
                continue
            }

            // Validate: negative cost is not allowed
            val costNumberPer = resolvedCostSpec.numberPer
            if (costNumberPer != null && costNumberPer < Decimal.ZERO) {
                errors.add(LoadError(
                    transaction.meta,
                    "Cost is negative for posting: ${posting.account}",
                    transaction
                ))
            }

            val inventory = localInventories.getOrPut(posting.account) { Inventory() }
            val isReduction = bookingMethod != io.github.tonyzhye.beancount.core.Booking.NONE &&
                              inventory.isReducedBy(units)

            if (isReduction) {
                // Reduction - use booking method to match lots
                var matches = inventory.findMatches(units.currency, costSpec)

                // Crossover handling: negative inventory + positive posting with no direct match.
                // When a posting reduces a negative position (crossing from short to long),
                // we first reduce the negative lots to zero, then augment the remainder.
                val totalInventory = inventory.getPositions(units.currency)
                    .fold(Decimal.ZERO) { acc, p -> acc + p.units.number }
                val isCrossover = matches.isEmpty() &&
                    totalInventory.isNegative() &&
                    units.number.isPositive()

                if (isCrossover) {
                    val emptyMatches = inventory.findMatches(units.currency, CostSpec())
                    if (emptyMatches.isNotEmpty()) {
                        // Reduce at most the buy amount (units.number), not all shorts
                        val reduceAmount = minOf(totalInventory.abs(), units.number)
                        val remaining = units.number - reduceAmount

                        // Sort matches by booking method preference
                        val sortedMatches = when (bookingMethod) {
                            io.github.tonyzhye.beancount.core.Booking.FIFO ->
                                emptyMatches.sortedBy { it.cost?.date }
                            io.github.tonyzhye.beancount.core.Booking.LIFO ->
                                emptyMatches.sortedByDescending { it.cost?.date }
                            io.github.tonyzhye.beancount.core.Booking.HIFO ->
                                emptyMatches.sortedByDescending { it.cost?.number }
                            else -> emptyMatches
                        }

                        // Reduce negative lots to zero
                        var remainingReduce = reduceAmount
                        for (match in sortedMatches) {
                            if (remainingReduce.isZero()) break
                            val available = match.units.number.abs()
                            val consume = if (remainingReduce <= available) remainingReduce else available
                            val consumeUnits = Amount(consume, match.units.currency)
                            result.add(
                                posting.copy(
                                    units = consumeUnits,
                                    cost = costSpecFromCost(match.cost)
                                )
                            )
                            // Adding positive units to negative inventory brings it toward zero
                            inventory.addAmount(consumeUnits, match.cost)
                            remainingReduce -= consume
                        }

                        // Augment remaining with original cost spec
                        if (remaining.isPositive()) {
                            val augmentUnits = Amount(remaining, units.currency)
                            val resolvedCost = interpolateCostSpec(costSpec, augmentUnits, transaction.date)
                            val augCost = resolvedCost.toCost(transaction.date, remaining)
                            if (augCost != null) {
                                inventory.addAmount(augmentUnits, augCost)
                            }
                            result.add(posting.copy(units = augmentUnits, cost = resolvedCost))
                        }
                        continue
                    }
                }

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
                        BookResult(
                            errors = listOf(LoadError(
                                transaction.meta,
                                "AVERAGE method is not supported",
                                transaction
                            ))
                        )
                    }
                }

                result.addAll(bookResult.postings)
                errors.addAll(bookResult.errors)
            } else {
                // Augmentation (including NONE method)
                if (bookingMethod == io.github.tonyzhye.beancount.core.Booking.NONE) {
                    // NONE: return posting unchanged, no inventory update
                    result.add(posting.copy(cost = resolvedCostSpec))
                } else {
                    // Augmentation: keep CostSpec, add to inventory with resolved cost
                    val cost = resolvedCostSpec.toCost(transaction.date, units.number)
                    if (cost != null) {
                        inventory.addAmount(units, cost)
                    } else {
                        errors.add(LoadError(
                            transaction.meta,
                            "Cannot resolve cost spec for posting: ${posting.account}",
                            transaction
                        ))
                    }
                    result.add(posting.copy(cost = resolvedCostSpec))
                }
            }
        }

        return Pair(result, errors)
    }

    /**
     * Book reductions for a group of postings independently.
     * Matches Python's book_reductions() - processes lot matching for reductions
     * while leaving augmentations with CostSpec intact for interpolation.
     *
     * Unlike bookGroup(), this function does NOT modify the caller's inventories.
     * It creates local copies of balances for side-effect-free reduction processing.
     */
    internal fun bookReductions(
        transaction: Transaction,
        groupPostings: List<Posting>,
        balances: Map<Account, Inventory>,
        accountBookingMethods: Map<Account, io.github.tonyzhye.beancount.core.Booking>
    ): Pair<List<Posting>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val bookedPostings = mutableListOf<Posting>()
        val localBalances = mutableMapOf<Account, Inventory>()

        for (posting in groupPostings) {
            val units = posting.units
            val costSpec = posting.cost

            if (costSpec == null || units == null || units.number.isZero()) {
                bookedPostings.add(posting)
                continue
            }

            // Get or create local balance copy (preserving original balances)
            val balance = if (posting.account in localBalances) {
                localBalances[posting.account]!!
            } else {
                val copied = balances[posting.account]?.copy() ?: Inventory()
                localBalances[posting.account] = copied
                copied
            }

            val bookingMethod = accountBookingMethods[posting.account]
                ?: io.github.tonyzhye.beancount.core.Booking.STRICT
            val isReduction = bookingMethod != io.github.tonyzhye.beancount.core.Booking.NONE &&
                              balance.isReducedBy(units)

            if (isReduction) {
                // Resolve CostSpec for matching
                val resolvedCostSpec = interpolateCostSpec(costSpec, units, transaction.date)
                var matches = balance.findMatches(units.currency, costSpec)

                // Crossover handling
                val totalInventory = balance.getPositions(units.currency)
                    .fold(Decimal.ZERO) { acc, p -> acc + p.units.number }
                val isCrossover = matches.isEmpty() &&
                    totalInventory.isNegative() &&
                    units.number.isPositive()

                if (isCrossover) {
                    val emptyMatches = balance.findMatches(units.currency, CostSpec())
                    if (emptyMatches.isNotEmpty()) {
                        val reduceAmount = minOf(totalInventory.abs(), units.number)
                        val remaining = units.number - reduceAmount

                        val sortedMatches = when (bookingMethod) {
                            io.github.tonyzhye.beancount.core.Booking.FIFO ->
                                emptyMatches.sortedBy { it.cost?.date }
                            io.github.tonyzhye.beancount.core.Booking.LIFO ->
                                emptyMatches.sortedByDescending { it.cost?.date }
                            io.github.tonyzhye.beancount.core.Booking.HIFO ->
                                emptyMatches.sortedByDescending { it.cost?.number }
                            else -> emptyMatches
                        }

                        var remainingReduce = reduceAmount
                        for (match in sortedMatches) {
                            if (remainingReduce.isZero()) break
                            val available = match.units.number.abs()
                            val consume = if (remainingReduce <= available) remainingReduce else available
                            val consumeUnits = Amount(consume, match.units.currency)
                            bookedPostings.add(
                                posting.copy(
                                    units = consumeUnits,
                                    cost = costSpecFromCost(match.cost)
                                )
                            )
                            balance.addAmount(consumeUnits, match.cost)
                            remainingReduce -= consume
                        }

                        if (remaining.isPositive()) {
                            val augmentUnits = Amount(remaining, units.currency)
                            val resolvedCost = interpolateCostSpec(costSpec, augmentUnits, transaction.date)
                            val augCost = resolvedCost.toCost(transaction.date, remaining)
                            if (augCost != null) {
                                balance.addAmount(augmentUnits, augCost)
                            }
                            bookedPostings.add(posting.copy(units = augmentUnits, cost = resolvedCost))
                        }
                        continue
                    }
                }

                val bookResult = when (bookingMethod) {
                    io.github.tonyzhye.beancount.core.Booking.STRICT ->
                        bookStrict(transaction, posting, costSpec, balance, matches)
                    io.github.tonyzhye.beancount.core.Booking.STRICT_WITH_SIZE ->
                        bookStrictWithSize(transaction, posting, costSpec, balance, matches)
                    io.github.tonyzhye.beancount.core.Booking.NONE ->
                        bookNone(posting, costSpec, balance)
                    io.github.tonyzhye.beancount.core.Booking.FIFO ->
                        bookXifo(transaction, posting, costSpec, balance, matches,
                                 sortBy = { it.cost?.date }, reverse = false)
                    io.github.tonyzhye.beancount.core.Booking.LIFO ->
                        bookXifo(transaction, posting, costSpec, balance, matches,
                                 sortBy = { it.cost?.date }, reverse = true)
                    io.github.tonyzhye.beancount.core.Booking.HIFO ->
                        bookXifo(transaction, posting, costSpec, balance, matches,
                                 sortBy = { it.cost?.number }, reverse = true)
                    io.github.tonyzhye.beancount.core.Booking.AVERAGE -> {
                        BookResult(
                            errors = listOf(LoadError(
                                transaction.meta,
                                "AVERAGE method is not supported",
                                transaction
                            ))
                        )
                    }
                }

                if (bookResult.errors.isNotEmpty()) {
                    errors.addAll(bookResult.errors)
                    return Pair(emptyList(), errors)
                }

                bookedPostings.addAll(bookResult.postings)
                // Update local balance to avoid matching same lots twice
                for (bp in bookResult.postings) {
                    bp.units?.let { u ->
                        val c = bp.cost?.let { cs ->
                            Cost(cs.numberPer ?: Decimal.ZERO, cs.currency ?: "", cs.date ?: transaction.date, cs.label)
                        }
                        if (c != null) {
                            balance.addAmount(u, c)
                        }
                    }
                }
            } else {
                // Augmentation: keep CostSpec intact for interpolation
                val datedCostSpec = if (costSpec.date == null) {
                    costSpec.copy(date = transaction.date)
                } else {
                    costSpec
                }
                bookedPostings.add(posting.copy(cost = datedCostSpec))
            }
        }

        return Pair(bookedPostings, errors)
    }

    /**
     * Detect self-reduction in a transaction.
     * Self-reduction occurs when a transaction has both augmentation and reduction
     * postings for the same account with the same currency and cost basis.
     * This matches Python beancount's has_self_reduction() check.
     */
    internal fun detectSelfReduction(
        postings: List<Posting>,
        accountBookingMethods: Map<Account, io.github.tonyzhye.beancount.core.Booking> = emptyMap()
    ): List<Account> {
        val accountCurrencyPairs = mutableMapOf<Pair<Account, Currency>, Pair<Boolean, Boolean>>()

        for (posting in postings) {
            val units = posting.units ?: continue
            val costSpec = posting.cost ?: continue // Only check postings with cost basis

            // Skip Booking.NONE accounts (same as Python's has_self_reduction)
            val method = accountBookingMethods[posting.account]
            if (method == io.github.tonyzhye.beancount.core.Booking.NONE) continue

            val key = posting.account to units.currency
            val isReduction = units.number.isNegative()
            val (hasAug, hasRed) = accountCurrencyPairs[key] ?: (false to false)

            accountCurrencyPairs[key] = Pair(
                hasAug || !isReduction,
                hasRed || isReduction
            )
        }

        return accountCurrencyPairs
            .filter { (_, flags) -> flags.first && flags.second }
            .map { (key, _) -> key.first }
            .distinct()
    }

    /**
     * STRICT booking method.
     * Requires exactly one matching lot. If multiple lots match, reports an error.
     */
    internal fun bookStrict(
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
            // No matching lots
            return BookResult(
                errors = listOf(LoadError(
                    transaction.meta,
                    "Insufficient lots for ${formatPosition(units, costSpec)} in ${posting.account}: " +
                    "needed $requiredAmount, found 0",
                    transaction
                )),
                insufficient = true
            )
        }

        if (matches.size > 1) {
            // STRICT special case: if all matches sum to exactly the required amount, consume all
            val totalAvailable = matches.fold(Decimal.ZERO) { acc, match -> acc + match.units.number.abs() }
            if (totalAvailable == requiredAmount) {
                val newPostings = mutableListOf<Posting>()
                for (match in matches) {
                    val availableAmount = match.units.number.abs()
                    val reductionUnits = Amount(availableAmount * sign, match.units.currency)
                    newPostings.add(
                        posting.copy(units = reductionUnits, cost = costSpecFromCost(match.cost))
                    )
                    inventory.addAmount(Amount(-availableAmount, match.units.currency), match.cost)
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
    internal fun bookStrictWithSize(
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
    internal fun <T : Comparable<T>> bookXifo(
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
            // No matching lots
            return BookResult(
                errors = listOf(LoadError(
                    transaction.meta,
                    "Insufficient lots for ${formatPosition(units, costSpec)} in ${posting.account}: " +
                    "needed $requiredAmount, found 0",
                    transaction
                )),
                insufficient = true
            )
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
    internal fun bookNone(
        posting: Posting,
        costSpec: CostSpec,
        inventory: Inventory
    ): BookResult {
        val units = posting.units!!
        val cost = costSpec.toCost(LocalDate(1970, 1, 1), units.number)
        if (cost != null) {
            inventory.addAmount(units, cost)
        }
        return BookResult(postings = listOf(posting.copy(cost = costSpec)))
    }

    /**
     * Compute the per-unit cost number from a CostSpec and units amount.
     *
     * If numberTotal is specified, computes the effective per-unit cost:
     *   (numberPer * abs(units) + numberTotal) / abs(units)
     * If only numberPer is specified, returns it directly.
     * If neither is specified, returns null.
     *
     * Matches Python beancount.parser.booking_full.compute_cost_number().
     */
    internal fun computeCostNumber(costSpec: CostSpec, units: Amount): Decimal? {
        val numberPer = costSpec.numberPer
        val numberTotal = costSpec.numberTotal

        return when {
            numberTotal != null -> {
                val unitsNumber = units.number.abs()
                val costTotal = if (numberPer != null) {
                    numberTotal + numberPer * unitsNumber
                } else {
                    numberTotal
                }
                costTotal / unitsNumber
            }
            numberPer != null -> numberPer
            else -> null
        }
    }

    /**
     * Interpolate a CostSpec, filling in missing fields.
     * - date defaults to transaction date
     * - numberPer is computed from numberTotal if provided (and numberTotal is consumed)
     *
     * Note: When numberPer is derived from numberTotal, numberTotal is cleared to
     * prevent double-counting in toCost() which adds numberPer * units to numberTotal.
     */
    internal fun interpolateCostSpec(
        spec: CostSpec,
        units: Amount,
        transactionDate: LocalDate
    ): CostSpec {
        var numberPer = spec.numberPer
        var numberTotal = spec.numberTotal

        // If numberTotal is specified but numberPer is not, compute numberPer
        // and consume numberTotal so toCost() does not double-count.
        if (numberPer == null && numberTotal != null && !units.number.isZero()) {
            numberPer = numberTotal / units.number.abs()
            numberTotal = null
        }

        // If currency is present but no cost number is specified, default to zero
        // This handles {USD} → {0 USD}
        if (numberPer == null && spec.currency != null) {
            numberPer = Decimal.ZERO
        }

        return spec.copy(
            numberPer = numberPer,
            numberTotal = numberTotal,
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
     * Types of missing values that can be interpolated.
     * Matches Python's MissingType enum.
     */
    internal enum class MissingType {
        UNITS,
        COST_PER,
        COST_TOTAL,
        PRICE
    }

    /**
     * Resolve a CostSpec to a fully specified CostSpec by computing numberPer
     * from numberTotal if needed. Equivalent to Python's convert_costspec_to_cost,
     * but returns a resolved CostSpec instead of Cost (Kotlin type constraint).
     */
    internal fun resolveCostSpec(posting: Posting): Posting {
        val cost = posting.cost ?: return posting
        val units = posting.units ?: return posting

        // If numberTotal is specified, compute the effective numberPer
        val numberTotal = cost.numberTotal
        val numberPer = cost.numberPer
        val resolvedNumberPer = when {
            numberTotal != null && !units.number.isZero() -> {
                var total = numberTotal
                if (numberPer != null) {
                    total += numberPer * units.number.abs()
                }
                total / units.number.abs()
            }
            numberPer != null -> numberPer
            else -> Decimal.ZERO
        }

        return posting.copy(
            cost = cost.copy(
                numberPer = resolvedNumberPer,
                numberTotal = null,
                missingFields = emptySet()
            )
        )
    }

    /**
     * Interpolate missing values within a currency group.
     * Matches Python's interpolate_group behavior.
     * Supports 4 missing types: UNITS, COST_PER, COST_TOTAL, PRICE.
     */
    internal fun interpolateGroup(
        postings: List<Posting>,
        tolerances: Map<Currency, Decimal> = emptyMap(),
        groupCurrency: Currency = "XXX"
    ): Pair<List<Posting>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()

        // Identify incomplete postings and their missing types.
        val incomplete = mutableListOf<Pair<MissingType, Int>>()
        for ((index, posting) in postings.withIndex()) {
            if (posting.units == null) {
                incomplete.add(MissingType.UNITS to index)
            }
            val cost = posting.cost
            if (cost != null) {
                if ("numberPer" in cost.missingFields) {
                    incomplete.add(MissingType.COST_PER to index)
                }
                if ("numberTotal" in cost.missingFields) {
                    incomplete.add(MissingType.COST_TOTAL to index)
                }
            }
            if (posting.missingPriceNumber) {
                incomplete.add(MissingType.PRICE to index)
            }
        }

        // No missing values: resolve all CostSpec to fully specified CostSpec.
        if (incomplete.isEmpty()) {
            val outPostings = postings.map { resolveCostSpec(it) }
            return Pair(outPostings, errors)
        }

        // Too many missing values.
        if (incomplete.size > 1) {
            val (_, index) = incomplete[0]
            errors.add(
                LoadError(
                    postings[index].meta ?: emptyMap(),
                    "Too many missing numbers for currency group '$groupCurrency'"
                )
            )
            return Pair(emptyList(), errors)
        }

        // Single missing value: interpolate it.
        val (missing, index) = incomplete[0]
        val incompletePosting = postings[index]

        // Resolve all other postings' CostSpec so their weights are fully known.
        val newPostings = postings.mapIndexed { i, posting ->
            if (i == index) posting else resolveCostSpec(posting)
        }.toMutableList()

        // Compute residual from all postings except the incomplete one.
        val residualPostings = newPostings.filterIndexed { i, _ -> i != index }
        val residual = computeResidual(residualPostings)

        val (weight, weightCurrency) = if (!residual.isEmpty()) {
            val resPos = residual.toList()[0]
            -resPos.units.number to resPos.units.currency
        } else {
            Decimal.ZERO to groupCurrency
        }

        val newPosting: Posting? = when (missing) {
            MissingType.UNITS -> {
                val cost = incompletePosting.cost
                val price = incompletePosting.price
                val costNumberPer = cost?.numberPer
                val unitsNumber = when {
                    cost != null && costNumberPer != null -> {
                        if (costNumberPer.isZero()) {
                            errors.add(
                                LoadError(
                                    incompletePosting.meta ?: emptyMap(),
                                    "Cannot infer per-unit cost only from total"
                                )
                            )
                            return Pair(postings, errors)
                        }
                        val costTotal = cost.numberTotal ?: Decimal.ZERO
                        (weight - costTotal) / costNumberPer
                    }
                    price != null -> weight / price.number
                    else -> weight
                }
                val quantized = quantizeWithTolerance(tolerances, weightCurrency, unitsNumber)
                val baseMeta = incompletePosting.meta ?: emptyMap<String, Any>()
                val newMeta = baseMeta + mapOf(AUTOMATIC_META to true)
                incompletePosting.copy(
                    units = Amount(quantized, weightCurrency),
                    meta = newMeta
                )
            }
            MissingType.COST_PER -> {
                val units = incompletePosting.units!!
                val cost = incompletePosting.cost!!
                val numberPer = if (!units.number.isZero()) {
                    (weight - (cost.numberTotal ?: Decimal.ZERO)) / units.number
                } else {
                    Decimal.ZERO
                }
                val newCost = cost.copy(numberPer = numberPer)
                val baseMeta = incompletePosting.meta ?: emptyMap<String, Any>()
                val newMeta = baseMeta + mapOf(AUTOMATIC_META to true)
                incompletePosting.copy(cost = newCost, meta = newMeta)
            }
            MissingType.COST_TOTAL -> {
                val units = incompletePosting.units!!
                val cost = incompletePosting.cost!!
                val costNumberPer = cost.numberPer ?: Decimal.ZERO
                val numberTotal = weight - (costNumberPer * units.number)
                val newCost = cost.copy(numberTotal = numberTotal)
                val baseMeta = incompletePosting.meta ?: emptyMap<String, Any>()
                val newMeta = baseMeta + mapOf(AUTOMATIC_META to true)
                incompletePosting.copy(cost = newCost, meta = newMeta)
            }
            MissingType.PRICE -> {
                val units = incompletePosting.units!!
                if (incompletePosting.cost != null) {
                    errors.add(
                        LoadError(
                            incompletePosting.meta ?: emptyMap(),
                            "Cannot infer price for postings with units held at cost"
                        )
                    )
                    return Pair(postings, errors)
                }
                val price = incompletePosting.price!!
                val newPriceNumber = (weight / units.number).abs()
                val baseMeta = incompletePosting.meta ?: emptyMap<String, Any>()
                val newMeta = baseMeta + mapOf(AUTOMATIC_META to true)
                incompletePosting.copy(
                    price = Amount(newPriceNumber, price.currency),
                    missingPriceNumber = false,
                    meta = newMeta
                )
            }
        }

        // Replace the incomplete posting with the interpolated one.
        if (newPosting != null) {
            newPostings[index] = resolveCostSpec(newPosting)
        } else {
            newPostings.removeAt(index)
        }

        // Validation: units non-zero, cost non-negative.
        for (posting in newPostings) {
            val postingCost = posting.cost ?: continue
            if (posting.units?.number?.isZero() == true) {
                errors.add(
                    LoadError(
                        posting.meta ?: emptyMap(),
                        "Amount is zero: ${posting.units}"
                    )
                )
            }
            val costNumber = postingCost.numberPer
            if (costNumber != null && costNumber < Decimal.ZERO) {
                errors.add(
                    LoadError(
                        posting.meta ?: emptyMap(),
                        "Cost is negative: ${posting.cost}"
                    )
                )
            }
        }

        return Pair(newPostings, errors)
    }
}
