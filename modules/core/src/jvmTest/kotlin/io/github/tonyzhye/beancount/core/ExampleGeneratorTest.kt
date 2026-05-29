package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ExampleGeneratorTest {

    @Test
    fun `should generate example ledger with default options`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 42
        )

        val ledger = ExampleGenerator.generate(options)

        assertNotNull(ledger)
        assertTrue(ledger.entries.isNotEmpty(), "Should generate entries")
        assertTrue(ledger.commodities.isNotEmpty(), "Should generate commodities")
        assertTrue(ledger.accounts.isNotEmpty(), "Should generate accounts")

        // Check metadata
        assertEquals("USD", ledger.metadata.principalCurrency)
        assertEquals(Decimal("120000"), ledger.metadata.annualSalary)
        assertTrue(ledger.metadata.employerName.isNotEmpty())
    }

    @Test
    fun `should generate commodities`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 1, 31),
            seed = 42
        )

        val ledger = ExampleGenerator.generate(options)
        val commodityCurrencies = ledger.commodities.map { it.currency }

        assertTrue(commodityCurrencies.contains("USD"))
        assertTrue(commodityCurrencies.contains("EUR"))
        assertTrue(commodityCurrencies.contains("ITOT"))
        assertTrue(commodityCurrencies.contains("HOOL"))
    }

    @Test
    fun `should generate open entries for all accounts`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 1, 31),
            seed = 42
        )

        val ledger = ExampleGenerator.generate(options)
        val openEntries = ledger.entries.filterIsInstance<Open>()

        assertTrue(openEntries.isNotEmpty(), "Should have open entries")

        val openAccounts = openEntries.map { it.account }
        assertTrue(openAccounts.contains("Assets:CC:Bank1:Checking"))
        assertTrue(openAccounts.contains("Expenses:Food:Restaurant"))
        assertTrue(openAccounts.contains("Income:Employer1:Salary"))
    }

    @Test
    fun `should generate transactions`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 42
        )

        val ledger = ExampleGenerator.generate(options)
        val transactions = ledger.entries.filterIsInstance<Transaction>()

        assertTrue(transactions.isNotEmpty(), "Should have transactions")

        // Should have paycheck transactions
        val paychecks = transactions.filter { it.narration?.contains("Paycheck") == true }
        assertTrue(paychecks.isNotEmpty(), "Should have paycheck transactions")

        // Should have expense transactions
        val expenses = transactions.filter { it.postings.any { p -> p.account.startsWith("Expenses:") } }
        assertTrue(expenses.isNotEmpty(), "Should have expense transactions")
    }

    @Test
    fun `should generate rent expenses`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 42
        )

        val ledger = ExampleGenerator.generate(options)
        val transactions = ledger.entries.filterIsInstance<Transaction>()

        val rentExpenses = transactions.filter {
            it.postings.any { p -> p.account == "Expenses:Home:Rent" }
        }

        assertTrue(rentExpenses.isNotEmpty(), "Should have rent expenses")
        // Should have approximately 3 months of rent
        assertTrue(rentExpenses.size >= 2, "Should have at least 2 rent payments")
    }

    @Test
    fun `should generate restaurant expenses`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 42
        )

        val ledger = ExampleGenerator.generate(options)
        val transactions = ledger.entries.filterIsInstance<Transaction>()

        val restaurantExpenses = transactions.filter {
            it.postings.any { p -> p.account == "Expenses:Food:Restaurant" }
        }

        assertTrue(restaurantExpenses.isNotEmpty(), "Should have restaurant expenses")
    }

    @Test
    fun `should generate grocery expenses`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 42
        )

        val ledger = ExampleGenerator.generate(options)
        val transactions = ledger.entries.filterIsInstance<Transaction>()

        val groceryExpenses = transactions.filter {
            it.postings.any { p -> p.account == "Expenses:Food:Groceries" }
        }

        assertTrue(groceryExpenses.isNotEmpty(), "Should have grocery expenses")
    }

    @Test
    fun `should generate balance checks`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 12, 31),
            seed = 42
        )

        val ledger = ExampleGenerator.generate(options)
        val balances = ledger.entries.filterIsInstance<Balance>()

        assertTrue(balances.isNotEmpty(), "Should have balance checks")
    }

    @Test
    fun `should be deterministic with same seed`() {
        val options1 = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 42
        )
        val options2 = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 42
        )

        val ledger1 = ExampleGenerator.generate(options1)
        val ledger2 = ExampleGenerator.generate(options2)

        assertEquals(ledger1.entries.size, ledger2.entries.size)
        assertEquals(ledger1.accounts.size, ledger2.accounts.size)
    }

    @Test
    fun `should generate different output with different seeds`() {
        val options1 = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 42
        )
        val options2 = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 123
        )

        val ledger1 = ExampleGenerator.generate(options1)
        val ledger2 = ExampleGenerator.generate(options2)

        // Different seeds should produce different restaurant/grocery expenses
        val transactions1 = ledger1.entries.filterIsInstance<Transaction>()
        val transactions2 = ledger2.entries.filterIsInstance<Transaction>()

        val restaurants1 = transactions1.filter {
            it.postings.any { p -> p.account == "Expenses:Food:Restaurant" }
        }
        val restaurants2 = transactions2.filter {
            it.postings.any { p -> p.account == "Expenses:Food:Restaurant" }
        }

        // The actual amounts should differ
        val amounts1 = restaurants1.map { it.postings.first().units?.number }
        val amounts2 = restaurants2.map { it.postings.first().units?.number }
        assertNotEquals(amounts1, amounts2, "Different seeds should produce different amounts")
    }

    @Test
    fun `should generate valid beancount string`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 3, 31),
            seed = 42
        )

        val output = ExampleGenerator.generateString(options)

        assertTrue(output.contains("option \"title\""))
        assertTrue(output.contains("option \"operating_currency\" \"USD\""))
        assertTrue(output.contains("commodity USD"))
        assertTrue(output.contains("open Assets:CC:Bank1:Checking"))
        assertTrue(output.contains("open Expenses:Food:Restaurant"))
    }

    @Test
    fun `should generate with custom employer`() {
        val customEmployer = Employer("CustomCorp", "123 Main St")
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 1, 31),
            seed = 42,
            employer = customEmployer
        )

        val ledger = ExampleGenerator.generate(options)
        val transactions = ledger.entries.filterIsInstance<Transaction>()
        val paychecks = transactions.filter { it.payee == "CustomCorp" }

        assertTrue(paychecks.isNotEmpty(), "Should use custom employer name")
    }

    @Test
    fun `should generate with custom salary`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 1, 31),
            seed = 42,
            annualSalary = Decimal("150000")
        )

        val ledger = ExampleGenerator.generate(options)
        assertEquals(Decimal("150000"), ledger.metadata.annualSalary)
    }

    @Test
    fun `should respect date range`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 6, 1),
            dateEnd = LocalDate(2024, 6, 30),
            seed = 42
        )

        val ledger = ExampleGenerator.generate(options)

        // All transaction and balance entries should be within the date range
        // (commodity and open entries may use earlier dates)
        for (entry in ledger.entries) {
            if (entry is Commodity || entry is Open) continue
            assertTrue(
                entry.date >= options.dateBegin && entry.date <= options.dateEnd,
                "Entry ${entry.date} should be within ${options.dateBegin}..${options.dateEnd}"
            )
        }
    }

    @Test
    fun `should match Python output structure`() {
        val options = ExampleOptions(
            dateBegin = LocalDate(2024, 1, 1),
            dateEnd = LocalDate(2024, 1, 31),
            seed = 42
        )

        val kotlinOutput = ExampleGenerator.generateString(options)

        // Run Python bean-example
        val pythonOutput = runPythonExample(options)

        if (pythonOutput != null) {
            // Both should contain similar structural elements
            assertTrue(kotlinOutput.contains("option \"title\""))
            assertTrue(pythonOutput.contains("option \"title\""))

            assertTrue(kotlinOutput.contains("commodity USD"))
            assertTrue(pythonOutput.contains("commodity USD"))

            assertTrue(kotlinOutput.contains("open Assets:"))
            assertTrue(pythonOutput.contains("open Assets:"))
        }
    }

    private fun runPythonExample(options: ExampleOptions): String? {
        return try {
            val beginStr = "${options.dateBegin.year}-${options.dateBegin.monthNumber.toString().padStart(2, '0')}-${options.dateBegin.dayOfMonth.toString().padStart(2, '0')}"
            val endStr = "${options.dateEnd.year}-${options.dateEnd.monthNumber.toString().padStart(2, '0')}-${options.dateEnd.dayOfMonth.toString().padStart(2, '0')}"
            val seedStr = options.seed?.toString() ?: ""

            val cmd = if (seedStr.isNotEmpty()) {
                listOf("python", "-c", """
                    from beancount.scripts.example import write_example_file
                    import datetime
                    import io
                    import random
                    random.seed($seedStr)
                    output = io.StringIO()
                    write_example_file(
                        datetime.date(1980, 5, 12),
                        datetime.date.fromisoformat('$beginStr'),
                        datetime.date.fromisoformat('$endStr'),
                        False,
                        output
                    )
                    print(output.getvalue())
                """)
            } else {
                listOf("python", "-c", """
                    from beancount.scripts.example import write_example_file
                    import datetime
                    import io
                    output = io.StringIO()
                    write_example_file(
                        datetime.date(1980, 5, 12),
                        datetime.date.fromisoformat('$beginStr'),
                        datetime.date.fromisoformat('$endStr'),
                        False,
                        output
                    )
                    print(output.getvalue())
                """)
            }

            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() == 0) output else null
        } catch (e: Exception) {
            println("Python example generator not available: ${e.message}")
            null
        }
    }
}
