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
import io.github.tonyzhye.beancount.loader.loadFile
import io.github.tonyzhye.beancount.parser.Parser

/**
 * bean-check command implementation.
 * Based on beancount.scripts.check.main.
 */
class BeanCheckCommand(
    private val parser: Parser
) : CliktCommand(
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
    
    override fun run() {
        // Phase 1: Cache is not implemented yet, ignore noCache flag
        
        val result = loadFile(
            filename = filename.absolutePath,
            parser = parser,
            extraValidations = HARDCORE_VALIDATIONS
        )
        
        if (verbose) {
            // TODO: Print timing information
        }
        
        if (result.errors.isNotEmpty()) {
            result.errors.forEach { echo(it, err = true) }
            throw ProgramResult(1)
        }
    }
}
