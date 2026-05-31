package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Compress module.
 */
class CompressTest {

    private fun createTransaction(
        date: LocalDate,
        narration: String,
        postings: List<Posting>
    ): Transaction {
        return Transaction(
            meta = emptyMap(),
            date = date,
            flag = "*",
            narration = narration,
            postings = postings
        )
    }

    @Test
    fun `compress should merge consecutive matching transactions`() {
        val entries = listOf(
            createTransaction(
                LocalDate(2024, 1, 1), "Interest",
                listOf(
                    Posting("Assets:Bank", Amount(Decimal("0.01"), "USD")),
                    Posting("Income:Interest", Amount(Decimal("-0.01"), "USD"))
                )
            ),
            createTransaction(
                LocalDate(2024, 1, 2), "Interest",
                listOf(
                    Posting("Assets:Bank", Amount(Decimal("0.02"), "USD")),
                    Posting("Income:Interest", Amount(Decimal("-0.02"), "USD"))
                )
            ),
            createTransaction(
                LocalDate(2024, 1, 3), "Other",
                listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            )
        )

        val predicate: (Transaction) -> Boolean = { it.narration == "Interest" }
        val result = Compress.compress(entries, predicate)

        assertEquals(2, result.size)
        val compressed = result[0] as Transaction
        assertEquals(2, compressed.postings.size)

        // Check merged amounts
        val bankPosting = compressed.postings.find { it.account == "Assets:Bank" }
        assertEquals(Decimal("0.03"), bankPosting?.units?.number)

        val interestPosting = compressed.postings.find { it.account == "Income:Interest" }
        assertEquals(Decimal("-0.03"), interestPosting?.units?.number)
    }

    @Test
    fun `compress should not merge non-matching transactions`() {
        val entries = listOf(
            createTransaction(
                LocalDate(2024, 1, 1), "Interest",
                listOf(
                    Posting("Assets:Bank", Amount(Decimal("0.01"), "USD")),
                    Posting("Income:Interest", Amount(Decimal("-0.01"), "USD"))
                )
            ),
            createTransaction(
                LocalDate(2024, 1, 2), "Other",
                listOf(
                    Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                    Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                )
            ),
            createTransaction(
                LocalDate(2024, 1, 3), "Interest",
                listOf(
                    Posting("Assets:Bank", Amount(Decimal("0.02"), "USD")),
                    Posting("Income:Interest", Amount(Decimal("-0.02"), "USD"))
                )
            )
        )

        val predicate: (Transaction) -> Boolean = { it.narration == "Interest" }
        val result = Compress.compress(entries, predicate)

        assertEquals(3, result.size)
    }

    @Test
    fun `compress should handle empty list`() {
        val result = Compress.compress(emptyList()) { true }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `compress should preserve non-transaction entries`() {
        val entries = listOf(
            Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank"),
            createTransaction(
                LocalDate(2024, 1, 1), "Interest",
                listOf(
                    Posting("Assets:Bank", Amount(Decimal("0.01"), "USD")),
                    Posting("Income:Interest", Amount(Decimal("-0.01"), "USD"))
                )
            ),
            createTransaction(
                LocalDate(2024, 1, 2), "Interest",
                listOf(
                    Posting("Assets:Bank", Amount(Decimal("0.02"), "USD")),
                    Posting("Income:Interest", Amount(Decimal("-0.02"), "USD"))
                )
            ),
            Close(emptyMap(), LocalDate(2024, 1, 3), "Assets:Bank")
        )

        val predicate: (Transaction) -> Boolean = { it.narration == "Interest" }
        val result = Compress.compress(entries, predicate)

        assertEquals(3, result.size)
        assertTrue(result[0] is Open)
        assertTrue(result[1] is Transaction)
        assertTrue(result[2] is Close)
    }

    @Test
    fun `merge should aggregate postings correctly`() {
        val txn1 = createTransaction(
            LocalDate(2024, 1, 1), "Test",
            listOf(
                Posting("Assets:A", Amount(Decimal("10"), "USD")),
                Posting("Assets:B", Amount(Decimal("20"), "EUR"))
            )
        )
        val txn2 = createTransaction(
            LocalDate(2024, 1, 2), "Test",
            listOf(
                Posting("Assets:A", Amount(Decimal("5"), "USD")),
                Posting("Assets:C", Amount(Decimal("30"), "USD"))
            )
        )

        val result = Compress.merge(listOf(txn1, txn2), txn2)

        assertEquals(3, result.postings.size)
        assertEquals(LocalDate(2024, 1, 2), result.date)

        val postingA = result.postings.find { it.account == "Assets:A" }
        assertEquals(Decimal("15"), postingA?.units?.number)
        assertEquals("USD", postingA?.units?.currency)

        val postingB = result.postings.find { it.account == "Assets:B" }
        assertEquals(Decimal("20"), postingB?.units?.number)
        assertEquals("EUR", postingB?.units?.currency)

        val postingC = result.postings.find { it.account == "Assets:C" }
        assertEquals(Decimal("30"), postingC?.units?.number)
        assertEquals("USD", postingC?.units?.currency)
    }
}
