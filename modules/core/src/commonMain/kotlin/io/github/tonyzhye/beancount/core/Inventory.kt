package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * MatchResult indicates how a lot was booked to an inventory.
 * Based on beancount.core.inventory.MatchResult
 */
enum class MatchResult {
    /** A new lot was created. */
    CREATED,
    /** An existing lot was reduced. */
    REDUCED,
    /** An existing lot was augmented. */
    AUGMENTED
}

/**
 * Check if a position's cost matches a CostSpec.
 * A position matches if all specified fields in the spec match the cost.
 * Based on beancount.core.position.Position.matches
 */
fun Position.costMatches(spec: CostSpec): Boolean {
    if (cost != null) {
        if (spec.currency != null && cost.currency != spec.currency) return false
        if (spec.numberPer != null && cost.number != spec.numberPer) return false
        if (spec.date != null && cost.date != spec.date) return false
        if (spec.label != null && cost.label != spec.label) return false
    } else {
        // Position has no cost, but spec requires cost attributes
        if (spec.currency != null || spec.numberPer != null || spec.date != null || spec.label != null) return false
    }
    return true
}

/**
 * An Inventory is a set of positions, indexed by (currency, cost) for efficiency.
 * Based on beancount.core.inventory.Inventory
 */
class Inventory : Iterable<Position> {

    private val positions = mutableMapOf<Pair<String, Cost?>, Position>()

    /**
     * Create a new inventory from a list of existing positions.
     */
    constructor(positions: List<Position> = emptyList()) {
        positions.forEach { addPosition(it) }
    }

    /**
     * Create a copy of this inventory.
     */
    fun copy(): Inventory {
        return Inventory(toList())
    }

    /**
     * Return true if the inventory is empty.
     */
    fun isEmpty(): Boolean = positions.isEmpty()

    /**
     * Return the number of positions in the inventory.
     */
    fun size(): Int = positions.size

    /**
     * Return true if all positions are small (below tolerance).
     * Supports "*" key as default tolerance for any currency.
     */
    fun isSmall(tolerances: Map<String, Decimal>): Boolean {
        val defaultTolerance = tolerances["*"] ?: Decimal.ZERO
        return positions.values.all { position ->
            val tolerance = tolerances[position.units.currency] ?: defaultTolerance
            position.units.number.abs() <= tolerance
        }
    }

    /**
     * Return true if all positions are small (single tolerance for all).
     */
    fun isSmall(tolerance: Decimal): Boolean {
        return positions.values.all { position ->
            position.units.number.abs() <= tolerance
        }
    }

    /**
     * Return true if the inventory contains both positive and negative lots
     * for at least one currency.
     */
    fun isMixed(): Boolean {
        val signsMap = mutableMapOf<String, Boolean>()
        for (position in positions.values) {
            val sign = position.units.number >= Decimal.ZERO
            val prevSign = signsMap.putIfAbsent(position.units.currency, sign)
            if (prevSign != null && prevSign != sign) {
                return true
            }
        }
        return false
    }

    /**
     * Get the total amount across all positions in the given currency.
     */
    fun getCurrencyUnits(currency: String): Amount {
        var total = Decimal.ZERO
        for (position in positions.values) {
            if (position.units.currency == currency) {
                total += position.units.number
            }
        }
        return Amount(total, currency)
    }

    /**
     * Get all commodity currencies in this inventory.
     */
    fun currencies(): Set<String> {
        return positions.values.map { it.units.currency }.toSet()
    }

    /**
     * Get all cost currencies in this inventory.
     */
    fun costCurrencies(): Set<String> {
        return positions.values.mapNotNull { it.cost?.currency }.toSet()
    }

    /**
     * Get all positions as a list.
     */
    fun getPositions(): List<Position> = positions.values.toList()

    /**
     * Find all positions for a commodity that match the given CostSpec.
     * First filters by commodity currency, then matches cost spec.
     */
    fun findMatches(commodity: String, spec: CostSpec): List<Position> {
        return positions.values.filter { position ->
            position.units.currency == commodity && position.costMatches(spec)
        }
    }

    /**
     * Return true if this inventory is reduced by the given amount.
     * A reduction occurs when the inventory has a position of opposite sign
     * for the same currency.
     */
    fun isReducedBy(amount: Amount): Boolean {
        val positions = getPositions(amount.currency)
        if (positions.isEmpty()) return false
        val total = positions.fold(Decimal.ZERO) { acc, pos -> acc + pos.units.number }
        return (total.isPositive() && amount.number.isNegative()) ||
               (total.isNegative() && amount.number.isPositive())
    }

    /**
     * Get all positions for a specific currency.
     */
    fun getPositions(currency: String): List<Position> {
        return positions.values.filter { it.units.currency == currency }
    }

    /**
     * Add an amount with cost to this inventory using strict lot matching.
     *
     * @return A pair of (oldPosition, matchResult) where oldPosition is the
     *         position before modification (null if newly created or deleted).
     */
    fun addAmount(units: Amount, cost: Cost? = null): Pair<Position?, MatchResult> {

        val key = units.currency to cost
        val existing = positions[key]

        return if (existing == null) {
            // Create new position
            val newPosition = Position(units, cost)
            positions[key] = newPosition
            null to MatchResult.CREATED
        } else {
            // Add to existing position
            val oldPosition = existing
            val newUnits = Amount(existing.units.number + units.number, existing.units.currency)

            if (newUnits.number.isZero()) {
                // Remove if zero
                positions.remove(key)
                oldPosition to MatchResult.REDUCED
            } else {
                // Update position
                positions[key] = Position(newUnits, existing.cost)
                oldPosition to if (units.number.isPositive()) MatchResult.AUGMENTED else MatchResult.REDUCED
            }
        }
    }

    /**
     * Add a position to this inventory using strict lot matching.
     *
     * @return A pair of (oldPosition, matchResult) where oldPosition is the
     *         position before modification (null if newly created or deleted).
     */
    fun addPosition(position: Position): Pair<Position?, MatchResult> {
        return addAmount(position.units, position.cost)
    }

    /**
     * Reduce the inventory using a conversion function.
     */
    fun reduce(reducer: (Position) -> Amount): Inventory {
        val inventory = Inventory()
        for (position in positions.values) {
            inventory.addAmount(reducer(position))
        }
        return inventory
    }

    /**
     * Add another inventory to this one (in-place modification).
     */
    fun addInventory(other: Inventory): Inventory {
        for (position in other.positions.values) {
            addPosition(position)
        }
        return this
    }

    /**
     * Add another inventory to this one (returns new inventory, immutable).
     */
    operator fun plus(other: Inventory): Inventory {
        val newInventory = copy()
        newInventory.addInventory(other)
        return newInventory
    }

    /**
     * Negate all positions in this inventory (return new inventory).
     */
    fun negate(): Inventory {
        val result = Inventory()
        for ((key, position) in positions) {
            result.positions[key] = Position(
                Amount(-position.units.number, position.units.currency),
                position.cost
            )
        }
        return result
    }

    // Iterable implementation
    override fun iterator(): Iterator<Position> = positions.values.iterator()

    override fun toString(): String {
        return positions.values.joinToString(", ") { "${it.units} ${it.cost ?: ""}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Inventory) return false
        return positions == other.positions
    }

    override fun hashCode(): Int = positions.hashCode()

    companion object {
        /**
         * Create an inventory from a string representation.
         * Used for testing.
         */
        fun fromString(string: String): Inventory {
            val inventory = Inventory()
            // Simple parsing: "10 USD, 5 AAPL {100.00 USD}"
            val positionStrs = string.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            for (posStr in positionStrs) {
                // Parse format: "10 USD" or "5 AAPL {100.00 USD}"
                val parts = posStr.split(" ")
                if (parts.size >= 2) {
                    val number = Decimal(parts[0])
                    val currency = parts[1]

                     val cost = if (parts.size >= 4 && parts[2].startsWith("{")) {
                        val costNumber = Decimal(parts[2].removePrefix("{"))
                        val costCurrency = parts[3].replace("}", "")
                        Cost(costNumber, costCurrency, LocalDate(2000, 1, 1)) // Default date for parsing
                    } else null

                    inventory.addAmount(Amount(number, currency), cost)
                }
            }

            return inventory
        }
    }
}
