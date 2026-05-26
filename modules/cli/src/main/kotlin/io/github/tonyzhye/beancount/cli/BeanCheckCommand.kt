package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import io.github.tonyzhye.beancount.core.HARDCORE_VALIDATIONS
import io.github.tonyzhye.beancount.core.formatEntry
import io.github.tonyzhye.beancount.core.formatError
import io.github.tonyzhye.beancount.core.renderSource
import io.github.tonyzhye.beancount.loader.loadFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * bean-check command implementation.
 * Based on beancount.scripts.check.main.
 */
class BeanCheckCommand : CliktCommand(
    name = "bean-check",
    help = "Parse, check and realize a beancount ledger."
) {
    init {
        versionOption("3.2.3", names = setOf("--version"))
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
        .help("Output errors as JSON")

    override fun run() {
        // Phase 1: Cache is not implemented yet, ignore noCache and cacheFilename flags
        if (verbose) {
            echo("Loading ${filename.absolutePath}...")
        }
        
        val startTime = System.currentTimeMillis()
        
        val result = loadFile(
            filename = filename.absolutePath,
            extraValidations = HARDCORE_VALIDATIONS
        )
        
        val loadTime = System.currentTimeMillis() - startTime
        
        if (verbose) {
            echo("Load time: ${loadTime}ms")
            echo("Entries: ${result.entries.size}")
        }
        
        if (result.errors.isNotEmpty()) {
            if (json) {
                val errorsJson = result.errors.map { error ->
                    ErrorJson(
                        source = renderSource(error.source),
                        message = error.message,
                        entry = error.entry?.let { formatEntry(it) }
                    )
                }
                echo(Json.encodeToString(errorsJson))
            } else {
                result.errors.forEach { error ->
                    echo(formatError(error), err = true)
                }
            }
            throw ProgramResult(1)
        }
        
        if (verbose) {
            echo("Validation passed!")
        }
    }
}

/**
 * JSON representation of a beancount error for --json output.
 */
@Serializable
private data class ErrorJson(
    val source: String,
    val message: String,
    val entry: String? = null
)
