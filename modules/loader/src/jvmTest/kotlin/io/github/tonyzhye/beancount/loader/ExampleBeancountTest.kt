package io.github.tonyzhye.beancount.loader

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test loading the real example.beancount file.
 */
class ExampleBeancountTest {

    private val examplePath = File(System.getProperty("user.dir")).parentFile.parentFile
        .resolve("examples/example.beancount").absolutePath

    @Test
    fun `should load example beancount without fatal errors`() {
        val result = loadFile(examplePath)

        println("=== Example Beancount Load Result ===")
        println("Entries: ${result.entries.size}")
        println("Errors: ${result.errors.size}")

        // Group errors by type
        val errorGroups = result.errors.groupBy { error ->
            when {
                error.message.contains("Balance failed") -> "Balance"
                error.message.contains("Ambiguous") -> "AmbiguousMatch"
                error.message.contains("Insufficient") -> "Insufficient"
                error.message.contains("does not balance") -> "UnbalancedTxn"
                error.message.contains("Unknown keyword") -> "ParseError"
                error.message.contains("Expected") -> "ParseError"
                else -> "Other"
            }
        }

        println("\n=== Error Breakdown ===")
        errorGroups.entries.sortedByDescending { it.value.size }.forEach { (type, errors) ->
            println("$type: ${errors.size}")
        }

        println("\n=== Sample Errors ===")
        result.errors.take(10).forEachIndexed { i, err ->
            println("Error ${i+1}: ${err.message}")
        }

        // Write detailed report to file
        val reportFile = File("D:/test_balance_report.txt")
        reportFile.writeText(buildString {
            appendLine("=== Example Beancount Load Result ===")
            appendLine("Entries: ${result.entries.size}")
            appendLine("Errors: ${result.errors.size}")
            appendLine()
            appendLine("=== Error Breakdown ===")
            errorGroups.entries.sortedByDescending { it.value.size }.forEach { (type, errors) ->
                appendLine("$type: ${errors.size}")
            }
            appendLine()
            appendLine("=== All Errors ===")
            result.errors.forEachIndexed { i, err ->
                appendLine("Error ${i+1}: ${err.message}")
            }
        })
        println("Report written to: ${reportFile.absolutePath}")

        assertTrue(result.entries.size > 0, "Should parse some entries")
    }
}
