package com.rasmi.purevon.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Spam Detection operations
 */
interface SpamRepository {
    
    suspend fun checkIfSpam(phoneNumber: String): Pair<Boolean, Float>
    
    suspend fun reportSpam(phoneNumber: String)
    
    suspend fun markAsNotSpam(phoneNumber: String)
    
    fun getSpamNumbers(): Flow<List<String>>
    
    suspend fun updateSpamDatabase()
    
    suspend fun getSpamScore(phoneNumber: String): Float
}
