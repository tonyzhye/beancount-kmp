package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.github.tonyzhye.beancount.core.HARDCORE_VALIDATIONS
import io.github.tonyzhye.beancount.loader.LoadTimingsLogger
import io.github.tonyzhye.beancount.loader.cache.JsonFileCache
import io.github.tonyzhye.beancount.loader.loadFile

/**
 * bean-check command implementation.
 * Based on beancount.scripts.check.main.
 */
class BeanCheckCommand : CliktCommand(
    name = "bean-check",
    help = "Parse, check and realize a beancount ledger."
) {
    init {
        beancountVersionOption()
    }

    private val filename by argument(
        name = "FILENAME",
        help = "Beancount input file"
    ).file(mustExist = true, canBeDir = false)
    
    private val verbose by option("-v", "--verbose")
        .flag(default = false)
        .help("Print timings")
    
    private val noCache by option("-C", "--no-cache")
        .flag(default = false)
        .help("Disable the cache")
    
    private val cacheFilename by option("--cache-filename")
        .help("Override the cache filename")
    
    private val auto by option("-a", "--auto")
        .flag(default = false)
        .help("Implicitly enable auto-plugins")

    private val json by option("--json")
        .flag(default = false)
        .help("Output errors in JSON format")

    override fun run() {
        if (verbose) {
            echo("Loading ${filename.absolutePath}...")
        }

        val startTime = System.currentTimeMillis()
        val timingEvents = mutableListOf<Pair<String, Long>>()
        val logger = LoadTimingsLogger { operation, _ ->
            timingEvents.add(operation to System.currentTimeMillis())
        }

        // Check environment variable and flags for cache configuration
        val disableCache = System.getenv("BEANCOUNT_DISABLE_LOAD_CACHE") != null || noCache
        val cachePattern = cacheFilename ?: System.getenv("BEANCOUNT_LOAD_CACHE_FILENAME")

        val cache = if (!disableCache) {
            JsonFileCache(
                cacheDir = filename.parentFile,
                cachePattern = cachePattern ?: ".{filename}.beancount.kcache"
            )
        } else null

        val result = loadFile(
            filename = filename.absolutePath,
            extraValidations = HARDCORE_VALIDATIONS,
            autoPluginsEnabled = auto,
            cache = cache,
            logTimings = logger
        )

        val loadTime = System.currentTimeMillis() - startTime

        if (verbose) {
            echo("Load time: ${loadTime}ms")
            echo("Entries: ${result.entries.size}")
            // Print per-stage timings
            var lastTime = startTime
            for ((operation, time) in timingEvents) {
                val elapsed = time - lastTime
                echo("  $operation: ${elapsed}ms")
                lastTime = time
            }
        }

        if (result.errors.isNotEmpty()) {
            if (json) {
                outputJson(result.errors)
            } else {
                outputText(result.errors, verbose)
            }
            throw ProgramResult(1)
        }

        if (json) {
            echo("{\"errors\": [], \"summary\": {\"total\": 0, \"byType\": {}}}")
        } else if (verbose) {
            echo("Validation passed!")
        }
    }
    
    private fun outputJson(errors: List<io.github.tonyzhye.beancount.core.BeancountError>) {
        val errorList = errors.map { error ->
            val meta = error.source
            val filename = meta["filename"] as? String
            val lineno = meta["lineno"] as? Int
            """{"message": "${escapeJson(error.message)}", "filename": "${filename ?: ""}", "lineno": ${lineno ?: 0}}"""
        }
        
        // Group errors by type for summary
        val byType = errors.groupBy { error ->
            // Extract error type from message (e.g., "BalanceError", "OpenError")
            val message = error.message
            when {
                message.contains("balance", ignoreCase = true) -> "Balance"
                message.contains("open", ignoreCase = true) -> "Open"
                message.contains("close", ignoreCase = true) -> "Close"
                message.contains("commodity", ignoreCase = true) -> "Commodity"
                message.contains("duplicate", ignoreCase = true) -> "Duplicate"
                message.contains("pad", ignoreCase = true) -> "Pad"
                else -> "Other"
            }
        }
        
        val typeCounts = byType.map { (type, list) ->
            "\"$type\": ${list.size}"
        }.joinToString(", ")
        
        echo("{\"errors\": [${errorList.joinToString(", ")}], \"summary\": {\"total\": ${errors.size}, \"byType\": {$typeCounts}}}")
    }
    
    private fun outputText(errors: List<io.github.tonyzhye.beancount.core.BeancountError>, verbose: Boolean) {
        if (verbose) {
            // Group errors by type for detailed output
            val byType = errors.groupBy { error ->
                val message = error.message
                when {
                    message.contains("balance", ignoreCase = true) -> "Balance"
                    message.contains("open", ignoreCase = true) -> "Open"
                    message.contains("close", ignoreCase = true) -> "Close"
                    message.contains("commodity", ignoreCase = true) -> "Commodity"
                    message.contains("duplicate", ignoreCase = true) -> "Duplicate"
                    message.contains("pad", ignoreCase = true) -> "Pad"
                    else -> "Other"
                }
            }
            
            echo("")
            echo("Error Summary:")
            echo("  Total: ${errors.size}")
            byType.toSortedMap().forEach { (type, list) ->
                echo("  $type: ${list.size}")
            }
            echo("")
        }
        
        errors.forEach { error ->
            echo("ERROR: ${error.message}", err = true)
        }
    }
    
    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
