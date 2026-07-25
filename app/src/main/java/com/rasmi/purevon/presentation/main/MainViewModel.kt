package com.rasmi.purevon.presentation.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.domain.repository.SyncRepository
import com.rasmi.purevon.domain.usecase.message.SyncMessagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainViewModel - Manages main screen state and first-launch sync
 * Inspired by QKSMS (Quik) approach to fast initial loading
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncMessagesUseCase: SyncMessagesUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    /**
     * UI State for main screen
     */
    data class MainUiState(
        val isLoading: Boolean = true,
        val syncProgress: SyncRepository.SyncProgress = SyncRepository.SyncProgress.Idle,
        val needsInitialSync: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkAndPerformInitialSync()
    }

    /**
     * Check if initial sync is needed and perform it
     */
    private fun checkAndPerformInitialSync() {
        viewModelScope.launch {
            try {
                val hasCompletedSync = syncMessagesUseCase.hasCompletedInitialSync()

                if (!hasCompletedSync) {
                    Log.d(TAG, "First launch detected, starting full sync...")
                    _uiState.update { it.copy(needsInitialSync = true, isLoading = true) }

                    // Observe progress FOR DISPLAY ONLY (progress bar / stage text).
                    // We use drop(1) to skip the initial StateFlow Idle value — without this,
                    // the observer immediately receives Idle and sets isLoading=false before
                    // the sync even starts, leaving the UI with an empty conversation list.
                    val progressJob = launch {
                        syncMessagesUseCase.observeSyncProgress()
                            .drop(1) // ← skip initial Idle — the real fix for the empty-list bug
                            .collect { progress ->
                                _uiState.update { it.copy(syncProgress = progress) }
                            }
                    }

                    // performFullSync() is a suspend function that internally calls
                    // syncJob.join(), so it does NOT return until the sync is fully done.
                    syncMessagesUseCase.performFullSync()

                    // Cancel progress observer — sync is guaranteed complete here.
                    progressJob.cancel()

                    // Mark loading as done AFTER sync completes — not via the observer.
                    _uiState.update {
                        it.copy(
                            needsInitialSync = false,
                            isLoading = false,
                            syncProgress = SyncRepository.SyncProgress.Idle
                        )
                    }
                    Log.d(TAG, "Initial sync completed, UI updated")
                } else {
                    Log.d(TAG, "Initial sync already completed")
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking initial sync status", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /**
     * Manually trigger full sync (for settings/debugging)
     */
    fun triggerFullSync() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // Observe sync progress
                val progressJob = launch {
                    syncMessagesUseCase.observeSyncProgress()
                        .collect { progress ->
                            _uiState.update { it.copy(syncProgress = progress) }
                        }
                }
                
                syncMessagesUseCase.performFullSync()
                
                progressJob.cancel()
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        syncProgress = SyncRepository.SyncProgress.Idle
                    )
                }
                
                Log.d(TAG, "Full sync completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during full sync", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /**
     * Cancel ongoing sync
     */
    fun cancelSync() {
        syncMessagesUseCase.cancelSync()
        _uiState.update { 
            it.copy(
                isLoading = false,
                syncProgress = SyncRepository.SyncProgress.Idle
            )
        }
    }

    /**
     * Called when the user returns to the app (e.g. after granting permissions)
     */
    fun onResume() {
        // Guard: skip only when a sync is actively running (needsInitialSync=true means
        // the coroutine has already launched). If isLoading=true but needsInitialSync=false,
        // the first attempt may have failed silently (e.g. permissions not yet granted) —
        // allow retry so the user gets messages after granting permissions.
        if (_uiState.value.isLoading && _uiState.value.needsInitialSync) return
        checkAndPerformInitialSync()
    }
    
    // ✅ Dialer state - shared between MainActivity and PurevonNavHost without tight coupling
    private val _dialerPhoneNumber = MutableStateFlow<String?>(null)
    val dialerPhoneNumber: StateFlow<String?> = _dialerPhoneNumber.asStateFlow()
    
    private val _shouldClearDialerInput = MutableStateFlow(false)
    val shouldClearDialerInput: StateFlow<Boolean> = _shouldClearDialerInput.asStateFlow()
    
    fun setDialerPhoneNumber(phoneNumber: String?) {
        _dialerPhoneNumber.value = phoneNumber
    }
    
    fun clearDialerPhoneNumber() {
        _dialerPhoneNumber.value = null
    }
    
    fun setShouldClearDialerInput(clear: Boolean) {
        _shouldClearDialerInput.value = clear
    }
    
    fun clearDialerInputFlag() {
        _shouldClearDialerInput.value = false
    }
}
