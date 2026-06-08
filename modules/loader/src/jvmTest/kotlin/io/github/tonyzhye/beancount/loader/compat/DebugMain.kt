package io.github.tonyzhye.beancount.loader.compat

import io.github.tonyzhye.beancount.loader.loadFile
import java.io.File

fun main() {
    val resourceDir = File("modules/loader/src/jvmTest/resources")
    val file = File(resourceDir, "complex_test.beancount")
    
    println("File exists: ${file.exists()}")
    println("File path: ${file.absolutePath}")
    
    val result = loadFile(file.absolutePath)
    
    println("=== Complex Ledger Results ===")
    println("Entries: ${result.entries.size}")
    println("Errors: ${result.errors.size}")
    println("Error messages:")
    result.errors.forEach { println("  - ${it.message}") }
    
    println("\nDirective types:")
    result.entries.groupBy { it::class.simpleName }
        .forEach { (type, entries) -> println("  $type: ${entries.size}") }
}
