package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.loader.loadFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.assertTrue

/**
 * Performance comparison tests between Kotlin QueryEngine and Python beanquery.
 */
class QueryPerformanceComparisonTest {

    /** Resolve project root using multiple strategies for cross-environment compatibility */
    private fun resolveProjectRoot(): File? {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOfNotNull(
            if (cwd.name == "query" && cwd.parentFile?.name == "modules") cwd.parentFile.parentFile else null,
            cwd.takeIf { File(it, "modules").exists() },
            cwd.takeIf { File(it, "settings.gradle.kts").exists() },
            cwd.parentFile?.takeIf { File(it, "settings.gradle.kts").exists() },
            cwd.parentFile?.parentFile?.takeIf { File(it, "settings.gradle.kts").exists() }
        )
        return candidates.firstOrNull()
    }

    private fun getResourcePath(filename: String): String {
        val root = resolveProjectRoot()
        org.junit.jupiter.api.Assumptions.assumeTrue(
            root != null,
            "Project root not resolved (cwd=${System.getProperty("user.dir")}) - skipping test"
        )

        // Look in loader module's test resources first
        val loaderResource = File(root!!, "modules/loader/src/jvmTest/resources/$filename")
        if (loaderResource.exists()) {
            return loaderResource.absolutePath
        }
        // Fallback to query module's test resources
        val queryResource = File(root, "modules/query/src/jvmTest/resources/$filename")
        if (queryResource.exists()) {
            return queryResource.absolutePath
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(
            false,
            "Test resource not found: $filename (root=$root)"
        )
        throw IllegalStateException("unreachable")
    }

    private fun runKotlinQuery(entries: List<io.github.tonyzhye.beancount.core.Directive>, query: String): Long {
        val engine = QueryEngine(entries)
        val start = System.currentTimeMillis()
        engine.execute(query)
        return System.currentTimeMillis() - start
    }

    private fun runPythonQuery(beanFile: String, query: String): Long {
        val start = System.currentTimeMillis()
        PythonQueryRunner.executeQuery(beanFile, query)
        return System.currentTimeMillis() - start
    }

    @Test
    fun `should compare performance for simple SELECT on large ledger`() {
        assumeTrue(PythonQueryRunner.isAvailable(), "Python beanquery not available")

        val path = getResourcePath("large_ledger.bean")
        val entries = loadFile(path).entries
        val query = "SELECT date, account, number FROM postings"
        val iterations = 5

        // Warmup
        println("Warming up...")
        repeat(2) {
            runKotlinQuery(entries, query)
            runPythonQuery(path, query)
        }

        // Kotlin benchmark
        println("\n=== Kotlin QueryEngine Performance ===")
        println("Query: $query")
        val kotlinTimes = mutableListOf<Long>()
        repeat(iterations) { i ->
            val elapsed = runKotlinQuery(entries, query)
            kotlinTimes.add(elapsed)
            println("  Run ${i+1}: ${elapsed}ms")
        }
        val kotlinAvg = kotlinTimes.average()
        println("  Average: ${kotlinAvg.toInt()}ms")

        // Python benchmark
        println("\n=== Python beanquery Performance ===")
        println("Query: $query")
        val pythonTimes = mutableListOf<Long>()
        repeat(iterations) { i ->
            val elapsed = runPythonQuery(path, query)
            pythonTimes.add(elapsed)
            println("  Run ${i+1}: ${elapsed}ms")
        }
        val pythonAvg = pythonTimes.average()
        println("  Average: ${pythonAvg.toInt()}ms")

        // Compare
        println("\n=== Performance Comparison ===")
        val speedup = pythonAvg / kotlinAvg
        println("Kotlin speedup: ${String.format("%.2f", speedup)}x")
        println("Python/Kotlin ratio: ${String.format("%.2f", pythonAvg / kotlinAvg)}")

        assertTrue(
            kotlinAvg <= pythonAvg * 3,
            "Kotlin should not be more than 3x slower than Python. Kotlin=${kotlinAvg.toInt()}ms, Python=${pythonAvg.toInt()}ms"
        )
    }

    @Test
    fun `should compare performance for WHERE filter on large ledger`() {
        assumeTrue(PythonQueryRunner.isAvailable(), "Python beanquery not available")

        val path = getResourcePath("large_ledger.bean")
        val entries = loadFile(path).entries
        val query = "SELECT date, account FROM postings WHERE account ~ 'Expenses'"
        val iterations = 5

        // Warmup
        repeat(2) {
            runKotlinQuery(entries, query)
            runPythonQuery(path, query)
        }

        val kotlinTimes = (1..iterations).map { runKotlinQuery(entries, query) }
        val pythonTimes = (1..iterations).map { runPythonQuery(path, query) }

        val kotlinAvg = kotlinTimes.average()
        val pythonAvg = pythonTimes.average()

        println("\n=== WHERE Filter Performance ===")
        println("Query: $query")
        println("Kotlin: ${kotlinAvg.toInt()}ms (avg of $iterations)")
        println("Python: ${pythonAvg.toInt()}ms (avg of $iterations)")
        println("Speedup: ${String.format("%.2f", pythonAvg / kotlinAvg)}x")
    }

    @Test
    fun `should compare performance for GROUP BY aggregation on large ledger`() {
        assumeTrue(PythonQueryRunner.isAvailable(), "Python beanquery not available")

        val path = getResourcePath("large_ledger.bean")
        val entries = loadFile(path).entries
        val query = "SELECT account, sum(number) AS total FROM postings GROUP BY account"
        val iterations = 5

        // Warmup
        repeat(2) {
            runKotlinQuery(entries, query)
            runPythonQuery(path, query)
        }

        val kotlinTimes = (1..iterations).map { runKotlinQuery(entries, query) }
        val pythonTimes = (1..iterations).map { runPythonQuery(path, query) }

        val kotlinAvg = kotlinTimes.average()
        val pythonAvg = pythonTimes.average()

        println("\n=== GROUP BY Aggregation Performance ===")
        println("Query: $query")
        println("Kotlin: ${kotlinAvg.toInt()}ms (avg of $iterations)")
        println("Python: ${pythonAvg.toInt()}ms (avg of $iterations)")
        println("Speedup: ${String.format("%.2f", pythonAvg / kotlinAvg)}x")
    }

    @Test
    fun `should compare performance for ORDER BY on large ledger`() {
        assumeTrue(PythonQueryRunner.isAvailable(), "Python beanquery not available")

        val path = getResourcePath("large_ledger.bean")
        val entries = loadFile(path).entries
        val query = "SELECT date, account, number FROM postings ORDER BY date DESC"
        val iterations = 5

        // Warmup
        repeat(2) {
            runKotlinQuery(entries, query)
            runPythonQuery(path, query)
        }

        val kotlinTimes = (1..iterations).map { runKotlinQuery(entries, query) }
        val pythonTimes = (1..iterations).map { runPythonQuery(path, query) }

        val kotlinAvg = kotlinTimes.average()
        val pythonAvg = pythonTimes.average()

        println("\n=== ORDER BY Performance ===")
        println("Query: $query")
        println("Kotlin: ${kotlinAvg.toInt()}ms (avg of $iterations)")
        println("Python: ${pythonAvg.toInt()}ms (avg of $iterations)")
        println("Speedup: ${String.format("%.2f", pythonAvg / kotlinAvg)}x")
    }

    @Test
    fun `should compare performance for entries table query`() {
        assumeTrue(PythonQueryRunner.isAvailable(), "Python beanquery not available")

        val path = getResourcePath("company.bean")
        val entries = loadFile(path).entries
        val query = "SELECT date, type, flag FROM entries"
        val iterations = 5

        // Warmup
        repeat(2) {
            runKotlinQuery(entries, query)
            runPythonQuery(path, query)
        }

        val kotlinTimes = (1..iterations).map { runKotlinQuery(entries, query) }
        val pythonTimes = (1..iterations).map { runPythonQuery(path, query) }

        val kotlinAvg = kotlinTimes.average()
        val pythonAvg = pythonTimes.average()

        println("\n=== Entries Table Query Performance ===")
        println("Query: $query")
        println("Kotlin: ${kotlinAvg.toInt()}ms (avg of $iterations)")
        println("Python: ${pythonAvg.toInt()}ms (avg of $iterations)")
        println("Speedup: ${String.format("%.2f", pythonAvg / kotlinAvg)}x")
    }

    @Test
    fun `should compare performance for multiple queries on household ledger`() {
        assumeTrue(PythonQueryRunner.isAvailable(), "Python beanquery not available")

        val path = getResourcePath("household.bean")
        val entries = loadFile(path).entries

        val queries = listOf(
            "SELECT date, account, number FROM postings",
            "SELECT account, sum(number) AS total FROM postings GROUP BY account",
            "SELECT DISTINCT account FROM postings",
            "SELECT date, account FROM postings WHERE number > 0",
            "SELECT count(*) AS total FROM postings"
        )

        println("\n=== Household Ledger Query Performance Summary ===")
        println("File: household.bean")
        println()

        for (query in queries) {
            // Kotlin
            val kotlinStart = System.currentTimeMillis()
            val kotlinResult = QueryEngine(entries).execute(query)
            val kotlinTime = System.currentTimeMillis() - kotlinStart

            // Python
            val pythonStart = System.currentTimeMillis()
            val pythonResult = PythonQueryRunner.executeQuery(path, query)
            val pythonTime = System.currentTimeMillis() - pythonStart

            val speedup = pythonTime.toDouble() / kotlinTime

            println("Query: $query")
            println("  Kotlin: ${kotlinTime}ms (${kotlinResult.rows.size} rows)")
            println("  Python: ${pythonTime}ms (${pythonResult.size} rows)")
            println("  Speedup: ${String.format("%.2f", speedup)}x")
            println()
        }
    }
}
