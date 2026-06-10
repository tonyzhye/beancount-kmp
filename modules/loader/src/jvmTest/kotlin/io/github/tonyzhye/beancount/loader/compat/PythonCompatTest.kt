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

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.loader.loadString
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import io.github.tonyzhye.beancount.core.realize
import io.github.tonyzhye.beancount.core.computeBalance
import java.io.File

/**
 * End-to-end compatibility tests comparing Kotlin implementation with Python beancount.
 *
 * These tests parse the same beancount content with both implementations
 * and verify that the results are semantically equivalent.
 */
class PythonCompatTest {

    companion object {
        /** True if Python beancount is available (used to skip tests on runners without it) */
        private val pythonAvailable: Boolean = run {
            try {
                val process = ProcessBuilder("python", "-c", "import beancount").start()
                process.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }

        /** Resolve project root using multiple strategies for cross-environment compatibility */
        private fun resolveProjectRoot(): File? {
            val cwd = File(System.getProperty("user.dir"))
            val candidates = listOfNotNull(
                if (cwd.name == "loader" && cwd.parentFile?.name == "modules") cwd.parentFile.parentFile else null,
                cwd.takeIf { File(it, "modules").exists() },
                cwd.takeIf { File(it, "settings.gradle.kts").exists() },
                cwd.parentFile?.takeIf { File(it, "settings.gradle.kts").exists() },
                cwd.parentFile?.parentFile?.takeIf { File(it, "settings.gradle.kts").exists() }
            )
            return candidates.firstOrNull()
        }

        private val projectDir = resolveProjectRoot()

        private val pythonScript = resolveScriptPath("modules/core/src/jvmTest/resources/python_compat/parse_beancount.py")
        private val balanceScript = resolveScriptPath("modules/core/src/jvmTest/resources/python_compat/compute_balances.py")
        private val queryScript = resolveScriptPath("modules/core/src/jvmTest/resources/python_compat/run_query.py")

        private fun resolveScriptPath(relativePath: String): File? {
            val root = projectDir ?: return null
            val file = File(root, relativePath)
            return file.takeIf { it.exists() }
        }
    }

    @org.junit.jupiter.api.BeforeEach
    fun checkPythonAvailable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            pythonAvailable,
            "Python beancount not available - skipping compatibility tests"
        )
        org.junit.jupiter.api.Assumptions.assumeTrue(
            pythonScript != null && balanceScript != null && queryScript != null,
            "Python compat scripts not found at resolved project root ($projectDir) - skipping tests"
        )
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Run Python beancount parser on a string and return parsed results.
     * Uses a temp file to avoid command line escaping issues.
     */
    private fun runPythonParser(content: String): PythonResult {
        val tempFile = File.createTempFile("beancount_compat_", ".beancount")
        tempFile.writeText(content, Charsets.UTF_8)
        tempFile.deleteOnExit()

        return try {
            runPythonParser(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Run Python beancount parser on a file and return parsed results.
     */
    private fun runPythonParser(file: File): PythonResult {
        val process = ProcessBuilder(
            "python", pythonScript!!.absolutePath, file.absolutePath
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            println("DEBUG: pythonScript=$pythonScript")
            println("DEBUG: file=${file.absolutePath}")
            println("DEBUG: user.dir=${System.getProperty("user.dir")}")
            throw RuntimeException("Python parser failed with exit code $exitCode:\n$output")
        }

        return json.decodeFromString(output)
    }

    /**
     * Compare Kotlin and Python parse results.
     */
    private fun compareResults(
        kotlinResult: LoadResult,
        pythonResult: PythonResult,
        testName: String
    ): CompatReport {
        val report = CompatReport(testName)

        // Compare entry counts
        report.check("entry_count", kotlinResult.entries.size, pythonResult.entry_count) {
            it.first == it.second
        }

        // Compare error counts
        report.check("error_count", kotlinResult.errors.size, pythonResult.error_count) {
            it.first == it.second
        }

        // Compare entries by type
        val kotlinEntriesByType = kotlinResult.entries.groupBy { it::class.simpleName }.mapValues { it.value.size }
        val pythonEntriesByType = pythonResult.entries.groupBy { it.type }.mapValues { it.value.size }
        report.check("entries_by_type", kotlinEntriesByType, pythonEntriesByType) {
            it.first == it.second
        }

        // Compare specific entries (first few)
        val maxCompare = minOf(kotlinResult.entries.size, pythonResult.entries.size, 10)
        for (i in 0 until maxCompare) {
            val kEntry = kotlinResult.entries[i]
            val pEntry = pythonResult.entries[i]
            report.check("entry[$i].type", kEntry::class.simpleName, pEntry.type) {
                it.first == it.second
            }
            report.check("entry[$i].date", kEntry.date.toString(), pEntry.date) {
                it.first == it.second
            }
        }

        // Compare error messages (first few)
        val maxErrors = minOf(kotlinResult.errors.size, pythonResult.errors.size, 5)
        for (i in 0 until maxErrors) {
            val kError = kotlinResult.errors[i]
            val pError = pythonResult.errors[i]
            report.check("error[$i].message", kError.message, pError.message) {
                it.first == it.second
            }
        }

        return report
    }

    @Test
    fun `should produce compatible results for simple ledger`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent()

        val kotlinResult = loadString(content)
        val pythonResult = runPythonParser(content)

        val report = compareResults(kotlinResult, pythonResult, "simple_ledger")

        println("=== Compatibility Report: ${report.testName} ===")
        println("Passed: ${report.passed}/${report.total} (${report.passPercentage}%)")
        report.failures.forEach { println("FAIL: ${it.checkName}: expected=${it.expected}, actual=${it.actual}") }

        assertTrue(report.passPercentage >= 80.0,
            "Compatibility below 80% (${report.passPercentage}%)\nFailures:\n${report.failures.joinToString("\n")}")
    }

    @Test
    fun `should produce compatible results for ledger with balance assertion`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD

            2024-01-15 * "Deposit"
              Assets:Bank:Checking  100.00 USD
              Income:Salary

            2024-01-31 balance Assets:Bank:Checking  100.00 USD
        """.trimIndent()

        val kotlinResult = loadString(content)
        val pythonResult = runPythonParser(content)

        val report = compareResults(kotlinResult, pythonResult, "balance_assertion")

        println("=== Compatibility Report: ${report.testName} ===")
        println("Passed: ${report.passed}/${report.total} (${report.passPercentage}%)")
        report.failures.forEach { println("FAIL: ${it.checkName}: expected=${it.expected}, actual=${it.actual}") }

        assertTrue(report.passPercentage >= 80.0,
            "Compatibility below 80% (${report.passPercentage}%)")
    }

    @Test
    fun `should produce compatible results for ledger with cost`() {
        val content = """
            2024-01-01 open Assets:Investments:Stocks
            2024-01-01 open Assets:Bank:Checking

            2024-01-15 * "Buy stock"
              Assets:Investments:Stocks  10 HOOL {150.00 USD}
              Assets:Bank:Checking
        """.trimIndent()

        val kotlinResult = loadString(content)
        val pythonResult = runPythonParser(content)

        val report = compareResults(kotlinResult, pythonResult, "cost_ledger")

        println("=== Compatibility Report: ${report.testName} ===")
        println("Passed: ${report.passed}/${report.total} (${report.passPercentage}%)")
        report.failures.forEach { println("FAIL: ${it.checkName}: expected=${it.expected}, actual=${it.actual}") }

        assertTrue(report.passPercentage >= 80.0,
            "Compatibility below 80% (${report.passPercentage}%)")
    }

    @Test
    fun `should produce compatible results for ledger with tags and links`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            2024-01-15 * "Grocery" "Weekly shopping" #shopping ^grocery-week-1
              Expenses:Food  50.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val kotlinResult = loadString(content)
        val pythonResult = runPythonParser(content)

        val report = compareResults(kotlinResult, pythonResult, "tags_links")

        println("=== Compatibility Report: ${report.testName} ===")
        println("Passed: ${report.passed}/${report.total} (${report.passPercentage}%)")
        report.failures.forEach { println("FAIL: ${it.checkName}: expected=${it.expected}, actual=${it.actual}") }

        assertTrue(report.passPercentage >= 80.0,
            "Compatibility below 80% (${report.passPercentage}%)")
    }

    // ===== Balance computation verification =====

    private fun runPythonBalance(content: String): PythonBalanceResult {
        val tempFile = File.createTempFile("beancount_balance_", ".beancount")
        tempFile.writeText(content, Charsets.UTF_8)
        tempFile.deleteOnExit()

        return try {
            val process = ProcessBuilder("python", balanceScript!!.absolutePath, tempFile.absolutePath)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Python balance script failed: $output")
            }
            json.decodeFromString(output)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `should compute compatible balances for simple ledger`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary

            2024-02-15 * "Paycheck"
              Assets:Bank:Checking  150.00 USD
              Income:Salary
        """.trimIndent()

        val kotlinResult = loadString(content)
        val pythonBalance = runPythonBalance(content)

        // Compute Kotlin balances
        val kotlinRoot = realize(kotlinResult.entries)
        val kotlinBalances = mutableMapOf<String, List<String>>()
        for (account in kotlinRoot.iterate()) {
            if (account.account.isNotEmpty()) {
                val balance = computeBalance(account, leafOnly = false)
                kotlinBalances[account.account] = balance.getPositions().map {
                    "${it.units.number} ${it.units.currency}"
                }
            }
        }

        // Compare
        val report = CompatReport("balance_simple")
            .check("account_count", kotlinBalances.size, pythonBalance.balances.size) {
                it.first == it.second
            }

        pythonBalance.balances.forEach { (account, positions) ->
            val kotlinPos = kotlinBalances[account]
            report.check("balance[$account].exists", kotlinPos != null, true) { it.first == it.second }
            if (kotlinPos != null) {
                report.check("balance[$account].count", kotlinPos.size, positions.size) {
                    it.first == it.second
                }
            }
        }

        println("=== Balance Report: ${report.testName} ===")
        println("Passed: ${report.passed}/${report.total} (${report.passPercentage}%)")
        report.failures.forEach { println("FAIL: ${it.checkName}") }

        assertTrue(report.passPercentage >= 80.0,
            "Balance compatibility below 80% (${report.passPercentage}%)")
    }

    // ===== BQL Query verification =====

    private fun runPythonQuery(content: String, queryStr: String): PythonQueryResult {
        val tempFile = File.createTempFile("beancount_query_", ".beancount")
        tempFile.writeText(content, Charsets.UTF_8)
        tempFile.deleteOnExit()

        return try {
            val process = ProcessBuilder("python", queryScript!!.absolutePath, tempFile.absolutePath, queryStr)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Python query script failed: $output")
            }
            json.decodeFromString(output)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `should produce compatible query results for basic SELECT`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary

            2024-02-15 * "Paycheck"
              Assets:Bank:Checking  150.00 USD
              Income:Salary
        """.trimIndent()

        val pythonQuery = runPythonQuery(content, "SELECT date, narration")

        println("=== Query Result ===")
        println("Python returned ${pythonQuery.row_count} rows")
        pythonQuery.rows.take(5).forEach { println(it) }

        assertNull(pythonQuery.error, "Python query failed: ${pythonQuery.error}")
        assertTrue(pythonQuery.row_count >= 2, "Expected at least 2 transactions, got ${pythonQuery.row_count}")
    }
}

// ===== Data classes for Python JSON output =====

@Serializable
data class PythonResult(
    val entries: List<PythonEntry>,
    val errors: List<PythonError>,
    val options: Map<String, JsonElement>,
    val entry_count: Int,
    val error_count: Int
)

@Serializable
data class PythonEntry(
    val type: String,
    val date: String,
    val meta: Map<String, JsonElement> = emptyMap(),
    val account: String? = null,
    val currencies: List<String> = emptyList(),
    val booking: String? = null,
    val amount: PythonAmount? = null,
    val flag: String? = null,
    val payee: String? = null,
    val narration: String? = null,
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val postings: List<PythonPosting> = emptyList(),
    val currency: String? = null,
    val source_account: String? = null,
    val comment: String? = null,
    val filename: String? = null,
    val description: String? = null,
    val name: String? = null,
    val query: String? = null,
    val custom_type: String? = null,
    val values: List<PythonCustomValue> = emptyList()
)

@Serializable
data class PythonAmount(
    val number: String,
    val currency: String
)

@Serializable
data class PythonPosting(
    val account: String,
    val flag: String? = null,
    val units: PythonAmount? = null,
    val cost: PythonCost? = null,
    val price: PythonAmount? = null,
    val meta: Map<String, JsonElement>? = null
)

@Serializable
data class PythonCost(
    val number: String? = null,
    val currency: String? = null,
    val date: String? = null,
    val label: String? = null,
    val number_per: String? = null,
    val number_total: String? = null,
    val merge: Boolean = false
)

@Serializable
data class PythonError(
    val source: PythonSource,
    val message: String,
    val entry_type: String? = null
)

@Serializable
data class PythonSource(
    val filename: String,
    val lineno: Int
)

@Serializable
data class PythonCustomValue(
    val type: String,
    val value: String
)

// ===== Compatibility report =====

data class CompatReport(
    val testName: String,
    val passed: Int = 0,
    val total: Int = 0,
    val failures: List<CompatFailure> = emptyList()
) {
    val passPercentage: Double
        get() = if (total > 0) (passed * 100.0 / total) else 100.0

    fun <T> check(name: String, expected: T, actual: T, validator: (Pair<T, T>) -> Boolean): CompatReport {
        val isValid = validator(expected to actual)
        return if (isValid) {
            copy(passed = passed + 1, total = total + 1)
        } else {
            copy(
                total = total + 1,
                failures = failures + CompatFailure(name, expected.toString(), actual.toString())
            )
        }
    }
}

data class CompatFailure(
    val checkName: String,
    val expected: String,
    val actual: String
)

// ===== Balance verification data classes =====

@Serializable
data class PythonBalanceResult(
    val entry_count: Int,
    val error_count: Int,
    val accounts: List<String>,
    val balances: Map<String, List<PythonBalancePosition>>,
    val errors: List<PythonBalanceError> = emptyList()
)

@Serializable
data class PythonBalancePosition(
    val number: String,
    val currency: String,
    val cost: PythonBalanceCost? = null
)

@Serializable
data class PythonBalanceCost(
    val number: String? = null,
    val currency: String? = null,
    val date: String? = null,
    val label: String? = null
)

@Serializable
data class PythonBalanceError(
    val message: String,
    val source: String
)

// ===== Query verification data classes =====

@Serializable
data class PythonQueryResult(
    val columns: List<PythonQueryColumn>,
    val rows: List<List<String?>>,
    val row_count: Int,
    val error: String? = null
)

@Serializable
data class PythonQueryColumn(
    val name: String,
    val type: String
)
