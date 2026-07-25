package com.rasmi.purevon.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for syncing messages with system database
 * Inspired by QKSMS (Quik) sync mechanism
 * 
 * Purpose: Provide a single-shot full sync on first launch,
 * then incremental updates afterward for fast UI experience
 */
interface SyncRepository {
    
    /**
     * Sync progress states
     */
    sealed class SyncProgress {
        /**
         * No sync in progress
         */
        data object Idle : SyncProgress()
        
        /**
         * Sync is running
         * @param max Total items to sync (0 if unknown)
         * @param progress Current progress (items synced)
         * @param indeterminate Whether to show indeterminate progress
         * @param stage Current stage description (e.g., "Syncing conversations", "Syncing messages")
         */
        data class Running(
            val max: Int,
            val progress: Int,
            val indeterminate: Boolean,
            val stage: String = ""
        ) : SyncProgress()
    }
    
    /**
     * Observable sync progress
     */
    val syncProgress: Flow<SyncProgress>
    
    /**
     * Check if initial sync has been completed
     */
    suspend fun hasCompletedInitialSync(): Boolean
    
    /**
     * Perform a full sync of all messages and conversations
     * This is typically only called once on first launch
     */
    suspend fun performFullSync()
    
    /**
     * Sync a specific conversation's messages
     */
    suspend fun syncConversation(threadId: Long)
    
    /**
     * Sync only conversations (fast, for initial load)
     */
    suspend fun syncConversationsOnly()
    
    /**
     * Cancel any ongoing sync operation
     */
    fun cancelSync()
}
