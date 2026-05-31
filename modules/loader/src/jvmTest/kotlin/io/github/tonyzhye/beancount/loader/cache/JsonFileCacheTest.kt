package io.github.tonyzhye.beancount.loader.cache

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JsonFileCacheTest {

    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun `cache should store and retrieve load result`() {
        val cache = JsonFileCache(cacheDir = tempDir)
        val filename = File(tempDir, "test.beancount").apply {
            writeText("2024-01-01 open Assets:Bank\n")
        }.canonicalPath

        val result = LoadResult(
            entries = listOf(
                Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank")
            ),
            errors = emptyList(),
            options = Options()
        )

        // Store
        cache.put(filename, autoPluginsEnabled = false, result)

        // Retrieve
        val cached = cache.get(filename, autoPluginsEnabled = false)
        assertNotNull(cached)
        assertEquals(1, cached!!.entries.size)
        assertTrue(cached.entries[0] is Open)
    }

    @Test
    fun `cache should return null for cache miss`() {
        val cache = JsonFileCache(cacheDir = tempDir)
        val filename = File(tempDir, "nonexistent.beancount").canonicalPath

        val cached = cache.get(filename, autoPluginsEnabled = false)
        assertNull(cached)
    }

    @Test
    fun `cache should invalidate when source file modified`() {
        val cache = JsonFileCache(cacheDir = tempDir)
        val sourceFile = File(tempDir, "test.beancount").apply {
            writeText("2024-01-01 open Assets:Bank\n")
        }
        val filename = sourceFile.canonicalPath

        val result = LoadResult(
            entries = listOf(
                Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank")
            ),
            errors = emptyList(),
            options = Options()
        )

        // Store
        cache.put(filename, autoPluginsEnabled = false, result)

        // Verify cache hit
        assertNotNull(cache.get(filename, autoPluginsEnabled = false))

        // Modify file
        Thread.sleep(100) // Ensure different modification time
        sourceFile.writeText("2024-01-01 open Assets:Bank\n2024-01-02 open Assets:Cash\n")

        // Verify cache miss (invalidated)
        val cached = cache.get(filename, autoPluginsEnabled = false)
        assertNull(cached)
    }

    @Test
    fun `cache should use correct filename pattern`() {
        val cache = JsonFileCache(cacheDir = tempDir)
        val sourceFile = File(tempDir, "myledger.beancount").apply {
            writeText("2024-01-01 open Assets:Bank\n")
        }

        val result = LoadResult(
            entries = emptyList(),
            errors = emptyList(),
            options = Options()
        )

        cache.put(sourceFile.canonicalPath, autoPluginsEnabled = false, result)

        // Verify cache file exists with expected name
        // nameWithoutExtension is "myledger", pattern is ".{filename}.beancount.kcache"
        val expectedCacheFile = File(tempDir, ".myledger.beancount.kcache")
        assertTrue(expectedCacheFile.exists(), "Cache file should exist at $expectedCacheFile")
    }

    @Test
    fun `cache should support custom pattern`() {
        val cache = JsonFileCache(
            cacheDir = tempDir,
            cachePattern = "cache_{filename}.json"
        )
        val sourceFile = File(tempDir, "test.beancount").apply {
            writeText("2024-01-01 open Assets:Bank\n")
        }
        val filename = sourceFile.canonicalPath

        val result = LoadResult(
            entries = emptyList(),
            errors = emptyList(),
            options = Options()
        )

        cache.put(filename, autoPluginsEnabled = false, result)

        // {filename} is nameWithoutExtension: "test"
        val expectedCacheFile = File(tempDir, "cache_test.json")
        assertTrue(expectedCacheFile.exists(), "Cache file should exist at $expectedCacheFile")
    }

    @Test
    fun `clear should remove cache file`() {
        val cache = JsonFileCache(cacheDir = tempDir)
        val sourceFile = File(tempDir, "test.beancount").apply {
            writeText("2024-01-01 open Assets:Bank\n")
        }
        val filename = sourceFile.canonicalPath

        val result = LoadResult(
            entries = emptyList(),
            errors = emptyList(),
            options = Options()
        )

        cache.put(filename, autoPluginsEnabled = false, result)
        assertNotNull(cache.get(filename, autoPluginsEnabled = false))

        cache.clear(filename)
        assertNull(cache.get(filename, autoPluginsEnabled = false))
    }

    @Test
    fun `cache should handle transactions with postings`() {
        val cache = JsonFileCache(cacheDir = tempDir)
        val sourceFile = File(tempDir, "test.beancount").apply {
            writeText("2024-01-01 * \"Test\"\n  Assets:Bank  100 USD\n  Income:Salary\n")
        }
        val filename = sourceFile.canonicalPath

        val result = LoadResult(
            entries = listOf(
                Transaction(
                    emptyMap(), LocalDate(2024, 1, 1), "*",
                    payee = null,
                    narration = "Test",
                    postings = listOf(
                        Posting("Assets:Bank", Amount(Decimal("100"), "USD")),
                        Posting("Income:Salary", Amount(Decimal("-100"), "USD"))
                    )
                )
            ),
            errors = emptyList(),
            options = Options()
        )

        cache.put(filename, autoPluginsEnabled = false, result)
        val cached = cache.get(filename, autoPluginsEnabled = false)

        assertNotNull(cached)
        assertEquals(1, cached!!.entries.size)
        val txn = cached.entries[0] as Transaction
        assertEquals(2, txn.postings.size)
        assertEquals("Assets:Bank", txn.postings[0].account)
        assertEquals(Decimal("100"), txn.postings[0].units!!.number)
    }

    @Test
    fun `cache should handle various directive types`() {
        val cache = JsonFileCache(cacheDir = tempDir)
        val sourceFile = File(tempDir, "test.beancount").apply {
            writeText("mixed directives")
        }
        val filename = sourceFile.canonicalPath

        val result = LoadResult(
            entries = listOf(
                Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", currencies = listOf("USD")),
                Commodity(emptyMap(), LocalDate(2024, 1, 1), "USD"),
                Price(emptyMap(), LocalDate(2024, 1, 1), "EUR", Amount(Decimal("1.1"), "USD")),
                Balance(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank", Amount(Decimal("100"), "USD")),
                Close(emptyMap(), LocalDate(2024, 12, 31), "Assets:Bank"),
                Note(emptyMap(), LocalDate(2024, 6, 1), "Assets:Bank", "Note text"),
                Event(emptyMap(), LocalDate(2024, 1, 1), "event-type", "Event description"),
                Query(emptyMap(), LocalDate(2024, 1, 1), "query-name", "SELECT *")
            ),
            errors = emptyList(),
            options = Options()
        )

        cache.put(filename, autoPluginsEnabled = false, result)
        val cached = cache.get(filename, autoPluginsEnabled = false)

        assertNotNull(cached)
        assertEquals(8, cached!!.entries.size)
        assertTrue(cached.entries[0] is Open)
        assertTrue(cached.entries[1] is Commodity)
        assertTrue(cached.entries[2] is Price)
        assertTrue(cached.entries[3] is Balance)
        assertTrue(cached.entries[4] is Close)
        assertTrue(cached.entries[5] is Note)
        assertTrue(cached.entries[6] is Event)
        assertTrue(cached.entries[7] is Query)
    }

    @Test
    fun `no-op cache should never cache`() {
        val cache = NoOpCache
        val result = LoadResult(
            entries = listOf(Open(emptyMap(), LocalDate(2024, 1, 1), "Assets:Bank")),
            errors = emptyList(),
            options = Options()
        )

        cache.put("test", false, result)
        assertNull(cache.get("test", false))
    }
}
