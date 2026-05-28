package io.github.tonyzhye.beancount.parser

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BookingDebugTest {

    @Test
    fun `debug cost basis completion`() {
        val entries = listOf(
            Open(
                meta = emptyMap(),
                date = LocalDate(2023, 1, 1),
                account = "Assets:Investments",
                currencies = listOf("AAPL")
            ),
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2023, 1, 16),
                flag = "*",
                narration = "部分卖出",
                postings = listOf(
                    Posting(
                        account = "Assets:Investments",
                        units = Amount(Decimal("-5"), "AAPL"),
                        cost = CostSpec(numberPer = Decimal("100.00"), currency = "USD", date = LocalDate(2023, 1, 15))
                    ),
                    Posting(
                        account = "Assets:Bank",
                        units = Amount(Decimal("600"), "USD")
                    ),
                    Posting(
                        account = "Income:CapitalGains"
                    )
                )
            )
        )

        val (result, errors) = Booking.book(entries, Options())

        println("=== Booking Debug ===")
        println("Errors: ${errors.size}")
        errors.forEach { println("  ${it.message}") }
        
        val txn = result.filterIsInstance<Transaction>().firstOrNull()
        assertNotNull(txn)
        
        println("Postings:")
        txn.postings.forEach { posting ->
            println("  ${posting.account}: units=${posting.units}, cost=${posting.cost}")
        }

        // The missing posting should be filled in
        val capitalGains = txn.postings.find { it.account == "Income:CapitalGains" }
        assertNotNull(capitalGains, "CapitalGains posting should be completed")
        assertEquals(Amount(Decimal("-100"), "USD"), capitalGains.units, "CapitalGains should be -100 USD")
    }
}
