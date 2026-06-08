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

package io.github.tonyzhye.beancount.parser

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Tests for HashSrc functionality.
 */
class HashSrcTest {

    @Test
    fun `computeFileHash should return consistent hash`() {
        val tempFile = File.createTempFile("hash_test", ".beancount")
        tempFile.writeText("2024-01-01 open Assets:Bank USD")
        tempFile.deleteOnExit()

        val hash1 = HashSrc.computeFileHash(tempFile.absolutePath)
        val hash2 = HashSrc.computeFileHash(tempFile.absolutePath)

        assertNotNull(hash1)
        assertEquals(hash1, hash2)
        assertEquals(32, hash1?.length)
    }

    @Test
    fun `computeFileHash should return null for nonexistent file`() {
        val hash = HashSrc.computeFileHash("/nonexistent/file.beancount")
        assertNull(hash)
    }

    @Test
    fun `computeStringHash should return consistent hash`() {
        val content = "2024-01-01 open Assets:Bank USD"
        val hash1 = HashSrc.computeStringHash(content)
        val hash2 = HashSrc.computeStringHash(content)

        assertEquals(hash1, hash2)
        assertEquals(32, hash1.length)
    }

    @Test
    fun `computeStringHash should return different hash for different content`() {
        val hash1 = HashSrc.computeStringHash("content1")
        val hash2 = HashSrc.computeStringHash("content2")

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `computeCombinedHash should combine multiple files`() {
        val tempFile1 = File.createTempFile("hash1", ".beancount")
        val tempFile2 = File.createTempFile("hash2", ".beancount")
        tempFile1.writeText("file1 content")
        tempFile2.writeText("file2 content")
        tempFile1.deleteOnExit()
        tempFile2.deleteOnExit()

        val combined = HashSrc.computeCombinedHash(listOf(
            tempFile1.absolutePath,
            tempFile2.absolutePath
        ))

        assertEquals(32, combined.length)
    }

    @Test
    fun `hasChanged should detect changes`() {
        val tempFile = File.createTempFile("change_test", ".beancount")
        tempFile.writeText("original content")
        tempFile.deleteOnExit()

        val originalHash = HashSrc.computeFileHash(tempFile.absolutePath)
        assertFalse(HashSrc.hasChanged(tempFile.absolutePath, originalHash))

        tempFile.writeText("modified content")
        assertTrue(HashSrc.hasChanged(tempFile.absolutePath, originalHash))
    }

    @Test
    fun `computeCacheKey should include auto plugins flag`() {
        val tempFile = File.createTempFile("cache_test", ".beancount")
        tempFile.writeText("test content")
        tempFile.deleteOnExit()

        val key1 = HashSrc.computeCacheKey(tempFile.absolutePath, false)
        val key2 = HashSrc.computeCacheKey(tempFile.absolutePath, true)

        assertNotEquals(key1, key2)
        assertTrue(key1.contains(":"))
    }
}
