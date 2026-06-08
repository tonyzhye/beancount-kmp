/*
 * Beancount JVM - A JVM implementation of Beancount
 * Copyright (C) 2026  Beancount JVM Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Beancount by Martin Blais
 * Original project: https://github.com/beancount/beancount
 */

package io.github.tonyzhye.beancount.core

/**
 * Runtime invariant checking utilities.
 * Based on beancount.utils.invariants.
 *
 * These utilities allow injecting preconditions and postconditions
 * to functions for testing and debugging purposes.
 *
 * Example usage:
 * ```kotlin
 * val checkedAdd = invariant(
 *     pre = { a: Int, b: Int -> a >= 0 && b >= 0 },
 *     post = { result: Int -> result >= 0 }
 * ) { a: Int, b: Int -> a + b }
 *
 * checkedAdd(1, 2) // OK
 * checkedAdd(-1, 2) // Throws InvariantViolationError
 * ```
 */

/** Exception thrown when an invariant is violated */
class InvariantViolationError(message: String) : AssertionError(message)

/**
 * Wrap a function with invariant checks.
 *
 * @param pre Optional precondition check. Receives the same arguments as the function.
 * @param post Optional postcondition check. Receives the return value.
 * @param fn The function to wrap.
 * @return A new function with invariant checks.
 */
fun <A, R> invariant(
    pre: ((A) -> Boolean)? = null,
    post: ((R) -> Boolean)? = null,
    fn: (A) -> R
): (A) -> R {
    return { arg ->
        pre?.let {
            if (!it(arg)) {
                throw InvariantViolationError("Precondition failed for argument: $arg")
            }
        }
        val result = fn(arg)
        post?.let {
            if (!it(result)) {
                throw InvariantViolationError("Postcondition failed for result: $result")
            }
        }
        result
    }
}

/**
 * Wrap a function with two arguments and invariant checks.
 */
fun <A, B, R> invariant2(
    pre: ((A, B) -> Boolean)? = null,
    post: ((R) -> Boolean)? = null,
    fn: (A, B) -> R
): (A, B) -> R {
    return { a, b ->
        pre?.let {
            if (!it(a, b)) {
                throw InvariantViolationError("Precondition failed for arguments: $a, $b")
            }
        }
        val result = fn(a, b)
        post?.let {
            if (!it(result)) {
                throw InvariantViolationError("Postcondition failed for result: $result")
            }
        }
        result
    }
}

/**
 * Assert that a condition is true at runtime.
 * Unlike Kotlin's `require()` or `check()`, this is designed for
 * invariant checking in data processing code.
 *
 * @param condition The condition to check.
 * @param message The error message if the condition is false.
 */
fun assertInvariant(condition: Boolean, message: () -> String) {
    if (!condition) {
        throw InvariantViolationError(message())
    }
}

/**
 * Assert that a value is not null.
 *
 * @param value The value to check.
 * @param name The name of the value for error reporting.
 * @return The non-null value.
 */
fun <T : Any> assertNotNull(value: T?, name: String = "value"): T {
    if (value == null) {
        throw InvariantViolationError("Invariant violated: $name is null")
    }
    return value
}

/**
 * Assert that a collection is not empty.
 *
 * @param collection The collection to check.
 * @param name The name of the collection for error reporting.
 * @return The non-empty collection.
 */
fun <T, C : Collection<T>> assertNotEmpty(collection: C, name: String = "collection"): C {
    if (collection.isEmpty()) {
        throw InvariantViolationError("Invariant violated: $name is empty")
    }
    return collection
}

/**
 * Assert that a string is not blank.
 *
 * @param str The string to check.
 * @param name The name of the string for error reporting.
 * @return The non-blank string.
 */
fun assertNotBlank(str: String?, name: String = "string"): String {
    if (str.isNullOrBlank()) {
        throw InvariantViolationError("Invariant violated: $name is blank")
    }
    return str
}

/**
 * Assert that a number is non-negative.
 *
 * @param number The number to check.
 * @param name The name of the number for error reporting.
 * @return The non-negative number.
 */
fun assertNonNegative(number: Number, name: String = "number"): Number {
    if (number.toDouble() < 0) {
        throw InvariantViolationError("Invariant violated: $name is negative ($number)")
    }
    return number
}

/**
 * Assert that a date range is valid (start <= end).
 *
 * @param start The start date.
 * @param end The end date.
 */
fun assertDateRange(start: kotlinx.datetime.LocalDate, end: kotlinx.datetime.LocalDate) {
    if (start > end) {
        throw InvariantViolationError(
            "Invariant violated: start date ($start) is after end date ($end)"
        )
    }
}
