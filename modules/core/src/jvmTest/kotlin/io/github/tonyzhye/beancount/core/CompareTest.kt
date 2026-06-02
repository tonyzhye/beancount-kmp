package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for compare_entries, hash_entry, and related functions.
 *
 * Based on beancount.core.compare_test.
 */
class CompareTest {

    private val meta1 = newMetadata("test1.beancount", 1)
    private val meta2 = newMetadata("test2.beancount", 2)

    private val tx1 = Transaction(
        meta = meta1,
        date = LocalDate(2023, 1, 15),
        flag = "*",
        payee = "Coffee Shop",
        narration = "Morning coffee",
        tags = setOf("coffee", "food"),
        links = emptySet(),
        postings = listOf(
            Posting(
                account = "Expenses:Food:Coffee",
                units = Amount(Decimal("4.50"), "USD")
            ),
            Posting(
                account = "Assets:Cash",
                units = Amount(Decimal("-4.50"), "USD")
            )
        )
    )

    private val tx1SameContent = Transaction(
        meta = meta2,  // different meta
        date = LocalDate(2023, 1, 15),
        flag = "*",
        payee = "Coffee Shop",
        narration = "Morning coffee",
        tags = setOf("coffee", "food"),
        links = emptySet(),
        postings = listOf(
            Posting(
                account = "Expenses:Food:Coffee",
                units = Amount(Decimal("4.50"), "USD")
            ),
            Posting(
                account = "Assets:Cash",
                units = Amount(Decimal("-4.50"), "USD")
            )
        )
    )

    private val tx2 = Transaction(
        meta = meta1,
        date = LocalDate(2023, 1, 16),
        flag = "*",
        payee = "Grocery Store",
        narration = "Weekly groceries",
        tags = emptySet(),
        links = emptySet(),
        postings = listOf(
            Posting(
                account = "Expenses:Food:Groceries",
                units = Amount(Decimal("45.00"), "USD")
            ),
            Posting(
                account = "Assets:Bank:Checking",
                units = Amount(Decimal("-45.00"), "USD")
            )
        )
    )

    private val open1 = Open(
        meta = meta1,
        date = LocalDate(2023, 1, 1),
        account = "Assets:Cash",
        currencies = listOf("USD"),
        booking = Booking.STRICT
    )

    private val open1Same = Open(
        meta = meta2,  // different meta
        date = LocalDate(2023, 1, 1),
        account = "Assets:Cash",
        currencies = listOf("USD"),
        booking = Booking.STRICT
    )

    @Test
    fun `hashEntry includes meta by default`() {
        val hash1 = hashEntry(tx1)
        val hash2 = hashEntry(tx1SameContent)
        assertNotEquals(hash1, hash2, "Same content with different meta should have different hashes")
    }

    @Test
    fun `hashEntry excludes meta when requested`() {
        val hash1 = hashEntry(tx1, excludeMeta = true)
        val hash2 = hashEntry(tx1SameContent, excludeMeta = true)
        assertEquals(hash1, hash2, "Same content should have same hash when meta is excluded")
    }

    @Test
    fun `hashEntry produces different hashes for different entries`() {
        val hash1 = hashEntry(tx1, excludeMeta = true)
        val hash2 = hashEntry(tx2, excludeMeta = true)
        assertNotEquals(hash1, hash2, "Different entries should have different hashes")
    }

    @Test
    fun `compareEntries returns true for identical entries`() {
        val entries1 = listOf(tx1, open1)
        val entries2 = listOf(tx1SameContent, open1Same)

        val (same, missing1, missing2) = compareEntries(entries1, entries2)

        assertTrue(same, "Entries with same content should be identical")
        assertTrue(missing1.isEmpty(), "There should be no missing from first list")
        assertTrue(missing2.isEmpty(), "There should be no missing from second list")
    }

    @Test
    fun `compareEntries returns false for different entries`() {
        val entries1 = listOf(tx1, open1)
        val entries2 = listOf(tx2, open1)

        val (same, missing1, missing2) = compareEntries(entries1, entries2)

        assertFalse(same, "Different entries should not be identical")
        assertEquals(1, missing1.size, "First list should have one missing entry")
        assertEquals(1, missing2.size, "Second list should have one missing entry")
    }

    @Test
    fun `compareEntries handles empty lists`() {
        val (same, missing1, missing2) = compareEntries(emptyList(), emptyList())

        assertTrue(same, "Two empty lists should be identical")
        assertTrue(missing1.isEmpty())
        assertTrue(missing2.isEmpty())
    }

    @Test
    fun `compareEntries handles one empty list`() {
        val entries1 = listOf(tx1)
        val (same, missing1, missing2) = compareEntries(entries1, emptyList())

        assertFalse(same, "Empty and non-empty lists should differ")
        assertEquals(1, missing1.size, "All entries from first list should be missing")
        assertTrue(missing2.isEmpty(), "Second list should have no missing entries")
    }

    @Test
    fun `hashEntries detects duplicates`() {
        val entries = listOf(tx1, tx1)  // duplicate

        val (hashMap, errors) = hashEntries(entries, excludeMeta = true)

        assertEquals(1, hashMap.size, "Hash map should contain only one unique entry")
        assertEquals(1, errors.size, "Should detect one duplicate")
    }

    @Test
    fun `hashEntries allows duplicate prices`() {
        val price1 = Price(
            meta = meta1,
            date = LocalDate(2023, 1, 15),
            currency = "EUR",
            amount = Amount(Decimal("1.10"), "USD")
        )
        val price2 = Price(
            meta = meta2,
            date = LocalDate(2023, 1, 15),
            currency = "EUR",
            amount = Amount(Decimal("1.10"), "USD")
        )
        val entries = listOf(price1, price2)

        val (hashMap, errors) = hashEntries(entries, excludeMeta = true)

        assertTrue(errors.isEmpty(), "Price duplicates should not be errors")
    }

    @Test
    fun `hashEntries throws on duplicate non-price entries via compareEntries`() {
        val entries1 = listOf(tx1, tx1)
        val entries2 = listOf(tx1)

        assertThrows<IllegalStateException> {
            compareEntries(entries1, entries2)
        }
    }

    @Test
    fun `includesEntries checks subset relationship`() {
        val entries = listOf(tx1, tx2, open1)
        val subset = listOf(tx1, open1)

        val (included, missing) = includesEntries(subset, entries)

        assertTrue(included, "Subset should be included")
        assertTrue(missing.isEmpty(), "There should be no missing entries")
    }

    @Test
    fun `includesEntries detects missing entries`() {
        val entries = listOf(tx1, open1)
        val subset = listOf(tx1, tx2)  // tx2 is not in entries

        val (included, missing) = includesEntries(subset, entries)

        assertFalse(included, "Subset should not be included")
        assertEquals(1, missing.size, "Should report one missing entry")
    }

    @Test
    fun `excludesEntries checks exclusion`() {
        val entries = listOf(tx1, open1)
        val subset = listOf(tx2)

        val (excluded, present) = excludesEntries(subset, entries)

        assertTrue(excluded, "Subset should be excluded from entries")
        assertTrue(present.isEmpty(), "There should be no present entries")
    }

    @Test
    fun `excludesEntries detects present entries`() {
        val entries = listOf(tx1, tx2, open1)
        val subset = listOf(tx1, tx2)

        val (excluded, present) = excludesEntries(subset, entries)

        assertFalse(excluded, "Subset should not be excluded")
        assertEquals(2, present.size, "Should report two present entries")
    }

    @Test
    fun `hashEntry is stable for transactions with tags in different order`() {
        val txWithTags = tx1.copy(tags = setOf("food", "coffee"))

        val hash1 = hashEntry(tx1, excludeMeta = true)
        val hash2 = hashEntry(txWithTags, excludeMeta = true)

        assertEquals(hash1, hash2, "Tag order should not affect hash")
    }

    @Test
    fun `hashEntry is stable for transactions with postings in same order`() {
        // Postings order DOES matter in beancount (it's a list, not a set)
        val txReordered = tx1.copy(
            postings = tx1.postings.reversed()
        )

        val hash1 = hashEntry(tx1, excludeMeta = true)
        val hash2 = hashEntry(txReordered, excludeMeta = true)

        assertNotEquals(hash1, hash2, "Posting order should affect hash")
    }

    @Test
    fun `compareEntries with balance ignores diffAmount`() {
        val balance1 = Balance(
            meta = meta1,
            date = LocalDate(2023, 1, 31),
            account = "Assets:Cash",
            amount = Amount(Decimal("100.00"), "USD"),
            tolerance = null,
            diffAmount = Amount(Decimal("5.00"), "USD")
        )
        val balance2 = Balance(
            meta = meta2,
            date = LocalDate(2023, 1, 31),
            account = "Assets:Cash",
            amount = Amount(Decimal("100.00"), "USD"),
            tolerance = null,
            diffAmount = Amount(Decimal("-3.00"), "USD")  // different diffAmount
        )

        val (same, missing1, missing2) = compareEntries(listOf(balance1), listOf(balance2))

        assertTrue(same, "Balance entries should be equal regardless of diffAmount")
        assertTrue(missing1.isEmpty())
        assertTrue(missing2.isEmpty())
    }

    @Test
    fun `hashEntry produces consistent results`() {
        val hash1 = hashEntry(tx1, excludeMeta = true)
        val hash2 = hashEntry(tx1, excludeMeta = true)

        assertEquals(hash1, hash2, "Same entry should produce same hash")
    }
}
