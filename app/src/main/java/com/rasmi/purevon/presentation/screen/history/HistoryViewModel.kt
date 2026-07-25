package com.rasmi.purevon.presentation.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.usecase.block.BlockNumberUseCase
import com.rasmi.purevon.domain.usecase.call.DeleteCallLogsByNumberUseCase
import com.rasmi.purevon.domain.usecase.call.DeleteCallLogsByNumbersUseCase

import com.rasmi.purevon.domain.usecase.call.GetAllCallLogsUseCase
import com.rasmi.purevon.domain.usecase.call.SyncCallLogsUseCase
import com.rasmi.purevon.util.sim.SimCallAction
import com.rasmi.purevon.util.sim.SimCallRouter
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "HistoryVM"

@OptIn(FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getAllCallLogsUseCase: GetAllCallLogsUseCase,
    private val deleteCallLogsByNumberUseCase: DeleteCallLogsByNumberUseCase,
    private val deleteCallLogsByNumbersUseCase: DeleteCallLogsByNumbersUseCase,
    private val syncCallLogsUseCase: SyncCallLogsUseCase,
    private val blockNumberUseCase: BlockNumberUseCase,
    private val getAllContactsUseCase: com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase,
    private val contactNoteRepository: com.rasmi.purevon.domain.repository.ContactNoteRepository,
    private val simCallRouter: SimCallRouter // ✅ لحل شريحة الاتصال
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(CallFilter.ALL)
    
    // Job to manage call logs observation lifecycle
    private var observeCallLogsJob: kotlinx.coroutines.Job? = null
    
    // Undo functionality
    private var undoTimerJob: kotlinx.coroutines.Job? = null
    private val UNDO_DELAY_MS = 5000L // 5 seconds to undo

    init {
        observeCallLogs()
        loadContacts()
        observePhoneNumbersWithNotes()
    }
    
    private fun loadContacts() {
        viewModelScope.launch {
            getAllContactsUseCase()
                .catch { e -> Log.e(TAG, "Failed to load contacts", e) }
                .collect { contacts ->
                    _uiState.update { it.copy(contacts = contacts) }
                }
        }
    }

    private fun observePhoneNumbersWithNotes() {
        viewModelScope.launch {
            contactNoteRepository.getPhoneNumbersWithNotes()
                .catch { e -> Log.e(TAG, "Failed to observe notes", e) }
                .collect { phoneNumbers ->
                    _uiState.update { it.copy(phoneNumbersWithNotes = phoneNumbers.toSet()) }
                }
        }
    }

    private fun observeCallLogs() {
        // Cancel existing observation job to prevent memory leak
        observeCallLogsJob?.cancel()
        
        observeCallLogsJob = viewModelScope.launch {
            // Combine all logs, filter, and search query into one flow
            combine(
                getAllCallLogsUseCase(),
                selectedFilter,
                searchQuery
            ) { logs, filter, query ->
                Triple(logs, filter, query)
            }
            // debounce only when user is typing; emit immediately for empty string
            .debounce { (_, _, query) -> if (query.isEmpty()) 0L else 300L }
            .collect { (logs, filter, query) ->
                // First apply filter
                val filteredByType = filterCallLogs(logs, filter)
                
                // Then apply search query with improved phone number matching
                val finalLogs = if (query.isEmpty()) {
                    filteredByType
                } else {
                    val normalizedQuery = normalizePhoneNumber(query)
                    filteredByType.filter { log ->
                        // Match by contact name (always search with original query)
                        log.name?.contains(query, ignoreCase = true) == true ||
                        log.contactName?.contains(query, ignoreCase = true) == true ||
                        // Match by phone number (both formatted and original query)
                        log.phoneNumber.contains(query, ignoreCase = true) ||
                        // Match by normalized phone number ONLY if query contains digits
                        (normalizedQuery.isNotEmpty() && 
                         normalizePhoneNumber(log.phoneNumber).contains(normalizedQuery, ignoreCase = true))
                    }
                }
                
                _uiState.update { state ->
                    val grouped = groupLogsByContact(finalLogs)
                    state.copy(
                        callLogs = finalLogs,
                        groupedContactCalls = grouped,
                        isLoading = false,
                        statistics = CallStatistics(
                            totalCalls = filteredByType.size,
                            missedCalls = filteredByType.count { it.callType == CallType.MISSED },
                            incomingCalls = filteredByType.count { it.callType == CallType.INCOMING },
                            outgoingCalls = filteredByType.count { it.callType == CallType.OUTGOING },
                            totalDuration = filteredByType.sumOf { it.duration },
                            averageDuration = if (filteredByType.isNotEmpty()) 
                                filteredByType.sumOf { it.duration } / filteredByType.size else 0L,
                            mostCalledNumber = filteredByType
                                .groupBy { it.phoneNumber }
                                .maxByOrNull { it.value.size }
                                ?.key
                        )
                    )
                }
            }
        }
    }


    fun onEvent(event: HistoryUiEvent) {
        when (event) {
            is HistoryUiEvent.FilterSelected -> {
                selectedFilter.value = event.filter
                _uiState.update { it.copy(selectedFilter = event.filter) }
            }

            is HistoryUiEvent.SearchQueryChanged -> {
                searchQuery.value = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
            }

            is HistoryUiEvent.ViewModeChanged -> {
                _uiState.update { it.copy(viewMode = event.mode) }
            }

            is HistoryUiEvent.BlockNumber -> {
                viewModelScope.launch {
                    try {
                        blockNumberUseCase(event.phoneNumber, "Blocked from call history")
                        _uiState.update { it.copy(successMessage = "Number blocked successfully") }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }

            is HistoryUiEvent.RefreshLogs -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isRefreshing = true) }
                    try {
                        // Force re-observe call logs from system
                        syncCallLogsUseCase()
                        // No need to re-observe - the existing flow will update automatically
                        // Just wait a moment for the system to process
                        kotlinx.coroutines.delay(100)
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    } finally {
                        // Always ensure refreshing is set to false
                        _uiState.update { it.copy(isRefreshing = false) }
                    }
                }
            }

            is HistoryUiEvent.DismissError -> {
                _uiState.update { it.copy(error = null) }
            }
            
            is HistoryUiEvent.DismissSuccessMessage -> {
                _uiState.update { it.copy(successMessage = null) }
            }
            
            is HistoryUiEvent.ShowDeleteAllForNumber -> {
                _uiState.update {
                    it.copy(
                        showDeleteAllForNumberConfirmation = true,
                        pendingDeleteAllPhoneNumber = event.phoneNumber
                    )
                }
            }

            is HistoryUiEvent.ConfirmDeleteAllForNumber -> {
                val phone = _uiState.value.pendingDeleteAllPhoneNumber ?: return
                viewModelScope.launch {
                    try {
                        deleteCallLogsByNumberUseCase(phone)
                        _uiState.update {
                            it.copy(
                                showDeleteAllForNumberConfirmation = false,
                                pendingDeleteAllPhoneNumber = null,
                                successMessage = "Calls removed from history"
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                showDeleteAllForNumberConfirmation = false,
                                pendingDeleteAllPhoneNumber = null,
                                error = e.message
                            )
                        }
                    }
                }
            }

            is HistoryUiEvent.DismissDeleteAllForNumber -> {
                _uiState.update {
                    it.copy(
                        showDeleteAllForNumberConfirmation = false,
                        pendingDeleteAllPhoneNumber = null
                    )
                }
            }

            is HistoryUiEvent.ShowBlockConfirmation -> {
                _uiState.update { 
                    it.copy(
                        showBlockConfirmation = true,
                        pendingActionCallLog = event.callLog
                    ) 
                }
            }
            
            is HistoryUiEvent.ConfirmBlock -> {
                _uiState.value.pendingActionCallLog?.let { callLog ->
                    // Cancel any existing undo timer
                    undoTimerJob?.cancel()
                    
                    // Store the phone number for potential undo
                    _uiState.update { 
                        it.copy(
                            showBlockConfirmation = false,
                            pendingActionCallLog = null,
                            blockedNumber = callLog.phoneNumber,
                            showUndoSnackbar = true
                        ) 
                    }
                    
                    // Start timer for permanent blocking
                    undoTimerJob = viewModelScope.launch {
                        try {
                            kotlinx.coroutines.delay(UNDO_DELAY_MS)
                            // Time expired, perform actual blocking
                            blockNumberUseCase(callLog.phoneNumber, "Blocked from call history")
                            _uiState.update { 
                                it.copy(
                                    blockedNumber = null,
                                    showUndoSnackbar = false,
                                    successMessage = "Number blocked successfully"
                                ) 
                            }
                        } catch (e: Exception) {
                            if (e !is kotlinx.coroutines.CancellationException) {
                                _uiState.update { 
                                    it.copy(
                                        blockedNumber = null,
                                        showUndoSnackbar = false,
                                        error = e.message
                                    ) 
                                }
                            }
                        }
                    }
                }
            }
            
            is HistoryUiEvent.DismissBlockConfirmation -> {
                _uiState.update { 
                    it.copy(
                        showBlockConfirmation = false,
                        pendingActionCallLog = null
                    ) 
                }
            }
            
            // Undo (block confirmation flow only)
            is HistoryUiEvent.UndoBlock -> {
                // Cancel the blocking timer
                undoTimerJob?.cancel()
                
                // Clear the blocked number (no actual unblocking needed as it wasn't blocked yet)
                _uiState.update { 
                    it.copy(
                        blockedNumber = null,
                        showUndoSnackbar = false,
                        successMessage = "Block cancelled"
                    ) 
                }
            }
            
            is HistoryUiEvent.DismissUndoSnackbar -> {
                undoTimerJob?.cancel()
                _uiState.update {
                    it.copy(
                        showUndoSnackbar = false,
                        blockedNumber = null
                    )
                }
            }
            
            is HistoryUiEvent.PrepareCall -> prepareCallWithSim(event.phoneNumber)
            
            is HistoryUiEvent.ClearSimCallAction -> {
                _uiState.update { it.copy(simCallAction = null) }
            }
            
            // Selection mode (grouped rows — key is normalized phone group key)
            is HistoryUiEvent.GroupedRowLongPressed -> {
                _uiState.update {
                    it.copy(
                        isSelectionMode = true,
                        selectedGroupKeys = setOf(event.groupKey)
                    )
                }
            }

            is HistoryUiEvent.ToggleGroupedSelection -> {
                val currentSelected = _uiState.value.selectedGroupKeys
                val newSelected = if (event.groupKey in currentSelected) {
                    currentSelected - event.groupKey
                } else {
                    currentSelected + event.groupKey
                }
                if (newSelected.isEmpty()) {
                    _uiState.update { it.copy(isSelectionMode = false, selectedGroupKeys = emptySet()) }
                } else {
                    _uiState.update { it.copy(selectedGroupKeys = newSelected) }
                }
            }

            is HistoryUiEvent.ExitSelectionMode -> {
                _uiState.update { it.copy(isSelectionMode = false, selectedGroupKeys = emptySet()) }
            }

            is HistoryUiEvent.SelectAllCallLogs -> {
                val keys = _uiState.value.groupedContactCalls.map { it.groupKey }.toSet()
                _uiState.update { it.copy(selectedGroupKeys = keys) }
            }

            is HistoryUiEvent.DeselectAllCallLogs -> {
                _uiState.update { it.copy(isSelectionMode = false, selectedGroupKeys = emptySet()) }
            }

            is HistoryUiEvent.DeleteSelectedCallLogs -> {
                val keys = _uiState.value.selectedGroupKeys
                if (keys.isEmpty()) return
                val groups = _uiState.value.groupedContactCalls.filter { it.groupKey in keys }
                val phoneNumbers = groups.map { it.phoneNumber }
                viewModelScope.launch {
                    try {
                        deleteCallLogsByNumbersUseCase(phoneNumbers)
                        val cleared = groups.sumOf { it.callCount }
                        _uiState.update {
                            it.copy(
                                isSelectionMode = false,
                                selectedGroupKeys = emptySet(),
                                successMessage = "${groups.size} conversations removed ($cleared entries)"
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }

            is HistoryUiEvent.BlockSelectedCallLogs -> {
                val keys = _uiState.value.selectedGroupKeys
                if (keys.isEmpty()) return
                viewModelScope.launch {
                    try {
                        val groups = _uiState.value.groupedContactCalls.filter { it.groupKey in keys }
                        val uniqueNumbers = groups.map { it.phoneNumber }.distinct()
                        uniqueNumbers.forEach { number ->
                            blockNumberUseCase(number, "Blocked from call history")
                        }
                        _uiState.update {
                            it.copy(
                                isSelectionMode = false,
                                selectedGroupKeys = emptySet(),
                                successMessage = "${uniqueNumbers.size} numbers blocked"
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }
        }
    }

    private fun prepareCallWithSim(phoneNumber: String) {
        viewModelScope.launch {
            when (val route = simCallRouter.resolveRoute()) {
                is SimCallRouter.CallRoute.Direct -> {
                    _uiState.update { it.copy(simCallAction = SimCallAction.MakeCall(phoneNumber, route.subscriptionId)) }
                }
                is SimCallRouter.CallRoute.AskSim -> {
                    _uiState.update { it.copy(simCallAction = SimCallAction.ShowSimPicker(phoneNumber, route.availableSims)) }
                }
            }
        }
    }

    private fun filterCallLogs(logs: List<CallLog>, filter: CallFilter): List<CallLog> {
        return when (filter) {
            CallFilter.ALL -> logs
            CallFilter.MISSED -> logs.filter { it.callType == CallType.MISSED }
            CallFilter.INCOMING -> logs.filter { it.callType == CallType.INCOMING }
            CallFilter.OUTGOING -> logs.filter { it.callType == CallType.OUTGOING }
            CallFilter.REJECTED -> logs.filter { it.callType == CallType.REJECTED }
            // Show calls from blocked numbers OR calls with BLOCKED type
            CallFilter.BLOCKED -> logs.filter { it.isBlocked || it.callType == CallType.BLOCKED }
        }.sortedByDescending { it.timestamp }
    }

    private fun groupLogsByContact(logs: List<CallLog>): List<GroupedContactCalls> {
        val groupedCalls = mutableListOf<GroupedContactCalls>()
        var currentBatch = mutableListOf<CallLog>()
        var currentNumberKey: String? = null
        var currentCallType: CallType? = null

        fun flushCurrentBatch() {
            if (currentBatch.isEmpty()) return

            val latestCall = currentBatch.first()
            val numberKey = normalizePhoneNumber(latestCall.phoneNumber)
            val missedCount = currentBatch.count { it.callType == CallType.MISSED }

            groupedCalls += GroupedContactCalls(
                groupKey = "${numberKey}_${latestCall.callType}_${latestCall.timestamp}",
                phoneNumber = latestCall.phoneNumber,
                contactName = latestCall.contactName,
                contactPhotoUri = latestCall.contactPhotoUri,
                latestCall = latestCall,
                callCount = currentBatch.size,
                missedCount = missedCount,
                lastTimestamp = latestCall.timestamp
            )
        }

        logs.sortedByDescending { it.timestamp }.forEach { call ->
            val numberKey = normalizePhoneNumber(call.phoneNumber)
            val shouldContinueBatch = currentBatch.isNotEmpty() &&
                currentNumberKey == numberKey &&
                currentCallType == call.callType

            if (!shouldContinueBatch) {
                flushCurrentBatch()
                currentBatch = mutableListOf()
                currentNumberKey = numberKey
                currentCallType = call.callType
            }

            currentBatch += call
        }

        flushCurrentBatch()

        return groupedCalls.sortedByDescending { it.lastTimestamp }
    }

    /**
     * Normalize phone number for grouping
     */
    private fun normalizePhoneNumber(phone: String): String {
        val cleaned = phone.replace(Regex("[^0-9]"), "")
        // Use last 9 digits for matching (handles different country codes)
        return if (cleaned.length > 9) cleaned.takeLast(9) else cleaned
    }
    
    /**
     * ✅ Load notes for a specific phone number
     * ✅ FIX 2.6: Cancel previous collection job before starting a new one
     */
    private var notesJob: kotlinx.coroutines.Job? = null
    
    fun loadNotesForNumber(phoneNumber: String) {
        notesJob?.cancel() // ✅ Cancel previous collector to prevent stale data
        notesJob = viewModelScope.launch {
            try {
                val normalized = normalizePhoneNumber(phoneNumber)
                
                // Get all notes and filter by phone number
                contactNoteRepository.getAllNotes().collect { allNotes ->
                    val matchingNotes = allNotes.filter { note ->
                        val noteNormalized = normalizePhoneNumber(note.phoneNumber)
                        // Match if normalized versions are equal or one contains the other
                        noteNormalized == normalized ||
                        noteNormalized.endsWith(normalized) ||
                        normalized.endsWith(noteNormalized) ||
                        note.phoneNumber == phoneNumber
                    }
                    _uiState.update { it.copy(notesForSelectedNumber = matchingNotes) }
                }
            } catch (e: Exception) {
                // Ignore errors, just don't show notes
                _uiState.update { it.copy(notesForSelectedNumber = emptyList()) }
            }
        }
    }
    
    /**
     * ✅ Clear notes when bottom sheet is dismissed
     */
    fun clearSelectedNotes() {
        _uiState.update { it.copy(notesForSelectedNumber = emptyList()) }
    }
    
    /**
     * Clean up resources when ViewModel is destroyed
     */
    override fun onCleared() {
        super.onCleared()
        observeCallLogsJob?.cancel()
        undoTimerJob?.cancel()
        notesJob?.cancel()
    }
}
