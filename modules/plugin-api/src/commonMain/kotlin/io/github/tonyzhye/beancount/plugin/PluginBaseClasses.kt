package io.github.tonyzhye.beancount.plugin

import io.github.tonyzhye.beancount.core.BeancountError
import io.github.tonyzhye.beancount.core.Directive
import io.github.tonyzhye.beancount.core.Transaction

/**
 * Abstract base class for plugins that only process transactions.
 *
 * Subclasses only need to implement [processTransaction].
 */
abstract class TransactionPlugin : BeancountPlugin {

    override fun transform(context: PluginContext): PluginResult {
        val errors = mutableListOf<BeancountError>()
        val newEntries = context.entries.map { entry ->
            if (entry is Transaction) {
                val (newEntry, entryErrors) = processTransaction(entry, context)
                errors.addAll(entryErrors)
                newEntry ?: entry
            } else {
                entry
            }
        }

        return PluginResult(
            entries = newEntries,
            errors = errors
        )
    }

    /**
     * Process a single transaction.
     *
     * @param transaction The transaction to process
     * @param context The plugin context
     * @return Pair of (modified transaction or null to keep original, list of errors)
     */
    abstract fun processTransaction(
        transaction: Transaction,
        context: PluginContext
    ): Pair<Transaction?, List<BeancountError>>
}

/**
 * Abstract base class for plugins that add new entries.
 *
 * Subclasses implement [generateEntries] to produce new directives.
 */
abstract class GeneratorPlugin : BeancountPlugin {

    override fun transform(context: PluginContext): PluginResult {
        val (newEntries, errors) = generateEntries(context)
        val allEntries = (context.entries + newEntries).sorted()

        return PluginResult(
            entries = allEntries,
            errors = errors
        )
    }

    /**
     * Generate new entries to add to the ledger.
     *
     * @param context The plugin context
     * @return Pair of (new entries, list of errors)
     */
    abstract fun generateEntries(
        context: PluginContext
    ): Pair<List<Directive>, List<BeancountError>>
}

/**
 * Abstract base class for filter plugins.
 *
 * Subclasses implement [shouldKeep] to determine which entries to retain.
 */
abstract class FilterPlugin : BeancountPlugin {

    override fun transform(context: PluginContext): PluginResult {
        val errors = mutableListOf<BeancountError>()
        val filteredEntries = context.entries.filter { entry ->
            val (keep, entryErrors) = shouldKeep(entry, context)
            errors.addAll(entryErrors)
            keep
        }

        return PluginResult(
            entries = filteredEntries,
            errors = errors
        )
    }

    /**
     * Determine whether an entry should be kept.
     *
     * @param entry The entry to evaluate
     * @param context The plugin context
     * @return Pair of (keep entry, list of errors)
     */
    abstract fun shouldKeep(
        entry: Directive,
        context: PluginContext
    ): Pair<Boolean, List<BeancountError>>
}
