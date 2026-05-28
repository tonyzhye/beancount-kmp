package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.assertTrue

/**
 * Performance comparison tests between Kotlin and Python beancount.
 */
class PerformanceComparisonTest {

    private fun getResourcePath(filename: String): String {
        val url = javaClass.classLoader.getResource(filename)
            ?: throw IllegalStateException("Test resource not found: $filename")
        return File(url.toURI()).absolutePath
    }

    @Test
    fun `should compare performance for large ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("large_ledger.bean")
        val iterations = 3

        // Warmup
        println("Warming up...")
        repeat(2) {
            loadFile(path)
            PythonBeancountRunner.loadFile(path)
        }

        // Kotlin benchmark
        println("\n=== Kotlin Performance ===")
        val kotlinTimes = mutableListOf<Long>()
        repeat(iterations) { i ->
            val start = System.currentTimeMillis()
            val result = loadFile(path)
            val elapsed = System.currentTimeMillis() - start
            kotlinTimes.add(elapsed)
            println("Run ${i+1}: ${elapsed}ms (${result.entries.size} entries)")
        }
        val kotlinAvg = kotlinTimes.average()
        println("Average: ${kotlinAvg.toInt()}ms")

        // Python benchmark
        println("\n=== Python Performance ===")
        val pythonTimes = mutableListOf<Long>()
        repeat(iterations) { i ->
            val start = System.currentTimeMillis()
            val result = PythonBeancountRunner.loadFile(path)
            val elapsed = System.currentTimeMillis() - start
            pythonTimes.add(elapsed)
            println("Run ${i+1}: ${elapsed}ms (${result.entryCount} entries)")
        }
        val pythonAvg = pythonTimes.average()
        println("Average: ${pythonAvg.toInt()}ms")

        // Compare
        println("\n=== Comparison ===")
        val speedup = pythonAvg / kotlinAvg
        println("Kotlin speedup: ${String.format("%.2f", speedup)}x")

        // Kotlin should be at least as fast as Python for large files
        assertTrue(
            kotlinAvg <= pythonAvg * 2,
            "Kotlin should not be more than 2x slower than Python. Kotlin=${kotlinAvg.toInt()}ms, Python=${pythonAvg.toInt()}ms"
        )
    }

    @Test
    fun `should compare performance for company ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("company.bean")

        val kotlinStart = System.currentTimeMillis()
        val kotlinResult = loadFile(path)
        val kotlinTime = System.currentTimeMillis() - kotlinStart

        val pythonStart = System.currentTimeMillis()
        val pythonResult = PythonBeancountRunner.loadFile(path)
        val pythonTime = System.currentTimeMillis() - pythonStart

        println("=== Company Ledger Performance ===")
        println("Kotlin: ${kotlinTime}ms (${kotlinResult.entries.size} entries)")
        println("Python: ${pythonTime}ms (${pythonResult.entryCount} entries)")
        println("Speedup: ${String.format("%.2f", pythonTime.toDouble() / kotlinTime)}x")
    }

    @Test
    fun `should compare performance for multi-currency ledger`() {
        assumeTrue(PythonBeancountRunner.isAvailable(), "Python beancount not available")

        val path = getResourcePath("multi_currency.bean")

        val kotlinStart = System.currentTimeMillis()
        val kotlinResult = loadFile(path)
        val kotlinTime = System.currentTimeMillis() - kotlinStart

        val pythonStart = System.currentTimeMillis()
        val pythonResult = PythonBeancountRunner.loadFile(path)
        val pythonTime = System.currentTimeMillis() - pythonStart

        println("=== Multi-Currency Ledger Performance ===")
        println("Kotlin: ${kotlinTime}ms (${kotlinResult.entries.size} entries)")
        println("Python: ${pythonTime}ms (${pythonResult.entryCount} entries)")
        println("Speedup: ${String.format("%.2f", pythonTime.toDouble() / kotlinTime)}x")
    }
}
