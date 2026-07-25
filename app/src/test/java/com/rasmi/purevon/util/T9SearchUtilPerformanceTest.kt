package com.rasmi.purevon.util

import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Performance test for T9SearchUtil
 * Run with: ./gradlew test --tests T9SearchUtilPerformanceTest
 */
class T9SearchUtilPerformanceTest {
    
    private val testNames = listOf(
        "John Smith",
        "Jane Doe",
        "محمد أحمد",
        "Sarah Johnson",
        "Michael Brown",
        "Jessica Williams",
        "David Miller",
        "Emma Davis",
        "Christopher Wilson",
        "Olivia Anderson"
    )
    
    @Test
    fun `measure textToT9 performance`() {
        val iterations = 10000
        
        println("\n=== T9SearchUtil Performance Test ===")
        println("Testing with $iterations iterations per name\n")
        
        // Warm up
        repeat(100) {
            testNames.forEach { name ->
                T9SearchUtil.textToT9(name)
            }
        }
        
        // Clear cache for fair comparison
        T9SearchUtil.clearCache()
        
        // First run (cold cache)
        val coldTime = measureTimeMillis {
            repeat(iterations) {
                testNames.forEach { name ->
                    T9SearchUtil.textToT9(name)
                }
            }
        }
        
        println("Cold cache (${iterations * testNames.size} conversions): ${coldTime}ms")
        println("Average per conversion: ${coldTime.toFloat() / (iterations * testNames.size)}ms")
        
        // Second run (warm cache)
        val warmTime = measureTimeMillis {
            repeat(iterations) {
                testNames.forEach { name ->
                    T9SearchUtil.textToT9(name)
                }
            }
        }
        
        println("\nWarm cache (${iterations * testNames.size} conversions): ${warmTime}ms")
        println("Average per conversion: ${warmTime.toFloat() / (iterations * testNames.size)}ms")
        println("Speed improvement: ${(coldTime.toFloat() / warmTime * 100).toInt()}%")
        
        // Cache stats
        val stats = T9SearchUtil.getCacheStats()
        println("\nCache Statistics:")
        println("  T9 Cache: ${stats.t9CacheSize}/${stats.t9CacheMaxSize}")
        println("  Initials Cache: ${stats.initialsCacheSize}/${stats.initialsCacheMaxSize}")
    }
    
    @Test
    fun `measure matchesT9 performance`() {
        val queries = listOf("5646", "526", "64", "27", "843")
        val iterations = 5000
        
        println("\n=== matchesT9 Performance Test ===")
        
        // Warm up
        repeat(50) {
            queries.forEach { query ->
                testNames.forEach { name ->
                    T9SearchUtil.matchesT9(name, query = query)
                }
            }
        }
        
        T9SearchUtil.clearCache()
        
        // Cold cache
        val coldTime = measureTimeMillis {
            repeat(iterations) {
                queries.forEach { query ->
                    testNames.forEach { name ->
                        T9SearchUtil.matchesT9(name, query = query)
                    }
                }
            }
        }
        
        println("Cold cache: ${coldTime}ms")
        
        // Warm cache
        val warmTime = measureTimeMillis {
            repeat(iterations) {
                queries.forEach { query ->
                    testNames.forEach { name ->
                        T9SearchUtil.matchesT9(name, query = query)
                    }
                }
            }
        }
        
        println("Warm cache: ${warmTime}ms")
        println("Speed improvement: ${(coldTime.toFloat() / warmTime * 100).toInt()}%")
    }
    
    @Test
    fun `verify T9 conversion correctness`() {
        println("\n=== T9 Conversion Verification ===")
        
        val testCases = mapOf(
            "hello" to "43556",
            "world" to "96753",
            "john" to "5646",
            "abc" to "222",
            "HELLO" to "43556" // Should be case-insensitive
        )
        
        testCases.forEach { (input, expected) ->
            val actual = T9SearchUtil.textToT9(input)
            val status = if (actual == expected) "✓" else "✗"
            println("$status '$input' -> '$actual' (expected: '$expected')")
        }
    }
}


