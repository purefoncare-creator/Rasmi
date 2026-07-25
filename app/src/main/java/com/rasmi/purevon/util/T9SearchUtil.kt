package com.rasmi.purevon.util

import androidx.collection.LruCache

/**
 * Simple Contact Search Utility - Straightforward Algorithm
 * No T9 complexity, just direct number and name matching
 * 
 * Strategy:
 * - For numeric queries (e.g., "05954"): Search in phone numbers using contains()
 * - For text queries (e.g., "ahmed"): Search in names using contains()
 * 
 * Performance improvements:
 * - LRU cache kept for optional T9 features (backward compatibility)
 * - Optimized string operations
 */
object T9SearchUtil {
    
    // T9 mapping: number -> letters (kept for backward compatibility)
    private val t9Map = mapOf(
        '2' to "abc",
        '3' to "def",
        '4' to "ghi",
        '5' to "jkl",
        '6' to "mno",
        '7' to "pqrs",
        '8' to "tuv",
        '9' to "wxyz"
    )
    
    // Reverse map for O(1) lookup: letter -> number
    private val reverseT9Map: Map<Char, Char> = buildMap {
        t9Map.forEach { (digit, letters) ->
            letters.forEach { letter ->
                put(letter, digit)
            }
        }
    }
    
    // LRU Cache for textToT9 results (max 500 entries)
    private val t9Cache = LruCache<String, String>(500)
    
    // Cache for initials T9 (max 500 entries)
    private val initialsCache = LruCache<String, String>(500)
    
    /**
     * Match score constants for ranking results
     * Simple and direct scoring system
     */
    object MatchScore {
        // Phone number matches (highest priority - simple and direct)
        const val NUMBER_STARTS_WITH = 1000           // Phone starts with query
        const val NUMBER_CONTAINS = 900               // Phone contains query anywhere
        
        // Name text matches (medium priority - direct text matching)
        const val NAME_STARTS_WITH = 800              // Name starts with query
        const val NAME_WORD_STARTS_WITH = 700         // Any word starts with query
        const val NAME_CONTAINS = 600                 // Name contains query
        const val NAME_INITIALS = 500                 // Initials match (e.g., "ah" for "Ahmed Hassan")
        
        const val NO_MATCH = 0
    }
    
    /**
     * Calculate match score for a contact - Simple and Direct Algorithm
     * No T9 complexity, just straightforward matching
     * 
     * Strategy:
     * - For numeric queries: Search in phone number first (contains)
     * - For text queries: Search in name (contains, starts with)
     * 
     * Higher score = better match = should appear first
     */
    fun calculateMatchScore(
        displayName: String,
        phoneNumber: String,
        query: String
    ): Int {
        if (query.isEmpty()) return MatchScore.NO_MATCH
        
        val isNumericQuery = query.all { it.isDigit() }
        
        // Strategy 1: Numeric query - Simple phone number search
        if (isNumericQuery) {
            // Normalize: remove all non-digits (spaces, dashes, parentheses, etc.)
            val normalizedPhone = phoneNumber.replace(Regex("[^0-9]"), "")
            
            // Simple and direct: does the phone number contain the query?
            when {
                normalizedPhone.startsWith(query) -> return MatchScore.NUMBER_STARTS_WITH
                normalizedPhone.contains(query) -> return MatchScore.NUMBER_CONTAINS
            }
            
            return MatchScore.NO_MATCH
        }
        
        // Strategy 2: Text query - Simple name search
        val lowerName = displayName.lowercase()
        val lowerQuery = query.lowercase()
        
        // Check if name starts with query
        if (lowerName.startsWith(lowerQuery)) {
            return MatchScore.NAME_STARTS_WITH
        }
        
        // Check if any word starts with query
        val words = displayName.split(Regex("\\s+"))
        for (word in words) {
            if (word.lowercase().startsWith(lowerQuery)) {
                return MatchScore.NAME_WORD_STARTS_WITH
            }
        }
        
        // Check initials (e.g., "ah" for "Ahmed Hassan")
        val initials = words
            .mapNotNull { it.firstOrNull()?.lowercaseChar() }
            .joinToString("")
        if (initials.startsWith(lowerQuery)) {
            return MatchScore.NAME_INITIALS
        }
        
        // Check if name contains query anywhere
        if (lowerName.contains(lowerQuery)) {
            return MatchScore.NAME_CONTAINS
        }
        
        return MatchScore.NO_MATCH
    }
    
    /**
     * Check if a contact matches query - Simple and Direct
     * Returns true if there's any match
     * 
     * For numeric queries: checks phone number (contains)
     * For text queries: checks name (contains)
     */
    fun matchesT9(name: String, phoneNumber: String = "", query: String): Boolean {
        if (query.isEmpty()) return true
        if (name.isEmpty() && phoneNumber.isEmpty()) return false
        
        val isNumericQuery = query.all { it.isDigit() }
        
        // For numeric queries: simple phone number matching
        if (isNumericQuery && phoneNumber.isNotEmpty()) {
            val normalizedPhone = phoneNumber.replace(Regex("[^0-9]"), "")
            return normalizedPhone.contains(query)
        }
        
        // For text queries: simple name matching
        return name.contains(query, ignoreCase = true)
    }
    
    /**
     * Convert text to T9 sequence - Kept for backward compatibility
     * Example: "hello" -> "43556"
     */
    fun textToT9(text: String): String {
        if (text.isEmpty()) return ""
        
        // Check cache first
        t9Cache.get(text)?.let { return it }
        
        // Build T9 sequence using StringBuilder for better performance
        val result = StringBuilder(text.length)
        val lowerText = text.lowercase()
        
        for (char in lowerText) {
            reverseT9Map[char]?.let { digit ->
                result.append(digit)
            }
        }
        
        val t9String = result.toString()
        
        // Cache the result
        t9Cache.put(text, t9String)
        
        return t9String
    }
    
    /**
     * Get T9 sequence for initials (first letter of each word)
     * Example: "John Smith" -> "57" (J=5, S=7)
     */
    fun getInitialsT9(text: String): String {
        if (text.isEmpty()) return ""
        
        // Check cache first
        initialsCache.get(text)?.let { return it }
        
        val result = StringBuilder()
        var isNewWord = true
        val lowerText = text.lowercase()
        
        for (char in lowerText) {
            if (char.isWhitespace()) {
                isNewWord = true
            } else if (isNewWord) {
                reverseT9Map[char]?.let { digit ->
                    result.append(digit)
                }
                isNewWord = false
            }
        }
        
        val initialsT9 = result.toString()
        initialsCache.put(text, initialsT9)
        
        return initialsT9
    }
    
    /**
     * Highlight matching parts in text/phone number for UI display
     * Uses bracket notation: [matched] text
     */
    fun highlightMatches(
        text: String,
        query: String,
        isPhoneNumber: Boolean = false
    ): String {
        if (query.isEmpty() || text.isEmpty()) return text
        
        val isNumericQuery = query.all { it.isDigit() }
        
        // For phone numbers with numeric query
        if (isPhoneNumber && isNumericQuery) {
            return highlightPhoneNumber(text, query)
        }
        
        // For names with text query
        if (!isPhoneNumber && !isNumericQuery) {
            return highlightName(text, query)
        }
        
        return text
    }
    
    /**
     * Legacy function name - kept for backward compatibility
     * Alias for highlightMatches()
     */
    fun getHighlightedText(text: String, query: String): String {
        // Detect if text is a phone number (contains digits)
        val isPhoneNumber = text.any { it.isDigit() }
        return highlightMatches(text, query, isPhoneNumber)
    }
    
    /**
     * Highlight phone number matches
     * Simple contains() matching
     */
    private fun highlightPhoneNumber(phoneNumber: String, query: String): String {
        val normalizedPhone = phoneNumber.replace(Regex("[^0-9]"), "")
        val matchIndex = normalizedPhone.indexOf(query)
        
        if (matchIndex == -1) return phoneNumber
        
        // Build result with brackets around matched digits
        val result = StringBuilder()
        var digitCount = 0
        var highlightStart = -1
        var highlightEnd = -1
        
        // Find actual positions in original phone string
        for (i in phoneNumber.indices) {
            if (phoneNumber[i].isDigit()) {
                if (digitCount == matchIndex) {
                    highlightStart = i
                }
                if (digitCount == matchIndex + query.length - 1) {
                    highlightEnd = i
                    break
                }
                digitCount++
            }
        }
        
        // Build highlighted string
        if (highlightStart != -1 && highlightEnd != -1) {
            result.append(phoneNumber.substring(0, highlightStart))
            result.append('[')
            result.append(phoneNumber.substring(highlightStart, highlightEnd + 1))
            result.append(']')
            if (highlightEnd + 1 < phoneNumber.length) {
                result.append(phoneNumber.substring(highlightEnd + 1))
            }
            return result.toString()
        }
        
        return phoneNumber
    }
    
    /**
     * Highlight name matches
     * Simple contains() matching (case-insensitive)
     */
    private fun highlightName(name: String, query: String): String {
        val lowerName = name.lowercase()
        val lowerQuery = query.lowercase()
        val matchIndex = lowerName.indexOf(lowerQuery)
        
        if (matchIndex == -1) return name
        
        val result = StringBuilder()
        result.append(name.substring(0, matchIndex))
        result.append('[')
        result.append(name.substring(matchIndex, matchIndex + query.length))
        result.append(']')
        if (matchIndex + query.length < name.length) {
            result.append(name.substring(matchIndex + query.length))
        }
        
        return result.toString()
    }
    
    /**
     * Clear all caches (useful for testing or memory management)
     */
    fun clearCache() {
        t9Cache.evictAll()
        initialsCache.evictAll()
    }
    
    /**
     * Get cache statistics for monitoring
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            t9CacheSize = t9Cache.size(),
            t9CacheMaxSize = t9Cache.maxSize(),
            initialsCacheSize = initialsCache.size(),
            initialsCacheMaxSize = initialsCache.maxSize()
        )
    }
    
    data class CacheStats(
        val t9CacheSize: Int,
        val t9CacheMaxSize: Int,
        val initialsCacheSize: Int,
        val initialsCacheMaxSize: Int
    )
    
    /**
     * Get all possible letter combinations for a T9 sequence
     * Example: "23" -> ["ad", "ae", "af", "bd", "be", "bf", "cd", "ce", "cf"]
     * Kept for backward compatibility
     */
    fun getT9Combinations(query: String): List<String> {
        if (query.isEmpty()) return emptyList()
        
        fun generateCombinations(digits: String, current: String = ""): List<String> {
            if (digits.isEmpty()) return listOf(current)
            
            val digit = digits[0]
            val letters = t9Map[digit] ?: return generateCombinations(digits.drop(1), current)
            
            return letters.flatMap { letter ->
                generateCombinations(digits.drop(1), current + letter)
            }
        }
        
        return generateCombinations(query)
    }
}
