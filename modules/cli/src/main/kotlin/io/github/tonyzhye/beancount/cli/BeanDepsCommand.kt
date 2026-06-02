package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.github.tonyzhye.beancount.core.Include
import io.github.tonyzhye.beancount.parser.BeancountParser
import java.io.File

/**
 * bean-deps command implementation.
 * Shows the dependency graph between beancount files.
 */
class BeanDepsCommand : CliktCommand(
    name = "bean-deps",
    help = "Show dependency graph between beancount files."
) {
    init {
        beancountVersionOption()
    }

    private val filename by argument(
        name = "FILENAME",
        help = "Beancount input file"
    ).file(mustExist = true, canBeDir = false)

    private val noRecursive by option("--no-recursive")
        .flag(default = false)
        .help("Only show direct includes")

    override fun run() {
        val visited = mutableSetOf<String>()

        fun collectDeps(file: File, indent: String = "") {
            val canonicalPath = file.canonicalPath
            if (canonicalPath in visited) {
                echo("${indent}${file.name} (already shown)")
                return
            }
            visited.add(canonicalPath)

            echo("${indent}${file.name}")

            val content = file.readText()
            val parser = BeancountParser()
            val parseResult = parser.parseString(content)

            val includes = parseResult.entries
                .filterIsInstance<Include>()
                .map { includeEntry ->
                    val includedFile = File(includeEntry.filename)
                    if (includedFile.isAbsolute) {
                        includedFile
                    } else {
                        File(file.parentFile, includeEntry.filename)
                    }
                }
                .filter { it.exists() }
                .map { it.canonicalPath }

            val shouldRecurse = !noRecursive
            includes.forEach { includePath ->
                val includeFile = File(includePath)
                if (shouldRecurse) {
                    collectDeps(includeFile, "$indent  ")
                } else {
                    echo("${indent}  ${includeFile.name}")
                }
            }
        }

        collectDeps(filename)
    }
}
