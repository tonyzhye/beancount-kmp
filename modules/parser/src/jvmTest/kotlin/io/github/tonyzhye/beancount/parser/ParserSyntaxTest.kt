package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for advanced parser syntax features.
 */
class ParserSyntaxTest {

    @Test
    fun `should parse per-unit price with @`() {
        val input = """
            2015-10-02 *
              Assets:Account  10 HOOL {100.00 USD} @ 120.00 USD
              Assets:Other
        """.trimIndent()

        val parser = BeancountParser()
        val result = parser.parseString(input)

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(1, result.entries.size)

        val txn = result.entries[0] as Transaction
        val posting = txn.postings[0]
        assertNotNull(posting.price)
        assertEquals(Decimal("120.00"), posting.price?.number)
        assertEquals("USD", posting.price?.currency)
    }

    @Test
    fun `should parse total price with @@`() {
        val input = """
            2015-10-02 *
              Assets:Account  10 HOOL {100.00 USD} @@ 1200.00 USD
              Assets:Other
        """.trimIndent()

        val parser = BeancountParser()
        val result = parser.parseString(input)

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(1, result.entries.size)

        val txn = result.entries[0] as Transaction
        val posting = txn.postings[0]
        assertNotNull(posting.price)
        // @@ 1200 / 10 = 120 per unit
        assertEquals(Decimal("120"), posting.price?.number)
        assertEquals("USD", posting.price?.currency)
    }

    @Test
    fun `should parse posting metadata`() {
        val input = """
            2015-10-02 *
              Assets:Account  100.00 USD
                memo: "Grocery store"
                category: "Food"
              Assets:Other
        """.trimIndent()

        val parser = BeancountParser()
        val result = parser.parseString(input)

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(1, result.entries.size)

        val txn = result.entries[0] as Transaction
        val posting = txn.postings[0]
        assertNotNull(posting.meta)
        assertEquals("Grocery store", posting.meta?.get("memo"))
        assertEquals("Food", posting.meta?.get("category"))
    }

    @Test
    fun `should parse directive metadata`() {
        val input = """
            2015-10-02 open Assets:Checking USD
              bank: "Chase"
              account_number: "1234567890"
        """.trimIndent()

        val parser = BeancountParser()
        val result = parser.parseString(input)

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(1, result.entries.size)

        val open = result.entries[0] as Open
        assertEquals("Chase", open.meta["bank"])
        assertEquals("1234567890", open.meta["account_number"])
    }

    @Test
    fun `should parse commodity metadata`() {
        val input = """
            2015-10-02 commodity USD
              name: "US Dollar"
              export: "CASH"
        """.trimIndent()

        val parser = BeancountParser()
        val result = parser.parseString(input)

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(1, result.entries.size)

        val commodity = result.entries[0] as Commodity
        assertEquals("US Dollar", commodity.meta["name"])
        assertEquals("CASH", commodity.meta["export"])
    }

    @Test
    fun `should parse transaction metadata`() {
        val input = """
            2015-10-02 * "Shopping"
              merchant: "Whole Foods"
              location: "New York"
              Assets:Account  100.00 USD
              Assets:Other
        """.trimIndent()

        val parser = BeancountParser()
        val result = parser.parseString(input)

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(1, result.entries.size)

        val txn = result.entries[0] as Transaction
        println("Transaction meta keys: ${txn.meta.keys}")
        println("Transaction meta: ${txn.meta}")
        assertEquals("Whole Foods", txn.meta["merchant"])
        assertEquals("New York", txn.meta["location"])
    }

    @Test
    fun `should parse mixed posting with metadata and price`() {
        val input = """
            2015-10-02 *
              Assets:Account  10 HOOL {100.00 USD} @ 120.00 USD
                order_id: "12345"
              Assets:Other
        """.trimIndent()

        val parser = BeancountParser()
        val result = parser.parseString(input)

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(1, result.entries.size)

        val txn = result.entries[0] as Transaction
        val posting = txn.postings[0]
        assertNotNull(posting.price)
        assertEquals(Decimal("120.00"), posting.price?.number)
        assertEquals("USD", posting.price?.currency)
        assertEquals("12345", posting.meta?.get("order_id"))
    }
}
