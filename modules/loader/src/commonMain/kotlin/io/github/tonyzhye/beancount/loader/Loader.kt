package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.loader.cache.BeancountCache
import io.github.tonyzhye.beancount.parser.BeancountParser
import io.github.tonyzhye.beancount.parser.Booking
import io.github.tonyzhye.beancount.parser.Parser
import java.io.File
import java.security.MessageDigest

/**
 * Timing logger interface for load operations.
 * Based on beancount.loader.load_file log_timings parameter.
 */
fun interface LoadTimingsLogger {
    fun logTiming(operation: String, indent: Int)
}

/**
 * Load a beancount file.
 * Based on beancount.loader.load_file.
 *
 * @param filename The name of the file to be parsed.
 * @param parser Parser implementation to use.
 * @param logTimings Optional logger for timing information.
 * @param extraValidations Additional validation functions to run.
 * @param autoPluginsEnabled Whether to enable auto plugins (auto_accounts, implicit_prices).
 * @param encoding File encoding (currently unused, always UTF-8).
 * @param cache Optional cache for loading results.
 * @return LoadResult containing entries, errors, and options.
 */
fun loadFile(
    filename: String,
    parser: Parser = BeancountParser(),
    logTimings: LoadTimingsLogger? = null,
    extraValidations: List<Validation> = emptyList(),
    autoPluginsEnabled: Boolean = false,
    encoding: String? = null,
    cache: BeancountCache? = null
): LoadResult {
    // Normalize filename (expand vars, make absolute)
    val normalizedFilename = File(filename).let { file ->
        val expanded = File(System.getenv().entries.fold(file.path) { path, (key, value) ->
            path.replace("$$key", value)
        }).let { File(it.path.replace("~", System.getProperty("user.home") ?: "~")) }
        if (expanded.isAbsolute) expanded else File(File(".").absolutePath, expanded.path)
    }.let {
        // canonicalPath may throw IOException on Windows (e.g. temp files with 8.3 short names),
        // so fall back to absolutePath when it fails.
        try { it.canonicalPath } catch (_: java.io.IOException) { it.absolutePath }
    }

    // Try cache first
    if (cache != null) {
        val cached = cache.get(normalizedFilename, autoPluginsEnabled)
        if (cached != null) {
            // Run validations on cached result
            logTimings?.logTiming("validate (cached)", indent = 1)
            val allValidations = BASIC_VALIDATIONS + extraValidations
            val validationErrors = allValidations.flatMap {
                it(cached.entries, cached.options)
            }
            return LoadResult(
                entries = cached.entries,
                errors = cached.errors + validationErrors,
                options = cached.options
            )
        }
    }

    val visitedFiles = mutableSetOf<String>()
    val result = loadFileInternal(
        normalizedFilename,
        parser,
        visitedFiles,
        logTimings,
        autoPluginsEnabled
    )

    // Store in cache
    cache?.put(normalizedFilename, autoPluginsEnabled, result)

    // Run validations only on the top-level result
    logTimings?.logTiming("validate", indent = 1)
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
    visitedFiles: MutableSet<String>,
    logTimings: LoadTimingsLogger? = null,
    autoPluginsEnabled: Boolean = false
): LoadResult {
    // Prevent circular includes
    val canonicalFile = try { File(filename).canonicalPath } catch (_: java.io.IOException) { File(filename).absolutePath }
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
    logTimings?.logTiming("parse", indent = 1)
    val (entries, parseErrors, options) = parser.parseFile(filename)

    // 2. Process includes
    val (resolvedEntries, includeErrors, mergedOptions) = resolveIncludes(
        entries, filename, parser, visitedFiles, options, logTimings, autoPluginsEnabled
    )

    // 3. Sort entries
    val sortedEntries = resolvedEntries.sorted()

    // 4. Booking - complete incomplete postings
    logTimings?.logTiming("booking", indent = 1)
    val (bookedEntries, bookingErrors) = Booking.book(sortedEntries, mergedOptions)

    // 5. Run transformations (plugins)
    logTimings?.logTiming("run_transformations", indent = 1)
    val optionsWithAuto = if (autoPluginsEnabled) {
        mergedOptions.copy(autoPluginsEnabled = true)
    } else mergedOptions
    val (transformedEntries, transformErrors) = runTransformations(bookedEntries, optionsWithAuto)

    // 6. Return result (validation is done at top level)
    return LoadResult(
        entries = transformedEntries,
        errors = parseErrors + includeErrors + bookingErrors + transformErrors,
        options = optionsWithAuto
    )
}

/**
 * Merge options from included files into the base options.
 * Based on beancount.loader.aggregate_options_map.
 *
 * Merging rules:
 * - Title: included overrides base if non-empty
 * - Operating currencies: union of both lists, preserving order
 * - Documents: concatenate lists
 * - Plugins: concatenate lists
 * - Display context: merge both contexts
 * - Tolerance map: merge both maps
 */
private fun mergeOptions(base: Options, included: Options): Options {
    val mergedDcontext = DisplayContext().apply {
        updateFrom(base.dcontext)
        updateFrom(included.dcontext)
    }

    val mergedToleranceMap = base.toleranceMap.toMutableMap().apply {
        putAll(included.toleranceMap)
    }

    return base.copy(
        title = included.title.takeIf { it.isNotEmpty() } ?: base.title,
        operatingCurrencies = (base.operatingCurrencies + included.operatingCurrencies).distinct(),
        documents = base.documents + included.documents,
        include = base.include + included.include,
        plugin = base.plugin + included.plugin,
        dcontext = mergedDcontext,
        toleranceMap = mergedToleranceMap,
        toleranceMultiplier = included.toleranceMultiplier.takeIf { it != Decimal("0.5") } ?: base.toleranceMultiplier,
        inferToleranceFromCost = base.inferToleranceFromCost || included.inferToleranceFromCost,
        allowDeprecatedNoneForTagsAndLinks = base.allowDeprecatedNoneForTagsAndLinks || included.allowDeprecatedNoneForTagsAndLinks,
        insertPythonpath = base.insertPythonpath || included.insertPythonpath
    )
}

/**
 * Load a beancount string.
 * Convenience function for testing.
 *
 * @param content Beancount source string.
 * @param parser Parser implementation to use.
 * @param logTimings Optional logger for timing information.
 * @param extraValidations Additional validation functions to run.
 * @param autoPluginsEnabled Whether to enable auto plugins.
 * @return LoadResult containing entries, errors, and options.
 */
fun loadString(
    content: String,
    parser: Parser = BeancountParser(),
    logTimings: LoadTimingsLogger? = null,
    extraValidations: List<Validation> = emptyList(),
    autoPluginsEnabled: Boolean = false
): LoadResult {
    // 1. Parse the string
    logTimings?.logTiming("parse", indent = 1)
    val (entries, parseErrors, options) = parser.parseString(content)

    // 2. Sort entries
    val sortedEntries = entries.sorted()

    // 3. Booking - complete incomplete postings
    logTimings?.logTiming("booking", indent = 1)
    val (bookedEntries, bookingErrors) = Booking.book(sortedEntries, options)

    // 4. Run transformations (plugins)
    logTimings?.logTiming("run_transformations", indent = 1)
    val optionsWithAuto = if (autoPluginsEnabled) {
        options.copy(autoPluginsEnabled = true)
    } else options
    val (transformedEntries, transformErrors) = runTransformations(bookedEntries, optionsWithAuto)

    // 5. Validate
    logTimings?.logTiming("validate", indent = 1)
    val allValidations = BASIC_VALIDATIONS + extraValidations
    val validationErrors = allValidations.flatMap {
        it(transformedEntries, optionsWithAuto)
    }

    // 6. Return result
    return LoadResult(
        entries = transformedEntries,
        errors = parseErrors + bookingErrors + transformErrors + validationErrors,
        options = optionsWithAuto
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
    baseOptions: Options,
    logTimings: LoadTimingsLogger? = null,
    autoPluginsEnabled: Boolean = false
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
                visitedFiles,
                logTimings,
                autoPluginsEnabled
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

/**
 * Compute an MD5 hash of the input file contents for cache invalidation detection.
 * Based on beancount.loader.compute_input_hash.
 *
 * @param filename The name of the file to hash.
 * @return A hex string of the MD5 hash, or null if the file cannot be read.
 */
fun computeInputHash(filename: String): String? {
    return try {
        val file = File(filename)
        if (!file.exists()) return null
        val digest = MessageDigest.getInstance("MD5")
        digest.update(file.readBytes())
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }
}

/**
 * Compute an MD5 hash of a string content for cache invalidation detection.
 *
 * @param content The string content to hash.
 * @return A hex string of the MD5 hash.
 */
fun computeContentHash(content: String): String {
    val digest = MessageDigest.getInstance("MD5")
    digest.update(content.toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString("") { "%02x".format(it) }
}
