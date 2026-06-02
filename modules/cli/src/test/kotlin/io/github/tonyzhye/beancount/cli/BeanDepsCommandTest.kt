package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class BeanDepsCommandTest {

    @Test
    fun `should show direct includes`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "beancount_deps_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val mainFile = File(tempDir, "main.beancount")
            val includeFile = File(tempDir, "included.beancount")

            mainFile.writeText("""
                2024-01-01 include "included.beancount"
                
                2024-01-01 open Assets:Bank USD
            """.trimIndent())

            includeFile.writeText("""
                2024-01-01 open Expenses:Food USD
            """.trimIndent())

            val command = BeanDepsCommand()
            val result = command.test(listOf(mainFile.absolutePath, "--no-recursive"))

            println("DEBUG OUTPUT: [${result.output}]")
            println("DEBUG STDERR: [${result.stderr}]")
            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("main.beancount"), "Expected output to contain 'main.beancount' but was: ${result.output}")
            assertTrue(result.output.contains("included.beancount"), "Expected output to contain 'included.beancount' but was: ${result.output}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `should show recursive includes`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "beancount_deps_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val mainFile = File(tempDir, "main.beancount")
            val includeFile1 = File(tempDir, "level1.beancount")
            val includeFile2 = File(tempDir, "level2.beancount")

            mainFile.writeText("""
                2024-01-01 include "level1.beancount"
                
                2024-01-01 open Assets:Bank USD
            """.trimIndent())

            includeFile1.writeText("""
                2024-01-01 include "level2.beancount"
                
                2024-01-01 open Expenses:Food USD
            """.trimIndent())

            includeFile2.writeText("""
                2024-01-01 open Income:Salary USD
            """.trimIndent())

            val command = BeanDepsCommand()
            val result = command.test(listOf(mainFile.absolutePath))

            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("main.beancount"))
            assertTrue(result.output.contains("level1.beancount"))
            assertTrue(result.output.contains("level2.beancount"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `should handle circular includes`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "beancount_deps_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val mainFile = File(tempDir, "main.beancount")
            val includeFile = File(tempDir, "included.beancount")

            mainFile.writeText("""
                2024-01-01 include "included.beancount"
                
                2024-01-01 open Assets:Bank USD
            """.trimIndent())

            includeFile.writeText("""
                2024-01-01 include "main.beancount"
                
                2024-01-01 open Expenses:Food USD
            """.trimIndent())

            val command = BeanDepsCommand()
            val result = command.test(listOf(mainFile.absolutePath))

            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("already shown"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
