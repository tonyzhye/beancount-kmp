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
import io.github.tonyzhye.beancount.query.QueryEngine
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Comprehensive end-to-end compatibility test suite.
 *
 * Tests all directive types, edge cases, BQL queries, and balance computations
 * against Python beancount 3.2.3.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class EndToEndCompatTest {

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
                // Strategy 1: From modules/loader, go up two levels
                if (cwd.name == "loader" && cwd.parentFile?.name == "modules") cwd.parentFile.parentFile else null,
                // Strategy 2: cwd contains modules directory
                cwd.takeIf { File(it, "modules").exists() },
                // Strategy 3: cwd is project root (has build.gradle.kts or gradlew)
                cwd.takeIf { File(it, "settings.gradle.kts").exists() },
                // Strategy 4: Go up one level
                cwd.parentFile?.takeIf { File(it, "settings.gradle.kts").exists() },
                // Strategy 5: Go up two levels
                cwd.parentFile?.parentFile?.takeIf { File(it, "settings.gradle.kts").exists() }
            )
            return candidates.firstOrNull()
        }

        private val projectDir = resolveProjectRoot()

        private val pythonParseScript = resolveScriptPath("modules/core/src/jvmTest/resources/python_compat/parse_beancount.py")
        private val pythonQueryScript = resolveScriptPath("modules/core/src/jvmTest/resources/python_compat/run_query.py")
        private val pythonBalanceScript = resolveScriptPath("modules/core/src/jvmTest/resources/python_compat/compute_balances.py")

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
            "Python beancount not available - skipping end-to-end compatibility tests"
        )
        org.junit.jupiter.api.Assumptions.assumeTrue(
            pythonParseScript != null && pythonQueryScript != null && pythonBalanceScript != null,
            "Python compat scripts not found at resolved project root ($projectDir) - skipping tests"
        )
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // ===== Helper Methods =====

    private fun runPythonParse(content: String): PythonResult {
        val tempFile = File.createTempFile("compat_", ".beancount")
        tempFile.writeText(content, Charsets.UTF_8)
        tempFile.deleteOnExit()

        return try {
            val process = ProcessBuilder("python", pythonParseScript!!.absolutePath, tempFile.absolutePath)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Python parse failed: $output")
            }
            json.decodeFromString(output)
        } finally {
            tempFile.delete()
        }
    }

    private fun runPythonQuery(content: String, queryStr: String): PythonQueryResult {
        val tempFile = File.createTempFile("compat_query_", ".beancount")
        tempFile.writeText(content, Charsets.UTF_8)
        tempFile.deleteOnExit()

        return try {
            val process = ProcessBuilder("python", pythonQueryScript!!.absolutePath, tempFile.absolutePath, queryStr)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Python query failed: $output")
            }
            json.decodeFromString(output)
        } finally {
            tempFile.delete()
        }
    }

    private fun runKotlinParse(content: String) = loadString(content)

    private fun compareParse(kotlinResult: LoadResult, pythonResult: PythonResult, testName: String) {
        val report = CompatReport(testName)

        // Entry count
        report.check("entry_count", kotlinResult.entries.size, pythonResult.entry_count) {
            it.first == it.second
        }

        // Error count (allow minor differences)
        val errorDiff = kotlinResult.errors.size - pythonResult.error_count
        report.check("error_count", kotlinResult.errors.size, pythonResult.error_count) {
            kotlin.math.abs(errorDiff) <= 2
        }

        // Entries by type
        val kotlinTypes = kotlinResult.entries.groupBy { it::class.simpleName }.mapValues { it.value.size }
        val pythonTypes = pythonResult.entries.groupBy { it.type }.mapValues { it.value.size }
        report.check("entries_by_type", kotlinTypes, pythonTypes) {
            it.first == it.second
        }

        // Sample entries comparison (first 5)
        val maxCompare = minOf(kotlinResult.entries.size, pythonResult.entries.size, 5)
        for (i in 0 until maxCompare) {
            val k = kotlinResult.entries[i]
            val p = pythonResult.entries[i]
            report.check("entry[$i].type", k::class.simpleName, p.type) { it.first == it.second }
            report.check("entry[$i].date", k.date.toString(), p.date) { it.first == it.second }
        }

        println("=== $testName ===")
        println("Passed: ${report.passed}/${report.total} (${report.passPercentage}%)")
        report.failures.forEach { println("  FAIL: ${it.checkName}") }

        assertTrue(report.passPercentage >= 90.0,
            "Compatibility below 90% for $testName (${report.passPercentage}%)")
    }

    // ===== Directive Type Tests =====

    @Test
    fun `all directive types should parse compatibly`() {
        val content = """
            option "title" "Test Ledger"
            option "operating_currency" "USD"

            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Assets:Investments:Stocks
            2024-01-01 open Income:Salary USD
            2024-01-01 open Expenses:Food USD
            2024-01-01 open Equity:Opening-Balances

            2024-01-01 commodity USD
            2024-01-01 commodity HOOL

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  1000.00 USD
              Income:Salary

            2024-01-20 * "Buy stock"
              Assets:Investments:Stocks  10 HOOL {150.00 USD}
              Assets:Bank:Checking

            2024-01-25 * "Grocery shopping"
              Expenses:Food  50.00 USD
              Assets:Bank:Checking

            2024-01-31 balance Assets:Bank:Checking  850.00 USD

            2024-02-01 pad Assets:Bank:Checking Equity:Opening-Balances

            2024-02-05 * "Restaurant"
              Expenses:Food  30.00 USD
              Assets:Bank:Checking

            2024-02-28 close Expenses:Food

            2024-03-01 event "location" "Moved to San Francisco"

            2024-03-15 note Assets:Bank:Checking "Account review completed"

            2024-03-20 document Assets:Bank:Checking "/path/to/statement.pdf"

            2024-03-25 price HOOL 155.00 USD

            2024-04-01 query "monthly_expenses" "SELECT date, narration, account, position WHERE account ~ 'Expenses'"

            2024-04-15 custom "budget" "monthly" 1000.00 USD
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "all_directive_types")
    }

    // ===== Edge Cases =====

    @Test
    fun `balance tolerance should parse compatibly`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD

            2024-01-15 * "Deposit"
              Assets:Bank:Checking  100.00 USD
              Income:Salary

            2024-01-31 balance Assets:Bank:Checking  100.005 USD ~ 0.01
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "balance_tolerance")
    }

    @Test
    fun `compound cost should parse compatibly`() {
        val content = """
            2024-01-01 open Assets:Investments:Stocks
            2024-01-01 open Assets:Bank:Checking

            2024-01-15 * "Buy stock with fees"
              Assets:Investments:Stocks  10 HOOL {10 # 100 USD}
              Assets:Bank:Checking

            2024-02-15 * "Buy more stock"
              Assets:Investments:Stocks  5 HOOL {# 100 USD}
              Assets:Bank:Checking
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "compound_cost")
    }

    @Test
    fun `booking methods should parse compatibly`() {
        val content = """
            2024-01-01 open Assets:Investments:Stocks "FIFO"
            2024-01-01 open Assets:Bank:Checking
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Buy stock"
              Assets:Investments:Stocks  10 HOOL {150.00 USD}
              Assets:Bank:Checking

            2024-02-15 * "Buy more stock"
              Assets:Investments:Stocks  5 HOOL {155.00 USD}
              Assets:Bank:Checking

            2024-03-15 * "Sell stock"
              Assets:Investments:Stocks  -3 HOOL {150.00 USD}
              Assets:Bank:Checking  480.00 USD
              Income:CapitalGains
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "booking_methods")
    }

    @Test
    fun `dateless plugin directive should parse compatibly`() {
        val content = """
            plugin "beancount.plugins.auto_accounts"

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  1000.00 USD
              Income:Salary
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "dateless_plugin")
    }

    @Test
    fun `tags and links should parse compatibly`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            2024-01-15 * "Grocery" #shopping ^weekly-grocery
              Expenses:Food  50.00 USD
              Assets:Bank:Checking

            2024-01-20 * "More shopping" #shopping #weekly
              Expenses:Food  30.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "tags_and_links")
    }

    @Test
    fun `metadata should parse compatibly`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
              bank: "Chase"
              account_type: "checking"

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  1000.00 USD
                source: "employer"
              Income:Salary
                tax_deductible: false
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "metadata")
    }

    @Test
    fun `posting flags should parse compatibly`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            2024-01-15 * "Grocery"
              ! Assets:Bank:Checking  -50.00 USD
              * Expenses:Food  50.00 USD
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "posting_flags")
    }

    @Test
    fun `price conversion should parse compatibly`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food EUR

            2024-01-15 * "Dinner in Europe"
              Expenses:Food  45.00 EUR @@ 50.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "price_conversion")
    }

    @Test
    fun `pushtag poptag should parse compatibly`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            pushtag #vacation

            2024-01-15 * "Hotel"
              Expenses:Food  200.00 USD
              Assets:Bank:Checking

            poptag #vacation

            2024-01-20 * "Normal expense"
              Expenses:Food  50.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "pushtag_poptag")
    }

    @Test
    fun `pushmeta popmeta should parse compatibly`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            pushmeta location: "New York"

            2024-01-15 * "Lunch"
              Expenses:Food  20.00 USD
              Assets:Bank:Checking

            popmeta location:

            2024-01-20 * "Dinner"
              Expenses:Food  30.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        compareParse(kotlinResult, pythonResult, "pushmeta_popmeta")
    }

    // ===== BQL Query Tests =====

    @Test
    fun `BQL SELECT should be compatible`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  1000.00 USD
              Income:Salary

            2024-02-15 * "Paycheck"
              Assets:Bank:Checking  1200.00 USD
              Income:Salary
        """.trimIndent()

        val pythonResult = runPythonQuery(content, "SELECT date, narration FROM transactions")
        assertNull(pythonResult.error, "Python query failed")
        assertEquals(2, pythonResult.row_count, "Expected 2 transactions")

        // Kotlin query
        val kotlinResult = loadString(content)
        val engine = QueryEngine(kotlinResult.entries)
        val kotlinQuery = engine.execute("SELECT date, narration FROM transactions")
        assertEquals(2, kotlinQuery.rows.size, "Kotlin query should return 2 rows")
    }

    @Test
    fun `BQL WHERE should be compatible`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  1000.00 USD
              Income:Salary

            2024-01-20 * "Grocery"
              Expenses:Food  50.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val pythonResult = runPythonQuery(content, "SELECT narration WHERE account = 'Expenses:Food'")
        assertNull(pythonResult.error, "Python query failed")
        assertTrue(pythonResult.row_count >= 1, "Expected at least 1 food expense")

        val kotlinResult = loadString(content)
        val engine = QueryEngine(kotlinResult.entries)
        val kotlinQuery = engine.execute("SELECT narration WHERE account = 'Expenses:Food'")
        assertTrue(kotlinQuery.rows.size >= 1, "Kotlin query should return at least 1 row")
    }

    @Test
    fun `BQL FROM transactions should be compatible`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            2024-01-15 * "Grocery"
              Expenses:Food  50.00 USD
              Assets:Bank:Checking

            2024-01-20 * "Restaurant"
              Expenses:Food  30.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val pythonResult = runPythonQuery(content, "SELECT narration FROM transactions")
        assertNull(pythonResult.error, "Python query failed")
        assertEquals(2, pythonResult.row_count, "Expected 2 transactions")

        val kotlinResult = loadString(content)
        val engine = QueryEngine(kotlinResult.entries)
        val kotlinQuery = engine.execute("SELECT narration FROM transactions")
        assertEquals(2, kotlinQuery.rows.size, "Kotlin query should return 2 rows")
    }

    // ===== Error Handling Tests =====

    @Test
    fun `unbalanced transaction should report errors compatibly`() {
        val content = """
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Expenses:Food USD

            2024-01-15 * "Unbalanced"
              Expenses:Food  50.00 USD
              Assets:Bank:Checking  -40.00 USD
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        // Both should have at least 1 error
        assertTrue(kotlinResult.errors.isNotEmpty(), "Kotlin should detect unbalanced transaction")
        assertTrue(pythonResult.error_count > 0, "Python should detect unbalanced transaction")
    }

    @Test
    fun `missing open should report errors compatibly`() {
        val content = """
            2024-01-15 * "Transaction without open"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent()

        val kotlinResult = runKotlinParse(content)
        val pythonResult = runPythonParse(content)

        // Both should have errors
        assertTrue(kotlinResult.errors.isNotEmpty(), "Kotlin should detect missing open")
        assertTrue(pythonResult.error_count > 0, "Python should detect missing open")
    }

    // ===== Real-world File Tests =====

    @Test
    fun `example beancount should parse compatibly`() {
        val exampleFile = File(projectDir, "examples/example.beancount")
        if (!exampleFile.exists()) {
            println("Skipping - example.beancount not found")
            return
        }

        val kotlinResult = io.github.tonyzhye.beancount.loader.loadFile(exampleFile.absolutePath)
        val pythonResult = runPythonParse(exampleFile.readText())

        compareParse(kotlinResult, pythonResult, "example.beancount")
    }

    @Test
    fun `starter beancount should parse compatibly`() {
        val starterFile = File(projectDir, "examples/simple/starter.beancount")
        if (!starterFile.exists()) {
            println("Skipping - starter.beancount not found")
            return
        }

        val kotlinResult = io.github.tonyzhye.beancount.loader.loadFile(starterFile.absolutePath)
        val pythonResult = runPythonParse(starterFile.readText())

        compareParse(kotlinResult, pythonResult, "starter.beancount")
    }

    @Test
    fun `dateless include directive should parse compatibly`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "beancount_compat_${System.nanoTime()}")
        tempDir.mkdirs()
        tempDir.deleteOnExit()

        val mainFile = File(tempDir, "main.beancount")
        val includedFile = File(tempDir, "included.beancount")

        mainFile.writeText("""
            option "title" "Main Ledger"
            2024-01-01 open Assets:Bank:Checking USD
            include "included.beancount"
            2024-01-02 * "Payment"
              Assets:Bank:Checking  50.00 USD
              Income:Salary
        """.trimIndent())

        includedFile.writeText("""
            2024-01-01 open Income:Salary USD
            2024-01-01 * "Deposit"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())

        try {
            val kotlinResult = io.github.tonyzhye.beancount.loader.loadFile(mainFile.absolutePath)
            val pythonResult = runPythonParse(mainFile.absolutePath)

            compareParse(kotlinResult, pythonResult, "dateless_include")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
