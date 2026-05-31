package io.github.tonyzhye.beancount.core

/**
 * Compress multiple entries into a single one.
 *
 * This can be used during import to compress the effective output, for accounts
 * with a large number of similar entries. For example, a trading account
 * which pays out interest every single day. Compressing these interest-only
 * entries to monthly ones makes sense.
 *
 * Based on beancount.ops.compress
 */
object Compress {

    /**
     * Compress multiple transactions into single transactions.
     *
     * Replace consecutive sequences of Transaction entries that fulfill the given
     * predicate by a single entry at the date of the last matching entry.
     *
     * @param entries A list of directives.
     * @param predicate A function which accepts an entry and returns true if the entry
     *        is intended to be compressed.
     * @return A list of directives, with compressible transactions replaced by a summary
     *         equivalent.
     */
    fun compress(entries: List<Directive>, predicate: (Transaction) -> Boolean): List<Directive> {
        val newEntries = mutableListOf<Directive>()
        val pending = mutableListOf<Transaction>()

        for (entry in entries) {
            if (entry is Transaction && predicate(entry)) {
                pending.add(entry)
            } else {
                if (pending.isNotEmpty()) {
                    newEntries.add(merge(pending, pending.last()))
                    pending.clear()
                }
                newEntries.add(entry)
            }
        }

        if (pending.isNotEmpty()) {
            newEntries.add(merge(pending, pending.last()))
        }

        return newEntries
    }

    /**
     * Merge the postings of a list of Transactions into a single one.
     *
     * @param entries A list of transactions.
     * @param prototypeTxn A Transaction which is used to create the compressed
     *        Transaction instance. Its list of postings is ignored.
     * @return A new Transaction instance which contains all the postings from the input
     *         entries merged together.
     */
    fun merge(entries: List<Transaction>, prototypeTxn: Transaction): Transaction {
        // Aggregate the postings together. Map of numberless posting key to total units.
        val postingsMap = mutableMapOf<PostingKey, Decimal>()

        for (entry in entries) {
            for (posting in entry.postings) {
                val units = posting.units ?: continue
                val key = PostingKey(
                    posting.account,
                    units.currency,
                    posting.cost,
                    posting.price,
                    posting.flag
                )
                postingsMap[key] = (postingsMap[key] ?: Decimal.ZERO) + units.number
            }
        }

        // Sort for stability of output.
        val sortedItems = postingsMap.toList().sortedWith(
            compareBy(
                { it.first.account },
                { it.first.currency },
                { it.second }
            )
        )

        // Issue the merged postings.
        val mergedPostings = sortedItems.map { (key, number) ->
            Posting(
                account = key.account,
                units = Amount(number, key.currency),
                cost = key.cost,
                price = key.price,
                flag = key.flag,
                meta = null
            )
        }

        return Transaction(
            meta = prototypeTxn.meta,
            date = prototypeTxn.date,
            flag = prototypeTxn.flag,
            payee = prototypeTxn.payee,
            narration = prototypeTxn.narration,
            tags = emptySet(),
            links = emptySet(),
            postings = mergedPostings
        )
    }

    /**
     * Key for grouping postings during compression.
     * Everything must be the same except the number.
     */
    private data class PostingKey(
        val account: Account,
        val currency: Currency,
        val cost: CostSpec?,
        val price: Amount?,
        val flag: Flag?
    )
}
