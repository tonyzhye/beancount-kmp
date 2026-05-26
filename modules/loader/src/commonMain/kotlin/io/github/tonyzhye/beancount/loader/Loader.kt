package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.parser.ParseResult
import io.github.tonyzhye.beancount.parser.Parser

/**
 * Load a beancount file.
 * Based on beancount.loader.load_file.
 */
fun loadFile(
    filename: String,
    parser: Parser,
    extraValidations: List<Validation> = emptyList()
): LoadResult {
    // 1. Parse the file
    val (entries, parseErrors, options) = parser.parseFile(filename)
    
    // 2. Sort entries
    val sortedEntries = entries.sorted()
    
    // 3. TODO: Booking - apply incomplete entries
    // val (bookedEntries, bookingErrors) = booking.book(sortedEntries, options)
    
    // 4. TODO: Run transformations
    // val (transformedEntries, transformErrors) = runTransformations(bookedEntries, options)
    
    // 5. Validate
    val allValidations = BASIC_VALIDATIONS + extraValidations
    val validationErrors = allValidations.flatMap { 
        it(sortedEntries, options) 
    }
    
    // 6. Return result
    return LoadResult(
        entries = sortedEntries,
        errors = parseErrors + validationErrors,
        options = options
    )
}
