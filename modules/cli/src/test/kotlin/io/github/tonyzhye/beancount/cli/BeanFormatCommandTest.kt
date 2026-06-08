package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for bean-format CLI command.
 */
class BeanFormatCommandTest {

    @Test
    fun `should format single file to stdout`() {
        val tempFile = File.createTempFile("format_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = BeanFormatCommand()
        val result = command.test(listOf(tempFile.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Assets:Bank:Checking"))
    }

    @Test
    fun `should format multiple files`() {
        val tempFile1 = File.createTempFile("format1_", ".beancount")
        val tempFile2 = File.createTempFile("format2_", ".beancount")

        tempFile1.writeText("2024-01-01 open Assets:Bank:Checking USD\n")
        tempFile2.writeText("2024-01-01 open Expenses:Food USD\n")

        tempFile1.deleteOnExit()
        tempFile2.deleteOnExit()

        val command = BeanFormatCommand()
        val result = command.test(listOf(tempFile1.absolutePath, tempFile2.absolutePath))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should reject output with multiple files`() {
        val tempFile1 = File.createTempFile("format1_", ".beancount")
        val tempFile2 = File.createTempFile("format2_", ".beancount")

        tempFile1.writeText("2024-01-01 open Assets:Bank:Checking USD\n")
        tempFile2.writeText("2024-01-01 open Expenses:Food USD\n")

        tempFile1.deleteOnExit()
        tempFile2.deleteOnExit()

        val command = BeanFormatCommand()
        val result = command.test(listOf(tempFile1.absolutePath, tempFile2.absolutePath, "-o", "out.txt"))

        assertEquals(1, result.statusCode)
    }

    @Test
    fun `should write to output file for single input`() {
        val tempFile = File.createTempFile("format_out_", ".beancount")
        val outputFile = File.createTempFile("formatted_", ".beancount")

        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD
        """.trimIndent())

        tempFile.deleteOnExit()
        outputFile.deleteOnExit()

        val command = BeanFormatCommand()
        val result = command.test(listOf(tempFile.absolutePath, "-o", outputFile.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(outputFile.exists())
    }

    @Test
    fun `should format in place`() {
        val tempFile = File.createTempFile("format_inplace_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = BeanFormatCommand()
        val result = command.test(listOf(tempFile.absolutePath, "-i"))

        assertEquals(0, result.statusCode)
        assertTrue(tempFile.readText().contains("Assets:Bank:Checking"))
    }
}
