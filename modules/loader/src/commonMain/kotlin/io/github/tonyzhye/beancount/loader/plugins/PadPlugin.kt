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
 *
 * This is a simplified implementation that handles the most common case.
 */
object PadPlugin {

    /**
     * Plugin entry point.
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val result = entries.toMutableList()
        val padEntries = entries.filterIsInstance<Pad>()

        for (pad in padEntries) {
            // Find the next Balance assertion for this account after the pad date
            val nextBalance = entries
                .filterIsInstance<Balance>()
                .filter { it.account == pad.account && it.date >= pad.date }
                .minByOrNull { it.date }

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
                // Create a pad transaction
                val padTransaction = Transaction(
                    meta = newMetadata(options.filename, 0),
                    date = pad.date,
                    flag = "P",
                    narration = "Padding for balance assertion",
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
            }
        }

        return Pair(result.sorted(), errors)
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
