package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for bean-query CLI command.
 */
class BeanQueryCommandTest {

    private fun createTestFile(content: String): File {
        val tempFile = File.createTempFile("query_", ".beancount")
        tempFile.writeText(content.trimIndent())
        tempFile.deleteOnExit()
        return tempFile
    }

    @Test
    fun `should execute query in single mode`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = BeanQueryCommand()
        val result = command.test(listOf(
            file.absolutePath,
            "SELECT narration FROM transactions"
        ))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Paycheck"))
    }

    @Test
    fun `should output csv format`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = BeanQueryCommand()
        val result = command.test(listOf(
            file.absolutePath,
            "SELECT narration FROM transactions",
            "-f", "csv"
        ))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Paycheck"))
    }

    @Test
    fun `should output text format`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = BeanQueryCommand()
        val result = command.test(listOf(
            file.absolutePath,
            "SELECT narration FROM transactions",
            "-f", "text"
        ))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should output beancount format`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = BeanQueryCommand()
        val result = command.test(listOf(
            file.absolutePath,
            "SELECT narration FROM transactions",
            "-f", "beancount"
        ))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should write to output file`() {
        val file = createTestFile("""
            2024-01-01 open Assets:Bank:Checking USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)
        val outputFile = File.createTempFile("query_out_", ".txt")
        outputFile.deleteOnExit()

        val command = BeanQueryCommand()
        val result = command.test(listOf(
            file.absolutePath,
            "SELECT narration FROM transactions",
            "-o", outputFile.absolutePath
        ))

        assertEquals(0, result.statusCode)
        assertTrue(outputFile.exists())
    }

    @Test
    fun `should suppress errors with -q flag`() {
        val file = createTestFile("""
            2024-01-15 * "Transaction without open"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        val command = BeanQueryCommand()
        val result = command.test(listOf(
            file.absolutePath,
            "SELECT narration FROM transactions",
            "-q"
        ))

        assertEquals(0, result.statusCode)
    }
}
