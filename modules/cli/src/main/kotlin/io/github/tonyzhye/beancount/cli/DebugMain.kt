package io.github.tonyzhye.beancount.cli

import io.github.tonyzhye.beancount.loader.loadString

fun main() {
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
    
    println("=== Debug Output ===")
    println("Entries: ${result.entries.size}")
    println("Errors: ${result.errors.size}")
    result.errors.forEach { error ->
        println("ERROR: ${error.message}")
    }
    println("===================")
}
