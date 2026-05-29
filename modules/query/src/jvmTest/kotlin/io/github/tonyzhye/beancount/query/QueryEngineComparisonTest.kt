package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.loader.loadFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * End-to-end tests comparing Kotlin query engine output with Python beanquery.
 * These tests are only run when Python with beanquery is available.
 */
@EnabledIf("isPythonBeanqueryAvailable")
class QueryEngineComparisonTest {

    private val testFile = File("src/jvmTest/resources/test-query.beancount").absolutePath
    private val kotlinEntries by lazy { loadFile(testFile).entries }
    private val kotlinEngine by lazy { QueryEngine(kotlinEntries) }

    companion object {
        @JvmStatic
        fun isPythonBeanqueryAvailable(): Boolean {
            return PythonQueryRunner.isAvailable()
        }
    }

    @Test
    fun `should match Python for basic SELECT all`() {
        compareQuery("SELECT date, account, number FROM postings")
    }

    @Test
    fun `should match Python for SELECT with WHERE`() {
        compareQuery("SELECT date, account, number FROM postings WHERE account = 'Expenses:Food:Groceries'")
    }

    @Test
    fun `should match Python for SELECT with regex`() {
        compareQuery("SELECT account FROM postings WHERE account ~ 'Expenses'")
    }

    @Test
    fun `should match Python for SELECT with LIMIT`() {
        compareQuery("SELECT date, account FROM postings LIMIT 5")
    }

    @Test
    fun `should match Python for SELECT DISTINCT`() {
        compareQuery("SELECT DISTINCT account FROM postings")
    }

    @Test
    fun `should match Python for SELECT with ORDER BY`() {
        compareQuery("SELECT date, account, number FROM postings ORDER BY number DESC")
    }

    @Test
    fun `should match Python for SELECT with function year`() {
        compareQuery("SELECT year(date) AS year, account FROM postings")
    }

    @Test
    fun `should match Python for SELECT count aggregate`() {
        compareQuery("SELECT count(*) AS total FROM postings")
    }

    @Test
    fun `should match Python for SELECT sum aggregate`() {
        compareQuery("SELECT sum(number) AS total FROM postings")
    }

    @Test
    fun `should match Python for SELECT with GROUP BY`() {
        compareQuery("SELECT account, sum(number) AS total FROM postings GROUP BY account")
    }

    @Test
    fun `should match Python for entries table`() {
        compareQuery("SELECT date, type FROM entries")
    }

    /**
     * Compare Kotlin and Python query results.
     */
    private fun compareQuery(query: String) {
        println("Testing query: $query")

        // Execute with Kotlin
        val kotlinResult = kotlinEngine.execute(query)
        println("Kotlin result: ${kotlinResult.rows.size} rows")
        kotlinResult.rows.take(3).forEachIndexed { i, row ->
            println("  Kotlin row $i: ${row.map { it.raw?.toString() ?: "null" }}")
        }

        // Execute with Python
        val pythonResult = PythonQueryRunner.executeQuery(testFile, query)
        println("Python result: ${pythonResult.size} rows")
        pythonResult.take(3).forEachIndexed { i, row ->
            println("  Python row $i: $row")
        }

        // Compare row counts
        assertEquals(
            pythonResult.size,
            kotlinResult.rows.size,
            "Row count mismatch for query: $query"
        )

        // Compare column names
        val pythonCols = pythonResult.firstOrNull()?.keys?.toList() ?: emptyList()
        assertEquals(
            pythonCols.sorted(),
            kotlinResult.columnNames.sorted(),
            "Column names mismatch for query: $query"
        )

        // Compare row values
        for (i in kotlinResult.rows.indices) {
            val kotlinRow = kotlinResult.rows[i]
            val pythonRow = pythonResult[i]

            for (j in kotlinResult.columnNames.indices) {
                val colName = kotlinResult.columnNames[j]
                val kotlinValue = kotlinRow[j]
                val pythonValue = pythonRow[colName] ?: ""

                val kotlinStr = kotlinValue.raw?.toString() ?: ""
                
                // Normalize decimal values for comparison (strip trailing zeros)
                val normalizedPython = if (pythonValue.matches(Regex("^-?\\d+\\.\\d+$"))) {
                    pythonValue.trimEnd('0').trimEnd('.')
                } else pythonValue
                val normalizedKotlin = if (kotlinStr.matches(Regex("^-?\\d+\\.\\d+$"))) {
                    kotlinStr.trimEnd('0').trimEnd('.')
                } else kotlinStr
                
                if (normalizedPython != normalizedKotlin) {
                    println("MISMATCH at row $i, col '$colName': Python='$pythonValue', Kotlin='$kotlinStr'")
                }

                assertEquals(
                    normalizedPython,
                    normalizedKotlin,
                    "Value mismatch at row $i, column '$colName' for query: $query"
                )
            }
        }

        println("Query passed: $query")
    }
}
