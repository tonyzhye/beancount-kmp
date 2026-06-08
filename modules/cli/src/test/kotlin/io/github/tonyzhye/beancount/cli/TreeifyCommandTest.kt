package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for treeify CLI command.
 */
class TreeifyCommandTest {

    @Test
    fun `should treeify hierarchical text`() {
        val tempFile = File.createTempFile("treeify_", ".txt")
        tempFile.writeText("""
            Assets:Bank:Checking  100.00 USD
            Assets:Bank:Savings  200.00 USD
            Assets:Investments:Stocks  500.00 USD
            Expenses:Food  50.00 USD
            Expenses:Food:Restaurant  30.00 USD
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = TreeifyCommand()
        val result = command.test(listOf(tempFile.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Assets"))
        assertTrue(result.output.contains("Bank"))
    }

    @Test
    fun `should treeify from stdin when no input provided`() {
        val command = TreeifyCommand()
        val result = command.test(listOf())

        // Should succeed but may produce empty output for stdin
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should write to output file`() {
        val tempFile = File.createTempFile("treeify_in_", ".txt")
        val outputFile = File.createTempFile("treeify_out_", ".txt")

        tempFile.writeText("Assets:Bank:Checking  100.00 USD\n")
        tempFile.deleteOnExit()
        outputFile.deleteOnExit()

        val command = TreeifyCommand()
        val result = command.test(listOf(tempFile.absolutePath, "-o", outputFile.absolutePath))

        assertEquals(0, result.statusCode)
        assertTrue(outputFile.exists())
    }

    @Test
    fun `should use custom delimiter`() {
        val tempFile = File.createTempFile("treeify_delim_", ".txt")
        tempFile.writeText("""
            Assets;Bank;Checking  100.00 USD
            Assets;Bank;Savings  200.00 USD
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = TreeifyCommand()
        val result = command.test(listOf(tempFile.absolutePath, "-d", "\\s+"))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should use custom split pattern`() {
        val tempFile = File.createTempFile("treeify_split_", ".txt")
        tempFile.writeText("""
            Assets/Bank/Checking  100.00 USD
            Assets/Bank/Savings  200.00 USD
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = TreeifyCommand()
        val result = command.test(listOf(tempFile.absolutePath, "-s", "/"))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Assets"))
    }

    @Test
    fun `should use filenames pattern`() {
        val tempFile = File.createTempFile("treeify_files_", ".txt")
        tempFile.writeText("""
            /home/user/docs/file1.txt
            /home/user/docs/file2.txt
            /home/user/pictures/photo1.jpg
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = TreeifyCommand()
        val result = command.test(listOf(tempFile.absolutePath, "-F"))

        assertEquals(0, result.statusCode)
    }

    @Test
    fun `should use loose accounts pattern`() {
        val tempFile = File.createTempFile("treeify_loose_", ".txt")
        tempFile.writeText("""
            Assets:US:Bank:Checking  100.00 USD
            Assets:US:Bank:Savings  200.00 USD
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = TreeifyCommand()
        val result = command.test(listOf(tempFile.absolutePath, "-A"))

        assertEquals(0, result.statusCode)
    }
}
