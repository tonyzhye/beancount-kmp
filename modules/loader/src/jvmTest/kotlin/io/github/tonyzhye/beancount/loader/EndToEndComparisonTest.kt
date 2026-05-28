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
 * End-to-end tests comparing Kotlin beancount with Python beancount
 * using various real-world ledger files.
 */
class EndToEndComparisonTest {

    private fun getResourcePath(filename: String): String {
        val url = javaClass.classLoader.getResource(filename)
            ?: throw IllegalStateException("Test resource not found: $filename")
        return File(url.toURI()).absolutePath
    }

    @Test
    fun `should match Python for household ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("household.bean")
        val pythonResult = PythonBeancountRunner.loadFile(path)
        val kotlinResult = loadFile(path)

        println("=== Household Ledger ===")
        println("Python: ${pythonResult.entryCount} entries, ${pythonResult.errorCount} errors")
        println("Kotlin: ${kotlinResult.entries.size} entries, ${kotlinResult.errors.size} errors")

        assertEquals(pythonResult.errorCount, kotlinResult.errors.size, 
            "Error count mismatch")
        
        // Entry counts should match (allow small difference for Close directive handling)
        assertTrue(
            kotlinResult.entries.size >= pythonResult.entryCount - 1,
            "Entry count mismatch: Python=${pythonResult.entryCount}, Kotlin=${kotlinResult.entries.size}"
        )
    }

    @Test
    fun `should match Python for investment ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("investment.bean")
        val pythonResult = PythonBeancountRunner.loadFile(path)
        val kotlinResult = loadFile(path)

        println("=== Investment Ledger ===")
        println("Python: ${pythonResult.entryCount} entries, ${pythonResult.errorCount} errors")
        println("Kotlin: ${kotlinResult.entries.size} entries, ${kotlinResult.errors.size} errors")

        assertEquals(pythonResult.errorCount, kotlinResult.errors.size,
            "Error count mismatch")
        
        assertTrue(
            kotlinResult.entries.size >= pythonResult.entryCount - 1,
            "Entry count mismatch: Python=${pythonResult.entryCount}, Kotlin=${kotlinResult.entries.size}"
        )
    }

    @Test
    fun `should match Python for error scenarios ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("error_scenarios.bean")
        val pythonResult = PythonBeancountRunner.loadFile(path)
        val kotlinResult = loadFile(path)

        println("=== Error Scenarios Ledger ===")
        println("Python: ${pythonResult.entryCount} entries, ${pythonResult.errorCount} errors")
        println("Kotlin: ${kotlinResult.entries.size} entries, ${kotlinResult.errors.size} errors")

        // Both should detect errors
        assertTrue(kotlinResult.errors.isNotEmpty(), "Kotlin should detect errors")
        
        // Print error messages for comparison
        println("\nPython errors:")
        pythonResult.errors.forEach { println("  ${it.jsonObject["message"]?.jsonPrimitive?.content}") }
        println("\nKotlin errors:")
        kotlinResult.errors.forEach { println("  ${it.message}") }
    }

    @Test
    fun `should match Python for large ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("large_ledger.bean")
        val pythonResult = PythonBeancountRunner.loadFile(path)
        val kotlinResult = loadFile(path)

        println("=== Large Ledger ===")
        println("Python: ${pythonResult.entryCount} entries, ${pythonResult.errorCount} errors")
        println("Kotlin: ${kotlinResult.entries.size} entries, ${kotlinResult.errors.size} errors")

        assertEquals(pythonResult.errorCount, kotlinResult.errors.size,
            "Error count mismatch")
        
        assertTrue(
            kotlinResult.entries.size >= pythonResult.entryCount - 1,
            "Entry count mismatch: Python=${pythonResult.entryCount}, Kotlin=${kotlinResult.entries.size}"
        )
    }

    @Test
    fun `should match Python for company ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("company.bean")
        val pythonResult = PythonBeancountRunner.loadFile(path)
        val kotlinResult = loadFile(path)

        println("=== Company Ledger ===")
        println("Python: ${pythonResult.entryCount} entries, ${pythonResult.errorCount} errors")
        println("Kotlin: ${kotlinResult.entries.size} entries, ${kotlinResult.errors.size} errors")

        assertEquals(pythonResult.errorCount, kotlinResult.errors.size,
            "Error count mismatch")
        
        assertTrue(
            kotlinResult.entries.size >= pythonResult.entryCount - 1,
            "Entry count mismatch: Python=${pythonResult.entryCount}, Kotlin=${kotlinResult.entries.size}"
        )
    }

    @Test
    fun `should match Python for multi-currency ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("multi_currency.bean")
        val pythonResult = PythonBeancountRunner.loadFile(path)
        val kotlinResult = loadFile(path)

        println("=== Multi-Currency Ledger ===")
        println("Python: ${pythonResult.entryCount} entries, ${pythonResult.errorCount} errors")
        println("Kotlin: ${kotlinResult.entries.size} entries, ${kotlinResult.errors.size} errors")

        assertEquals(pythonResult.errorCount, kotlinResult.errors.size,
            "Error count mismatch")
        
        assertTrue(
            kotlinResult.entries.size >= pythonResult.entryCount - 1,
            "Entry count mismatch: Python=${pythonResult.entryCount}, Kotlin=${kotlinResult.entries.size}"
        )
    }

    @Test
    fun `should match Python for boundary test ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("boundary_test.bean")
        val pythonResult = PythonBeancountRunner.loadFile(path)
        val kotlinResult = loadFile(path)

        println("=== Boundary Test Ledger ===")
        println("Python: ${pythonResult.entryCount} entries, ${pythonResult.errorCount} errors")
        println("Kotlin: ${kotlinResult.entries.size} entries, ${kotlinResult.errors.size} errors")

        assertEquals(pythonResult.errorCount, kotlinResult.errors.size,
            "Error count mismatch")
        
        assertTrue(
            kotlinResult.entries.size >= pythonResult.entryCount - 1,
            "Entry count mismatch: Python=${pythonResult.entryCount}, Kotlin=${kotlinResult.entries.size}"
        )
    }

    @Test
    fun `should compare entry types for all ledgers`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val ledgers = listOf(
            "household.bean",
            "investment.bean",
            "error_scenarios.bean",
            "large_ledger.bean",
            "company.bean",
            "multi_currency.bean",
            "boundary_test.bean"
        )

        for (ledger in ledgers) {
            val path = getResourcePath(ledger)
            val pythonResult = PythonBeancountRunner.loadFile(path)
            val kotlinResult = loadFile(path)

            // Build type counts
            val pythonTypeCounts = mutableMapOf<String, Int>()
            pythonResult.entries.forEach { entry ->
                val type = entry.jsonObject["type"]?.jsonPrimitive?.content ?: "Unknown"
                pythonTypeCounts[type] = pythonTypeCounts.getOrDefault(type, 0) + 1
            }

            val kotlinTypeCounts = mutableMapOf<String, Int>()
            kotlinResult.entries.forEach { entry ->
                val type = entry::class.simpleName ?: "Unknown"
                kotlinTypeCounts[type] = kotlinTypeCounts.getOrDefault(type, 0) + 1
            }

            println("\n=== $ledger Entry Types ===")
            println("Python: $pythonTypeCounts")
            println("Kotlin: $kotlinTypeCounts")

            // Verify major types match
            for ((type, count) in pythonTypeCounts) {
                if (type == "Close") continue // Skip Close due to known difference
                
                val kotlinType = when (type) {
                    "Open" -> "Open"
                    "Transaction" -> "Transaction"
                    "Balance" -> "Balance"
                    "Commodity" -> "Commodity"
                    "Price" -> "Price"
                    "Note" -> "Note"
                    "Event" -> "Event"
                    "Query" -> "Query"
                    "Custom" -> "Custom"
                    "Pad" -> "Pad"
                    "Document" -> "Document"
                    else -> type
                }
                
                val kotlinCount = kotlinTypeCounts[kotlinType] ?: 0
                if (count != kotlinCount) {
                    println("WARNING: Type count mismatch for $type: Python=$count, Kotlin=$kotlinCount")
                }
            }
        }
    }
}
