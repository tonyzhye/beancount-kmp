package io.github.tonyzhye.beancount.core

/**
 * Basic filtering and aggregation operations on lists of entries.
 * Based on beancount.ops.basicops.
 */

/**
 * Filter entries that have the given tag.
 * Based on beancount.ops.basicops.filter_tag.
 *
 * @param tag The tag to filter by.
 * @param entries A list of directive instances.
 * @return A list of entries that have the given tag.
 */
fun filterTag(tag: String, entries: List<Directive>): List<Directive> {
    return entries.filter { entry ->
        when (entry) {
            is Transaction -> tag in entry.tags
            is Note -> entry.tags?.contains(tag) == true
            is Document -> entry.tags?.contains(tag) == true
            else -> false
        }
    }
}

/**
 * Filter entries that have the given link.
 * Based on beancount.ops.basicops.filter_link.
 *
 * @param link The link to filter by.
 * @param entries A list of directive instances.
 * @return A list of entries that have the given link.
 */
fun filterLink(link: String, entries: List<Directive>): List<Directive> {
    return entries.filter { entry ->
        when (entry) {
            is Transaction -> link in entry.links
            is Note -> entry.links?.contains(link) == true
            is Document -> entry.links?.contains(link) == true
            else -> false
        }
    }
}

/**
 * Group entries by link.
 * Based on beancount.ops.basicops.group_entries_by_link.
 *
 * @param entries A list of directive instances.
 * @return A map of link name to list of entries.
 */
fun groupEntriesByLink(entries: List<Directive>): Map<String, List<Directive>> {
    val result = mutableMapOf<String, MutableList<Directive>>()
    for (entry in entries) {
        val links = when (entry) {
            is Transaction -> entry.links
            is Note -> entry.links ?: emptySet()
            is Document -> entry.links ?: emptySet()
            else -> emptySet()
        }
        for (link in links) {
            result.getOrPut(link) { mutableListOf() }.add(entry)
        }
    }
    return result
}

/**
 * Get the set of common accounts between two entries.
 * Based on beancount.ops.basicops.get_common_accounts.
 *
 * @param entry1 A directive instance.
 * @param entry2 A directive instance.
 * @return A set of account names common to both entries.
 */
fun getCommonAccounts(entry1: Directive, entry2: Directive): Set<Account> {
    val accounts1 = getEntryAccounts(entry1)
    val accounts2 = getEntryAccounts(entry2)
    return accounts1.intersect(accounts2)
}

/**
 * Remove all postings with the given account.
 * Based on beancount.core.data.remove_account_postings.
 *
 * @param account The account name whose postings to remove.
 * @param entries A list of directive instances.
 * @return A list of entries without postings for the given account.
 */
fun removeAccountPostings(account: Account, entries: List<Directive>): List<Directive> {
    return entries.map { entry ->
        when (entry) {
            is Transaction -> entry.copy(
                postings = entry.postings.filter { it.account != account }
            )
            else -> entry
        }
    }
}
