package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for bean-doctor CLI command and subcommands.
 */
class BeanDoctorCommandTest {

    private fun createTestFile(content: String): File {
        val tempFile = File.createTempFile("doctor_", ".beancount")
        tempFile.writeText(content.trimIndent())
        tempFile.deleteOnExit()
        return tempFile
    }

    @Test
    fun `doctor parse should show entry counts`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = DoctorParseCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Parsed"))
    }

    @Test
    fun `doctor accounts should list accounts`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD
        """)

        val command = DoctorAccountsCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Assets:Bank:Checking"))
        assertTrue(result.output.contains("Expenses:Food"))
    }

    @Test
    fun `doctor commodities should list commodities`() {
        val file = createTestFile("""
            2024-01-01 commodity USD
            2024-01-01 commodity EUR
        """)

        val command = DoctorCommoditiesCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("USD"))
        assertTrue(result.output.contains("EUR"))
    }

    @Test
    fun `doctor stats should show statistics`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = DoctorStatsCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Transactions"))
    }

    @Test
    fun `doctor errors should show error information`() {
        val file = createTestFile("""
            2024-01-15 * "Transaction without open"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = DoctorErrorsCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Errors"))
    }

    @Test
    fun `doctor missing-open should find missing opens`() {
        val file = createTestFile("""
            2024-01-15 * "Transaction"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = DoctorMissingOpenCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `doctor list-options should list all options`() {
        val command = DoctorListOptionsCommand()
        val result = command.test(listOf())

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Available Options"))
    }

    @Test
    fun `doctor print-options should print ledger options`() {
        val file = createTestFile("""
            option "title" "Test Ledger"
            option "operating_currency" "USD"

            2024-01-01 open Assets:Bank:Checking USD
        """)

        val command = DoctorPrintOptionsCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Options"))
    }

    @Test
    fun `doctor linked should find tagged transactions`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            2024-01-15 * "Grocery" #shopping
              Expenses:Food  50.00 USD
              Assets:Bank:Checking
        """)

        val command = DoctorLinkedCommand()
        val result = command.test(listOf(file.absolutePath, "#shopping"))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Linked Entries"))
    }

    @Test
    fun `doctor linked should find linked transactions`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            2024-01-15 * "Grocery" ^grocery-week-1
              Expenses:Food  50.00 USD
              Assets:Bank:Checking
        """)

        val command = DoctorLinkedCommand()
        val result = command.test(listOf(file.absolutePath, "^grocery-week-1"))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Linked Entries"))
    }

    @Test
    fun `doctor linked should list all transactions when no tag specified`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            2024-01-15 * "Grocery"
              Expenses:Food  50.00 USD
              Assets:Bank:Checking
        """)

        val command = DoctorLinkedCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("All transactions"))
    }

    @Test
    fun `doctor lex should dump tokens`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
        """)

        val command = DoctorLexCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("TOKEN"))
    }

    @Test
    fun `doctor roundtrip should work`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = DoctorRoundtripCommand()
        val result = command.test(listOf(file.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Entries"))
    }
}
