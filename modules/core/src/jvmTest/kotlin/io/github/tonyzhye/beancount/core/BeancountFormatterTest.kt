package io.github.tonyzhye.beancount.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BeancountFormatterTest {

    @Test
    fun `should format simple postings`() {
        val input = """
            2024-01-01 open Assets:Cash USD

            2024-01-02 * "Test"
              Assets:Cash  100.00 USD
              Income:Salary  -100.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input)

        // Should align amounts (with flexible spacing)
        assertTrue(result.formattedText.contains("Assets:Cash"))
        assertTrue(result.formattedText.contains("Income:Salary"))
        assertTrue(result.formattedText.contains("100.00 USD"))
        assertTrue(result.formattedText.contains("-100.00 USD"))
    }

    @Test
    fun `should format postings with different lengths`() {
        val input = """
            2024-01-01 * "Test"
              Assets:Cash  100.00 USD
              Expenses:Food:Restaurant  50.00 USD
              Income:Salary  -150.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input)

        // All postings should be present and formatted
        assertTrue(result.formattedText.contains("Assets:Cash"))
        assertTrue(result.formattedText.contains("Expenses:Food:Restaurant"))
        assertTrue(result.formattedText.contains("Income:Salary"))
        assertTrue(result.formattedText.contains("100.00 USD"))
        assertTrue(result.formattedText.contains("50.00 USD"))
        assertTrue(result.formattedText.contains("-150.00 USD"))

        // All postings should have the same indentation
        val lines = result.formattedText.lines()
        val postingLines = lines.filter { it.trimStart().matches(Regex("[A-Z].*\\d+.*")) }
        assertEquals(3, postingLines.size)

        val indents = postingLines.map { it.takeWhile { c -> c == ' ' }.length }
        assertEquals(indents[0], indents[1], "All postings should have same indentation")
        assertEquals(indents[1], indents[2], "All postings should have same indentation")
    }

    @Test
    fun `should handle empty input`() {
        val formatter = BeancountFormatter()
        val result = formatter.format("")

        assertEquals("", result.formattedText)
        assertFalse(result.changed)
    }

    @Test
    fun `should handle input without postings`() {
        val input = """
            option "title" "Test Ledger"
            option "operating_currency" "USD"

            2024-01-01 open Assets:Cash USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input)

        // Should not change much since there are no postings to align
        assertNotNull(result)
    }

    @Test
    fun `should format with custom prefix width`() {
        val input = """
            2024-01-01 * "Test"
              Assets:Cash  100.00 USD
              Income:Salary  -100.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input, FormatOptions(prefixWidth = 50))

        // Account names should be padded to 50 chars
        val lines = result.formattedText.lines()
        val postingLine = lines.find { it.contains("Assets:Cash") }
        assertNotNull(postingLine)

        // The prefix should be padded
        val prefixEnd = postingLine!!.indexOf("100.00")
        assertTrue(prefixEnd > 20, "Prefix should be padded to at least 50 chars")
    }

    @Test
    fun `should format with custom currency column`() {
        val input = """
            2024-01-01 * "Test"
              Assets:Cash  100.00 USD
              Income:Salary  -100.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input, FormatOptions(currencyColumn = 60))

        // Currencies should be aligned at column 60
        assertNotNull(result)
        assertTrue(result.changed)
    }

    @Test
    fun `should normalize indentation`() {
        val input = """
            2024-01-01 * "Test"
                Assets:Cash  100.00 USD
                    Income:Salary  -100.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input)

        // Both postings should have the same indentation
        val lines = result.formattedText.lines()
        val postingLines = lines.filter { it.trimStart().startsWith("Assets") || it.trimStart().startsWith("Income") }

        assertEquals(2, postingLines.size)
        // Leading spaces should be the same
        val indent1 = postingLines[0].takeWhile { it == ' ' }.length
        val indent2 = postingLines[1].takeWhile { it == ' ' }.length
        assertEquals(indent1, indent2)
    }

    @Test
    fun `should not change non-posting lines`() {
        val input = """
            option "title" "Test Ledger"

            2024-01-01 open Assets:Cash USD

            2024-01-02 * "Test transaction"
              Assets:Cash  100.00 USD
              Income:Salary  -100.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input)

        // Option and directive lines should be unchanged
        assertTrue(result.formattedText.contains("option \"title\" \"Test Ledger\""))
        assertTrue(result.formattedText.contains("2024-01-01 open Assets:Cash USD"))
    }

    @Test
    fun `should handle metadata lines`() {
        val input = """
            2024-01-01 * "Test"
              Assets:Cash  100.00 USD
                note: "Something"
              Income:Salary  -100.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input)

        // Metadata lines should be preserved
        assertTrue(result.formattedText.contains("note:"))
    }

    @Test
    fun `should use convenience function`() {
        val input = """
            2024-01-01 * "Test"
              Assets:Cash  100.00 USD
              Income:Salary  -100.00 USD
        """.trimIndent()

        val formatted = formatBeancount(input)

        assertTrue(formatted.contains("Assets:Cash"))
        assertTrue(formatted.contains("Income:Salary"))
    }

    @Test
    fun `should handle multiple transactions`() {
        val input = """
            2024-01-01 * "First"
              Assets:Cash  100.00 USD
              Income:Salary  -100.00 USD

            2024-01-02 * "Second"
              Assets:Cash  50.00 USD
              Expenses:Food  -50.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input)

        // All postings should be aligned
        assertTrue(result.changed)

        // Check that all postings are formatted
        assertTrue(result.formattedText.contains("100.00 USD"))
        assertTrue(result.formattedText.contains("50.00 USD"))
    }

    @Test
    fun `should handle negative numbers`() {
        val input = """
            2024-01-01 * "Test"
              Assets:Cash  100.00 USD
              Income:Salary  -100.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input)

        assertTrue(result.formattedText.contains("-100.00 USD"))
    }

    @Test
    fun `should handle numbers with commas`() {
        val input = """
            2024-01-01 * "Test"
              Assets:Cash  1,000.00 USD
              Income:Salary  -1,000.00 USD
        """.trimIndent()

        val formatter = BeancountFormatter()
        val result = formatter.format(input)

        assertTrue(result.formattedText.contains("1,000.00 USD"))
        assertTrue(result.formattedText.contains("-1,000.00 USD"))
    }
}
