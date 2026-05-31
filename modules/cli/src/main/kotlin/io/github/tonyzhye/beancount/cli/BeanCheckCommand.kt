package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.github.tonyzhye.beancount.core.HARDCORE_VALIDATIONS
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
    
    override fun run() {
        if (verbose) {
            echo("Loading ${filename.absolutePath}...")
        }

        val startTime = System.currentTimeMillis()

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
            cache = cache
        )

        val loadTime = System.currentTimeMillis() - startTime

        if (verbose) {
            echo("Load time: ${loadTime}ms")
            echo("Entries: ${result.entries.size}")
        }

        if (result.errors.isNotEmpty()) {
            result.errors.forEach { error ->
                echo("ERROR: ${error.message}", err = true)
            }
            throw ProgramResult(1)
        }

        if (verbose) {
            echo("Validation passed!")
        }
    }
}
