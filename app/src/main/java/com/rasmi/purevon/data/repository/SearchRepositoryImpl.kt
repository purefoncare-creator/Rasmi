package com.rasmi.purevon.data.repository

import android.content.ContentResolver
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.data.local.dao.SearchDao
import com.rasmi.purevon.data.local.entity.SearchMessageEntity
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.SearchFilters
import com.rasmi.purevon.domain.model.SearchResult
import com.rasmi.purevon.domain.repository.ContactRepository
import com.rasmi.purevon.domain.repository.SearchRepository
import com.rasmi.purevon.data.local.dao.SearchQueryBuilder
import com.rasmi.purevon.util.search.SearchOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight holder for raw SMS data read from the cursor,
 * before contact resolution. Used to decouple cursor iteration
 * from the N contact-lookup queries.
 */
private data class RawSmsRow(
    val id: Long,
    val address: String,
    val body: String,
    val threadId: Long,
    val date: Long,
    val type: Int
)

/**
 * Implementation of SearchRepository
 * Manages FTS indexing and search operations with caching
 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val searchDao: SearchDao,
    private val contentResolver: ContentResolver,
    private val contactRepository: ContactRepository
) : SearchRepository {
    
    companion object {
        private const val TAG = "SearchRepository"
        private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    private fun com.rasmi.purevon.data.local.entity.SearchResult.toDomain() = SearchResult(
        rowid = rowid,
        address = address,
        contactName = contactName ?: "",
        body = body,
        snippetText = snippetText,
        threadId = threadId,
        date = date,
        type = type,
        rankScore = rankScore.toFloat()
    )
    
    /**
     * Index a single message for search
     */
    override suspend fun indexMessage(message: Message): Unit = withContext(Dispatchers.IO) {
        try {
            val contactName = contactRepository.getContactByNumber(message.phoneNumber)?.name
            
            val searchEntity = SearchMessageEntity(
                rowid = message.id,
                address = message.phoneNumber,
                contactName = contactName,
                body = message.body ?: "",
                threadId = message.threadId,
                date = message.timestamp,
                type = message.type
            )
            
            searchDao.insertMessage(searchEntity)
            Log.d(TAG, "Indexed message ${message.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing message ${message.id}", e)
        }
    }
    
    /**
     * Index multiple messages in batch
     */
    override suspend fun indexMessages(messages: List<Message>): Unit = withContext(Dispatchers.IO) {
        try {
            // Pre-load contacts for all unique phone numbers to avoid N+1 queries
            val contactNameMap = messages.map { it.phoneNumber }.distinct().associateWith { number ->
                contactRepository.getContactByNumber(number)?.name
            }
            
            val searchEntities = messages.map { message ->
                SearchMessageEntity(
                    rowid = message.id,
                    address = message.phoneNumber,
                    contactName = contactNameMap[message.phoneNumber],
                    body = message.body ?: "",
                    threadId = message.threadId,
                    date = message.timestamp,
                    type = message.type
                )
            }
            
            searchDao.insertMessages(searchEntities)
            Log.d(TAG, "Indexed ${messages.size} messages")
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing messages batch", e)
        }
    }
    
    /**
     * Remove message from search index
     */
    override suspend fun removeFromIndex(messageId: Long): Unit = withContext(Dispatchers.IO) {
        try {
            searchDao.deleteMessage(messageId)
            
            // Clear cache that might contain this message
            SearchOptimizer.clearCache()
            
            Log.d(TAG, "Removed message $messageId from index")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing message from index", e)
        }
    }
    
    /**
     * Search messages with simple query
     */
    override suspend fun searchMessages(
        query: String,
        limit: Int,
        offset: Int
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            // Sanitize and convert query
            val ftsQuery = SearchQueryBuilder.toFtsQuery(query)
            
            // Check cache first
            val cacheKey = "search:$ftsQuery:$limit:$offset"
            SearchOptimizer.getCachedResults<List<SearchResult>>(cacheKey)?.let {
                Log.d(TAG, "Returning cached results for: $query")
                return@withContext it
            }
            
            // Search in database
            val results = searchDao.searchMessages(ftsQuery, limit, offset).map { it.toDomain() }
            
            // Cache results
            SearchOptimizer.cacheResults(cacheKey, results, CACHE_EXPIRY_MS)
            
            // Add to search history
            SearchOptimizer.addToHistory(query)
            
            Log.d(TAG, "Found ${results.size} results for: $query")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error searching messages", e)
            emptyList()
        }
    }
    
    /**
     * Search messages with advanced filters
     */
    override suspend fun searchMessages(filters: SearchFilters): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val ftsQuery = SearchQueryBuilder.toFtsQuery(filters.query)
            
            // Use basic search and filter results in memory
            // This is simpler than RawQuery and works well for most cases
            var results: List<SearchResult> = (if (filters.threadId != null) {
                searchDao.searchInThread(ftsQuery, filters.threadId, limit = 1000, offset = 0)
            } else if (filters.phoneNumber != null) {
                searchDao.searchFromNumber(ftsQuery, filters.phoneNumber, limit = 1000, offset = 0)
            } else {
                searchDao.searchMessages(ftsQuery, limit = 1000, offset = 0)
            }).map { it.toDomain() }
            
            // Apply additional filters in memory
            if (filters.fromDate != null) {
                results = results.filter { it.date >= filters.fromDate }
            }
            if (filters.toDate != null) {
                results = results.filter { it.date <= filters.toDate }
            }
            if (filters.messageType != null) {
                results = results.filter { it.type == filters.messageType }
            }
            
            // Apply pagination
            results = results.drop(filters.offset).take(filters.limit)
            
            // Add to search history
            SearchOptimizer.addToHistory(filters.query)
            
            Log.d(TAG, "Advanced search found ${results.size} results")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error in advanced search", e)
            emptyList()
        }
    }
    
    /**
     * Search in specific conversation/thread
     */
    override suspend fun searchInThread(
        query: String,
        threadId: Long,
        limit: Int,
        offset: Int
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val ftsQuery = SearchQueryBuilder.toFtsQuery(query)
            
            // Check cache
            val cacheKey = "thread:$threadId:$ftsQuery:$limit:$offset"
            SearchOptimizer.getCachedResults<List<SearchResult>>(cacheKey)?.let {
                return@withContext it
            }
            
            val results = searchDao.searchInThread(ftsQuery, threadId, limit, offset).map { it.toDomain() }
            
            // Cache results
            SearchOptimizer.cacheResults(cacheKey, results, CACHE_EXPIRY_MS)
            
            Log.d(TAG, "Found ${results.size} results in thread $threadId")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error searching in thread", e)
            emptyList()
        }
    }
    
    /**
     * Search messages from specific phone number
     */
    override suspend fun searchFromNumber(
        query: String,
        phoneNumber: String,
        limit: Int,
        offset: Int
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val ftsQuery = SearchQueryBuilder.toFtsQuery(query)
            val results = searchDao.searchFromNumber(ftsQuery, phoneNumber, limit, offset).map { it.toDomain() }
            
            Log.d(TAG, "Found ${results.size} results from $phoneNumber")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error searching from number", e)
            emptyList()
        }
    }
    
    /**
     * Get search suggestions based on query
     */
    override suspend fun getSearchSuggestions(
        queryPrefix: String,
        limit: Int
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            // Combine database suggestions with history
            val dbSuggestions = searchDao.getSearchSuggestions(queryPrefix, limit / 2)
            val historySuggestions = SearchOptimizer.getSuggestions(queryPrefix, limit / 2)
            
            (dbSuggestions + historySuggestions).distinct().take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting suggestions", e)
            SearchOptimizer.getSuggestions(queryPrefix, limit)
        }
    }
    
    /**
     * Get count of search results
     */
    override suspend fun getSearchResultsCount(query: String): Int = withContext(Dispatchers.IO) {
        try {
            val ftsQuery = SearchQueryBuilder.toFtsQuery(query)
            searchDao.getSearchResultsCount(ftsQuery)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting results count", e)
            0
        }
    }
    
    /**
     * Clear entire search index
     */
    override suspend fun clearIndex(): Unit = withContext(Dispatchers.IO) {
        try {
            searchDao.clearIndex()
            SearchOptimizer.clearCache()
            Log.d(TAG, "Cleared search index")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing index", e)
        }
    }
    
    /**
     * Rebuild search index from system messages
     */
    override suspend fun rebuildIndex(): Unit = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting index rebuild...")
            
            // Clear existing index
            searchDao.clearIndex()
            
            // Query all messages from system
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )
            
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )
            
            cursor?.use { c ->
                val rawMessages = mutableListOf<RawSmsRow>()
                
                val idIndex = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val threadIdIndex = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                
                // Read all rows from cursor first
                while (c.moveToNext()) {
                    rawMessages.add(
                        RawSmsRow(
                            id = c.getLong(idIndex),
                            address = c.getString(addressIndex) ?: "",
                            body = c.getString(bodyIndex) ?: "",
                            threadId = c.getLong(threadIdIndex),
                            date = c.getLong(dateIndex),
                            type = c.getInt(typeIndex)
                        )
                    )
                }
                
                // Process in batches of 500
                rawMessages.chunked(500).forEach { batch ->
                    // Pre-load contacts for unique addresses in this batch
                    val contactNameMap = batch.map { it.address }.filter { it.isNotEmpty() }
                        .distinct().associateWith { address ->
                            contactRepository.getContactByNumber(address)?.name
                        }
                    
                    val entities = batch.map { row ->
                        SearchMessageEntity(
                            rowid = row.id,
                            address = row.address,
                            contactName = contactNameMap[row.address],
                            body = row.body,
                            threadId = row.threadId,
                            date = row.date,
                            type = row.type
                        )
                    }
                    
                    searchDao.insertMessages(entities)
                    Log.d(TAG, "Indexed ${entities.size} messages...")
                }
                
                Log.d(TAG, "Index rebuild complete. Total messages: ${c.count}")
            }
            
            // Optimize index after rebuild
            searchDao.optimizeIndex()
            
            // Clear cache
            SearchOptimizer.clearCache()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error rebuilding index", e)
        }
    }
    
    /**
     * Optimize search index (maintenance)
     */
    override suspend fun optimizeIndex(): Unit = withContext(Dispatchers.IO) {
        try {
            searchDao.optimizeIndex()
            SearchOptimizer.clearExpiredCache()
            Log.d(TAG, "Optimized search index")
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing index", e)
        }
    }
    
    /**
     * Get search index statistics
     */
    override suspend fun getIndexStats(): Int = withContext(Dispatchers.IO) {
        try {
            searchDao.getIndexStats()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting index stats", e)
            0
        }
    }
}
