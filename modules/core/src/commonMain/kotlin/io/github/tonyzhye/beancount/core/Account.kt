package io.github.tonyzhye.beancount.core

/**
 * Account manipulation utilities.
 *
 * Based on beancount.core.account
 */

/** Account component separator */
const val ACCOUNT_SEPARATOR = ":"

/** Regex for valid account name components */
val ACCOUNT_COMPONENT_TYPE_RE = Regex("[A-Z][A-Za-z0-9\\-]*")
val ACCOUNT_COMPONENT_NAME_RE = Regex("[A-Z0-9][A-Za-z0-9\\-]*")
val ACCOUNT_RE = Regex("(?:[A-Z][A-Za-z0-9\\-]*)(?::[A-Z0-9][A-Za-z0-9\\-]*)+")

/**
 * Check if a string is a valid root account name.
 */
fun isValidRoot(string: String): Boolean {
    return ACCOUNT_COMPONENT_TYPE_RE.matches(string)
}

/**
 * Check if a string is a valid leaf account name.
 */
fun isValidLeaf(string: String): Boolean {
    return string.split(ACCOUNT_SEPARATOR).all { ACCOUNT_COMPONENT_NAME_RE.matches(it) }
}

/**
 * Check if a string is a valid account name.
 */
fun isValidAccount(string: String): Boolean {
    return ACCOUNT_RE.matches(string)
}

/**
 * Join account components with the separator.
 */
fun accountJoin(vararg components: String): Account {
    return components.joinToString(ACCOUNT_SEPARATOR)
}

/**
 * Split an account name into its components.
 */
fun accountSplit(accountName: Account): List<String> {
    return accountName.split(ACCOUNT_SEPARATOR)
}

/**
 * Get the parent account name, or null if at root.
 */
fun accountParent(accountName: Account): Account? {
    if (accountName.isEmpty()) return null
    val components = accountName.split(ACCOUNT_SEPARATOR)
    return components.dropLast(1).joinToString(ACCOUNT_SEPARATOR)
}

/**
 * Get the leaf (last component) of an account name.
 */
fun accountLeaf(accountName: Account): Account? {
    return accountName.split(ACCOUNT_SEPARATOR).lastOrNull()
}

/**
 * Get the account name without the root component.
 */
fun accountSansRoot(accountName: Account): Account? {
    val components = accountName.split(ACCOUNT_SEPARATOR).drop(1)
    return if (components.isNotEmpty()) components.joinToString(ACCOUNT_SEPARATOR) else null
}

/**
 * Get the first N components of an account name.
 */
fun accountRoot(numComponents: Int, accountName: Account): Account {
    return accountName.split(ACCOUNT_SEPARATOR).take(numComponents).joinToString(ACCOUNT_SEPARATOR)
}

/**
 * Check if an account contains a given component.
 */
fun accountHasComponent(accountName: Account, component: String): Boolean {
    return Regex("(^|:)$component(:|$)").containsMatchIn(accountName)
}

/**
 * Find the common prefix of a list of account names.
 */
fun accountCommonPrefix(accounts: Iterable<Account>): Account {
    val lists = accounts.map { it.split(ACCOUNT_SEPARATOR) }
    if (lists.isEmpty()) return ""

    val minLength = lists.minOf { it.size }
    var commonLength = 0

    for (i in 0 until minLength) {
        val component = lists[0][i]
        if (lists.all { it[i] == component }) {
            commonLength++
        } else {
            break
        }
    }

    return lists[0].take(commonLength).joinToString(ACCOUNT_SEPARATOR)
}

/**
 * Build a predicate to check if an account is under a given parent.
 */
fun parentMatcher(accountName: Account): (Account) -> Boolean {
    val regex = Regex("^${Regex.escape(accountName)}($|:${Regex.escape(ACCOUNT_SEPARATOR)})")
    return { account: Account -> regex.containsMatchIn(account) }
}

/**
 * Get all parents of an account, including itself.
 */
fun accountParents(accountName: Account): Sequence<Account> {
    return generateSequence(accountName) { accountParent(it) }
}

/**
 * Account name transformer for filesystem compatibility.
 */
class AccountTransformer(private val replacementSeparator: String? = null) {
    fun render(accountName: Account): String {
        return replacementSeparator?.let { accountName.replace(ACCOUNT_SEPARATOR, it) } ?: accountName
    }

    fun parse(transformedName: String): Account {
        return replacementSeparator?.let { transformedName.replace(it, ACCOUNT_SEPARATOR) } ?: transformedName
    }
}
