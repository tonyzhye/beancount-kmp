package io.github.tonyzhye.beancount.core

/**
 * Compute a stable hash for a single directive.
 *
 * Based on beancount.core.compare.hash_entry
 *
 * @param entry The directive to hash.
 * @param excludeMeta If true, ignores the `meta` and `diffAmount` fields.
 *   This is useful for comparing entries from different sources where
 *   only the file location (metadata) differs.
 * @return A 32-character hexadecimal MD5 hash string.
 */
fun hashEntry(entry: Directive, excludeMeta: Boolean = false): String {
    return stableHash(entry, excludeMeta)
}

/**
 * Compute stable hashes for a list of entries.
 *
 * Based on beancount.core.compare.hash_entries
 *
 * @param entries List of directives to hash.
 * @param excludeMeta If true, ignores metadata fields.
 * @return Pair of (hash map, list of errors).
 *   The hash map maps hash strings to their corresponding entries.
 *   Errors list contains entries that had duplicate hashes (except Price entries).
 */
fun hashEntries(
    entries: List<Directive>,
    excludeMeta: Boolean = false
): Pair<Map<String, Directive>, List<Directive>> {
    val hashMap = mutableMapOf<String, Directive>()
    val errors = mutableListOf<Directive>()

    for (entry in entries) {
        val hash = hashEntry(entry, excludeMeta)
        if (hashMap.containsKey(hash)) {
            // Price entries are allowed to have duplicates
            if (entry !is Price) {
                errors.add(entry)
            }
        } else {
            hashMap[hash] = entry
        }
    }

    return hashMap to errors
}

/**
 * Compare two lists of entries for equality, ignoring metadata.
 *
 * Based on beancount.core.compare.compare_entries
 *
 * This is the main entry point for comparing two ledgers. It computes
 * hashes for all entries (ignoring metadata like filename and line number)
 * and compares the sets.
 *
 * @param entries1 First list of directives.
 * @param entries2 Second list of directives.
 * @return Triple of (same, missing1, missing2):
 *   - same: true if both lists contain exactly the same entries.
 *   - missing1: entries present in entries1 but not in entries2.
 *   - missing2: entries present in entries2 but not in entries1.
 * @throws IllegalStateException if there are duplicate non-Price entries.
 */
fun compareEntries(
    entries1: List<Directive>,
    entries2: List<Directive>
): Triple<Boolean, List<Directive>, List<Directive>> {
    val (hashes1, errors1) = hashEntries(entries1, excludeMeta = true)
    val (hashes2, errors2) = hashEntries(entries2, excludeMeta = true)

    if (errors1.isNotEmpty() || errors2.isNotEmpty()) {
        val firstError = (errors1 + errors2).first()
        throw IllegalStateException("Duplicate entry found: $firstError")
    }

    val keys1 = hashes1.keys
    val keys2 = hashes2.keys

    val same = keys1 == keys2

    val missing1 = keys1.subtract(keys2)
        .map { hashes1.getValue(it) }
        .sorted()

    val missing2 = keys2.subtract(keys1)
        .map { hashes2.getValue(it) }
        .sorted()

    return Triple(same, missing1, missing2)
}

/**
 * Check if all entries in subset are present in entries.
 *
 * Based on beancount.core.compare.includes_entries
 *
 * @param subsetEntries The subset to check.
 * @param entries The full list of entries.
 * @return Pair of (included, missing) where missing are entries from
 *   subsetEntries not found in entries.
 */
fun includesEntries(
    subsetEntries: List<Directive>,
    entries: List<Directive>
): Pair<Boolean, List<Directive>> {
    val (subsetHashes, _) = hashEntries(subsetEntries, excludeMeta = true)
    val (entryHashes, _) = hashEntries(entries, excludeMeta = true)

    val missing = subsetHashes.keys
        .subtract(entryHashes.keys)
        .map { subsetHashes.getValue(it) }
        .sorted()

    return (missing.isEmpty()) to missing
}

/**
 * Check if none of the entries in subset are present in entries.
 *
 * Based on beancount.core.compare.excludes_entries
 *
 * @param subsetEntries The subset to check.
 * @param entries The full list of entries.
 * @return Pair of (excluded, present) where present are entries from
 *   subsetEntries found in entries.
 */
fun excludesEntries(
    subsetEntries: List<Directive>,
    entries: List<Directive>
): Pair<Boolean, List<Directive>> {
    val (subsetHashes, _) = hashEntries(subsetEntries, excludeMeta = true)
    val (entryHashes, _) = hashEntries(entries, excludeMeta = true)

    val present = subsetHashes.keys
        .intersect(entryHashes.keys)
        .map { subsetHashes.getValue(it) }
        .sorted()

    return (present.isEmpty()) to present
}

// ----------------------------------------------------------------------
// Private implementation
// ----------------------------------------------------------------------

/**
 * Compute a stable hash for any value.
 *
 * This recursively handles:
 * - Directive types: manually serializes all fields
 * - Posting: manually serializes all fields
 * - Collections (List, Set): hashes each element, sorts hashes, combines
 * - Maps: converts to sorted key-value pairs, hashes each
 * - All other values: uses toString() for serialization
 */
private fun stableHash(value: Any?, excludeMeta: Boolean): String {
    if (value == null) {
        return md5Hash("null")
    }

    return when (value) {
        is Directive -> hashDirective(value, excludeMeta)
        is Posting -> hashPosting(value, excludeMeta)
        is Collection<*> -> hashCollection(value, excludeMeta)
        is Map<*, *> -> hashMap(value, excludeMeta)
        else -> md5Hash(value.toString())
    }
}

/**
 * Manually serialize a Directive to a stable string and hash it.
 *
 * We manually handle each directive type to ensure field order and
 * completeness matches Python's NamedTuple _fields order.
 */
private fun hashDirective(entry: Directive, excludeMeta: Boolean): String {
    val parts = mutableListOf<String>()

    when (entry) {
        is Transaction -> {
            parts.add("Transaction")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("flag=${entry.flag}")
            parts.add("payee=${entry.payee}")
            parts.add("narration=${entry.narration}")
            parts.add("tags=${entry.tags.sorted()}")
            parts.add("links=${entry.links.sorted()}")
            parts.add("postings=[${entry.postings.joinToString(",") { stableHash(it, excludeMeta) }}]")
        }
        is Open -> {
            parts.add("Open")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("account=${entry.account}")
            parts.add("currencies=${entry.currencies.sorted()}")
            parts.add("booking=${entry.booking}")
        }
        is Close -> {
            parts.add("Close")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("account=${entry.account}")
        }
        is Commodity -> {
            parts.add("Commodity")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("currency=${entry.currency}")
        }
        is Pad -> {
            parts.add("Pad")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("account=${entry.account}")
            parts.add("sourceAccount=${entry.sourceAccount}")
        }
        is Balance -> {
            parts.add("Balance")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("account=${entry.account}")
            parts.add("amount=${entry.amount}")
            parts.add("tolerance=${entry.tolerance}")
            // diffAmount is always ignored (not part of identity)
        }
        is Note -> {
            parts.add("Note")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("account=${entry.account}")
            parts.add("comment=${entry.comment}")
            parts.add("tags=${entry.tags?.sorted()}")
            parts.add("links=${entry.links?.sorted()}")
        }
        is Event -> {
            parts.add("Event")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("type=${entry.type}")
            parts.add("description=${entry.description}")
        }
        is Query -> {
            parts.add("Query")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("name=${entry.name}")
            parts.add("queryString=${entry.queryString}")
        }
        is Price -> {
            parts.add("Price")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("currency=${entry.currency}")
            parts.add("amount=${entry.amount}")
        }
        is Document -> {
            parts.add("Document")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("account=${entry.account}")
            parts.add("filename=${entry.filename}")
            parts.add("tags=${entry.tags?.sorted()}")
            parts.add("links=${entry.links?.sorted()}")
        }
        is Custom -> {
            parts.add("Custom")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("type=${entry.type}")
            parts.add("values=${entry.values}")
        }
        is Include -> {
            parts.add("Include")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("filename=${entry.filename}")
        }
        is PushTag -> {
            parts.add("PushTag")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("tag=${entry.tag}")
        }
        is PopTag -> {
            parts.add("PopTag")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("tag=${entry.tag}")
        }
        is PushMeta -> {
            parts.add("PushMeta")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("key=${entry.key}")
            parts.add("value=${entry.value}")
        }
        is PopMeta -> {
            parts.add("PopMeta")
            if (!excludeMeta) parts.add("meta=${entry.meta}")
            parts.add("date=${entry.date}")
            parts.add("key=${entry.key}")
        }
    }

    return md5Hash(parts.joinToString(";"))
}

/**
 * Serialize a Posting to a stable hash.
 */
private fun hashPosting(posting: Posting, excludeMeta: Boolean): String {
    val parts = mutableListOf<String>()
    parts.add("Posting")
    parts.add("account=${posting.account}")
    parts.add("units=${posting.units}")
    parts.add("cost=${posting.cost}")
    parts.add("price=${posting.price}")
    parts.add("flag=${posting.flag}")
    if (!excludeMeta) parts.add("meta=${posting.meta}")
    return md5Hash(parts.joinToString(";"))
}

/**
 * Hash a collection by hashing each element, sorting the hashes,
 * and combining them. This ensures order-independent hashing for sets.
 */
private fun hashCollection(collection: Collection<*>, excludeMeta: Boolean): String {
    val elementHashes = collection.map { stableHash(it, excludeMeta) }
    val sortedHashes = elementHashes.sorted()
    val combined = sortedHashes.joinToString("")
    return md5Hash(combined)
}

/**
 * Hash a map by converting to sorted key-value pairs.
 */
private fun hashMap(map: Map<*, *>, excludeMeta: Boolean): String {
    val entries = map.entries
        .sortedBy { it.key.toString() }
        .map { "${it.key}=${stableHash(it.value, excludeMeta)}" }
    return md5Hash(entries.joinToString(";"))
}
