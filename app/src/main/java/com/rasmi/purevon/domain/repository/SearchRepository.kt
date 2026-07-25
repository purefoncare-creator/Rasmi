package com.rasmi.purevon.domain.repository

import com.rasmi.purevon.domain.model.SearchFilters
import com.rasmi.purevon.domain.model.SearchResult
import com.rasmi.purevon.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for search operations
 * Provides fast full-text search capabilities for messages
 */
interface SearchRepository {
    
    /**
     * Index a single message for search
     */
    suspend fun indexMessage(message: Message)
    
    /**
     * Index multiple messages in batch
     */
    suspend fun indexMessages(messages: List<Message>)
    
    /**
     * Remove message from search index
     */
    suspend fun removeFromIndex(messageId: Long)
    
    /**
     * Search messages with simple query
     */
    suspend fun searchMessages(
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<SearchResult>
    
    /**
     * Search messages with advanced filters
     */
    suspend fun searchMessages(filters: SearchFilters): List<SearchResult>
    
    /**
     * Search in specific conversation/thread
     */
    suspend fun searchInThread(
        query: String,
        threadId: Long,
        limit: Int = 50,
        offset: Int = 0
    ): List<SearchResult>
    
    /**
     * Search messages from specific phone number
     */
    suspend fun searchFromNumber(
        query: String,
        phoneNumber: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<SearchResult>
    
    /**
     * Get search suggestions based on query
     */
    suspend fun getSearchSuggestions(
        queryPrefix: String,
        limit: Int = 10
    ): List<String>
    
    /**
     * Get count of search results
     */
    suspend fun getSearchResultsCount(query: String): Int
    
    /**
     * Clear entire search index
     */
    suspend fun clearIndex()
    
    /**
     * Rebuild search index from system messages
     */
    suspend fun rebuildIndex()
    
    /**
     * Optimize search index (maintenance)
     */
    suspend fun optimizeIndex()
    
    /**
     * Get search index statistics
     */
    suspend fun getIndexStats(): Int
}
