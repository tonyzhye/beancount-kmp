package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for bean-example CLI command.
 */
class BeanExampleCommandTest {

    @Test
    fun `should generate example ledger to stdout`() {
        val command = BeanExampleCommand()
        val result = command.test(listOf())

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("option"))
    }

    @Test
    fun `should generate example with seed`() {
        val command = BeanExampleCommand()
        val result = command.test(listOf("--seed", "12345"))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should generate example with date range`() {
        val command = BeanExampleCommand()
        val result = command.test(listOf(
            "--date-begin", "2024-01-01",
            "--date-end", "2024-03-01"
        ))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should respect --no-investments flag`() {
        val command = BeanExampleCommand()
        val result = command.test(listOf("--no-investments"))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should respect --no-taxes flag`() {
        val command = BeanExampleCommand()
        val result = command.test(listOf("--no-taxes"))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should respect --no-reformat flag`() {
        val command = BeanExampleCommand()
        val result = command.test(listOf("--no-reformat"))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should write to output file`() {
        val outputFile = java.io.File.createTempFile("example_", ".beancount")
        outputFile.deleteOnExit()

        val command = BeanExampleCommand()
        val result = command.test(listOf("-o", outputFile.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 0)
    }

    @Test
    fun `should accept --currency`() {
        val command = BeanExampleCommand()
        val result = command.test(listOf("--currency", "EUR"))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("EUR"))
    }

    @Test
    fun `should accept --date-birth`() {
        val command = BeanExampleCommand()
        val result = command.test(listOf("--date-birth", "1990-05-15"))

        assertEquals(0, result.statusCode)
    }
}
