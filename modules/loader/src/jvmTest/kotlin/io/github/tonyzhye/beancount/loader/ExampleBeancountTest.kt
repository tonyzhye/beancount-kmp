package io.github.tonyzhye.beancount.loader

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test loading the real example.beancount file.
 */
class ExampleBeancountTest {

    private val examplePath: String? = run {
        // Try multiple strategies to find the example file
        val candidates = listOf(
            // Strategy 1: From project root via user.dir (works in IDE)
            File(System.getProperty("user.dir")).let { cwd ->
                when {
                    cwd.name == "loader" && cwd.parentFile?.name == "modules" ->
                        cwd.parentFile.parentFile.resolve("examples/example.beancount")
                    else -> cwd.resolve("examples/example.beancount")
                }
            },
            // Strategy 2: From classloader resource (works in CI)
            javaClass.classLoader.getResource("examples/example.beancount")?.let {
                File(it.toURI())
            },
            // Strategy 3: Relative to project root from any module
            File("examples/example.beancount"),
            // Strategy 4: Two levels up (gradle test from module)
            File("../../examples/example.beancount")
        )
        candidates.filterNotNull().firstOrNull { it.exists() }?.absolutePath
    }

    @Test
    fun `should load example beancount without fatal errors`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            examplePath != null,
            "examples/example.beancount not found - skipping test"
        )

        val result = loadFile(examplePath!!)

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

        assertTrue(result.entries.size > 0, "Should parse some entries")
    }
}
