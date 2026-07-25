package com.rasmi.purevon.data.repository

import android.content.Context
import android.util.Log
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.domain.repository.SyncRepository
import com.rasmi.purevon.domain.repository.SyncRepository.SyncProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SyncRepository inspired by QKSMS (Quik)
 * 
 * This provides a fast, efficient syncing strategy:
 * 1. Full sync on first launch (one-time)
 * 2. Incremental updates afterward
 * 3. Progress tracking for UX
 * 4. Cancellable operations
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository
) : SyncRepository {

    companion object {
        private const val TAG = "SyncRepository"
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_INITIAL_SYNC_COMPLETED = "initial_sync_completed"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        
        // Batch size for processing messages
        private const val BATCH_SIZE = 100
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _syncProgress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    override val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun hasCompletedInitialSync(): Boolean {
        // SharedPrefs flag must be set AND Room cache must actually contain data.
        // This handles corrupt state where a previous sync set the flag but wrote
        // no data (e.g. crash, kill, or migration wipe).
        val flagSet = prefs.getBoolean(KEY_INITIAL_SYNC_COMPLETED, false)
        if (!flagSet) return false
        return messageRepository.hasCachedConversations()
    }

    override suspend fun performFullSync() {
        // Cancel any existing sync before starting a new one
        syncJob?.cancelAndJoin()
        syncJob = scope.launch(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting full sync...")
            
            // Step 1: Sync conversations first (fast)
            _syncProgress.value = SyncProgress.Running(
                max = 0,
                progress = 0,
                indeterminate = true,
                stage = "Syncing conversations..."
            )
            
            syncConversationsOnly()

            if (!isActive) {
                _syncProgress.value = SyncProgress.Idle
                return@launch
            }

            // Step 2: Sync ALL messages in ONE bulk query — Quik approach
            // Old code: 2 + N queries (N = number of conversations)
            // New code: 2 queries total, regardless of how many conversations exist
            _syncProgress.value = SyncProgress.Running(
                max = 0,
                progress = 0,
                indeterminate = true,
                stage = "Syncing messages..."
            )

            messageRepository.syncAllMessages()
            
            // Mark sync as completed
            prefs.edit()
                .putBoolean(KEY_INITIAL_SYNC_COMPLETED, true)
                .putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
                .apply()
            
            _syncProgress.value = SyncProgress.Idle
            Log.d(TAG, "Full sync completed successfully")
            
        } catch (e: CancellationException) {
            Log.d(TAG, "Sync cancelled")
            _syncProgress.value = SyncProgress.Idle
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during full sync", e)
            _syncProgress.value = SyncProgress.Idle
            throw e
        }
        } // end scope.launch
        syncJob?.join() // suspend caller until sync finishes (or is cancelled)
    }

    override suspend fun syncConversation(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            _syncProgress.value = SyncProgress.Running(
                max = 0,
                progress = 0,
                indeterminate = true,
                stage = "Syncing conversation..."
            )
            
            messageRepository.syncMessages(threadId)
            
            _syncProgress.value = SyncProgress.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing conversation $threadId", e)
            _syncProgress.value = SyncProgress.Idle
            throw e
        }
    }

    override suspend fun syncConversationsOnly() = withContext(Dispatchers.IO) {
        try {
            // This should trigger the conversations sync in MessageRepository
            // We'll just ensure all conversations are in the database
            messageRepository.syncAllConversations()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing conversations", e)
            throw e
        }
    }

    override fun cancelSync() {
        syncJob?.cancel()
        _syncProgress.value = SyncProgress.Idle
        Log.d(TAG, "Sync cancelled by user")
    }

    /**
     * Get the last sync timestamp
     */
    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0)
    }
    
    /**
     * Reset sync state (for testing/debugging)
     */
    fun resetSyncState() {
        prefs.edit()
            .putBoolean(KEY_INITIAL_SYNC_COMPLETED, false)
            .putLong(KEY_LAST_SYNC_TIMESTAMP, 0)
            .apply()
        Log.d(TAG, "Sync state reset")
    }
}
