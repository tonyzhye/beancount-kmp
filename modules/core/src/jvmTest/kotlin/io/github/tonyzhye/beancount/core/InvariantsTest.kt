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

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for invariant checking functionality.
 */
class InvariantsTest {

    @Test
    fun `invariant should pass when conditions are met`() {
        val add = invariant(
            pre = { a: Int -> a >= 0 },
            post = { result: Int -> result >= 0 }
        ) { a: Int -> a + 1 }

        assertEquals(3, add(2))
    }

    @Test
    fun `invariant should fail when precondition violated`() {
        val add = invariant(
            pre = { a: Int -> a >= 0 }
        ) { a: Int -> a + 1 }

        assertThrows(InvariantViolationError::class.java) {
            add(-1)
        }
    }

    @Test
    fun `invariant should fail when postcondition violated`() {
        val subtract = invariant(
            post = { result: Int -> result >= 0 }
        ) { a: Int -> a - 10 }

        assertThrows(InvariantViolationError::class.java) {
            subtract(5)
        }
    }

    @Test
    fun `invariant2 should work with two arguments`() {
        val add = invariant2(
            pre = { a: Int, b: Int -> a >= 0 && b >= 0 },
            post = { result: Int -> result >= 0 }
        ) { a: Int, b: Int -> a + b }

        assertEquals(5, add(2, 3))
        assertThrows(InvariantViolationError::class.java) {
            add(-1, 3)
        }
    }

    @Test
    fun `assertInvariant should pass for true condition`() {
        assertDoesNotThrow {
            assertInvariant(true) { "This should not fail" }
        }
    }

    @Test
    fun `assertInvariant should fail for false condition`() {
        val error = assertThrows(InvariantViolationError::class.java) {
            assertInvariant(false) { "Expected error message" }
        }
        assertTrue(error.message?.contains("Expected error message") == true)
    }

    @Test
    fun `assertNotNull should return value`() {
        val value = assertNotNull("test", "myValue")
        assertEquals("test", value)
    }

    @Test
    fun `assertNotNull should fail for null`() {
        val error = assertThrows(InvariantViolationError::class.java) {
            assertNotNull(null, "myValue")
        }
        assertTrue(error.message?.contains("myValue is null") == true)
    }

    @Test
    fun `assertNotEmpty should return collection`() {
        val list = assertNotEmpty(listOf(1, 2, 3), "myList")
        assertEquals(3, list.size)
    }

    @Test
    fun `assertNotEmpty should fail for empty`() {
        val error = assertThrows(InvariantViolationError::class.java) {
            assertNotEmpty(emptyList<Int>(), "myList")
        }
        assertTrue(error.message?.contains("myList is empty") == true)
    }

    @Test
    fun `assertNotBlank should return string`() {
        val str = assertNotBlank("hello", "myString")
        assertEquals("hello", str)
    }

    @Test
    fun `assertNotBlank should fail for blank`() {
        assertThrows(InvariantViolationError::class.java) {
            assertNotBlank("", "myString")
        }
        assertThrows(InvariantViolationError::class.java) {
            assertNotBlank("   ", "myString")
        }
        assertThrows(InvariantViolationError::class.java) {
            assertNotBlank(null, "myString")
        }
    }

    @Test
    fun `assertNonNegative should pass for positive`() {
        assertDoesNotThrow {
            assertNonNegative(5, "count")
            assertNonNegative(0, "count")
        }
    }

    @Test
    fun `assertNonNegative should fail for negative`() {
        val error = assertThrows(InvariantViolationError::class.java) {
            assertNonNegative(-1, "count")
        }
        assertTrue(error.message?.contains("count is negative") == true)
    }

    @Test
    fun `assertDateRange should pass for valid range`() {
        assertDoesNotThrow {
            assertDateRange(
                LocalDate(2024, 1, 1),
                LocalDate(2024, 12, 31)
            )
        }
    }

    @Test
    fun `assertDateRange should fail for invalid range`() {
        assertThrows(InvariantViolationError::class.java) {
            assertDateRange(
                LocalDate(2024, 12, 31),
                LocalDate(2024, 1, 1)
            )
        }
    }
}
