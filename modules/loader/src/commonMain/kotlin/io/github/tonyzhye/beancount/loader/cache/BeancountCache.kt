package io.github.tonyzhye.beancount.loader.cache

import io.github.tonyzhye.beancount.core.LoadResult

/**
 * Interface for beancount file loading cache.
 *
 * Implementations should handle:
 * - Cache key computation (file content hash, config hash, etc.)
 * - Cache storage and retrieval
 * - Cache invalidation when source files change
 * - Cleanup of stale cache entries
 *
 * This interface is defined in commonMain with zero dependencies
 * to maintain KMP compatibility.
 */
interface BeancountCache {
    /**
     * Retrieve a cached load result for the given file.
     *
     * @param filename Canonical path of the file to load
     * @param autoPluginsEnabled Whether auto plugins are enabled (affects cache key)
     * @return Cached LoadResult if valid, null if cache miss or invalid
     */
    fun get(filename: String, autoPluginsEnabled: Boolean): LoadResult?

    /**
     * Store a load result in the cache.
     *
     * @param filename Canonical path of the file
     * @param autoPluginsEnabled Whether auto plugins are enabled
     * @param result Load result to cache
     */
    fun put(filename: String, autoPluginsEnabled: Boolean, result: LoadResult)

    /**
     * Clear cache for a specific file.
     *
     * @param filename The canonical file path
     */
    fun clear(filename: String)
}

/**
 * No-op cache implementation that never caches.
 * Useful when caching is disabled.
 */
object NoOpCache : BeancountCache {
    override fun get(filename: String, autoPluginsEnabled: Boolean): LoadResult? = null
    override fun put(filename: String, autoPluginsEnabled: Boolean, result: LoadResult) {}
    override fun clear(filename: String) {}
}
