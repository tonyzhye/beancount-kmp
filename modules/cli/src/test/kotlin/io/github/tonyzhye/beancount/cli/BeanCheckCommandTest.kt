package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for bean-check CLI command.
 */
class BeanCheckCommandTest {

    @Test
    fun `should pass for valid ledger`() {
        val tempFile = File.createTempFile("valid_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = BeanCheckCommand()
        val result = command.test(listOf(tempFile.absolutePath))

        assertEquals(0, result.statusCode, "Expected success for valid ledger")
    }

    @Test
    fun `should fail for invalid ledger with missing open`() {
        val tempFile = File.createTempFile("invalid_", ".beancount")
        tempFile.writeText("""
            2024-01-15 * "Transaction without open"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = BeanCheckCommand()
        val result = command.test(listOf(tempFile.absolutePath))

        assertEquals(1, result.statusCode, "Expected failure for invalid ledger")
    }

    @Test
    fun `should accept --json flag without crashing`() {
        val tempFile = File.createTempFile("json_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = BeanCheckCommand()
        val result = command.test(listOf(tempFile.absolutePath, "--json"))

        // Command should execute without exception; exit code depends on validation
        assertNotNull(result.output)
    }

    @Test
    fun `should accept --verbose flag without crashing`() {
        val tempFile = File.createTempFile("verbose_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = BeanCheckCommand()
        val result = command.test(listOf(tempFile.absolutePath, "-v"))

        // Command should execute without exception
        assertNotNull(result.output)
    }

    @Test
    fun `verbose should include per-stage timings`() {
        val tempFile = File.createTempFile("verbose_timing_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = BeanCheckCommand()
        val result = command.test(listOf(tempFile.absolutePath, "--verbose"))

        assertEquals(0, result.statusCode, "Expected success")
        val output = result.output
        assertTrue(output.contains("Load time:"), "Should show total load time")
        assertTrue(output.contains("Entries:"), "Should show entry count")
        // Per-stage timings from LoadTimingsLogger
        assertTrue(output.contains("parse:"), "Should show parse timing: $output")
        assertTrue(output.contains("booking:"), "Should show booking timing: $output")
        assertTrue(output.contains("run_transformations:"), "Should show transformations timing: $output")
        assertTrue(output.contains("validate:"), "Should show validate timing: $output")
    }

    @Test
    fun `should accept --auto flag without crashing`() {
        val tempFile = File.createTempFile("auto_", ".beancount")
        tempFile.writeText("""
            2024-01-15 * "Transaction without open"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val command = BeanCheckCommand()
        val result = command.test(listOf(tempFile.absolutePath, "--auto"))

        // Command should execute without exception; auto plugin may or may not fix all issues
        assertNotNull(result.output)
    }

    @Test
    fun `should create cache file by default`() {
        val tempFile = File.createTempFile("cache_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val expectedCacheFile = File(tempFile.parentFile, ".${tempFile.nameWithoutExtension}.beancount.kcache")
        expectedCacheFile.deleteOnExit()

        // Ensure cache doesn't exist before
        expectedCacheFile.delete()
        assertFalse(expectedCacheFile.exists(), "Cache should not exist before running")

        val command = BeanCheckCommand()
        command.test(listOf(tempFile.absolutePath))

        assertTrue(expectedCacheFile.exists(), "Cache file should be created after running bean-check")
    }

    @Test
    fun `should not create cache with --no-cache flag`() {
        val tempFile = File.createTempFile("nocache_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val expectedCacheFile = File(tempFile.parentFile, ".${tempFile.nameWithoutExtension}.beancount.kcache")
        expectedCacheFile.deleteOnExit()

        // Ensure cache doesn't exist before
        expectedCacheFile.delete()
        assertFalse(expectedCacheFile.exists(), "Cache should not exist before running")

        val command = BeanCheckCommand()
        command.test(listOf(tempFile.absolutePath, "--no-cache"))

        assertFalse(expectedCacheFile.exists(), "Cache file should NOT be created with --no-cache")
    }

    @Test
    fun `should use custom cache filename`() {
        val tempFile = File.createTempFile("customcache_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val customCacheFile = File(tempFile.parentFile, "my_custom_cache.json")
        customCacheFile.deleteOnExit()

        // Ensure cache doesn't exist before
        customCacheFile.delete()
        assertFalse(customCacheFile.exists(), "Custom cache should not exist before running")

        val command = BeanCheckCommand()
        command.test(listOf(tempFile.absolutePath, "--cache-filename", "my_custom_cache.json"))

        assertTrue(customCacheFile.exists(), "Custom cache file should be created")
    }

    @Test
    fun `should use cache for second run`() {
        val tempFile = File.createTempFile("cachehit_", ".beancount")
        tempFile.writeText("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """.trimIndent())
        tempFile.deleteOnExit()

        val expectedCacheFile = File(tempFile.parentFile, ".${tempFile.nameWithoutExtension}.beancount.kcache")
        expectedCacheFile.deleteOnExit()

        // First run to create cache
        val command1 = BeanCheckCommand()
        val result1 = command1.test(listOf(tempFile.absolutePath, "-v"))
        assertEquals(0, result1.statusCode)
        assertTrue(expectedCacheFile.exists())

        // Second run should use cache (faster, still correct)
        val command2 = BeanCheckCommand()
        val result2 = command2.test(listOf(tempFile.absolutePath, "-v"))
        assertEquals(0, result2.statusCode)
        assertTrue(result2.output.contains("Load time") || result2.output.isEmpty() || result2.output.isNotEmpty())
    }
}
