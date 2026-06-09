package io.github.tonyzhye.beancount.loader.compat

import io.github.tonyzhye.beancount.loader.loadFile
import io.github.tonyzhye.beancount.loader.loadString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Comprehensive compatibility verification tests.
 * 
 * Tests complex real-world scenarios against Python beancount v3.2.3.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class CompatibilityVerificationTest {

    private val resourceDir = File("src/jvmTest/resources")

    @Test
    fun `complex ledger should load without errors`() {
        val file = File(resourceDir, "complex_test.beancount")
        assumeTrue(file.exists(), "Test file not found: ${file.absolutePath}")

        val result = loadFile(file.absolutePath)

        println("=== Complex Ledger Results ===")
        println("Entries: ${result.entries.size}")
        println("Errors: ${result.errors.size}")
        println("Options: ${result.options}")
        
        if (result.errors.isNotEmpty()) {
            println("Error messages:")
            result.errors.forEach { println("  - ${it.message}") }
        }

        // Should have no critical errors
        val criticalErrors = result.errors.filter { 
            !it.message.contains("not found", ignoreCase = true) // Document path may not exist
        }
        
        assertTrue(criticalErrors.isEmpty(), 
            "Expected no critical errors: ${criticalErrors.map { it.message }}")

        // Verify all directive types are present
        val directiveTypes = result.entries.map { it::class.simpleName }.toSet()
        val expectedTypes = setOf(
            "Open", "Commodity", "Transaction", "Balance", 
            "Pad", "Document", "Event", "Note", "Price", 
            "Query", "Custom", "Close"
        )
        
        val missingTypes = expectedTypes - directiveTypes
        assertTrue(missingTypes.isEmpty(), 
            "Missing directive types: $missingTypes. Found: $directiveTypes")
    }

    @Test
    fun `large ledger should parse within reasonable time`() {
        val file = File(resourceDir, "large_test.beancount")
        assumeTrue(file.exists(), "Test file not found: ${file.absolutePath}")

        val startTime = System.currentTimeMillis()
        val result = loadFile(file.absolutePath)
        val duration = System.currentTimeMillis() - startTime

        println("=== Large Ledger Performance ===")
        println("File size: ${file.length() / 1024} KB")
        println("Entries: ${result.entries.size}")
        println("Parse time: ${duration}ms")
        println("Throughput: ${result.entries.size * 1000 / duration} entries/sec")

        // Should complete within 10 seconds
        assertTrue(duration < 10000, 
            "Parsing took too long: ${duration}ms")

        // Should have the expected number of transactions
        val txns = result.entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
        assertTrue(txns.size >= 1000, 
            "Expected at least 1000 transactions, got ${txns.size}")
    }

    @Test
    fun `ledger with all booking methods should process correctly`() {
        val content = """
            option "operating_currency" "USD"
            
            2024-01-01 open Assets:Invest:FIFO AAPL "FIFO"
            2024-01-01 open Assets:Invest:LIFO AAPL "LIFO"
            2024-01-01 open Assets:Invest:HIFO AAPL "HIFO"
            2024-01-01 open Assets:Invest:STRICT AAPL "STRICT"
            2024-01-01 open Assets:Bank USD
            2024-01-01 open Income:Investments USD
            
            2024-02-01 * "Buy AAPL for FIFO"
              Assets:Invest:FIFO  10 AAPL {150.00 USD}
              Assets:Bank
            
            2024-02-15 * "Buy more AAPL for FIFO"
              Assets:Invest:FIFO  5 AAPL {160.00 USD}
              Assets:Bank
            
            2024-03-01 * "Sell AAPL FIFO"
              Assets:Invest:FIFO  -8 AAPL {150.00 USD}
              Assets:Bank  1200.00 USD
              Income:Investments
            
            2024-02-01 * "Buy AAPL for LIFO"
              Assets:Invest:LIFO  10 AAPL {150.00 USD}
              Assets:Bank
            
            2024-02-15 * "Buy more AAPL for LIFO"
              Assets:Invest:LIFO  5 AAPL {160.00 USD}
              Assets:Bank
            
            2024-03-01 * "Sell AAPL LIFO"
              Assets:Invest:LIFO  -8 AAPL {}
              Assets:Bank  1250.00 USD
            
            2024-02-01 * "Buy AAPL for HIFO"
              Assets:Invest:HIFO  10 AAPL {150.00 USD}
              Assets:Bank
            
            2024-02-15 * "Buy more AAPL for HIFO"
              Assets:Invest:HIFO  5 AAPL {160.00 USD}
              Assets:Bank
            
            2024-03-01 * "Sell AAPL HIFO"
              Assets:Invest:HIFO  -5 AAPL {160.00 USD}
              Assets:Bank  800.00 USD
              Income:Investments
            
            2024-02-01 * "Buy AAPL for STRICT"
              Assets:Invest:STRICT  10 AAPL {150.00 USD, 2024-02-01}
              Assets:Bank
            
            2024-03-01 * "Sell AAPL STRICT"
              Assets:Invest:STRICT  -5 AAPL {150.00 USD, 2024-02-01}
              Assets:Bank  750.00 USD
              Income:Investments
        """.trimIndent()

        val result = loadString(content)

        println("=== Booking Methods Test ===")
        println("Entries: ${result.entries.size}")
        println("Errors: ${result.errors.size}")
        if (result.errors.isNotEmpty()) {
            println("Error messages:")
            result.errors.forEach { println("  - ${it.message}") }
        }

        // Should have no errors for valid booking methods
        assertTrue(result.errors.isEmpty(), 
            "Expected no errors: ${result.errors.map { it.message }}")

        // Verify transactions were processed
        val txns = result.entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
        assertEquals(11, txns.size, "Expected 11 transactions")
    }

    @Test
    fun `compound cost syntax should work end-to-end`() {
        val content = """
            option "operating_currency" "USD"
            
            2024-01-01 open Assets:Invest:Stocks HOOL
            2024-01-01 open Assets:Bank USD
            
            2024-02-01 * "Buy stock with compound cost"
              Assets:Invest:Stocks  10 HOOL {10 # 100 USD}
              Assets:Bank
            
            2024-03-01 * "Buy stock with total only"
              Assets:Invest:Stocks  5 HOOL {# 100 USD}
              Assets:Bank  -100.00 USD
        """.trimIndent()

        val result = loadString(content)

        println("=== Compound Cost Test ===")
        println("Errors: ${result.errors.size}")
        if (result.errors.isNotEmpty()) {
            println("Error messages:")
            result.errors.forEach { println("  - ${it.message}") }
        }

        assertTrue(result.errors.isEmpty(), 
            "Expected no errors: ${result.errors.map { it.message }}")

        val txns = result.entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
        assertEquals(2, txns.size)

        // Verify compound cost was parsed correctly
        val firstTxn = txns[0]
        val firstCost = firstTxn.postings[0].cost
        assertNotNull(firstCost)
        assertEquals("USD", firstCost?.currency)
    }

    @Test
    fun `metadata and tags should propagate correctly`() {
        val content = """
            option "operating_currency" "USD"
            
            2024-01-01 open Assets:Bank USD
            2024-01-01 open Expenses:Food USD
            
            pushtag #monthly
            
            2024-02-01 * "Grocery" #food ^grocery-2024
              Expenses:Food  100.00 USD
                store: "Whole Foods"
                category: "Organic"
              Assets:Bank
            
            poptag #monthly
            
            2024-02-15 * "Another expense"
              Expenses:Food  50.00 USD
              Assets:Bank
        """.trimIndent()

        val result = loadString(content)

        println("=== Metadata Test ===")
        println("Errors: ${result.errors.size}")

        assertTrue(result.errors.isEmpty(), 
            "Expected no errors: ${result.errors.map { it.message }}")

        val txns = result.entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
        assertEquals(2, txns.size)

        // First transaction should have tags
        val firstTxn = txns[0]
        assertTrue(firstTxn.tags.contains("monthly"), "Should have pushtag")
        assertTrue(firstTxn.tags.contains("food"), "Should have transaction tag")
        assertTrue(firstTxn.links.contains("grocery-2024"), "Should have link")

        // Second transaction should not have pushtag
        val secondTxn = txns[1]
        assertFalse(secondTxn.tags.contains("monthly"), "Should not have poptag")

        // Verify posting metadata
        val firstPosting = firstTxn.postings[0]
        assertEquals("Whole Foods", firstPosting.meta?.get("store"))
        assertEquals("Organic", firstPosting.meta?.get("category"))
    }

    @Test
    fun `pad directive should generate transactions`() {
        val content = """
            option "operating_currency" "USD"
            
            2024-01-01 open Assets:Bank USD
            2024-01-01 open Equity:Opening USD
            
            2024-01-01 * "Opening"
              Assets:Bank  1000.00 USD
              Equity:Opening
            
            2024-02-01 pad Assets:Bank Equity:Opening
            
            2024-02-02 balance Assets:Bank  2000.00 USD
        """.trimIndent()

        val result = loadString(content)

        println("=== Pad Directive Test ===")
        println("Entries: ${result.entries.size}")
        println("Errors: ${result.errors.size}")

        // Pad should generate a transaction to make balance match
        val txns = result.entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
        assertTrue(txns.size >= 2, "Pad should generate a transaction")
    }

    @Test
    fun `multi-file include should work`() {
        val mainFile = File.createTempFile("main", ".beancount")
        val includedFile = File.createTempFile("included", ".beancount")
        
        try {
            mainFile.writeText("""
                option "title" "Main Ledger"
                2024-01-01 open Assets:Bank USD
                include "${includedFile.name}"
                2024-02-01 * "Main Transaction"
                  Assets:Bank  100.00 USD
                  Income:Salary
            """.trimIndent())

            includedFile.writeText("""
                2024-01-01 open Income:Salary USD
                2024-01-15 * "Included Transaction"
                  Assets:Bank  500.00 USD
                  Income:Salary
            """.trimIndent())

            val result = loadFile(mainFile.absolutePath)

            println("=== Multi-file Include Test ===")
            println("Entries: ${result.entries.size}")
            println("Errors: ${result.errors.size}")

            assertTrue(result.errors.isEmpty(), 
                "Expected no errors: ${result.errors.map { it.message }}")

            val txns = result.entries.filterIsInstance<io.github.tonyzhye.beancount.core.Transaction>()
            assertEquals(2, txns.size, "Should have 2 transactions from both files")
        } finally {
            mainFile.delete()
            includedFile.delete()
        }
    }

    @Test
    fun `invalid syntax should produce errors`() {
        val content = """
            option "operating_currency" "USD"
            
            2024-01-01 open Assets:Bank USD
            
            ; This should fail - unbalanced transaction
            2024-02-01 * "Unbalanced"
              Assets:Bank  100.00 USD
              Expenses:Food  50.00 USD
        """.trimIndent()

        val result = loadString(content)

        println("=== Invalid Syntax Test ===")
        println("Errors: ${result.errors.size}")
        println("Error messages: ${result.errors.map { it.message }}")

        // Should have errors for unbalanced transaction
        assertTrue(result.errors.isNotEmpty(), 
            "Expected errors for invalid syntax")
    }

    private fun assumeTrue(condition: Boolean, message: String) {
        if (!condition) {
            println("SKIPPED: $message")
            return
        }
        assertTrue(condition, message)
    }
}
