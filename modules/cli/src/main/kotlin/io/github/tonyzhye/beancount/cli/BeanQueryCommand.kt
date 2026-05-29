package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.github.tonyzhye.beancount.loader.loadFile
import io.github.tonyzhye.beancount.query.QueryEngine
import io.github.tonyzhye.beancount.query.QueryFormatter
import java.io.File

/**
 * bean-query command implementation.
 *
 * Provides interactive and batch query execution against beancount ledgers.
 */
class BeanQueryCommand : CliktCommand(
    name = "bean-query",
    help = "Query a beancount ledger using BQL"
) {
    private val filename by argument("FILENAME")
        .file(mustExist = true, canBeDir = false)
        .help("Beancount input file")

    private val queryFile by option("-f", "--file")
        .file(mustExist = true, canBeDir = false)
        .help("Execute queries from file")

    private val format by option("--format")
        .default("table")
        .help("Output format: table, csv, json, tsv")

    private val query by argument("QUERY")
        .help("BQL query to execute (optional in interactive mode)")
        .optional()

    override fun run() {
        // Load ledger
        echo("Loading ${filename.name}...", err = true)
        val result = loadFile(filename.absolutePath)

        if (result.errors.isNotEmpty()) {
            result.errors.forEach { error ->
                echo("ERROR: ${error.message}", err = true)
            }
        }

        val engine = QueryEngine(result.entries)
        val outputFormat = parseFormat(format)

        when {
            queryFile != null -> {
                // Batch mode from file
                executeFromFile(engine, queryFile as java.io.File, outputFormat)
            }
            query != null -> {
                // Single query mode
                executeQuery(engine, query as String, outputFormat)
            }
            else -> {
                // Interactive REPL
                runInteractive(engine, outputFormat)
            }
        }
    }

    private fun executeQuery(engine: QueryEngine, queryString: String, format: QueryFormatter.Format) {
        try {
            val startTime = System.currentTimeMillis()
            val queryResult = engine.execute(queryString)
            val elapsed = System.currentTimeMillis() - startTime

            echo(QueryFormatter.format(queryResult, format))
            echo("(${queryResult.rows.size} rows in ${elapsed}ms)", err = true)
        } catch (e: Exception) {
            echo("ERROR: ${e.message}", err = true)
        }
    }

    private fun executeFromFile(engine: QueryEngine, file: File, format: QueryFormatter.Format) {
        val queries = file.readText().lines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("--") && !it.trimStart().startsWith("#") }
            .joinToString(" ")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (q in queries) {
            echo("\n\u003e $q", err = true)
            executeQuery(engine, q, format)
        }
    }

    private fun runInteractive(engine: QueryEngine, format: QueryFormatter.Format) {
        echo("Beancount Query (BQL)", err = true)
        echo("Type .tables to list tables, .schema TABLE for columns, .quit to exit", err = true)
        echo("", err = true)

        while (true) {
            print("bql> ")
            System.out.flush()

            val input = readlnOrNull()?.trim() ?: break

            when {
                input.isBlank() -> continue
                input == ".quit" || input == ".exit" || input == ".q" -> break
                input == ".tables" -> {
                    echo("Tables: ${engine.getTableNames().joinToString(", ")}", err = true)
                }
                input.startsWith(".schema") -> {
                    val tableName = input.substringAfter(".schema").trim()
                    if (tableName.isEmpty()) {
                        engine.getTableNames().forEach { table ->
                            echo("$table: ${engine.getColumnNames(table).joinToString(", ")}", err = true)
                        }
                    } else {
                        echo("$tableName: ${engine.getColumnNames(tableName).joinToString(", ")}", err = true)
                    }
                }
                input.startsWith(".") -> {
                    echo("Unknown command: $input", err = true)
                }
                else -> {
                    executeQuery(engine, input, format)
                }
            }
        }
    }

    private fun parseFormat(format: String): QueryFormatter.Format {
        return when (format.lowercase()) {
            "csv" -> QueryFormatter.Format.CSV
            "json" -> QueryFormatter.Format.JSON
            "tsv" -> QueryFormatter.Format.TSV
            else -> QueryFormatter.Format.TABLE
        }
    }
}
