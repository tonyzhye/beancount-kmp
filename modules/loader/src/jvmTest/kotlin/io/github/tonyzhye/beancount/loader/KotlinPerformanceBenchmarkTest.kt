package io.github.tonyzhye.beancount.loader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Kotlin-only performance benchmark tests.
 *
 * Validates that the Kotlin beancount implementation meets performance targets
 * without requiring Python beancount to be installed.
 *
 * Targets:
 * - 1MB ledger: parse time < 2 seconds (JVM cold start)
 * - Memory usage: < 200MB
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class KotlinPerformanceBenchmarkTest {

    companion object {
        /** Performance target: 1MB ledger must parse in under this many milliseconds */
        const val TARGET_1MB_TIME_MS = 2000L

        /** Performance target: memory usage must stay under this many MB */
        const val TARGET_MEMORY_MB = 200L

        /** Number of warm-up iterations before measurement */
        const val WARMUP_ITERATIONS = 2

        /** Number of measured iterations */
        const val MEASURED_ITERATIONS = 3
    }

    /**
     * Generate a synthetic beancount ledger of approximately the target size.
     *
     * The file contains a mix of directive types to be representative:
     * - Open directives
     * - Transactions (the bulk of the file)
     * - Balance assertions, Prices, Notes (sparse)
     */
    private fun generateLedger(targetSizeBytes: Long): File {
        val tempFile = File.createTempFile("perf_", ".beancount")
        tempFile.deleteOnExit()

        val accounts = listOf(
            "Assets:Bank:Checking", "Assets:Bank:Savings",
            "Assets:Invest:Stocks", "Assets:Invest:Bonds",
            "Expenses:Food", "Expenses:Rent", "Expenses:Utilities",
            "Expenses:Transport", "Expenses:Entertainment",
            "Income:Salary", "Income:Investments", "Income:Freelance",
            "Liabilities:CreditCard", "Liabilities:Mortgage",
            "Equity:Opening-Balances"
        )
        val currencies = listOf("USD", "EUR", "GBP", "JPY", "AAPL", "MSFT")
        val payees = listOf("Grocery Store", "Landlord", "Electric Co", "Employer", "Client A")

        tempFile.bufferedWriter().use { out ->
            out.write("option \"title\" \"Performance Test Ledger\"\n")
            out.write("option \"operating_currency\" \"USD\"\n\n")

            // Open directives
            for (acc in accounts) {
                val currency = currencies.random()
                out.write("2024-01-01 open $acc $currency\n")
            }
            out.write("\n")

            // Opening balance
            out.write("2024-01-01 * \"Opening Balance\"\n")
            out.write("  Assets:Bank:Checking  100000.00 USD\n")
            out.write("  Equity:Opening-Balances\n\n")

            var txnCount = 0
            while (tempFile.length() < targetSizeBytes) {
                val month = (txnCount % 12) + 1
                val day = (txnCount % 28) + 1
                val payee = payees[txnCount % payees.size]
                val amount = (txnCount % 5000) + 50
                val fromAccount = accounts[txnCount % 5] // Assets
                val toAccount = accounts[5 + (txnCount % 5)] // Expenses

                out.write("2024-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')} * \"$payee\"\n")
                out.write("  $toAccount  $amount.00 USD\n")
                out.write("  $fromAccount\n\n")

                txnCount++
                // Periodically write other directive types to keep it realistic
                if (txnCount % 100 == 0) {
                    out.write("2024-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')} price USD ${1.0 + (txnCount % 10) / 100.0} EUR\n\n")
                }
                if (txnCount % 200 == 0) {
                    out.write("2024-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')} balance Assets:Bank:Checking  ${100000 - txnCount * 10}.00 USD\n\n")
                }
            }
        }

        return tempFile
    }

    /**
     * Get currently used JVM memory in bytes.
     */
    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /**
     * Run a benchmark: warm up, then measure.
     *
     * @return Pair of (average time ms, peak memory MB)
     */
    private fun benchmark(file: File): Pair<Long, Long> {
        // Warm-up
        repeat(WARMUP_ITERATIONS) {
            loadFile(file.absolutePath)
        }

        System.gc()
        Thread.sleep(100)

        val times = mutableListOf<Long>()
        val memories = mutableListOf<Long>()

        repeat(MEASURED_ITERATIONS) {
            val memBefore = getUsedMemory()
            val start = System.currentTimeMillis()
            val result = loadFile(file.absolutePath)
            val elapsed = System.currentTimeMillis() - start
            val memAfter = getUsedMemory()

            times.add(elapsed)
            memories.add(memAfter - memBefore)

            println("  Iteration ${it + 1}: ${elapsed}ms, ${result.entries.size} entries, ${result.errors.size} errors, memory delta: ${(memories.last() / 1024 / 1024)}MB")
        }

        val avgTime = times.average().toLong()
        val peakMemory = memories.maxOrNull() ?: 0
        return Pair(avgTime, peakMemory / (1024 * 1024))
    }

    @Test
    fun `1MB synthetic ledger should parse under 2 seconds`() {
        val targetSize = 1L * 1024 * 1024
        val file = generateLedger(targetSize)
        println("Generated 1MB ledger: ${file.absolutePath} (${file.length() / 1024}KB)")

        try {
            val (avgTimeMs, peakMemMB) = benchmark(file)

            println("1MB Ledger Results:")
            println("  Average time: ${avgTimeMs}ms (target: < ${TARGET_1MB_TIME_MS}ms)")
            println("  Peak memory: ${peakMemMB}MB (target: < ${TARGET_MEMORY_MB}MB)")

            assertTrue(avgTimeMs < TARGET_1MB_TIME_MS,
                "1MB ledger parse time ${avgTimeMs}ms exceeds target ${TARGET_1MB_TIME_MS}ms")
            assertTrue(peakMemMB < TARGET_MEMORY_MB,
                "Memory usage ${peakMemMB}MB exceeds target ${TARGET_MEMORY_MB}MB")
        } finally {
            file.delete()
        }
    }

    @Test
    fun `real example beancount should parse quickly`() {
        val file = File("examples/example.beancount")
        if (!file.exists()) {
            println("Skipping - example.beancount not found")
            return
        }

        println("Benchmarking real file: ${file.absolutePath} (${file.length() / 1024}KB)")
        val (avgTimeMs, peakMemMB) = benchmark(file)

        println("Real File Results:")
        println("  Average time: ${avgTimeMs}ms")
        println("  Peak memory: ${peakMemMB}MB")

        // Real file should also be well under 2 seconds (it's only 340K)
        assertTrue(avgTimeMs < 1000,
            "Real example file ${avgTimeMs}ms exceeds 1000ms")
    }

    @Test
    fun `throughput should scale reasonably with file size`() {
        // Test three sizes to verify sub-linear or linear scaling
        val sizes = listOf(256L * 1024, 512L * 1024, 1024L * 1024)
        val results = mutableListOf<Triple<Long, Long, Int>>() // size, time, entries

        for (size in sizes) {
            val file = generateLedger(size)
            try {
                val (avgTimeMs, _) = benchmark(file)
                val result = loadFile(file.absolutePath)
                results.add(Triple(size, avgTimeMs, result.entries.size))
                println("Size ${size / 1024}KB: ${avgTimeMs}ms, ${result.entries.size} entries")
            } finally {
                file.delete()
            }
        }

        // Verify throughput doesn't degrade significantly as size increases
        // (throughput should not drop by more than 50% from smallest to largest)
        if (results.size >= 2) {
            val smallThroughput = results[0].third.toDouble() / results[0].second // entries/ms
            val largeThroughput = results.last().third.toDouble() / results.last().second
            val ratio = largeThroughput / smallThroughput

            println("Throughput scaling: small=${smallThroughput.toFixed(2)} entries/ms, large=${largeThroughput.toFixed(2)} entries/ms, ratio=$ratio")
            assertTrue(ratio > 0.5,
                "Throughput degraded too much: ratio=$ratio (large file throughput is less than 50% of small file)")
        }
    }

    private fun Double.toFixed(decimals: Int): String = String.format("%.${decimals}f", this)
}
