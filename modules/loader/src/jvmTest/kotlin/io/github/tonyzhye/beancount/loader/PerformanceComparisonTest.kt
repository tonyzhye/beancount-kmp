package io.github.tonyzhye.beancount.loader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Performance comparison tests between Kotlin/JVM and Python beancount.
 * Focus on small files (50KB - 1MB) for quick iteration.
 * 
 * Tests are skipped if Python beancount is not available.
 */
class PerformanceComparisonTest {

    /**
     * Small files for quick performance comparison.
     */
    private val smallTestFiles = listOf(
        Triple("test_50kb.bean", "50 KB", "examples/benchmark/test_50kb.bean"),
        Triple("test_100kb.bean", "100 KB", "examples/benchmark/test_100kb.bean"),
        Triple("test_500kb.bean", "500 KB", "examples/benchmark/test_500kb.bean"),
        Triple("test_1mb.bean", "1 MB", "examples/benchmark/test_1mb.bean"),
    )
    
    /**
     * Check if Python with beancount is available.
     */
    private fun isPythonBeancountAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("python", "-c", "import beancount; print(beancount.__version__)")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Measure Python beancount load time using direct command.
     */
    private fun measurePythonLoadTime(filepath: String): Long {
        val pythonPath = filepath.replace("\\", "/")
        val pythonScript = """
import time, os
from beancount.loader import load_file

filepath = os.environ.get('BEAN_FILE')

# Warmup
load_file(filepath)

# Timed run
times = []
for _ in range(3):
    start = time.perf_counter()
    entries, errors, options = load_file(filepath)
    end = time.perf_counter()
    times.append((end - start) * 1000)

avg = sum(times) / len(times)
print(round(avg, 1))
""".trimIndent()
        
        val pb = ProcessBuilder("python", "-c", pythonScript)
        pb.environment()["BEAN_FILE"] = pythonPath
        val process = pb
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        
        return output.toDouble().toLong()
    }
    
    /**
     * Measure Kotlin/JVM load time.
     */
    private fun measureJvmLoadTime(filepath: String): Long {
        // Warmup
        loadFile(filepath)
        
        // Timed runs
        val times = mutableListOf<Long>()
        repeat(3) {
            val time = measureTimeMillis {
                loadFile(filepath)
            }
            times.add(time)
        }
        
        return times.average().toLong()
    }
    
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `compare JVM vs Python for small files`() {
        assumeTrue(isPythonBeancountAvailable(), "Python beancount not available")
        
        println("\n" + "=".repeat(80))
        println("PERFORMANCE COMPARISON: Kotlin/JVM vs Python Beancount (Small Files)")
        println("=".repeat(80))
        println(String.format("%-20s %-10s %-12s %-12s %-10s", 
            "File", "Size", "JVM (ms)", "Python (ms)", "Ratio"))
        println("-".repeat(80))
        
        smallTestFiles.forEach { (filename, sizeLabel, relativePath) ->
            val projectRoot = File("").absoluteFile.parentFile.parentFile
            val filepath = File(projectRoot, relativePath).absolutePath
            val file = File(filepath)
            
            if (!file.exists()) {
                println(String.format("%-20s %-10s %-12s", filename, sizeLabel, "NOT FOUND"))
                return@forEach
            }
            
            try {
                // Measure JVM
                val jvmTime = measureJvmLoadTime(filepath)
                
                // Measure Python
                val pythonTime = measurePythonLoadTime(filepath)
                
                // Calculate ratio
                val ratio = if (pythonTime > 0) {
                    jvmTime.toDouble() / pythonTime.toDouble()
                } else 0.0
                
                val ratioStr = when {
                    ratio > 1.0 -> String.format("%.2fx slower", ratio)
                    ratio < 1.0 -> String.format("%.2fx faster", 1.0 / ratio)
                    else -> "same"
                }
                
                println(String.format("%-20s %-10s %-12d %-12d %-10s",
                    filename, sizeLabel, jvmTime, pythonTime, ratioStr))
                
            } catch (e: Exception) {
                println(String.format("%-20s %-10s ERROR: %s", filename, sizeLabel, e.message))
            }
        }
        
        println("=".repeat(80) + "\n")
    }
    
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `JVM-only benchmark for small files`() {
        println("\n" + "=".repeat(70))
        println("JVM-ONLY BENCHMARK")
        println("=".repeat(70))
        println(String.format("%-20s %-10s %-12s %-15s", 
            "File", "Size", "Time (ms)", "Throughput"))
        println("-".repeat(70))
        
        smallTestFiles.forEach { (filename, sizeLabel, relativePath) ->
            val projectRoot = File("").absoluteFile.parentFile.parentFile
            val filepath = File(projectRoot, relativePath).absolutePath
            val file = File(filepath)
            
            if (!file.exists()) {
                println(String.format("%-20s %-10s %-12s", filename, sizeLabel, "NOT FOUND"))
                return@forEach
            }
            
            try {
                // Warmup
                loadFile(filepath)
                
                // Measure
                val times = mutableListOf<Long>()
                repeat(3) {
                    val time = measureTimeMillis {
                        loadFile(filepath)
                    }
                    times.add(time)
                }
                
                val avgTime = times.average().toLong()
                val minTime = times.min()
                val fileSizeKb = file.length() / 1024.0
                val throughput = if (avgTime > 0) fileSizeKb / (avgTime / 1000.0) else 0.0
                
                println(String.format("%-20s %-10s %-12d %-15.1f KB/s",
                    filename, sizeLabel, avgTime, throughput))
                println(String.format("  (min: %dms)", minTime))
                
            } catch (e: Exception) {
                println(String.format("%-20s %-10s ERROR: %s", filename, sizeLabel, e.message))
            }
        }
        
        println("=".repeat(70) + "\n")
    }

    /**
     * Large files for extended performance comparison.
     */
    private val largeTestFiles = listOf(
        Triple("test_5mb.bean", "5 MB", "examples/benchmark/test_5mb.bean"),
        Triple("test_10mb.bean", "10 MB", "examples/benchmark/test_10mb.bean"),
    )
    
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    fun `compare JVM vs Python for large files`() {
        assumeTrue(isPythonBeancountAvailable(), "Python beancount not available")
        
        println("\n" + "=".repeat(80))
        println("PERFORMANCE COMPARISON: Kotlin/JVM vs Python Beancount (Large Files)")
        println("=".repeat(80))
        println(String.format("%-20s %-10s %-12s %-12s %-10s", 
            "File", "Size", "JVM (ms)", "Python (ms)", "Ratio"))
        println("-".repeat(80))
        
        largeTestFiles.forEach { (filename, sizeLabel, relativePath) ->
            val projectRoot = File("").absoluteFile.parentFile.parentFile
            val filepath = File(projectRoot, relativePath).absolutePath
            val file = File(filepath)
            
            if (!file.exists()) {
                println(String.format("%-20s %-10s %-12s", filename, sizeLabel, "NOT FOUND"))
                return@forEach
            }
            
            try {
                // Measure JVM
                val jvmTime = measureJvmLoadTime(filepath)
                
                // Measure Python
                val pythonTime = measurePythonLoadTime(filepath)
                
                // Calculate ratio
                val ratio = if (pythonTime > 0) {
                    jvmTime.toDouble() / pythonTime.toDouble()
                } else 0.0
                
                val ratioStr = when {
                    ratio > 1.0 -> String.format("%.2fx slower", ratio)
                    ratio < 1.0 -> String.format("%.2fx faster", 1.0 / ratio)
                    else -> "same"
                }
                
                println(String.format("%-20s %-10s %-12d %-12d %-10s",
                    filename, sizeLabel, jvmTime, pythonTime, ratioStr))
                
            } catch (e: Exception) {
                println(String.format("%-20s %-10s ERROR: %s", filename, sizeLabel, e.message))
            }
        }
        
        println("=".repeat(80) + "\n")
    }
    
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `JVM-only benchmark for large files`() {
        println("\n" + "=".repeat(70))
        println("JVM-ONLY BENCHMARK (Large Files)")
        println("=".repeat(70))
        println(String.format("%-20s %-10s %-12s %-15s", 
            "File", "Size", "Time (ms)", "Throughput"))
        println("-".repeat(70))
        
        largeTestFiles.forEach { (filename, sizeLabel, relativePath) ->
            val projectRoot = File("").absoluteFile.parentFile.parentFile
            val filepath = File(projectRoot, relativePath).absolutePath
            val file = File(filepath)
            
            if (!file.exists()) {
                println(String.format("%-20s %-10s %-12s", filename, sizeLabel, "NOT FOUND"))
                return@forEach
            }
            
            try {
                // Warmup
                loadFile(filepath)
                
                // Measure
                val times = mutableListOf<Long>()
                repeat(3) {
                    val time = measureTimeMillis {
                        loadFile(filepath)
                    }
                    times.add(time)
                }
                
                val avgTime = times.average().toLong()
                val minTime = times.min()
                val fileSizeKb = file.length() / 1024.0
                val throughput = if (avgTime > 0) fileSizeKb / (avgTime / 1000.0) else 0.0
                
                println(String.format("%-20s %-10s %-12d %-15.1f KB/s",
                    filename, sizeLabel, avgTime, throughput))
                println(String.format("  (min: %dms)", minTime))
                
            } catch (e: Exception) {
                println(String.format("%-20s %-10s ERROR: %s", filename, sizeLabel, e.message))
            }
        }
        
        println("=".repeat(70) + "\n")
    }
}
