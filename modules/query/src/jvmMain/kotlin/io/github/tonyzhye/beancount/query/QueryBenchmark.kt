package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.loader.loadFile
import java.io.File

/**
 * Standalone benchmark runner for JVM QueryEngine.
 * Compares with Python baseline results.
 */
fun main() {
    val testFiles = mapOf(
        "large_ledger" to "modules/loader/src/jvmTest/resources/large_ledger.bean",
        "company" to "modules/loader/src/jvmTest/resources/company.bean",
        "household" to "modules/loader/src/jvmTest/resources/household.bean"
    )
    
    val queries = listOf(
        "Simple SELECT" to "SELECT date, account, number FROM postings",
        "WHERE Filter" to "SELECT date, account FROM postings WHERE account ~ 'Expenses'",
        "GROUP BY" to "SELECT account, sum(number) AS total FROM postings GROUP BY account",
        "ORDER BY" to "SELECT date, account, number FROM postings ORDER BY date DESC",
        "Entries" to "SELECT date, type, flag FROM entries"
    )
    
    // Load Python baseline using simple JSON parsing
    val pythonBaseline = try {
        parseBaseline(File("modules/query/build/reports/python-baseline.json").readText())
    } catch (e: Exception) {
        println("Warning: Could not load Python baseline: ${e.message}")
        emptyMap()
    }
    
    val results = mutableListOf<BenchmarkResult>()
    
    println("=".repeat(80))
    println("         JVM QUERY ENGINE PERFORMANCE REPORT")
    println("=".repeat(80))
    println()
    
    for ((fileName, filePath) in testFiles) {
        println("\nFile: $fileName")
        println("-".repeat(80))
        
        val entries = loadFile(filePath).entries
        val pythonResults = pythonBaseline[fileName] ?: emptyMap()
        
        for ((queryName, queryStr) in queries) {
            try {
                // Warmup
                repeat(2) { QueryEngine(entries).execute(queryStr) }
                
                // Benchmark
                val times = (1..5).map {
                    val start = System.nanoTime()
                    QueryEngine(entries).execute(queryStr)
                    (System.nanoTime() - start) / 1_000_000.0  // Convert to ms
                }
                val avgTime = times.average()
                val result = QueryEngine(entries).execute(queryStr)
                val rowCount = result.rows.size
                
                val pythonTime = pythonResults[queryName]
                val speedup = if (pythonTime != null && avgTime > 0) pythonTime / avgTime else null
                
                results.add(BenchmarkResult(
                    file = fileName,
                    queryName = queryName,
                    query = queryStr,
                    kotlinTime = avgTime,
                    pythonTime = pythonTime,
                    rows = rowCount,
                    speedup = speedup
                ))
                
                if (speedup != null) {
                    println(String.format("  %-20s: %8.2fms (%4d rows) [Python: %6.2fms, Speedup: %.2fx]",
                        queryName, avgTime, rowCount, pythonTime, speedup))
                } else {
                    println(String.format("  %-20s: %8.2fms (%4d rows)",
                        queryName, avgTime, rowCount))
                }
            } catch (e: Exception) {
                println("  ${queryName.padEnd(20)}: ERROR - ${e.message}")
            }
        }
    }
    
    // Print summary table
    println("\n\n" + "=".repeat(80))
    println("                        PERFORMANCE COMPARISON SUMMARY")
    println("=".repeat(80))
    println()
    println(String.format("%-15s %-20s %10s %10s %10s %8s",
        "File", "Query", "Kotlin", "Python", "Speedup", "Rows"))
    println("-".repeat(80))
    
    results.forEach { r ->
        if (r.pythonTime != null && r.speedup != null) {
            println(String.format("%-15s %-20s %8.2fms %8.2fms %8.2fx %8d",
                r.file, r.queryName, r.kotlinTime, r.pythonTime, r.speedup, r.rows))
        } else {
            println(String.format("%-15s %-20s %8.2fms %10s %10s %8d",
                r.file, r.queryName, r.kotlinTime, "N/A", "N/A", r.rows))
        }
    }
    
    // Calculate averages
    val resultsWithSpeedup = results.filter { it.speedup != null && it.speedup!! < Double.POSITIVE_INFINITY }
    if (resultsWithSpeedup.isNotEmpty()) {
        val avgSpeedup = resultsWithSpeedup.map { it.speedup!! }.average()
        println("-".repeat(80))
        println(String.format("%-15s %-20s %10s %10s %8.2fx %8s",
            "", "AVERAGE", "", "", avgSpeedup, ""))
    }
    
    println("=".repeat(80))
}

private data class BenchmarkResult(
    val file: String,
    val queryName: String,
    val query: String,
    val kotlinTime: Double,
    val pythonTime: Double?,
    val rows: Int,
    val speedup: Double?
)

private fun parseBaseline(json: String): Map<String, Map<String, Double>> {
    val result = mutableMapOf<String, MutableMap<String, Double>>()
    
    // Split by objects - look for complete objects between { and }
    var i = 0
    while (i < json.length) {
        val start = json.indexOf('{', i)
        if (start == -1) break
        
        val end = json.indexOf('}', start)
        if (end == -1) break
        
        val obj = json.substring(start, end + 1)
        
        // Extract fields from this object
        val file = extractStringField(obj, "file")
        val queryName = extractStringField(obj, "query_name")
        val time = extractDoubleField(obj, "python_time")
        
        if (file != null && queryName != null && time != null) {
            result.getOrPut(file) { mutableMapOf() }[queryName] = time
        }
        
        i = end + 1
    }
    
    return result
}

private fun extractStringField(json: String, field: String): String? {
    val pattern = "\"$field\"\\s*:\\s*\"([^\"]+)\""
    val match = Regex(pattern).find(json)
    return match?.groupValues?.get(1)
}

private fun extractDoubleField(json: String, field: String): Double? {
    val pattern = "\"$field\"\\s*:\\s*([\\d.]+)"
    val match = Regex(pattern).find(json)
    return match?.groupValues?.get(1)?.toDoubleOrNull()
}
