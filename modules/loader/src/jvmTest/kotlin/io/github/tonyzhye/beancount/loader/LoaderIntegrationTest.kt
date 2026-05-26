package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the complete loadFile/loadString flow.
 */
class LoaderIntegrationTest {

    @Test
    fun `should load complete ledger with booking`() {
        val input = """
            option "title" "Test Ledger"
            option "operating_currency" "USD"
            
            2023-01-01 open Assets:Cash USD
            2023-01-01 open Expenses:Food USD
            2023-01-01 open Income:Salary USD
            
            2023-01-15 * "Grocery shopping"
              Expenses:Food  50.00 USD
              Assets:Cash
            
            2023-01-31 * "Salary"
              Assets:Cash  1000.00 USD
              Income:Salary
            
            2023-02-01 balance Assets:Cash 950.00 USD
        """.trimIndent()
        
        val result = loadString(input)
        
        // Debug: print errors
        result.errors.forEach { println("ERROR: ${it.message}") }
        
        // Should have no errors
        assertEquals(0, result.errors.size, "Expected no errors but got: ${result.errors}")
        
        // Should have 6 entries (3 open + 2 transactions + 1 balance)
        assertEquals(6, result.entries.size, "Expected 6 entries")
        
        // Check options
        assertEquals("Test Ledger", result.options.title)
        assertEquals(listOf("USD"), result.options.operatingCurrencies)
        
        // Verify transactions were booked (missing postings filled in)
        val transactions = result.entries.filterIsInstance<Transaction>()
        assertEquals(2, transactions.size)
        
        // First transaction: Grocery shopping
        val groceryTx = transactions[0]
        assertEquals("Grocery shopping", groceryTx.narration)
        assertEquals(2, groceryTx.postings.size)
        
        // Cash posting should have been filled in
        val cashPosting = groceryTx.postings[1]
        assertEquals("Assets:Cash", cashPosting.account)
        assertEquals(Decimal("-50.00"), cashPosting.units?.number)
        assertEquals("USD", cashPosting.units?.currency)
        
        // Second transaction: Salary
        val salaryTx = transactions[1]
        assertEquals("Salary", salaryTx.narration)
        val salaryPosting = salaryTx.postings[1]
        assertEquals(Decimal("-1000.00"), salaryPosting.units?.number)
    }

    @Test
    fun `should detect validation errors`() {
        val input = """
            2023-01-01 open Assets:Cash USD
            
            2023-01-15 * "Invalid transaction"
              Expenses:Food  50.00 USD
              Assets:Cash  30.00 USD
        """.trimIndent()
        
        val result = loadString(input)
        
        // Should have validation error for unbalanced transaction
        assertTrue(result.errors.isNotEmpty(), "Expected validation errors")
        assertTrue(
            result.errors.any { it.message.contains("does not balance") },
            "Expected balance error"
        )
    }

    @Test
    fun `should detect duplicate opens`() {
        val input = """
            2023-01-01 open Assets:Cash USD
            2023-01-02 open Assets:Cash EUR
        """.trimIndent()
        
        val result = loadString(input)
        
        assertTrue(result.errors.isNotEmpty(), "Expected duplicate open error")
        assertTrue(
            result.errors.any { it.message.contains("Duplicate open") },
            "Expected duplicate open error"
        )
    }

    @Test
    fun `should handle empty ledger`() {
        val input = ""
        
        val result = loadString(input)
        
        assertEquals(0, result.entries.size)
        assertEquals(0, result.errors.size)
    }

    @Test
    fun `should handle ledger with tags and links`() {
        val input = """
            2023-01-01 open Assets:Cash USD
            2023-01-01 open Expenses:Travel USD
            
            2023-01-15 * "Flight to NYC" #business ^trip-nyc
              Expenses:Travel  500.00 USD
              Assets:Cash
        """.trimIndent()
        
        val result = loadString(input)
        
        // Debug: print errors
        result.errors.forEach { println("ERROR: ${it.message}") }
        
        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors}")
        
        val tx = result.entries.filterIsInstance<Transaction>().first()
        assertTrue(tx.tags.contains("business"))
        assertTrue(tx.links.contains("trip-nyc"))
    }

    @Test
    fun `should handle ledger with multiple currencies`() {
        val input = """
            2023-01-01 open Assets:Cash USD,EUR
            2023-01-01 open Expenses:Food USD
            
            2023-01-15 * "Lunch"
              Expenses:Food  20.00 USD
              Assets:Cash
        """.trimIndent()
        
        val result = loadString(input)
        
        // Debug: print errors
        result.errors.forEach { println("ERROR: ${it.message}") }
        
        assertEquals(0, result.errors.size, "Expected no errors: ${result.errors}")
        
        val openEntry = result.entries.filterIsInstance<Open>().first()
        assertEquals(listOf("USD", "EUR"), openEntry.currencies)
    }
}
