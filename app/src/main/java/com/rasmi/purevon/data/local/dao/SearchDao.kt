package com.rasmi.purevon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rasmi.purevon.data.local.entity.SearchMessageEntity
import com.rasmi.purevon.data.local.entity.SearchResult
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Full-Text Search operations on messages
 * Uses FTS4 virtual table for fast text search
 */
@Dao
interface SearchDao {
    
    /**
     * Insert or update message in search index
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SearchMessageEntity)
    
    /**
     * Insert multiple messages in batch
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<SearchMessageEntity>)
    
    /**
     * Delete message from search index
     */
    @Query("DELETE FROM search_messages_fts WHERE rowid = :messageId")
    suspend fun deleteMessage(messageId: Long)
    
    /**
     * Clear entire search index
     */
    @Query("DELETE FROM search_messages_fts")
    suspend fun clearIndex()
    
    /**
     * Full-text search with ranking
     * FTS4 MATCH syntax:
     * - "word" - exact word
     * - "word*" - prefix match
     * - "word1 word2" - both words (AND)
     * - "word1 OR word2" - either word
     * - "phrase in quotes" - exact phrase
     */
    @Query("""
        SELECT 
            rowid,
            address,
            contactName,
            body,
            snippet(search_messages_fts, '<b>', '</b>', '...', -1, 32) as snippetText,
            threadId,
            date,
            type,
            0.0 as rankScore
        FROM search_messages_fts
        WHERE search_messages_fts MATCH :query
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchMessages(
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<SearchResult>
    
    /**
     * Search in specific thread
     */
    @Query("""
        SELECT 
            rowid,
            address,
            contactName,
            body,
            snippet(search_messages_fts, '<b>', '</b>', '...', -1, 32) as snippetText,
            threadId,
            date,
            type,
            0.0 as rankScore
        FROM search_messages_fts
        WHERE search_messages_fts MATCH :query
        AND threadId = :threadId
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchInThread(
        query: String,
        threadId: Long,
        limit: Int = 50,
        offset: Int = 0
    ): List<SearchResult>
    
    /**
     * Search messages from specific phone number
     */
    @Query("""
        SELECT 
            rowid,
            address,
            contactName,
            body,
            snippet(search_messages_fts, '<b>', '</b>', '...', -1, 32) as snippetText,
            threadId,
            date,
            type,
            0.0 as rankScore
        FROM search_messages_fts
        WHERE search_messages_fts MATCH :query
        AND address = :phoneNumber
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchFromNumber(
        query: String,
        phoneNumber: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<SearchResult>
    
    /**
     * Get search suggestions based on query prefix
     * Returns most common words matching the prefix
     */
    @Query("""
        SELECT DISTINCT body
        FROM search_messages_fts
        WHERE body MATCH :queryPrefix || '*'
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getSearchSuggestions(
        queryPrefix: String,
        limit: Int = 10
    ): List<String>
    
    /**
     * Get messages count for a query
     */
    @Query("""
        SELECT COUNT(*)
        FROM search_messages_fts
        WHERE search_messages_fts MATCH :query
    """)
    suspend fun getSearchResultsCount(query: String): Int
    
    /**
     * Get recent searches (most queried terms)
     * This would need a separate table to track, 
     * for now just return recent message words
     */
    @Query("""
        SELECT DISTINCT body
        FROM search_messages_fts
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getRecentSearches(limit: Int = 10): List<String>
    
    /**
     * Rebuild FTS index (maintenance operation)
     */
    @Query("INSERT INTO search_messages_fts(search_messages_fts) VALUES('rebuild')")
    suspend fun rebuildIndex()
    
    /**
     * Optimize FTS index (maintenance operation)
     */
    @Query("INSERT INTO search_messages_fts(search_messages_fts) VALUES('optimize')")
    suspend fun optimizeIndex()
    
    /**
     * Get index statistics
     */
    @Query("""
        SELECT COUNT(*) as total_messages
        FROM search_messages_fts
    """)
    suspend fun getIndexStats(): Int
}

/**
 * Helper for FTS search query utilities
 */
object SearchQueryBuilder {
    
    /**
     * Sanitize FTS query to prevent syntax errors
     */
    fun sanitizeQuery(query: String): String {
        return query
            .trim()
            .replace(Regex("[^\\w\\s*\"\\-]"), "") // Remove special chars except *, ", -
            .replace(Regex("\\s+"), " ") // Normalize spaces
            .takeIf { it.isNotBlank() } ?: "*"
    }
    
    /**
     * Convert plain text query to FTS query format
     */
    fun toFtsQuery(query: String): String {
        val sanitized = sanitizeQuery(query)
        
        // If query is in quotes, keep as phrase search
        if (sanitized.startsWith("\"") && sanitized.endsWith("\"")) {
            return sanitized
        }
        
        // Otherwise, add * for prefix matching
        return sanitized.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { if (it.endsWith("*")) it else "$it*" }
    }
}
