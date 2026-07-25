package com.rasmi.purevon.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * FTS4 (Full-Text Search) entity for fast message searching
 * This is a virtual table optimized for text search operations
 * 
 * Features:
 * - Fast full-text search using SQLite FTS4
 * - Automatic tokenization and indexing
 * - Ranking by relevance
 * - Supports phrase queries, prefix matching, and boolean operators
 * 
 * Note: FTS4 tables have a hidden 'rowid' column that must be used as primary key
 */
@Entity(tableName = "search_messages_fts")
@Fts4
data class SearchMessageEntity(
    /**
     * Row ID - required for FTS4
     */
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Long,
    
    /**
     * Phone number (searchable)
     */
    val address: String,
    
    /**
     * Contact name if available (searchable)
     */
    val contactName: String?,
    
    /**
     * Message body (main search content)
     */
    val body: String,
    
    /**
     * Thread ID for grouping
     */
    val threadId: Long,
    
    /**
     * Message timestamp (for sorting)
     */
    val date: Long,
    
    /**
     * Message type (1=received, 2=sent)
     */
    val type: Int
)

/**
 * Search result with ranking score
 * Used to return search results with relevance information
 */
data class SearchResult(
    val rowid: Long,
    val address: String,
    val contactName: String?,
    val body: String,
    val snippetText: String, // Highlighted snippet
    val threadId: Long,
    val date: Long,
    val type: Int,
    val rankScore: Double // FTS ranking score
)

/**
 * Search filters for advanced search
 */
data class SearchFilters(
    val query: String,
    val fromDate: Long? = null,
    val toDate: Long? = null,
    val phoneNumber: String? = null,
    val messageType: Int? = null, // 1=received, 2=sent
    val threadId: Long? = null,
    val limit: Int = 50,
    val offset: Int = 0
)
