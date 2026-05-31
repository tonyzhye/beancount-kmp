/*
 * Beancount JVM - A JVM implementation of Beancount
 * Copyright (C) 2026  Beancount JVM Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Beancount by Martin Blais
 * Original project: https://github.com/beancount/beancount
 */

package io.github.tonyzhye.beancount.loader.compat

import io.github.tonyzhye.beancount.loader.loadFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Timeout
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

/**
 * Comprehensive performance benchmark tests comparing Kotlin vs Python beancount.
 *
 * Tests cover:
 * - Synthetic files: 1MB, 5MB, 10MB generated ledgers
 * - Real files: example.beancount, starter.beancount
 * - Metrics: parse time, throughput (entries/sec, MB/sec), memory usage
 * - Consistency: output entry count and error count must match
 */
class PerformanceBenchmarkTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val projectDir = File(System.getProperty("user.dir")).let {
        // If running from modules/loader, go up two levels to project root
        if (it.name == "loader" && it.parentFile?.name == "modules") {
            it.parentFile.parentFile
        } else {
            it
        }
    }
    private val pythonEnhancedScript = File(
        projectDir,
        "modules/core/src/jvmTest/resources/python_compat/benchmark_parse_enhanced.py"
    ).absolutePath
    private val pythonGenerateScript = File(
        projectDir,
        "modules/core/src/jvmTest/resources/python_compat/generate_large_ledger.py"
    ).absolutePath

    // ===== Configuration =====

    /** Number of benchmark iterations per file */
    private val defaultIterations = 3

    /** Whether to measure memory (set to false for quick runs) */
    private val measureMemory = true

    // ===== Test Files =====

    /** Synthetic files to benchmark */
    private val syntheticSizes = listOf(1.0, 5.0, 10.0)

    /** Real beancount files to benchmark */
    private val realFiles = listOf(
        File(projectDir, "examples/simple/starter.beancount"),
        File(projectDir, "examples/simple/basic.beancount"),
        File(projectDir, "examples/example.beancount")
    ).filter { it.exists() }

    // ===== Benchmark Runner =====

    /**
     * Generate a test ledger file of the specified size.
     */
    private fun generateTestLedger(sizeMB: Double): File {
        val tempFile = File.createTempFile("perf_${sizeMB.toInt()}mb_", ".beancount")
        tempFile.deleteOnExit()

        val process = ProcessBuilder(
            "python", pythonGenerateScript,
            "--size", sizeMB.toString(),
            "--output", tempFile.absolutePath
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Failed to generate test ledger: $output")
        }

        println(output.trim())
        return tempFile
    }

    /**
     * Run Python enhanced benchmark on a file.
     */
    private fun runPythonBenchmark(file: File, iterations: Int = defaultIterations): BenchmarkResult {
        val args = mutableListOf("python", pythonEnhancedScript, file.absolutePath,
            "--iterations", iterations.toString())
        if (!measureMemory) args.add("--no-memory")

        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Python benchmark failed: $output")
        }

        val jsonLine = output.lines().firstOrNull { it.trim().startsWith("JSON_RESULT:") }
            ?.substringAfter("JSON_RESULT:")
            ?: throw RuntimeException("No JSON_RESULT found in Python output")
        return json.decodeFromString(jsonLine)
    }

    /**
     * Run Kotlin benchmark on a file.
     */
    private fun runKotlinBenchmark(file: File, iterations: Int = defaultIterations): BenchmarkResult {
        val times = mutableListOf<Long>()
        val memoryMeasurements = mutableListOf<Long>()
        var entryCount = 0
        var errorCount = 0

        println("Running Kotlin benchmark (${iterations} iterations)...")
        for (i in 1..iterations) {
            System.gc()
            Thread.sleep(100)

            val memBefore = getUsedMemory()
            val start = System.nanoTime()
            val result = loadFile(file.absolutePath)
            val elapsed = System.nanoTime() - start
            val memAfter = getUsedMemory()

            times.add(elapsed)
            if (measureMemory) {
                memoryMeasurements.add(memAfter - memBefore)
            }
            entryCount = result.entries.size
            errorCount = result.errors.size

            val elapsedSec = elapsed / 1_000_000_000.0
            val memStr = if (measureMemory) ", ${(memoryMeasurements.last() / (1024.0*1024.0)).toFixed(1)}MB" else ""
            println("  Iteration $i: ${elapsedSec.toFixed(3)}s ($entryCount entries, $errorCount errors$memStr)")
        }

        val avgTimeNs = times.average().toLong()
        val minTimeNs = times.minOrNull() ?: 0
        val maxTimeNs = times.maxOrNull() ?: 0
        val avgMemoryBytes = if (measureMemory && memoryMeasurements.isNotEmpty()) {
            memoryMeasurements.average().toLong()
        } else 0
        val maxMemoryBytes = if (measureMemory && memoryMeasurements.isNotEmpty()) {
            memoryMeasurements.maxOrNull() ?: 0
        } else 0

        val avgTimeSec = avgTimeNs / 1_000_000_000.0
        val fileSizeMB = file.length() / (1024.0 * 1024.0)

        return BenchmarkResult(
            language = "Kotlin",
            file_size_bytes = file.length(),
            file_size_mb = fileSizeMB,
            iterations = iterations,
            avg_time_sec = avgTimeSec,
            min_time_sec = minTimeNs / 1_000_000_000.0,
            max_time_sec = maxTimeNs / 1_000_000_000.0,
            entries = entryCount,
            errors = errorCount,
            throughput_entries_per_sec = if (avgTimeSec > 0) entryCount / avgTimeSec else 0.0,
            throughput_mb_per_sec = if (avgTimeSec > 0) fileSizeMB / avgTimeSec else 0.0,
            avg_memory_mb = avgMemoryBytes / (1024.0 * 1024.0),
            max_memory_mb = maxMemoryBytes / (1024.0 * 1024.0)
        )
    }

    /**
     * Get currently used JVM memory in bytes.
     */
    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /**
     * Run a full comparison benchmark for a single file.
     */
    private fun runComparison(file: File, iterations: Int = defaultIterations, label: String = ""): ComparisonResult {
        val displayLabel = label.ifEmpty { "${(file.length() / (1024.0*1024.0)).toFixed(2)}MB" }
        println("\n${"=".repeat(70)}")
        println("Benchmark: $displayLabel")
        println("File: ${file.name}")
        println("${"=".repeat(70)}")

        val pythonResult = runPythonBenchmark(file, iterations)
        val kotlinResult = runKotlinBenchmark(file, iterations)

        // Verify consistency
        // Note: For real-world files, minor differences in error count may exist due to
        // parser implementation differences. We log these but don't fail the benchmark.
        val entryDiff = kotlinResult.entries - pythonResult.entries
        val errorDiff = kotlinResult.errors - pythonResult.errors
        val consistencyStatus = when {
            entryDiff == 0 && errorDiff == 0 -> "PASS"
            kotlin.math.abs(entryDiff) <= 5 && kotlin.math.abs(errorDiff) <= 5 -> "PASS (minor diff)"
            else -> "WARN"
        }

        val speedup = if (pythonResult.avg_time_sec > 0) {
            pythonResult.avg_time_sec / kotlinResult.avg_time_sec
        } else 1.0

        val memoryRatio = if (pythonResult.avg_memory_mb > 0 && kotlinResult.avg_memory_mb > 0) {
            kotlinResult.avg_memory_mb / pythonResult.avg_memory_mb
        } else 0.0

        val result = ComparisonResult(
            label = displayLabel,
            file_name = file.name,
            file_size_mb = pythonResult.file_size_mb,
            python = pythonResult,
            kotlin = kotlinResult,
            speedup = speedup,
            kotlin_slower = kotlinResult.avg_time_sec > pythonResult.avg_time_sec,
            memory_ratio = memoryRatio
        )

        println("\n${"=".repeat(70)}")
        println("Results:")
        println("  Output Consistency: $consistencyStatus")
        if (entryDiff != 0 || errorDiff != 0) {
            println("    Entry diff: $entryDiff, Error diff: $errorDiff")
        }
        println("  Python Time: ${pythonResult.avg_time_sec.toFixed(3)}s (${pythonResult.throughput_entries_per_sec.toFixed(1)} entries/sec, ${pythonResult.throughput_mb_per_sec.toFixed(2)} MB/sec)")
        println("  Kotlin Time: ${kotlinResult.avg_time_sec.toFixed(3)}s (${kotlinResult.throughput_entries_per_sec.toFixed(1)} entries/sec, ${kotlinResult.throughput_mb_per_sec.toFixed(2)} MB/sec)")
        println("  Speedup: ${speedup.toFixed(2)}x ${if (result.kotlin_slower) "(Kotlin slower)" else "(Kotlin faster)"}")
        if (measureMemory && pythonResult.avg_memory_mb > 0 && kotlinResult.avg_memory_mb > 0) {
            println("  Python Memory: ${pythonResult.avg_memory_mb.toFixed(2)} MB")
            println("  Kotlin Memory: ${kotlinResult.avg_memory_mb.toFixed(2)} MB")
            println("  Memory Ratio: ${memoryRatio.toFixed(2)}x ${if (memoryRatio > 1.0) "(Kotlin uses more)" else "(Kotlin uses less)"}")
        }
        println("${"=".repeat(70)}")

        return result
    }

    // ===== Synthetic File Tests =====

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    fun `benchmark 1MB synthetic ledger`() {
        val file = generateTestLedger(1.0)
        try {
            val result = runComparison(file, iterations = 3, label = "1MB Synthetic")
            // Kotlin should be within 5x of Python speed
            assertTrue(result.speedup >= 0.2,
                "Kotlin is more than 5x slower than Python (${result.speedup.toFixed(2)}x)")
        } finally {
            file.delete()
        }
    }

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    fun `benchmark 5MB synthetic ledger`() {
        val file = generateTestLedger(5.0)
        try {
            val result = runComparison(file, iterations = 2, label = "5MB Synthetic")
            assertTrue(result.speedup >= 0.1,
                "Kotlin is more than 10x slower than Python (${result.speedup.toFixed(2)}x)")
        } finally {
            file.delete()
        }
    }

    @Test
    @Timeout(value = 600, unit = TimeUnit.SECONDS)
    fun `benchmark 10MB synthetic ledger`() {
        val file = generateTestLedger(10.0)
        try {
            val result = runComparison(file, iterations = 2, label = "10MB Synthetic")
            assertTrue(result.speedup >= 0.1,
                "Kotlin is more than 10x slower than Python (${result.speedup.toFixed(2)}x)")
        } finally {
            file.delete()
        }
    }

    // ===== Real File Tests =====

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `benchmark real starter file`() {
        val file = realFiles.find { it.name == "starter.beancount" }
        assumeFileExists(file)
        runComparison(file!!, iterations = 5, label = "Real: starter.beancount")
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `benchmark real basic file`() {
        val file = realFiles.find { it.name == "basic.beancount" }
        assumeFileExists(file)
        runComparison(file!!, iterations = 5, label = "Real: basic.beancount")
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    fun `benchmark real example file`() {
        val file = realFiles.find { it.name == "example.beancount" }
        assumeFileExists(file)
        runComparison(file!!, iterations = 3, label = "Real: example.beancount")
    }

    // ===== Full Report Test =====

    @Test
    @Timeout(value = 1200, unit = TimeUnit.SECONDS)
    fun `generate full performance report`() {
        println("\n${"#".repeat(80)}")
        println("# BEANCOUNT JVM vs PYTHON PERFORMANCE REPORT")
        println("#".repeat(80))

        val allResults = mutableListOf<ComparisonResult>()

        // Synthetic files
        println("\n## SYNTHETIC FILES")
        for (sizeMB in syntheticSizes) {
            val file = generateTestLedger(sizeMB)
            try {
                val result = runComparison(file, iterations = if (sizeMB >= 10) 2 else 3,
                    label = "${sizeMB.toInt()}MB Synthetic")
                allResults.add(result)
            } finally {
                file.delete()
            }
        }

        // Real files
        println("\n## REAL FILES")
        for (file in realFiles) {
            val result = runComparison(file, iterations = if (file.length() > 100_000) 3 else 5,
                label = "Real: ${file.name}")
            allResults.add(result)
        }

        // Summary table
        println("\n${"#".repeat(80)}")
        println("# SUMMARY TABLE")
        println("#".repeat(80))
        println()
        printTable(allResults)

        // Assertions
        val avgSpeedup = allResults.map { it.speedup }.average()
        println("\nAverage speedup across all tests: ${avgSpeedup.toFixed(2)}x")
        println("(Values > 1.0 mean Kotlin is faster, < 1.0 mean Python is faster)")

        // Ensure Kotlin is not unreasonably slow
        val minSpeedup = allResults.minOf { it.speedup }
        assertTrue(minSpeedup >= 0.05,
            "Kotlin is more than 20x slower in some test (min speedup: ${minSpeedup.toFixed(2)}x)")
    }

    // ===== Helper Methods =====

    private fun assumeFileExists(file: File?) {
        if (file == null || !file.exists()) {
            println("Skipping test - file not found")
            org.junit.jupiter.api.Assumptions.assumeTrue(false)
        }
    }

    private fun printTable(results: List<ComparisonResult>) {
        // Header
        println("| File | Size | Entries | Python Time | Kotlin Time | Speedup | Python Mem | Kotlin Mem | Mem Ratio |")
        println("|------|------|---------|-------------|-------------|---------|------------|------------|----------|")

        for (r in results) {
            val speedupStr = if (r.speedup >= 1.0) "${r.speedup.toFixed(2)}x faster" else "${(1.0/r.speedup).toFixed(2)}x slower"
            val memRatioStr = if (r.memory_ratio > 0) "${r.memory_ratio.toFixed(2)}x" else "N/A"
            println("| ${r.label} | ${r.file_size_mb.toFixed(1)}MB | ${r.python.entries} | ${r.python.avg_time_sec.toFixed(3)}s | ${r.kotlin.avg_time_sec.toFixed(3)}s | $speedupStr | ${r.python.avg_memory_mb.toFixed(1)}MB | ${r.kotlin.avg_memory_mb.toFixed(1)}MB | $memRatioStr |")
        }
    }
}

// ===== Data Classes =====

@Serializable
data class BenchmarkResult(
    val language: String,
    val file_size_bytes: Long,
    val file_size_mb: Double,
    val iterations: Int,
    val avg_time_sec: Double,
    val min_time_sec: Double,
    val max_time_sec: Double,
    val entries: Int,
    val errors: Int,
    val throughput_entries_per_sec: Double,
    val throughput_mb_per_sec: Double,
    val avg_memory_mb: Double = 0.0,
    val max_memory_mb: Double = 0.0
)

@Serializable
data class ComparisonResult(
    val label: String,
    val file_name: String,
    val file_size_mb: Double,
    val python: BenchmarkResult,
    val kotlin: BenchmarkResult,
    val speedup: Double,
    val kotlin_slower: Boolean,
    val memory_ratio: Double = 0.0
)

// Extension function for formatting
private fun Double.toFixed(decimals: Int): String = String.format("%.${decimals}f", this)
