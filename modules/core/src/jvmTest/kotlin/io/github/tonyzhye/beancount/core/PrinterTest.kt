package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Printer formatting functions.
 */
class PrinterTest {

    private fun createMeta(filename: String = "test.bean", lineno: Int = 1) = mapOf(
        "filename" to filename,
        "lineno" to lineno
    )

    @Test
    fun `renderSource should format filename and line number`() {
        val meta = createMeta("ledger.bean", 42)
        val result = renderSource(meta)
        assertEquals("ledger.bean:42:", result)
    }

    @Test
    fun `renderSource should handle missing values`() {
        val meta = emptyMap<String, Any>()
        val result = renderSource(meta)
        assertEquals(":0:", result)
    }

    @Test
    fun `formatError should format error without entry`() {
        val error = ValidationError(
            createMeta("test.bean", 10),
            "Something went wrong"
        )
        val result = formatError(error)
        assertEquals("test.bean:10: Something went wrong\n", result)
    }

    @Test
    fun `formatError should format error with entry`() {
        val entry = Open(createMeta("test.bean", 5), LocalDate(2023, 1, 1), "Assets:Cash")
        val error = ValidationError(
            createMeta("test.bean", 10),
            "Duplicate open",
            entry
        )
        val result = formatError(error)
        assertTrue(result.contains("test.bean:10: Duplicate open"))
        assertTrue(result.contains("2023-01-01 open Assets:Cash"))
    }

    @Test
    fun `formatEntry should format Open directive`() {
        val entry = Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", listOf("USD", "EUR"))
        val result = formatEntry(entry)
        assertEquals("2023-01-01 open Assets:Cash USD,EUR\n", result)
    }

    @Test
    fun `formatEntry should format Open with booking`() {
        val entry = Open(createMeta(), LocalDate(2023, 1, 1), "Assets:Invest", listOf("USD"), Booking.STRICT)
        val result = formatEntry(entry)
        assertEquals("2023-01-01 open Assets:Invest USD \"STRICT\"\n", result)
    }

    @Test
    fun `formatEntry should format Close directive`() {
        val entry = Close(createMeta(), LocalDate(2023, 12, 31), "Assets:Cash")
        val result = formatEntry(entry)
        assertEquals("2023-12-31 close Assets:Cash\n", result)
    }

    @Test
    fun `formatEntry should format Commodity directive`() {
        val entry = Commodity(createMeta(), LocalDate(2023, 1, 1), "USD")
        val result = formatEntry(entry)
        assertEquals("2023-01-01 commodity USD\n", result)
    }

    @Test
    fun `formatEntry should format Balance directive`() {
        val entry = Balance(createMeta(), LocalDate(2023, 1, 15), "Assets:Cash", Amount(Decimal("100.50"), "USD"))
        val result = formatEntry(entry)
        assertEquals("2023-01-15 balance Assets:Cash 100.50 USD\n", result)
    }

    @Test
    fun `formatEntry should format Balance with tolerance`() {
        val entry = Balance(
            createMeta(), LocalDate(2023, 1, 15), "Assets:Cash",
            Amount(Decimal("100"), "USD"),
            tolerance = Decimal("0.01")
        )
        val result = formatEntry(entry)
        assertEquals("2023-01-15 balance Assets:Cash 100 ~ 0.01 USD\n", result)
    }

    @Test
    fun `formatEntry should format Price directive`() {
        val entry = Price(createMeta(), LocalDate(2023, 1, 1), "USD", Amount(Decimal("1.25"), "CAD"))
        val result = formatEntry(entry)
        assertEquals("2023-01-01 price USD 1.25 CAD\n", result)
    }

    @Test
    fun `formatEntry should format Note directive`() {
        val entry = Note(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", "Opening balance")
        val result = formatEntry(entry)
        assertEquals("2023-01-01 note Assets:Cash \"Opening balance\"\n", result)
    }

    @Test
    fun `formatEntry should format Document directive`() {
        val entry = Document(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", "/path/to/doc.pdf")
        val result = formatEntry(entry)
        assertEquals("2023-01-01 document Assets:Cash \"/path/to/doc.pdf\"\n", result)
    }

    @Test
    fun `formatEntry should format Pad directive`() {
        val entry = Pad(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", "Equity:OpeningBalances")
        val result = formatEntry(entry)
        assertEquals("2023-01-01 pad Assets:Cash Equity:OpeningBalances\n", result)
    }

    @Test
    fun `formatEntry should format Event directive`() {
        val entry = Event(createMeta(), LocalDate(2023, 1, 1), "location", "New York")
        val result = formatEntry(entry)
        assertEquals("2023-01-01 event \"location\" \"New York\"\n", result)
    }

    @Test
    fun `formatEntry should format Query directive`() {
        val entry = Query(createMeta(), LocalDate(2023, 1, 1), "balances", "SELECT *")
        val result = formatEntry(entry)
        assertEquals("2023-01-01 query \"balances\" \"SELECT *\"\n", result)
    }

    @Test
    fun `formatEntry should format Transaction with postings`() {
        val entry = Transaction(
            createMeta(),
            LocalDate(2023, 1, 15),
            "*",
            payee = "Grocery Store",
            narration = "Weekly shopping",
            postings = listOf(
                Posting("Expenses:Food", Amount(Decimal("50.00"), "USD")),
                Posting("Assets:Cash", Amount(Decimal("-50.00"), "USD"))
            )
        )
        val result = formatEntry(entry)
        assertTrue(result.contains("2023-01-15 * \"Grocery Store\" \"Weekly shopping\""))
        assertTrue(result.contains("Expenses:Food"))
        assertTrue(result.contains("50.00 USD"))
        assertTrue(result.contains("Assets:Cash"))
        assertTrue(result.contains("-50.00 USD"))
    }

    @Test
    fun `formatEntry should format Transaction without payee`() {
        val entry = Transaction(
            createMeta(),
            LocalDate(2023, 1, 15),
            "*",
            narration = "Simple transaction",
            postings = listOf(
                Posting("Expenses:Food", Amount(Decimal("50.00"), "USD")),
                Posting("Assets:Cash")
            )
        )
        val result = formatEntry(entry)
        assertTrue(result.contains("2023-01-15 * \"Simple transaction\""))
    }

    @Test
    fun `formatEntry should format Transaction with tags and links`() {
        val entry = Transaction(
            createMeta(),
            LocalDate(2023, 1, 15),
            "!",
            narration = "Important",
            tags = setOf("tag1", "tag2"),
            links = setOf("link1"),
            postings = listOf(
                Posting("Expenses:Food", Amount(Decimal("100"), "USD"))
            )
        )
        val result = formatEntry(entry)
        assertTrue(result.contains("^link1"))
        assertTrue(result.contains("#tag1"))
        assertTrue(result.contains("#tag2"))
    }

    @Test
    fun `formatEntry should format posting with cost`() {
        val entry = Transaction(
            createMeta(),
            LocalDate(2023, 1, 15),
            "*",
            narration = "Buy stock",
            postings = listOf(
                Posting(
                    "Assets:Invest",
                    Amount(Decimal("10"), "AAPL"),
                    cost = Cost(Decimal("150.00"), "USD")
                ),
                Posting("Assets:Cash", Amount(Decimal("-1500.00"), "USD"))
            )
        )
        val result = formatEntry(entry)
        assertTrue(result.contains("10 AAPL {150.00 USD}"))
    }

    @Test
    fun `formatEntry should format posting with price`() {
        val entry = Transaction(
            createMeta(),
            LocalDate(2023, 1, 15),
            "*",
            narration = "Exchange",
            postings = listOf(
                Posting(
                    "Assets:EUR",
                    Amount(Decimal("100"), "EUR"),
                    price = Amount(Decimal("1.10"), "USD")
                )
            )
        )
        val result = formatEntry(entry)
        assertTrue(result.contains("@ 1.10 USD"))
    }

    @Test
    fun `formatEntry should format Custom directive`() {
        val entry = Custom(
            createMeta(),
            LocalDate(2023, 1, 1),
            "budget",
            listOf("Assets:Cash", Decimal("1000"), "USD")
        )
        val result = formatEntry(entry)
        assertEquals("2023-01-01 custom \"budget\" \"Assets:Cash\" 1000 \"USD\"\n", result)
    }

    @Test
    fun `formatEntries should insert blank lines between transactions`() {
        val entries = listOf(
            Transaction(createMeta(lineno = 1), LocalDate(2023, 1, 1), "*", narration = "First", postings = emptyList()),
            Transaction(createMeta(lineno = 2), LocalDate(2023, 1, 2), "*", narration = "Second", postings = emptyList())
        )
        val result = formatEntries(entries)
        // Should have blank line between transactions
        assertTrue(result.contains("\n\n2023-01-02"))
    }

    @Test
    fun `formatEntries should insert blank lines between different directive types`() {
        val entries = listOf(
            Open(createMeta(lineno = 1), LocalDate(2023, 1, 1), "Assets:Cash"),
            Close(createMeta(lineno = 2), LocalDate(2023, 12, 31), "Assets:Cash")
        )
        val result = formatEntries(entries)
        assertTrue(result.contains("\n\n2023-12-31"))
    }

    @Test
    fun `formatEntries should return empty string for empty list`() {
        val result = formatEntries(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `formatErrors should format multiple errors`() {
        val errors = listOf(
            ValidationError(createMeta("a.bean", 1), "Error one"),
            ValidationError(createMeta("b.bean", 2), "Error two")
        )
        val result = formatErrors(errors)
        assertTrue(result.contains("a.bean:1: Error one"))
        assertTrue(result.contains("b.bean:2: Error two"))
    }

    @Test
    fun `formatEntry should escape quotes in strings`() {
        val entry = Note(createMeta(), LocalDate(2023, 1, 1), "Assets:Cash", "Say \"hello\"")
        val result = formatEntry(entry)
        assertTrue(result.contains("Say \\\"hello\\\""))
    }

    @Test
    fun `formatEntry should include metadata`() {
        val meta = mapOf(
            "filename" to "test.bean",
            "lineno" to 1,
            "location" to "New York"
        )
        val entry = Open(meta, LocalDate(2023, 1, 1), "Assets:Cash")
        val result = formatEntry(entry)
        assertTrue(result.contains("  location: \"New York\""))
        assertTrue(!result.contains("filename"))
        assertTrue(!result.contains("lineno"))
    }
}
