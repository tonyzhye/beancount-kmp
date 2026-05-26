package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import io.github.tonyzhye.beancount.parser.BeancountParser
import io.github.tonyzhye.beancount.parser.Booking
import io.github.tonyzhye.beancount.parser.Parser

/**
 * Load a beancount file.
 * Based on beancount.loader.load_file.
 */
fun loadFile(
    filename: String,
    parser: Parser = BeancountParser(),
    extraValidations: List<Validation> = emptyList()
): LoadResult {
    // 1. Parse the file
    val (entries, parseErrors, options) = parser.parseFile(filename)
    
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
