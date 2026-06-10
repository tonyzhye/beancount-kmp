package io.github.tonyzhye.beancount.loader.cache

import io.github.tonyzhye.beancount.core.LoadResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.security.MessageDigest

/**
 * JSON file-based cache implementation for beancount loading.
 *
 * Cache file location: same directory as source file, named `.{filename}.beancount.kcache`
 * Customizable via constructor parameter.
 *
 * This implementation is JVM-only and uses kotlinx-serialization-json.
 */
class JsonFileCache(
    /** Base directory for cache files. Default: same directory as source file. */
    private val cacheDir: File? = null,
    /** Cache filename pattern. Use {filename} placeholder for source filename. */
    private val cachePattern: String = ".{filename}.beancount.kcache"
) : BeancountCache {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "_class"
    }

    override fun get(filename: String, autoPluginsEnabled: Boolean): LoadResult? {
        val cacheFile = resolveCacheFile(filename)
        if (!cacheFile.exists()) return null

        try {
            val content = cacheFile.readText()
            val entry = json.decodeFromString<CacheEntryDto>(content)

            // Check cache version
            if (entry.version != 1) return null

            // Verify source files haven't changed
            for ((sourceFile, cachedModTime) in entry.sourceFiles) {
                val file = File(sourceFile)
                if (!file.exists() || file.lastModified() != cachedModTime) {
                    return null
                }
            }

            return entry.toDomain()
        } catch (e: Exception) {
            // Cache corrupted or unreadable
            cacheFile.delete()
            return null
        }
    }

    override fun put(filename: String, autoPluginsEnabled: Boolean, result: LoadResult) {
        val cacheFile = resolveCacheFile(filename)

        // Collect source files and their modification times
        val sourceFiles = mutableMapOf<String, Long>()
        val mainFile = File(filename)
        if (mainFile.exists()) {
            // canonicalPath may throw IOException on Windows (8.3 short names); fall back to absolutePath
            val key = try { mainFile.canonicalPath } catch (_: java.io.IOException) { mainFile.absolutePath }
            sourceFiles[key] = mainFile.lastModified()
        }

        // Collect include files from the result
        val includeFiles = collectIncludeFiles(result)
        for (includeFile in includeFiles) {
            val file = File(includeFile)
            if (file.exists()) {
                // canonicalPath may throw IOException on Windows; fall back to absolutePath
                val key = try { file.canonicalPath } catch (_: java.io.IOException) { file.absolutePath }
                sourceFiles[key] = file.lastModified()
            }
        }

        val entry = result.toDto(sourceFiles)
        val content = json.encodeToString(entry)

        try {
            cacheFile.writeText(content)
        } catch (e: Exception) {
            // Failed to write cache - ignore silently
        }
    }

    override fun clear(filename: String) {
        val cacheFile = resolveCacheFile(filename)
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }

    /**
     * Resolve cache file path from source filename.
     */
    private fun resolveCacheFile(filename: String): File {
        val sourceFile = File(filename)
        val baseName = sourceFile.nameWithoutExtension
        val cacheName = cachePattern.replace("{filename}", baseName)

        return if (cacheDir != null) {
            File(cacheDir, cacheName)
        } else {
            File(sourceFile.parentFile ?: File("."), cacheName)
        }
    }

    /**
     * Collect all include file paths from entries.
     */
    private fun collectIncludeFiles(result: LoadResult): Set<String> {
        return result.entries
            .filterIsInstance<io.github.tonyzhye.beancount.core.Include>()
            .map { it.filename }
            .toSet()
    }
}

/**
 * Compute SHA-256 hash of file content.
 */
fun sha256File(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
