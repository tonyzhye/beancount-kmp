package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for include directive support.
 */
class IncludeTest {

    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun `should include another file`() {
        val mainFile = File(tempDir, "main.bean")
        val includedFile = File(tempDir, "included.bean")

        mainFile.writeText("""
            2023-01-01 open Assets:Cash USD
            2023-01-01 open Income:Salary USD
            2023-01-01 include "included.bean"
            2023-01-02 balance Assets:Cash 100.00 USD
        """.trimIndent())

        includedFile.writeText("""
            2023-01-01 * "Deposit"
              Assets:Cash  100.00 USD
              Income:Salary
        """.trimIndent())

        println("mainFile exists: ${mainFile.exists()}, size: ${mainFile.length()}")
        println("includedFile exists: ${includedFile.exists()}, size: ${includedFile.length()}")
        println("includedFile content: ${includedFile.readText()}")

        val result = loadFile(mainFile.absolutePath)

        println("Entries count: ${result.entries.size}")
        result.entries.forEach { println("Entry: ${it::class.simpleName} - $it") }
        println("Errors: ${result.errors.map { it.message }}")

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertEquals(4, result.entries.size, "Expected 4 entries (2 open, txn, balance)")

        // Check that Include directive was replaced
        assertTrue(result.entries.none { it is Include }, "Include should be resolved")
        assertTrue(result.entries.any { it is Transaction }, "Transaction should be included")
    }

    @Test
    fun `should include file with relative path`() {
        val subDir = File(tempDir, "sub")
        subDir.mkdir()

        val mainFile = File(tempDir, "main.bean")
        val includedFile = File(subDir, "included.bean")

        mainFile.writeText("""
            2023-01-01 open Assets:Cash USD
            2023-01-01 open Income:Salary USD
            2023-01-01 include "sub/included.bean"
        """.trimIndent())

        includedFile.writeText("""
            2023-01-01 * "Deposit"
              Assets:Cash  100.00 USD
              Income:Salary
        """.trimIndent())

        val result = loadFile(mainFile.absolutePath)

        assertEquals(0, result.errors.size)
        assertEquals(3, result.entries.size)
        assertTrue(result.entries.any { it is Transaction })
    }

    @Test
    fun `should report error for missing include file`() {
        val mainFile = File(tempDir, "main.bean")

        mainFile.writeText("""
            2023-01-01 open Assets:Cash USD
            2023-01-01 include "missing.bean"
        """.trimIndent())

        val result = loadFile(mainFile.absolutePath)

        assertTrue(result.errors.isNotEmpty(), "Expected error for missing file")
        assertTrue(result.errors.any { it.message.contains("not found") })
    }

    @Test
    fun `should detect circular includes`() {
        val fileA = File(tempDir, "a.bean")
        val fileB = File(tempDir, "b.bean")

        fileA.writeText("""
            2023-01-01 open Assets:Cash USD
            2023-01-01 include "b.bean"
        """.trimIndent())

        fileB.writeText("""
            2023-01-01 * "Deposit"
              Assets:Cash  100.00 USD
              Income:Salary
            2023-01-01 include "a.bean"
        """.trimIndent())

        val result = loadFile(fileA.absolutePath)

        assertTrue(result.errors.isNotEmpty(), "Expected error for circular include")
        assertTrue(result.errors.any { it.message.contains("Circular") })
    }

    @Test
    fun `should merge options from included files`() {
        val mainFile = File(tempDir, "main.bean")
        val includedFile = File(tempDir, "included.bean")

        mainFile.writeText("""
            option "title" "Main Ledger"
            2023-01-01 open Assets:Cash USD
            2023-01-01 open Income:Salary USD
            2023-01-01 include "included.bean"
        """.trimIndent())

        includedFile.writeText("""
            option "operating_currency" "USD"
            2023-01-01 * "Deposit"
              Assets:Cash  100.00 USD
              Income:Salary
        """.trimIndent())

        val result = loadFile(mainFile.absolutePath)

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        // Options from both files should be merged
        assertEquals("Main Ledger", result.options.title)
        assertTrue(result.options.operatingCurrencies.contains("USD"))
    }

    @Test
    fun `should handle deeply nested includes`() {
        val fileA = File(tempDir, "a.bean")
        val fileB = File(tempDir, "b.bean")
        val fileC = File(tempDir, "c.bean")

        fileA.writeText("""
            2023-01-01 open Assets:Cash USD
            2023-01-01 open Income:Salary USD
            2023-01-01 include "b.bean"
        """.trimIndent())

        fileB.writeText("""
            2023-01-01 include "c.bean"
        """.trimIndent())

        fileC.writeText("""
            2023-01-01 * "Deposit"
              Assets:Cash  100.00 USD
              Income:Salary
        """.trimIndent())

        val result = loadFile(fileA.absolutePath)

        assertEquals(0, result.errors.size)
        assertEquals(3, result.entries.size)
        assertTrue(result.entries.any { it is Transaction })
    }

    @Test
    fun `should support include directive without date`() {
        val mainFile = File(tempDir, "main.bean")
        val includedFile = File(tempDir, "included.bean")

        mainFile.writeText("""
            option "title" "Main Ledger"
            2023-01-01 open Assets:Cash USD
            include "included.bean"
            2023-01-02 * "Payment"
              Assets:Cash  50.00 USD
              Income:Salary
        """.trimIndent())

        includedFile.writeText("""
            2023-01-01 open Income:Salary USD
            2023-01-01 * "Deposit"
              Assets:Cash  100.00 USD
              Income:Salary
        """.trimIndent())

        val result = loadFile(mainFile.absolutePath)

        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors.map { it.message }}")
        assertTrue(result.entries.none { it is Include }, "Include should be resolved")
        assertEquals(4, result.entries.size, "Expected 4 entries")
        assertEquals(2, result.entries.count { it is Transaction })
    }

    @Test
    fun `should support plugin directive without date`() {
        val mainFile = File(tempDir, "main.bean")

        mainFile.writeText("""
            plugin "beancount.plugins.auto_accounts"
            2023-01-01 * "Payment"
              Assets:Cash  50.00 USD
              Income:Salary
        """.trimIndent())

        val result = loadFile(mainFile.absolutePath, autoPluginsEnabled = false)

        // auto_accounts plugin should add Open directives for used accounts
        assertTrue(result.entries.any { it is Transaction })
        assertTrue(result.entries.any { it is Open }, "auto_accounts plugin should create Open directives")
    }
}
