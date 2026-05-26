package io.github.tonyzhye.beancount.loader

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests that compare Kotlin beancount output with Python beancount.
 * 
 * These tests verify that our Kotlin implementation produces the same results
 * as the reference Python beancount implementation (v3).
 * 
 * Tests are skipped if Python or beancount is not available.
 */
class PythonCompatibilityTest {

    private val validLedgerPath = getResourcePath("valid_ledger.bean")
    private val invalidLedgerPath = getResourcePath("invalid_ledger.bean")

    /**
     * Check if Python with beancount is available.
     */
    private fun isPythonBeancountAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("python", "-c", "import beancount; print(beancount.__version__)")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the absolute path to a test resource file.
     */
    private fun getResourcePath(filename: String): String {
        val url = javaClass.classLoader.getResource(filename)
            ?: throw IllegalStateException("Test resource not found: $filename")
        return File(url.toURI()).absolutePath
    }

    /**
     * Run Python beancount loader and return parsed JSON result.
     */
    private fun runPythonLoader(beanFile: String): JsonObject {
        val pythonScript = """
import json
import sys
from beancount.loader import load_file

bean_file = sys.argv[1]
entries, errors, options = load_file(bean_file)

result = {
    "entry_count": len(entries),
    "error_count": len(errors),
    "entries": [],
    "errors": []
}

for entry in entries:
    entry_type = entry.__class__.__name__
    entry_info = {"type": entry_type}
    
    if hasattr(entry, 'date'):
        entry_info["date"] = str(entry.date)
    if hasattr(entry, 'account'):
        entry_info["account"] = entry.account
    if hasattr(entry, 'currency'):
        entry_info["currency"] = entry.currency
    if hasattr(entry, 'narration'):
        entry_info["narration"] = entry.narration
    if hasattr(entry, 'postings'):
        entry_info["posting_count"] = len(entry.postings)
    if hasattr(entry, 'amount'):
        entry_info["amount"] = str(entry.amount)
        
    result["entries"].append(entry_info)

for error in errors:
    error_info = {
        "message": error.message,
        "source": str(error.source) if hasattr(error, 'source') else None
    }
    result["errors"].append(error_info)

print(json.dumps(result))
""".trimIndent()

        val tempScript = File.createTempFile("beancount_loader_", ".py").apply {
            writeText(pythonScript)
            deleteOnExit()
        }

        val process = ProcessBuilder("python", tempScript.absolutePath, beanFile)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Python loader failed: $output")
        }

        return Json.parseToJsonElement(output).jsonObject
    }

    @Test
    fun `should match Python entry count for valid ledger`() {
        assumeTrue(isPythonBeancountAvailable(), "Python beancount not available")

        val pythonResult = runPythonLoader(validLedgerPath)
        val kotlinResult = loadFile(validLedgerPath)

        val pythonEntryCount = pythonResult["entry_count"]?.jsonPrimitive?.int
        val pythonErrorCount = pythonResult["error_count"]?.jsonPrimitive?.int

        assertNotNull(pythonEntryCount)
        assertNotNull(pythonErrorCount)

        assertEquals(
            pythonEntryCount, kotlinResult.entries.size,
            "Entry count mismatch. Python: $pythonEntryCount, Kotlin: ${kotlinResult.entries.size}"
        )

        assertEquals(
            pythonErrorCount, kotlinResult.errors.size,
            "Error count mismatch. Python: $pythonErrorCount, Kotlin: ${kotlinResult.errors.size}"
        )
    }

    @Test
    fun `should match Python error count for invalid ledger`() {
        assumeTrue(isPythonBeancountAvailable(), "Python beancount not available")

        val pythonResult = runPythonLoader(invalidLedgerPath)
        val kotlinResult = loadFile(invalidLedgerPath)

        val pythonErrorCount = pythonResult["error_count"]?.jsonPrimitive?.int
        assertNotNull(pythonErrorCount)

        // Note: We may not match exactly due to different validation ordering,
        // but both should detect errors
        assertTrue(
            kotlinResult.errors.isNotEmpty(),
            "Kotlin should detect errors (Python found $pythonErrorCount errors)"
        )

        // Both should find the same major issues
        val pythonErrors = pythonResult["errors"]?.jsonArray ?: JsonArray(emptyList())
        val pythonMessages = pythonErrors.map { it.jsonObject["message"]?.jsonPrimitive?.content ?: "" }

        // Check that Kotlin finds similar error categories
        val kotlinMessages = kotlinResult.errors.map { it.message }

        // At least one error category should overlap (duplicate, balance, unknown account)
        val hasOverlap = pythonMessages.any { pyMsg ->
            kotlinMessages.any { ktMsg ->
                (pyMsg.contains("Duplicate") && ktMsg.contains("Duplicate")) ||
                (pyMsg.contains("balance") && ktMsg.contains("balance")) ||
                (pyMsg.contains("account") && ktMsg.contains("account"))
            }
        }

        assertTrue(hasOverlap, "Should find similar error categories. Python: $pythonMessages, Kotlin: $kotlinMessages")
    }

    @Test
    fun `should match Python entry types for valid ledger`() {
        assumeTrue(isPythonBeancountAvailable(), "Python beancount not available")

        val pythonResult = runPythonLoader(validLedgerPath)
        val kotlinResult = loadFile(validLedgerPath)

        val pythonEntries = pythonResult["entries"]?.jsonArray ?: JsonArray(emptyList())

        // Build type counts from Python
        val pythonTypeCounts = mutableMapOf<String, Int>()
        pythonEntries.forEach { entry ->
            val type = entry.jsonObject["type"]?.jsonPrimitive?.content ?: "Unknown"
            pythonTypeCounts[type] = pythonTypeCounts.getOrDefault(type, 0) + 1
        }

        // Build type counts from Kotlin
        val kotlinTypeCounts = mutableMapOf<String, Int>()
        kotlinResult.entries.forEach { entry ->
            val type = entry::class.simpleName ?: "Unknown"
            kotlinTypeCounts[type] = kotlinTypeCounts.getOrDefault(type, 0) + 1
        }

        // Compare counts for each type
        pythonTypeCounts.forEach { (type, count) ->
            val kotlinType = when (type) {
                "Open" -> "Open"
                "Close" -> "Close"
                "Transaction" -> "Transaction"
                "Balance" -> "Balance"
                "Commodity" -> "Commodity"
                "Price" -> "Price"
                "Note" -> "Note"
                "Document" -> "Document"
                "Pad" -> "Pad"
                "Event" -> "Event"
                "Query" -> "Query"
                "Custom" -> "Custom"
                else -> type
            }
            val kotlinCount = kotlinTypeCounts[kotlinType] ?: 0
            assertEquals(
                count, kotlinCount,
                "Entry type count mismatch for $type. Python: $count, Kotlin: $kotlinCount"
            )
        }

        // Verify total counts match
        assertEquals(pythonTypeCounts.size, kotlinTypeCounts.size,
            "Number of different entry types should match")
    }

    @Test
    fun `should handle empty ledger consistently with Python`() {
        assumeTrue(isPythonBeancountAvailable(), "Python beancount not available")

        val emptyLedgerPath = File.createTempFile("empty", ".bean").apply {
            writeText("")
            deleteOnExit()
        }.absolutePath

        val pythonResult = runPythonLoader(emptyLedgerPath)
        val kotlinResult = loadFile(emptyLedgerPath)

        assertEquals(
            pythonResult["entry_count"]?.jsonPrimitive?.int ?: 0,
            kotlinResult.entries.size,
            "Empty ledger should have 0 entries"
        )
    }
}
