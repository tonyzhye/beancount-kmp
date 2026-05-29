package io.github.tonyzhye.beancount.core

/**
 * A realized account in a tree structure.
 *
 * Contains postings associated with this account (not including children),
 * and the final balance of those postings.
 *
 * Based on beancount.core.realization.RealAccount.
 *
 * @property account Full account name (e.g., "Assets:Bank:Checking")
 * @property txnPostings List of postings/entries for this account only
 * @property balance Final balance of this account's postings
 * @property children Child accounts (e.g., "Bank" under "Assets")
 */
class RealAccount(
    val account: String
) {
    val txnPostings = mutableListOf<Any>()  // TxnPosting or non-Transaction directives
    var balance = Inventory()
    val children = mutableMapOf<String, RealAccount>()

    /**
     * Get a child account by name, creating it if it doesn't exist.
     */
    fun getOrCreate(name: String): RealAccount {
        return children.getOrPut(name) {
            RealAccount(if (account.isEmpty()) name else "$account:$name")
        }
    }

    /**
     * Get a child account by name, returning null if not found.
     */
    operator fun get(name: String): RealAccount? = children[name]

    /**
     * Check if this account has a child with the given name.
     */
    operator fun contains(name: String): Boolean = name in children

    /**
     * Get all child accounts.
     */
    fun getChildren(): List<RealAccount> = children.values.toList()

    /**
     * Iterate over this account and all descendants.
     */
    fun iterate(depthFirst: Boolean = false): Sequence<RealAccount> = sequence {
        yield(this@RealAccount)
        if (depthFirst) {
            children.values.forEach { yieldAll(it.iterate(true)) }
        } else {
            val queue = ArrayDeque(children.values)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                yield(current)
                queue.addAll(current.children.values)
            }
        }
    }

    /**
     * Iterate over leaf accounts only.
     */
    fun iterateLeaves(): Sequence<RealAccount> = iterate().filter { it.isLeaf }

    /**
     * Check if this is a leaf account (no children).
     */
    val isLeaf: Boolean get() = children.isEmpty()

    /**
     * Get the short name (last component) of this account.
     */
    val shortName: String
        get() = account.substringAfterLast(":", account)

    override fun toString(): String = "RealAccount($account, ${txnPostings.size} postings, balance=$balance)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RealAccount) return false
        return account == other.account
    }

    override fun hashCode(): Int = account.hashCode()
}

/**
 * Group entries by account into a tree of realized accounts.
 *
 * This is the main entry point for realization. It groups all entries
 * by account and computes the final balance for each account.
 *
 * @param entries List of directives to realize
 * @param computeBalance Whether to compute balances (default true)
 * @return Root RealAccount containing the full tree
 */
fun realize(
    entries: List<Directive>,
    computeBalance: Boolean = true
): RealAccount {
    // Step 1: Group postings and entries by account
    val postingsByAccount = groupPostingsByAccount(entries)

    // Step 2: Build the tree structure
    val root = RealAccount("")

    for ((accountName, postings) in postingsByAccount) {
        // Create nodes along the path
        val parts = accountName.split(":")
        var current = root

        for (part in parts) {
            current = current.getOrCreate(part)
        }

        // Assign postings to the leaf node
        current.txnPostings.addAll(postings)
    }

    // Step 3: Compute balances if requested
    if (computeBalance) {
        computeBalances(root)
    }

    return root
}

/**
 * Group postings and entries by account.
 *
 * Transactions are split into individual postings.
 * Other entries (Open, Close, Balance, etc.) are included as-is.
 */
private fun groupPostingsByAccount(
    entries: List<Directive>
): Map<String, List<Any>> {
    val postingsMap = mutableMapOf<String, MutableList<Any>>()

    for (entry in entries.sorted()) {
        when (entry) {
            is Transaction -> {
                entry.postings.forEach { posting ->
                    postingsMap
                        .getOrPut(posting.account) { mutableListOf() }
                        .add(TxnPosting(entry, posting))
                }
            }
            is Open, is Close, is Balance, is Note, is Document -> {
                val account = when (entry) {
                    is Open -> entry.account
                    is Close -> entry.account
                    is Balance -> entry.account
                    is Note -> entry.account
                    is Document -> entry.account
                    else -> throw IllegalStateException()
                }
                postingsMap
                    .getOrPut(account) { mutableListOf() }
                    .add(entry)
            }
            is Pad -> {
                postingsMap
                    .getOrPut(entry.account) { mutableListOf() }
                    .add(entry)
                postingsMap
                    .getOrPut(entry.sourceAccount) { mutableListOf() }
                    .add(entry)
            }
            else -> {} // Skip other directive types
        }
    }

    return postingsMap
}

/**
 * Compute balances for all accounts in the tree.
 *
 * This calculates the balance for each account by summing:
 * 1. Its own postings
 * 2. All child account balances
 */
private fun computeBalances(root: RealAccount) {
    // Compute balance for each account from its own postings only
    // This matches Python behavior where balance is per-account, not aggregated
    for (account in root.iterate()) {
        account.balance = computePostingsBalance(account.txnPostings)
    }
}

/**
 * Compute the balance from a list of postings.
 */
private fun computePostingsBalance(postings: List<Any>): Inventory {
    val inventory = Inventory()

    for (posting in postings) {
        when (posting) {
            is TxnPosting -> {
                    posting.posting.units?.let { units ->
                        inventory.addAmount(units, posting.posting.cost?.let { cost ->
                            if (cost.currency != null && cost.date != null) {
                                Cost(cost.numberPer ?: cost.numberTotal ?: Decimal.ZERO, cost.currency, cost.date, cost.label)
                            } else null
                        })
                    }
            }
            is Pad -> {
                // Pad entries affect balance when they create padding transactions
                // The actual balance change is handled by the PadPlugin
            }
            else -> {} // Other entry types don't directly affect balance
        }
    }

    return inventory
}

/**
 * Compute the total balance of an account including all subaccounts.
 */
fun computeBalance(realAccount: RealAccount, leafOnly: Boolean = false): Inventory {
    return if (leafOnly) {
        realAccount.iterateLeaves()
            .map { it.balance }
            .fold(Inventory()) { acc, balance -> acc.addInventory(balance); acc }
    } else {
        realAccount.iterate()
            .map { it.balance }
            .fold(Inventory()) { acc, balance -> acc.addInventory(balance); acc }
    }
}

/**
 * Iterate over postings with running balance.
 *
 * Yields tuples of (entry, postings, change, balance) where:
 * - entry: The directive for this line
 * - postings: Postings that affect the balance
 * - change: Inventory change from this entry
 * - balance: Running balance after this entry
 */
fun iterateWithBalance(
    txnPostings: List<Any>
): List<BalanceIteration> {
    val results = mutableListOf<BalanceIteration>()
    var runningBalance = Inventory()

    for (item in txnPostings) {
        when (item) {
            is TxnPosting -> {
                val change = Inventory()
                item.posting.units?.let { units ->
                    change.addAmount(units, item.posting.cost?.let { cost ->
                        if (cost.currency != null && cost.date != null) {
                            Cost(cost.numberPer ?: cost.numberTotal ?: Decimal.ZERO, cost.currency, cost.date, cost.label)
                        } else null
                    })
                }
                runningBalance = runningBalance.addInventory(change)
                results.add(BalanceIteration(
                    entry = item.txn,
                    postings = listOf(item.posting),
                    change = change,
                    balance = runningBalance.copy()
                ))
            }
            is Pad -> {
                // Pad entries don't change balance directly
                results.add(BalanceIteration(
                    entry = item,
                    postings = emptyList(),
                    change = Inventory(),
                    balance = runningBalance.copy()
                ))
            }
            else -> {
                results.add(BalanceIteration(
                    entry = item as Directive,
                    postings = emptyList(),
                    change = Inventory(),
                    balance = runningBalance.copy()
                ))
            }
        }
    }

    return results
}

/**
 * Result of iterating with balance.
 */
data class BalanceIteration(
    val entry: Directive,
    val postings: List<Posting>,
    val change: Inventory,
    val balance: Inventory
)

/**
 * Extension function to add one inventory to another.
 */
private fun Inventory.addInventory(other: Inventory): Inventory {
    val result = this.copy()
    other.forEach { position ->
        result.addAmount(position.units, position.cost)
    }
    return result
}
