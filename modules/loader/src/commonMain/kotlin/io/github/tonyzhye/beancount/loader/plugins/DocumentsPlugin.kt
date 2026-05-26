package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*

/**
 * Documents plugin - processes document declarations.
 * Based on beancount.ops.documents.
 *
 * This is a simplified implementation that validates document paths
 * and ensures documents are associated with valid accounts.
 */
object DocumentsPlugin {

    /**
     * Plugin entry point.
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()
        val documents = entries.filterIsInstance<Document>()

        // Collect all open accounts
        val openAccounts = entries.filterIsInstance<Open>().map { it.account }.toSet()

        for (document in documents) {
            // Check that the account is open
            if (document.account !in openAccounts) {
                errors.add(
                    ValidationError(
                        document.meta,
                        "Document references unopened account '${document.account}'",
                        document
                    )
                )
            }

            // Check that the file path is not empty
            if (document.filename.isBlank()) {
                errors.add(
                    ValidationError(
                        document.meta,
                        "Document has empty filename",
                        document
                    )
                )
            }
        }

        return Pair(entries, errors)
    }
}
