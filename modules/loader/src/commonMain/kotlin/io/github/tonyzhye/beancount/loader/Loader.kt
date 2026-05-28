package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.parser.BeancountParser
import io.github.tonyzhye.beancount.parser.Booking
import io.github.tonyzhye.beancount.parser.Parser
import java.io.File

/**
 * Load a beancount file.
 * Based on beancount.loader.load_file.
 */
fun loadFile(
    filename: String,
    parser: Parser = BeancountParser(),
    extraValidations: List<Validation> = emptyList()
): LoadResult {
    val visitedFiles = mutableSetOf<String>()
    val result = loadFileInternal(filename, parser, visitedFiles)

    // Run validations only on the top-level result
    val allValidations = BASIC_VALIDATIONS + extraValidations
    val validationErrors = allValidations.flatMap {
        it(result.entries, result.options)
    }

    return LoadResult(
        entries = result.entries,
        errors = result.errors + validationErrors,
        options = result.options
    )
}

/**
 * Internal load function that handles includes but skips validation.
 * Validation is performed only at the top level.
 */
private fun loadFileInternal(
    filename: String,
    parser: Parser,
    visitedFiles: MutableSet<String>
): LoadResult {
    // Prevent circular includes
    val canonicalFile = File(filename).canonicalPath
    if (canonicalFile in visitedFiles) {
        return LoadResult(
            entries = emptyList(),
            errors = listOf(
                LoadError(
                    newMetadata(filename, 0),
                    "Circular include detected: $filename"
                )
            ),
            options = Options(filename = filename)
        )
    }
    visitedFiles.add(canonicalFile)

    // 1. Parse the file
    val (entries, parseErrors, options) = parser.parseFile(filename)

    // 2. Process includes
    val (resolvedEntries, includeErrors, mergedOptions) = resolveIncludes(
        entries, filename, parser, visitedFiles, options
    )

    // 3. Sort entries
    val sortedEntries = resolvedEntries.sorted()

    // 4. Booking - complete incomplete postings
    val (bookedEntries, bookingErrors) = Booking.book(sortedEntries, mergedOptions)

    // 5. Run transformations (plugins)
    val (transformedEntries, transformErrors) = runTransformations(bookedEntries, mergedOptions)

    // 6. Return result (validation is done at top level)
    return LoadResult(
        entries = transformedEntries,
        errors = parseErrors + includeErrors + bookingErrors + transformErrors,
        options = mergedOptions
    )
}

/**
 * Merge options from included files into the base options.
 */
private fun mergeOptions(base: Options, included: Options): Options {
    return base.copy(
        title = included.title.takeIf { it.isNotEmpty() } ?: base.title,
        operatingCurrencies = base.operatingCurrencies + included.operatingCurrencies,
        documents = base.documents + included.documents,
        plugin = base.plugin + included.plugin
    )
}

/**
 * Load a beancount string.
 * Convenience function for testing.
 */
fun loadString(
    content: String,
    parser: Parser = BeancountParser(),
    extraValidations: List<Validation> = emptyList()
): LoadResult {
    // 1. Parse the string
    val (entries, parseErrors, options) = parser.parseString(content)

    // 2. Sort entries
    val sortedEntries = entries.sorted()

    // 3. Booking - complete incomplete postings
    val (bookedEntries, bookingErrors) = Booking.book(sortedEntries, options)

    // 4. Run transformations (plugins)
    val (transformedEntries, transformErrors) = runTransformations(bookedEntries, options)

    // 5. Validate
    val allValidations = BASIC_VALIDATIONS + extraValidations
    val validationErrors = allValidations.flatMap {
        it(transformedEntries, options)
    }

    // 6. Return result
    return LoadResult(
        entries = transformedEntries,
        errors = parseErrors + bookingErrors + transformErrors + validationErrors,
        options = options
    )
}

/**
 * Resolve include directives by loading referenced files.
 * Returns a triple of (resolved entries, include errors, merged options).
 */
private fun resolveIncludes(
    entries: List<Directive>,
    baseFilename: String,
    parser: Parser,
    visitedFiles: MutableSet<String>,
    baseOptions: Options
): Triple<List<Directive>, List<BeancountError>, Options> {
    val resolvedEntries = mutableListOf<Directive>()
    val errors = mutableListOf<BeancountError>()
    var mergedOptions = baseOptions

    val baseDir = File(baseFilename).parentFile?.absolutePath ?: "."

    for (entry in entries) {
        if (entry is Include) {
            // Resolve the included file path
            val includedFile = if (File(entry.filename).isAbsolute) {
                File(entry.filename)
            } else {
                File(baseDir, entry.filename)
            }

            if (!includedFile.exists()) {
                errors.add(
                    LoadError(
                        entry.meta,
                        "Include file not found: ${entry.filename}",
                        entry
                    )
                )
                continue
            }

            // Recursively load the included file (without validation)
            val includedResult = loadFileInternal(
                includedFile.absolutePath,
                parser,
                visitedFiles
            )

            resolvedEntries.addAll(includedResult.entries)
            errors.addAll(includedResult.errors)
            mergedOptions = mergeOptions(mergedOptions, includedResult.options)
        } else {
            resolvedEntries.add(entry)
        }
    }

    return Triple(resolvedEntries, errors, mergedOptions)
}
