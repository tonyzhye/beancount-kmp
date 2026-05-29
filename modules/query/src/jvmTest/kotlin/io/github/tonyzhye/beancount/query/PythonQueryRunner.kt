package io.github.tonyzhye.beancount.query

import java.io.File

/**
 * Utility class for running Python beanquery and comparing results with Kotlin implementation.
 */
object PythonQueryRunner {

    /**
     * Check if Python with beanquery is available.
     */
    fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("python", "-c", "import beanquery; print('ok')")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Run a BQL query using Python beanquery and return results as list of maps.
     */
    fun executeQuery(beanFile: String, query: String): List<Map<String, String>> {
        val pythonScript = """
import json
import sys
from beancount import loader
from beanquery import query

bean_file = sys.argv[1]
query_str = sys.argv[2]

entries, errors, options = loader.load_file(bean_file)

# Execute the query
result = query.run_query(entries, options, query_str)

# Extract column names and rows
column_names = result[0]
rows = []
for row in result[1]:
    row_dict = {}
    for i, col in enumerate(column_names):
        col_name = col.name if hasattr(col, 'name') else str(col)
        value = row[i]
        if value is None:
            row_dict[col_name] = ""
        else:
            row_dict[col_name] = str(value)
    rows.append(row_dict)

print(json.dumps(rows))
""".trimIndent()

        val tempScript = File.createTempFile("beanquery_", ".py").apply {
            writeText(pythonScript)
            deleteOnExit()
        }

        val process = ProcessBuilder("python", tempScript.absolutePath, beanFile, query)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Python beanquery failed: $output")
        }

        return parseJsonArray(output)
    }

    /**
     * Get column names from Python beanquery for a given query.
     */
    fun getColumnNames(beanFile: String, query: String): List<String> {
        val pythonScript = """
import json
import sys
from beancount import loader
from beanquery import query

bean_file = sys.argv[1]
query_str = sys.argv[2]

entries, errors, options = loader.load_file(bean_file)

result = query.run_query(entries, options, query_str)
column_names = [col.name if hasattr(col, 'name') else str(col) for col in result[0]]

print(json.dumps(column_names))
""".trimIndent()

        val tempScript = File.createTempFile("beanquery_cols_", ".py").apply {
            writeText(pythonScript)
            deleteOnExit()
        }

        val process = ProcessBuilder("python", tempScript.absolutePath, beanFile, query)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Python beanquery failed: $output")
        }

        return parseJsonArray(output).firstOrNull()?.keys?.toList() ?: emptyList()
    }

    /**
     * Simple JSON array parser for basic objects.
     */
    private fun parseJsonArray(json: String): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return result
        }

        val content = trimmed.substring(1, trimmed.length - 1)
        val objects = splitObjects(content)

        for (obj in objects) {
            val map = mutableMapOf<String, String>()
            val pairs = splitPairs(obj.substring(1, obj.length - 1))
            for (pair in pairs) {
                val colonIndex = pair.indexOf(':')
                if (colonIndex > 0) {
                    val key = pair.substring(0, colonIndex).trim().trim('"')
                    val value = pair.substring(colonIndex + 1).trim()
                    map[key] = when {
                        value == "null" || value == "None" -> ""
                        value.startsWith("'") && value.endsWith("'") -> value.substring(1, value.length - 1)
                        value.startsWith("\"") && value.endsWith("\"") -> value.substring(1, value.length - 1)
                        else -> value
                    }
                }
            }
            result.add(map)
        }

        return result
    }

    private fun splitObjects(content: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = 0
        var inString = false
        var stringChar: Char? = null

        for (i in content.indices) {
            val char = content[i]
            when {
                !inString && (char == '"' || char == '\'') -> {
                    inString = true
                    stringChar = char
                }
                inString && char == stringChar && (i == 0 || content[i - 1] != '\\') -> {
                    inString = false
                    stringChar = null
                }
                !inString && char == '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                !inString && char == '}' -> {
                    depth--
                    if (depth == 0) {
                        objects.add(content.substring(start, i + 1))
                    }
                }
            }
        }

        return objects
    }

    private fun splitPairs(content: String): List<String> {
        val pairs = mutableListOf<String>()
        var depth = 0
        var start = 0
        var inString = false
        var stringChar: Char? = null

        for (i in content.indices) {
            val char = content[i]
            when {
                !inString && (char == '"' || char == '\'') -> {
                    inString = true
                    stringChar = char
                }
                inString && char == stringChar && (i == 0 || content[i - 1] != '\\') -> {
                    inString = false
                    stringChar = null
                }
                !inString && char == '{' -> depth++
                !inString && char == '}' -> depth--
                !inString && char == ',' && depth == 0 -> {
                    pairs.add(content.substring(start, i).trim())
                    start = i + 1
                }
            }
        }

        if (start < content.length) {
            pairs.add(content.substring(start).trim())
        }

        return pairs
    }
}
