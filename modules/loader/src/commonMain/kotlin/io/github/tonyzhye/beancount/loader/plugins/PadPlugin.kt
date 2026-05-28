package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate

/**
 * Pad plugin - automatically generates transactions to make balance assertions succeed.
 * Based on beancount.ops.pad.
 *
 * For each Pad directive, this plugin inserts a transaction that transfers the
 * difference between the current account balance and the expected balance from
 * the next Balance assertion.
 */
object PadPlugin {

    /**
     * Plugin entry point.
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val result = entries.toMutableList()
        val padEntries = entries.filterIsInstance<Pad>()
        val usedPads = mutableSetOf<Pad>()

        for (pad in padEntries) {
            // Find the next Balance assertion for this account after the pad date
            val nextBalance = findNextBalance(entries, pad)

            if (nextBalance == null) {
                errors.add(
                    LoadError(
                        pad.meta,
                        "No balance assertion found for pad account '${pad.account}'",
                        pad
                    )
                )
                continue
            }

            // Calculate current balance of the account at the pad date
            val currentBalance = calculateAccountBalance(
                entries.filter { it.date < nextBalance.date },
                pad.account
            )

            // Calculate the difference
            val expectedAmount = nextBalance.amount
            val diff = expectedAmount.number - currentBalance

            if (!diff.isZero()) {
                usedPads.add(pad)
                // Create a pad transaction with narration matching Python beancount
                val narration = "(Padding inserted for Balance of $expectedAmount for difference ${Amount(diff, expectedAmount.currency)})"
                val padTransaction = Transaction(
                    meta = newMetadata(options.filename, 0),
                    date = pad.date,
                    flag = "P",
                    narration = narration,
                    postings = listOf(
                        Posting(
                            account = pad.account,
                            units = Amount(diff, expectedAmount.currency)
                        ),
                        Posting(
                            account = pad.sourceAccount,
                            units = Amount(-diff, expectedAmount.currency)
                        )
                    )
                )
                result.add(padTransaction)
            } else {
                // Pad was used even if diff is zero (the balance already matched)
                usedPads.add(pad)
            }
        }

        // Generate errors for unused pad entries
        for (pad in padEntries) {
            if (pad !in usedPads) {
                errors.add(
                    LoadError(
                        pad.meta,
                        "Unused Pad entry",
                        pad
                    )
                )
            }
        }

        return Pair(result.sorted(), errors)
    }

    /**
     * Find the next Balance assertion for a pad directive.
     * This is the first balance assertion after the pad date for the same account.
     */
    private fun findNextBalance(entries: List<Directive>, pad: Pad): Balance? {
        return entries
            .filterIsInstance<Balance>()
            .filter { it.account == pad.account && it.date > pad.date }
            .minByOrNull { it.date }
    }

    /**
     * Calculate the balance of an account from a list of entries up to a certain date.
     */
    private fun calculateAccountBalance(entries: List<Directive>, account: Account): Decimal {
        var balance = Decimal.ZERO

        for (entry in entries) {
            if (entry is Transaction) {
                for (posting in entry.postings) {
                    val units = posting.units
                    if (posting.account == account && units != null) {
                        balance += units.number
                    }
                }
            }
        }

        return balance
    }
}
