package io.github.tonyzhye.beancount.loader

import io.github.tonyzhye.beancount.core.Open
import io.github.tonyzhye.beancount.core.Transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TestUtilsTest {

    @Test
    fun `loadDoc should parse basic ledger`() {
        val result = loadDoc("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        assertTrue(result.errors.isEmpty(), "Should have no errors: ${result.errors}")
        assertEquals(3, result.entries.size)
    }

    @Test
    fun `loadDocWithAuto should auto-create Open directives`() {
        val result = loadDocWithAuto("""
            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        assertTrue(result.errors.isEmpty(),
            "Should have no errors with auto plugins: ${result.errors}")

        val openEntries = result.entries.filterIsInstance<Open>()
        assertEquals(2, openEntries.size)
    }

    @Test
    fun `loadEntries should return only entries`() {
        val entries = loadEntries("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        assertEquals(2, entries.size)
        assertTrue(entries.any { it is Open })
        assertTrue(entries.any { it is Transaction })
    }

    @Test
    fun `loadDocAssertNoErrors should pass for valid ledger`() {
        val result = loadDocAssertNoErrors("""
            2024-01-01 open Assets:Bank:Checking USD
            2024-01-01 open Income:Salary USD

            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        assertEquals(3, result.entries.size)
    }

    @Test
    fun `assertNoErrors should throw for invalid ledger`() {
        val result = loadString("""
            2024-01-15 * "Paycheck"
              Assets:Bank:Checking  100.00 USD
              Income:Salary
        """)

        assertThrows(AssertionError::class.java) {
            assertNoErrors(result)
        }
    }
}
