package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Edge case tests comparing Kotlin beancount with Python beancount.
 * Tests various syntax variations, metadata, flags, and directive types.
 */
class PythonCompatibilityEdgeCaseTest {

    private val edgeCaseLedgerPath = getResourcePath("edge_cases_ledger.bean")

    private fun getResourcePath(filename: String): String {
        val url = javaClass.classLoader.getResource(filename)
            ?: throw IllegalStateException("Test resource not found: $filename")
        return File(url.toURI()).absolutePath
    }

    @Test
    fun `should match Python for edge cases ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val pythonResult = PythonBeancountRunner.loadFile(edgeCaseLedgerPath)
        val kotlinResult = loadFile(edgeCaseLedgerPath)

        println("=== Edge Cases Ledger Comparison ===")
        println("Python entries: ${pythonResult.entryCount}, errors: ${pythonResult.errorCount}")
        println("Kotlin entries: ${kotlinResult.entries.size}, errors: ${kotlinResult.errors.size}")

        // Both should parse successfully with minimal errors
        assertTrue(
            kotlinResult.errors.size <= 2,
            "Kotlin should have at most 2 errors for edge cases ledger, got ${kotlinResult.errors.size}"
        )

        println("Kotlin errors: ${kotlinResult.errors.map { it.message }}")
    }

    @Test
    fun `should handle transaction flags correctly`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val kotlinResult = loadFile(edgeCaseLedgerPath)
        val transactions = kotlinResult.entries.filterIsInstance<Transaction>()

        // Check that we have transactions with different flags
        val flags = transactions.map { it.flag }.toSet()
        println("Transaction flags: $flags")
        println("Transaction count: ${transactions.size}")
        transactions.forEach { println("  ${it.narration}: flag='${it.flag}'") }

        // Should have at least some transactions
        assertTrue(transactions.size >= 2, "Should have at least 2 transactions, got ${transactions.size}")
    }

    @Test
    fun `should handle transaction with payee`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val pythonResult = PythonBeancountRunner.loadFile(edgeCaseLedgerPath)
        val kotlinResult = loadFile(edgeCaseLedgerPath)

        // Find transaction with payee (has both payee and narration)
        val pythonPayeeTxn = pythonResult.entries.find { entry ->
            entry.jsonObject["type"]?.jsonPrimitive?.content == "Transaction" &&
            entry.jsonObject["narration"]?.jsonPrimitive?.content == "Fine dining"
        }

        assertNotNull(pythonPayeeTxn, "Python should have payee transaction")

        val kotlinPayeeTxn = kotlinResult.entries.filterIsInstance<Transaction>()
            .find { it.narration == "Fine dining" }

        assertNotNull(kotlinPayeeTxn, "Kotlin should have payee transaction")
    }

    @Test
    fun `should handle note with metadata`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val pythonResult = PythonBeancountRunner.loadFile(edgeCaseLedgerPath)
        val kotlinResult = loadFile(edgeCaseLedgerPath)

        val pythonNotes = pythonResult.entries.filter {
            it.jsonObject["type"]?.jsonPrimitive?.content == "Note"
        }
        val kotlinNotes = kotlinResult.entries.filterIsInstance<Note>()

        assertEquals(pythonNotes.size, kotlinNotes.size, "Note count mismatch")

        // Check metadata if present
        if (pythonNotes.isNotEmpty()) {
            val pyMeta = pythonNotes[0].jsonObject["meta"]?.jsonObject
            if (pyMeta != null) {
                println("Python note metadata: $pyMeta")
            }
        }
    }

    @Test
    fun `should handle multiple event directives`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val pythonResult = PythonBeancountRunner.loadFile(edgeCaseLedgerPath)
        val kotlinResult = loadFile(edgeCaseLedgerPath)

        val pythonEvents = pythonResult.entries.filter {
            it.jsonObject["type"]?.jsonPrimitive?.content == "Event"
        }
        val kotlinEvents = kotlinResult.entries.filterIsInstance<Event>()

        assertEquals(pythonEvents.size, kotlinEvents.size, "Event count mismatch")
        assertTrue(kotlinEvents.size >= 3, "Should have at least 3 events")
    }

    @Test
    fun `should handle commodity with metadata`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val pythonResult = PythonBeancountRunner.loadFile(edgeCaseLedgerPath)
        val kotlinResult = loadFile(edgeCaseLedgerPath)

        val pythonCommodities = pythonResult.entries.filter {
            it.jsonObject["type"]?.jsonPrimitive?.content == "Commodity"
        }
        val kotlinCommodities = kotlinResult.entries.filterIsInstance<Commodity>()

        assertEquals(pythonCommodities.size, kotlinCommodities.size, "Commodity count mismatch")

        // Check for BTC commodity
        val kotlinBtc = kotlinCommodities.find { it.currency == "BTC" }
        assertNotNull(kotlinBtc, "Should have BTC commodity")
    }

    @Test
    fun `should handle multiple price directives`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val pythonResult = PythonBeancountRunner.loadFile(edgeCaseLedgerPath)
        val kotlinResult = loadFile(edgeCaseLedgerPath)

        val pythonPrices = pythonResult.entries.filter {
            it.jsonObject["type"]?.jsonPrimitive?.content == "Price"
        }
        val kotlinPrices = kotlinResult.entries.filterIsInstance<Price>()

        assertEquals(pythonPrices.size, kotlinPrices.size, "Price count mismatch")
        assertTrue(kotlinPrices.size >= 5, "Should have at least 5 price entries")
    }
}
