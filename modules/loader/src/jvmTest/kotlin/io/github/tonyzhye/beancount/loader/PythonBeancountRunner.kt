package io.github.tonyzhye.beancount.loader

import kotlinx.serialization.json.*
import java.io.File

/**
 * Utility class for running Python beancount and comparing results with Kotlin implementation.
 */
object PythonBeancountRunner {

    /**
     * Check if Python with beancount is available.
     */
    fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("python", "-c", "import beancount; print(beancount.__version__)")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Run Python beancount loader and return detailed JSON result.
     */
    fun loadFile(beanFile: String): PythonLoadResult {
        val pythonScript = """
import json
import sys
from beancount.loader import load_file
from beancount.core import data

bean_file = sys.argv[1]
entries, errors, options = load_file(bean_file)

result = {
    "entry_count": len(entries),
    "error_count": len(errors),
    "entries": [],
    "errors": []
}

for entry in entries:
    entry_type = entry.__class__.__name__
    entry_info = {"type": entry_type}
    
    if hasattr(entry, 'date'):
        entry_info["date"] = str(entry.date)
    if hasattr(entry, 'account'):
        entry_info["account"] = entry.account
    if hasattr(entry, 'currency'):
        entry_info["currency"] = entry.currency
    if hasattr(entry, 'narration'):
        entry_info["narration"] = entry.narration
    if hasattr(entry, 'postings'):
        postings = []
        for p in entry.postings:
            posting_info = {
                "account": p.account,
                "units": str(p.units) if p.units else None,
                "cost": str(p.cost) if p.cost else None
            }
            postings.append(posting_info)
        entry_info["postings"] = postings
    if hasattr(entry, 'amount'):
        entry_info["amount"] = str(entry.amount)
    if hasattr(entry, 'tags'):
        entry_info["tags"] = list(entry.tags) if entry.tags else []
    if hasattr(entry, 'links'):
        entry_info["links"] = list(entry.links) if entry.links else []
    if hasattr(entry, 'meta'):
        meta = dict(entry.meta)
        # Remove internal keys and non-serializable values
        meta.pop('filename', None)
        meta.pop('lineno', None)
        meta.pop('__tolerances__', None)
        # Convert any remaining non-JSON values to strings
        serializable_meta = {}
        for k, v in meta.items():
            if isinstance(v, (str, int, float, bool, list, dict)) or v is None:
                serializable_meta[k] = v
            else:
                serializable_meta[k] = str(v)
        if serializable_meta:
            entry_info["meta"] = serializable_meta
        
    result["entries"].append(entry_info)

for error in errors:
    error_info = {
        "message": error.message,
        "source": str(error.source) if hasattr(error, 'source') else None
    }
    result["errors"].append(error_info)

print(json.dumps(result))
""".trimIndent()

        val tempScript = File.createTempFile("beancount_loader_", ".py").apply {
            writeText(pythonScript)
            deleteOnExit()
        }

        val process = ProcessBuilder("python", tempScript.absolutePath, beanFile)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Python loader failed: ${output}")
        }

        val jsonObject = Json.parseToJsonElement(output).jsonObject
        
        return PythonLoadResult(
            entryCount = jsonObject["entry_count"]?.jsonPrimitive?.int ?: 0,
            errorCount = jsonObject["error_count"]?.jsonPrimitive?.int ?: 0,
            entries = jsonObject["entries"]?.jsonArray ?: JsonArray(emptyList()),
            errors = jsonObject["errors"]?.jsonArray ?: JsonArray(emptyList())
        )
    }

    data class PythonLoadResult(
        val entryCount: Int,
        val errorCount: Int,
        val entries: JsonArray,
        val errors: JsonArray
    )
}
