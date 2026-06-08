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

package io.github.tonyzhye.beancount.loader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Tests for input hash functions.
 */
class InputHashTest {

    @Test
    fun `computeContentHash should compute consistent hash for string`() {
        val content = "2024-01-01 open Assets:Bank USD"
        val hash1 = computeContentHash(content)
        val hash2 = computeContentHash(content)

        assertEquals(hash1, hash2)
        assertEquals(32, hash1.length) // MD5 hex string length
    }

    @Test
    fun `computeContentHash should return different hash for different content`() {
        val hash1 = computeContentHash("content1")
        val hash2 = computeContentHash("content2")

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `computeInputHash should compute hash for file`() {
        val tempFile = File.createTempFile("hash_test", ".beancount")
        tempFile.writeText("2024-01-01 open Assets:Bank USD")
        tempFile.deleteOnExit()

        val hash = computeInputHash(tempFile.absolutePath)
        assertNotNull(hash)
        assertEquals(32, hash?.length)
    }

    @Test
    fun `computeInputHash should return null for nonexistent file`() {
        val hash = computeInputHash("/nonexistent/file.beancount")
        assertNull(hash)
    }

    @Test
    fun `computeContentHash should handle empty string`() {
        val hash = computeContentHash("")
        assertEquals(32, hash.length)
    }
}
