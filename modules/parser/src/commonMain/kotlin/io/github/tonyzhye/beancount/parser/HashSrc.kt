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

import java.io.File
import java.security.MessageDigest

/**
 * Compute a hash of the source code for cache invalidation and version control.
 * Based on beancount.parser.hashsrc.
 *
 * This is used to detect when a source file has changed and cached results
 * need to be invalidated.
 */
object HashSrc {

    /**
     * Compute an MD5 hash of a file's contents.
     *
     * @param filename The file to hash.
     * @return A hex string of the MD5 hash, or null if the file cannot be read.
     */
    fun computeFileHash(filename: String): String? {
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
     * Compute an MD5 hash of a string's contents.
     *
     * @param content The content to hash.
     * @return A hex string of the MD5 hash.
     */
    fun computeStringHash(content: String): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(content.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute a combined hash of multiple files.
     * This is useful when a ledger consists of multiple included files.
     *
     * @param filenames List of files to hash.
     * @return A hex string of the combined MD5 hash.
     */
    fun computeCombinedHash(filenames: List<String>): String {
        val digest = MessageDigest.getInstance("MD5")
        for (filename in filenames.sorted()) {
            val file = File(filename)
            if (file.exists()) {
                digest.update(file.name.toByteArray(Charsets.UTF_8))
                digest.update(file.readBytes())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Check if a file has changed by comparing its current hash with a stored hash.
     *
     * @param filename The file to check.
     * @param storedHash The previously stored hash.
     * @return True if the file has changed or cannot be read.
     */
    fun hasChanged(filename: String, storedHash: String?): Boolean {
        val currentHash = computeFileHash(filename)
        return currentHash == null || currentHash != storedHash
    }

    /**
     * Compute a hash for cache key generation.
     * Combines the file hash with processing options.
     *
     * @param filename The file to hash.
     * @param autoPluginsEnabled Whether auto plugins are enabled.
     * @return A cache key string.
     */
    fun computeCacheKey(filename: String, autoPluginsEnabled: Boolean = false): String {
        val fileHash = computeFileHash(filename) ?: "missing"
        return "$fileHash:$autoPluginsEnabled"
    }
}
