package com.rasmi.purevon.util.search

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/**
 * Search optimization utilities
 * - Query debouncing
 * - Query caching
 * - Search suggestions
 * - Query history
 */
object SearchOptimizer {
    
    private const val MIN_QUERY_LENGTH = 2
    private const val DEBOUNCE_DELAY_MS = 300L
    private const val MAX_CACHE_SIZE = 100
    private const val MAX_HISTORY_SIZE = 20
    
    // Cache for search results
    private val searchCache = ConcurrentHashMap<String, CachedSearchResult>()
    
    // Search history (thread-safe)
    private val searchHistory = java.util.concurrent.CopyOnWriteArrayList<String>()
    
    /**
     * Cached search result with timestamp
     */
    data class CachedSearchResult(
        val results: Any, // Generic to support any result type
        val timestamp: Long = System.currentTimeMillis(),
        val expiryMs: Long = 5 * 60 * 1000 // 5 minutes
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > expiryMs
    }
    
    /**
     * Optimize search query Flow with debouncing and filtering
     */
    @OptIn(FlowPreview::class)
    fun <T> Flow<T>.optimizeSearchQuery(
        minLength: Int = MIN_QUERY_LENGTH,
        debounceMs: Long = DEBOUNCE_DELAY_MS,
        transform: (T) -> String = { it.toString() }
    ): Flow<String> {
        return this
            .map { transform(it) }
            .map { it.trim() }
            .filter { it.length >= minLength }
            .distinctUntilChanged()
            .debounce(debounceMs)
    }
    
    /**
     * Get cached search results if available and not expired
     */
    fun <T> getCachedResults(query: String): T? {
        val normalized = normalizeQuery(query)
        val cached = searchCache[normalized]
        
        return if (cached != null && !cached.isExpired()) {
            @Suppress("UNCHECKED_CAST")
            cached.results as? T
        } else {
            // Remove expired cache
            if (cached != null) {
                searchCache.remove(normalized)
            }
            null
        }
    }
    
    /**
     * Cache search results
     */
    fun cacheResults(query: String, results: Any, expiryMs: Long = 5 * 60 * 1000) {
        val normalized = normalizeQuery(query)
        
        // Limit cache size
        if (searchCache.size >= MAX_CACHE_SIZE) {
            // Remove oldest entries
            val toRemove = searchCache.entries
                .sortedBy { it.value.timestamp }
                .take(MAX_CACHE_SIZE / 4)
                .map { it.key }
            
            toRemove.forEach { searchCache.remove(it) }
        }
        
        searchCache[normalized] = CachedSearchResult(results, expiryMs = expiryMs)
    }
    
    /**
     * Clear search cache
     */
    fun clearCache() {
        searchCache.clear()
    }
    
    /**
     * Clear expired cache entries
     */
    fun clearExpiredCache() {
        val expired = searchCache.filter { it.value.isExpired() }.keys
        expired.forEach { searchCache.remove(it) }
    }
    
    /**
     * Add query to search history
     */
    fun addToHistory(query: String) {
        val normalized = normalizeQuery(query)
        
        if (normalized.isBlank() || normalized.length < MIN_QUERY_LENGTH) {
            return
        }
        
        // Remove if already exists
        searchHistory.remove(normalized)
        
        // Add to beginning
        searchHistory.add(0, normalized)
        
        // Limit history size
        if (searchHistory.size > MAX_HISTORY_SIZE) {
            searchHistory.subList(MAX_HISTORY_SIZE, searchHistory.size).clear()
        }
    }
    
    /**
     * Get search history
     */
    fun getHistory(): List<String> {
        return searchHistory.toList()
    }
    
    /**
     * Clear search history
     */
    fun clearHistory() {
        searchHistory.clear()
    }
    
    /**
     * Get search suggestions based on history and current query
     */
    fun getSuggestions(query: String, limit: Int = 5): List<String> {
        val normalized = normalizeQuery(query)
        
        if (normalized.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }
        
        return searchHistory
            .filter { it.contains(normalized, ignoreCase = true) }
            .take(limit)
    }
    
    /**
     * Normalize query for caching and comparison
     */
    private fun normalizeQuery(query: String): String {
        return query.trim().lowercase()
    }
    
    /**
     * Highlight search terms in text
     */
    fun highlightMatches(
        text: String,
        query: String,
        prefix: String = "<b>",
        suffix: String = "</b>"
    ): String {
        if (query.isBlank()) return text
        
        val terms = query.split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        var result = text
        
        terms.forEach { term ->
            // Case-insensitive replacement
            val regex = Regex("(?i)(${Regex.escape(term)})")
            result = result.replace(regex, "$prefix$1$suffix")
        }
        
        return result
    }
    
    /**
     * Extract snippet around search match
     */
    fun extractSnippet(
        text: String,
        query: String,
        maxLength: Int = 100,
        ellipsis: String = "..."
    ): String {
        if (text.length <= maxLength) return text
        
        val normalized = normalizeQuery(query)
        val textLower = text.lowercase()
        
        // Find first occurrence
        val index = textLower.indexOf(normalized)
        
        if (index == -1) {
            // Query not found, return beginning
            return text.take(maxLength) + ellipsis
        }
        
        // Calculate snippet bounds
        val start = maxOf(0, index - maxLength / 2)
        val end = minOf(text.length, start + maxLength)
        
        val prefix = if (start > 0) ellipsis else ""
        val postfix = if (end < text.length) ellipsis else ""
        
        return prefix + text.substring(start, end) + postfix
    }
    
    /**
     * Calculate search relevance score
     */
    fun calculateRelevance(
        text: String,
        query: String,
        boostExactMatch: Boolean = true,
        boostPrefixMatch: Boolean = true
    ): Double {
        if (query.isBlank()) return 0.0
        
        val textLower = text.lowercase()
        val queryLower = query.lowercase().trim()
        val terms = queryLower.split(" ").filter { it.isNotBlank() }
        
        var score = 0.0
        
        // Exact match bonus
        if (boostExactMatch && textLower.contains(queryLower)) {
            score += 10.0
        }
        
        // Term matching
        terms.forEach { term ->
            // Exact term match
            if (textLower.contains(term)) {
                score += 5.0
                
                // Prefix match bonus
                if (boostPrefixMatch && textLower.startsWith(term)) {
                    score += 3.0
                }
                
                // Calculate term frequency
                val frequency = textLower.split(term).size - 1
                score += frequency * 0.5
            }
        }
        
        // Proximity bonus (terms close together)
        if (terms.size > 1) {
            val positions = terms.mapNotNull { term ->
                val index = textLower.indexOf(term)
                if (index >= 0) index else null
            }
            
            if (positions.size == terms.size) {
                val maxDistance = positions.max() - positions.min()
                // Closer terms = higher score
                val proximityScore = 5.0 / (1 + maxDistance / 10.0)
                score += proximityScore
            }
        }
        
        return score
    }
}

/**
 * Search suggestion generator
 */
class SearchSuggestionGenerator {
    
    private val commonPrefixes = setOf(
        "from", "to", "with", "about", "message", "today", "yesterday"
    )
    
    /**
     * Generate smart suggestions based on query
     */
    fun generateSuggestions(
        query: String,
        history: List<String>,
        recentContacts: List<String> = emptyList(),
        limit: Int = 10
    ): List<SearchSuggestion> {
        val suggestions = mutableListOf<SearchSuggestion>()
        val normalized = query.trim().lowercase()
        
        if (normalized.length < 2) {
            // Show recent history
            history.take(limit).forEach {
                suggestions.add(
                    SearchSuggestion(
                        text = it,
                        type = SuggestionType.HISTORY,
                        relevance = 1.0
                    )
                )
            }
            return suggestions
        }
        
        // History-based suggestions
        history.filter { it.contains(normalized) }
            .take(limit / 2)
            .forEach {
                suggestions.add(
                    SearchSuggestion(
                        text = it,
                        type = SuggestionType.HISTORY,
                        relevance = SearchOptimizer.calculateRelevance(it, query)
                    )
                )
            }
        
        // Contact-based suggestions
        recentContacts.filter { it.lowercase().contains(normalized) }
            .take(limit / 3)
            .forEach {
                suggestions.add(
                    SearchSuggestion(
                        text = "from $it",
                        type = SuggestionType.CONTACT,
                        relevance = 0.8
                    )
                )
            }
        
        // Common phrase suggestions
        commonPrefixes.filter { it.startsWith(normalized) }
            .take(limit / 4)
            .forEach {
                suggestions.add(
                    SearchSuggestion(
                        text = "$it $query",
                        type = SuggestionType.PHRASE,
                        relevance = 0.5
                    )
                )
            }
        
        return suggestions
            .sortedByDescending { it.relevance }
            .take(limit)
    }
}

/**
 * Search suggestion data class
 */
data class SearchSuggestion(
    val text: String,
    val type: SuggestionType,
    val relevance: Double
)

/**
 * Types of search suggestions
 */
enum class SuggestionType {
    HISTORY,    // From search history
    CONTACT,    // From contacts
    PHRASE,     // Common phrases
    TRENDING    // Trending searches
}
