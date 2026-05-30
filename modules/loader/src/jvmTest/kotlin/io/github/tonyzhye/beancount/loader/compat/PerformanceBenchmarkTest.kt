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
import io.github.tonyzhye.beancount.loader.loadString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Performance benchmark tests comparing Kotlin vs Python beancount parsing.
 *
 * These tests measure parse time and throughput for various ledger sizes.
 */
class PerformanceBenchmarkTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val pythonBenchmarkScript = File(
        File(System.getProperty("user.dir")).parentFile,
        "core/src/jvmTest/resources/python_compat/benchmark_parse.py"
    ).absolutePath
    private val pythonGenerateScript = File(
        File(System.getProperty("user.dir")).parentFile,
        "core/src/jvmTest/resources/python_compat/generate_large_ledger.py"
    ).absolutePath

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

        println(output)
        return tempFile
    }

    /**
     * Run Python benchmark on a file.
     */
    private fun runPythonBenchmark(file: File, iterations: Int = 3): BenchmarkResult {
        val process = ProcessBuilder(
            "python", pythonBenchmarkScript,
            file.absolutePath,
            "--iterations", iterations.toString()
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Python benchmark failed: $output")
        }

        // Parse JSON from output (look for JSON_RESULT: prefix)
        val jsonLine = output.lines().firstOrNull { it.trim().startsWith("JSON_RESULT:") }
            ?.substringAfter("JSON_RESULT:")
            ?: throw RuntimeException("No JSON_RESULT found in Python output")
        return json.decodeFromString(jsonLine)
    }

    /**
     * Run Kotlin benchmark on a file.
     */
    private fun runKotlinBenchmark(file: File, iterations: Int = 3): BenchmarkResult {
        val times = mutableListOf<Long>()
        var entryCount = 0
        var errorCount = 0

        println("Running Kotlin benchmark (${iterations} iterations)...")
        for (i in 1..iterations) {
            System.gc() // Request GC before each run for cleaner measurements
            Thread.sleep(100)

            val start = System.nanoTime()
            val result = loadFile(file.absolutePath)
            val elapsed = System.nanoTime() - start

            times.add(elapsed)
            entryCount = result.entries.size
            errorCount = result.errors.size

            val elapsedSec = elapsed / 1_000_000_000.0
            println("  Iteration $i: ${elapsedSec.toFixed(3)}s ($entryCount entries, $errorCount errors)")
        }

        val avgTimeNs = times.average().toLong()
        val minTimeNs = times.minOrNull() ?: 0
        val maxTimeNs = times.maxOrNull() ?: 0

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
            throughput_mb_per_sec = if (avgTimeSec > 0) fileSizeMB / avgTimeSec else 0.0
        )
    }

    /**
     * Run a full comparison benchmark.
     */
    private fun runComparison(sizeMB: Double, iterations: Int = 3): ComparisonResult {
        println("\n${"=".repeat(60)}")
        println("Benchmark: ${sizeMB}MB ledger")
        println("${"=".repeat(60)}")

        val testFile = generateTestLedger(sizeMB)
        println("Test file: ${testFile.absolutePath}")
        val fileSizeMB = testFile.length() / (1024.0 * 1024.0)
        println("File size: ${fileSizeMB.toFixed(2)} MB")

        try {
            val pythonResult = runPythonBenchmark(testFile, iterations)
            val kotlinResult = runKotlinBenchmark(testFile, iterations)

            val speedup = if (pythonResult.avg_time_sec > 0) {
                pythonResult.avg_time_sec / kotlinResult.avg_time_sec
            } else 1.0

            val result = ComparisonResult(
                file_size_mb = pythonResult.file_size_mb,
                python = pythonResult,
                kotlin = kotlinResult,
                speedup = speedup,
                kotlin_slower = kotlinResult.avg_time_sec > pythonResult.avg_time_sec
            )

            println("\n${"=".repeat(60)}")
            println("Results:")
            println("  Python: ${pythonResult.avg_time_sec.toFixed(3)}s (${pythonResult.throughput_entries_per_sec.toFixed(1)} entries/sec)")
            println("  Kotlin: ${kotlinResult.avg_time_sec.toFixed(3)}s (${kotlinResult.throughput_entries_per_sec.toFixed(1)} entries/sec)")
            println("  Speedup: ${speedup.toFixed(2)}x ${if (result.kotlin_slower) "(Kotlin slower)" else "(Kotlin faster)"}")
            println("${"=".repeat(60)}")

            return result
        } finally {
            testFile.delete()
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `benchmark 1MB ledger parsing`() {
        val result = runComparison(1.0, iterations = 3)

        // Kotlin should be within 5x of Python speed
        assertTrue(result.speedup >= 0.2,
            "Kotlin is more than 5x slower than Python (${result.speedup.toFixed(2)}x)")
    }

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    fun `benchmark 10MB ledger parsing`() {
        val result = runComparison(10.0, iterations = 2)

        // Kotlin should be within 10x of Python speed for larger files
        assertTrue(result.speedup >= 0.1,
            "Kotlin is more than 10x slower than Python (${result.speedup.toFixed(2)}x)")
    }
}

// ===== Data classes =====

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
    val throughput_mb_per_sec: Double
)

@Serializable
data class ComparisonResult(
    val file_size_mb: Double,
    val python: BenchmarkResult,
    val kotlin: BenchmarkResult,
    val speedup: Double,
    val kotlin_slower: Boolean
)

// Extension function for formatting
private fun Double.toFixed(decimals: Int): String = String.format("%.${decimals}f", this)
