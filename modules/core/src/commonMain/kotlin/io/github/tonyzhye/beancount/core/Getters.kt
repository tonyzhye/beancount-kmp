/*
 * Beancount JVM - A JVM implementation of Beancount
 * Copyright (C) 2026  Beancount JVM Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Beancount by Martin Blais
 * Original project: https://github.com/beancount/beancount
 */

package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate

/**
 * Get all transactions from a list of entries.
 * Based on beancount.core.data.filter_txns.
 *
 * @param entries A list of directive instances.
 * @return A list of Transaction directives.
 */
fun getTransactions(entries: List<Directive>): List<Transaction> {
    return entries.filterIsInstance<Transaction>()
}

/**
 * Gather all the accounts referenced by a single directive.
 * Based on beancount.core.getters.get_entry_accounts.
 *
 * @param entry A directive instance.
 * @return A set of account name strings.
 */
fun getEntryAccounts(entry: Directive): Set<Account> {
    return when (entry) {
        is Transaction -> entry.postings.map { it.account }.toSet()
        is Open -> setOf(entry.account)
        is Close -> setOf(entry.account)
        is Balance -> setOf(entry.account)
        is Note -> setOf(entry.account)
        is Document -> setOf(entry.account)
        is Pad -> setOf(entry.account, entry.sourceAccount)
        else -> emptySet()
    }
}

/**
 * Gather all the accounts referenced by a list of directives,
 * along with their first and last use dates.
 * Based on beancount.core.getters.get_accounts_use_map.
 *
 * @param entries A list of directive instances.
 * @return A pair of maps (first_date, last_date) from account to date.
 */
fun getAccountsUseMap(entries: List<Directive>): Pair<Map<Account, LocalDate>, Map<Account, LocalDate>> {
    val accountsFirst = mutableMapOf<Account, LocalDate>()
    val accountsLast = mutableMapOf<Account, LocalDate>()

    for (entry in entries) {
        val accounts = getEntryAccounts(entry)
        for (account in accounts) {
            if (account !in accountsFirst) {
                accountsFirst[account] = entry.date
            }
            accountsLast[account] = entry.date
        }
    }

    return accountsFirst to accountsLast
}

/**
 * Gather all the accounts referenced by a list of directives.
 * Based on beancount.core.getters.get_accounts.
 *
 * @param entries A list of directive instances.
 * @return A set of account strings.
 */
fun getAccounts(entries: List<Directive>): Set<Account> {
    val (_, accountsLast) = getAccountsUseMap(entries)
    return accountsLast.keys
}

/**
 * Fetch the open/close entries for each of the accounts.
 * Based on beancount.core.getters.get_account_open_close.
 *
 * If an open or close entry happens to be duplicated, the earliest
 * entry (chronologically) is accepted.
 *
 * @param entries A list of directive instances.
 * @return A map of account name strings to pairs of (open-directive, close-directive).
 */
fun getAccountOpenClose(entries: List<Directive>): Map<Account, Pair<Open?, Close?>> {
    val openCloseMap = mutableMapOf<Account, Pair<Open?, Close?>>()

    for (entry in entries) {
        when (entry) {
            is Open -> {
                val (existingOpen, existingClose) = openCloseMap[entry.account] ?: (null to null)
                val selectedOpen = if (existingOpen != null && existingOpen.date <= entry.date) {
                    existingOpen
                } else {
                    entry
                }
                openCloseMap[entry.account] = selectedOpen to existingClose
            }
            is Close -> {
                val (existingOpen, existingClose) = openCloseMap[entry.account] ?: (null to null)
                val selectedClose = if (existingClose != null && existingClose.date <= entry.date) {
                    existingClose
                } else {
                    entry
                }
                openCloseMap[entry.account] = existingOpen to selectedClose
            }
            else -> {}
        }
    }

    return openCloseMap
}

/**
 * Return a list of all the tags seen in the given entries.
 * Based on beancount.core.getters.get_all_tags.
 *
 * @param entries A list of directive instances.
 * @return A sorted list of tag strings.
 */
fun getAllTags(entries: List<Directive>): List<String> {
    val allTags = mutableSetOf<String>()
    for (entry in entries) {
        if (entry is Transaction && entry.tags.isNotEmpty()) {
            allTags.addAll(entry.tags)
        }
    }
    return allTags.sorted()
}

/**
 * Return a list of all the unique payees seen in the given entries.
 * Based on beancount.core.getters.get_all_payees.
 *
 * @param entries A list of directive instances.
 * @return A sorted list of payee strings.
 */
fun getAllPayees(entries: List<Directive>): List<String> {
    val allPayees = mutableSetOf<String>()
    for (entry in entries) {
        if (entry is Transaction) {
            entry.payee?.let { allPayees.add(it) }
        }
    }
    return allPayees.sorted()
}

/**
 * Return a list of all the links seen in the given entries.
 * Based on beancount.core.getters.get_all_links.
 *
 * @param entries A list of directive instances.
 * @return A sorted list of link strings.
 */
fun getAllLinks(entries: List<Directive>): List<String> {
    val allLinks = mutableSetOf<String>()
    for (entry in entries) {
        if (entry is Transaction && entry.links.isNotEmpty()) {
            allLinks.addAll(entry.links)
        }
    }
    return allLinks.sorted()
}

/**
 * Create map of commodity names to Commodity entries.
 * Based on beancount.core.getters.get_commodity_directives.
 *
 * @param entries A list of directive instances.
 * @return A map of commodity name strings to Commodity directives.
 */
fun getCommodityDirectives(entries: List<Directive>): Map<Currency, Commodity> {
    return entries.filterIsInstance<Commodity>()
        .associateBy { it.currency }
}

/**
 * Return the minimum and maximum dates in the list of entries.
 * Based on beancount.core.getters.get_min_max_dates.
 *
 * @param entries A list of directive instances.
 * @param types An optional set of types to restrict the entries to.
 * @return A pair of dates (minimum, maximum), or nulls if no matching entries.
 */
fun getMinMaxDates(
    entries: List<Directive>,
    types: Set<Class<out Directive>>? = null
): Pair<LocalDate?, LocalDate?> {
    val filtered = if (types != null) {
        entries.filter { types.any { t -> t.isInstance(it) } }
    } else entries

    if (filtered.isEmpty()) {
        return null to null
    }

    return filtered.first().date to filtered.last().date
}

/**
 * Return all the years that have at least one entry in them.
 * Based on beancount.core.getters.get_active_years.
 *
 * @param entries A list of directive instances.
 * @return A list of unique years seen in the directives.
 */
fun getActiveYears(entries: List<Directive>): List<Int> {
    return entries.map { it.date.year }.distinct()
}

/**
 * Gather all the account components available in the given directives.
 * Based on beancount.core.getters.get_account_components.
 *
 * @param entries A list of directive instances.
 * @return A sorted list of unique account components.
 */
fun getAccountComponents(entries: List<Directive>): List<String> {
    val accounts = getAccounts(entries)
    val components = mutableSetOf<String>()
    for (accountName in accounts) {
        components.addAll(accountName.split(":"))
    }
    return components.sorted()
}

/**
 * Get level-N parent accounts.
 * Based on beancount.core.getters.get_leveln_parent_accounts.
 *
 * @param accountNames A list of account name strings.
 * @param level The level of parent to extract (1 = first component).
 * @param nrepeats Number of times a component must repeat to be included.
 * @return A list of parent account names at the given level.
 */
fun getLevelNParentAccounts(
    accountNames: List<Account>,
    level: Int,
    nrepeats: Int = 0
): List<Account> {
    val parents = mutableListOf<Account>()
    val counts = mutableMapOf<Account, Int>()

    for (account in accountNames) {
        val parts = account.split(":")
        if (parts.size >= level) {
            val parent = parts.take(level).joinToString(":")
            counts[parent] = counts.getOrDefault(parent, 0) + 1
            if (counts[parent]!! > nrepeats) {
                parents.add(parent)
            }
        }
    }

    return parents.distinct()
}

/**
 * Get the leaf accounts from a list of account names.
 * Leaf accounts are those that are not parents of any other account.
 *
 * @param accountNames A list of account name strings.
 * @return A list of leaf account names.
 */
fun getLeafAccounts(accountNames: List<Account>): List<Account> {
    val accountSet = accountNames.toSet()
    return accountNames.filter { account ->
        // An account is a leaf if no other account starts with "account:"
        accountSet.none { it.startsWith("$account:") }
    }
}
