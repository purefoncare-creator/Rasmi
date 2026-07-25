package com.rasmi.purevon.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Call Blocking operations
 */
interface BlockRepository {
    
    fun getAllBlockedNumbers(): Flow<List<String>>
    
    suspend fun blockNumber(phoneNumber: String, reason: String?)
    
    suspend fun unblockNumber(phoneNumber: String)
    
    suspend fun isNumberBlocked(phoneNumber: String): Boolean
    
    suspend fun blockWildcard(pattern: String, reason: String?)
    
    suspend fun shouldBlockCall(phoneNumber: String): Boolean
    
    suspend fun shouldBlockMessage(phoneNumber: String): Boolean
}
