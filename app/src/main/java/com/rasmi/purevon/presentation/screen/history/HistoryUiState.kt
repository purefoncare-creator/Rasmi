package com.rasmi.purevon.presentation.screen.history

import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.util.sim.SimCallAction
/**
 * View Mode for History Screen (reserved)
 */
enum class HistoryViewMode {
    DETAILED
}

/**
 * Grouped calls by contact/number with latest call info
 */
data class GroupedContactCalls(
    /** Stable key from [HistoryViewModel] normalization — used for selection */
    val groupKey: String,
    val phoneNumber: String,
    val contactName: String?,
    val contactPhotoUri: String?,
    val latestCall: CallLog,
    val callCount: Int,
    val missedCount: Int,
    val lastTimestamp: Long
)

/**
 * UI State for Call History Screen
 */
data class HistoryUiState(
    val callLogs: List<CallLog> = emptyList(),
    /** One row per phone/contact — default history presentation */
    val groupedContactCalls: List<GroupedContactCalls> = emptyList(),
    val viewMode: HistoryViewMode = HistoryViewMode.DETAILED,
    val selectedFilter: CallFilter = CallFilter.ALL,
    val searchQuery: String = "",
    val statistics: CallStatistics? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val contacts: List<Contact> = emptyList(),
    val notesForSelectedNumber: List<com.rasmi.purevon.domain.model.ContactNote> = emptyList(),
    val showDeleteAllForNumberConfirmation: Boolean = false,
    val pendingDeleteAllPhoneNumber: String? = null,
    val showBlockConfirmation: Boolean = false,
    val pendingActionCallLog: CallLog? = null,
    val blockedNumber: String? = null,
    val showUndoSnackbar: Boolean = false,
    val simCallAction: SimCallAction? = null,
    val isSelectionMode: Boolean = false,
    val selectedGroupKeys: Set<String> = emptySet(),
    val phoneNumbersWithNotes: Set<String> = emptySet()
) {
    /**
     * Check if a phone number has saved notes
     * Uses suffix matching (last 9 digits) to handle country code variations
     */
    fun hasNoteForNumber(phoneNumber: String): Boolean {
        val normalizedInput = normalizePhoneNumber(phoneNumber)
        if (normalizedInput.length < 9) return phoneNumbersWithNotes.any { normalizePhoneNumber(it) == normalizedInput }
        val inputSuffix = normalizedInput.takeLast(9)
        return phoneNumbersWithNotes.any { 
            val itNorm = normalizePhoneNumber(it)
            itNorm.length >= 9 && itNorm.takeLast(9) == inputSuffix
        }
    }

    /**
     * Find contact by phone number - returns full contact info
     */
    fun getContactForNumber(phoneNumber: String): Contact? {
        val normalizedInput = normalizePhoneNumber(phoneNumber)
        
        return contacts.find { contact ->
            val normalizedContact = normalizePhoneNumber(contact.phoneNumber)
            
            // Exact match
            if (normalizedInput == normalizedContact) return@find true
            
            // Match without country code (last 9-10 digits) - only perform if both have at least 9 digits
            if (normalizedInput.length >= 9 && normalizedContact.length >= 9) {
                val inputSuffix = normalizedInput.takeLast(9)
                val contactSuffix = normalizedContact.takeLast(9)
                inputSuffix == contactSuffix
            } else {
                false
            }
        }
    }
    
    /**
     * Find contact ID by phone number - strict matching
     */
    fun getContactIdForNumber(phoneNumber: String): Long? {
        return getContactForNumber(phoneNumber)?.id
    }
    
    /**
     * Get contact name for a phone number, or null if not found
     */
    fun getContactNameForNumber(phoneNumber: String): String? {
        return getContactForNumber(phoneNumber)?.displayName
    }
    
    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
    }
}

/**
 * Call Filter Options
 */
enum class CallFilter {
    ALL,
    MISSED,
    INCOMING,
    OUTGOING,
    REJECTED,
    BLOCKED
}

/**
 * Call Statistics
 */
data class CallStatistics(
    val totalCalls: Int,
    val missedCalls: Int,
    val incomingCalls: Int,
    val outgoingCalls: Int,
    val totalDuration: Long, // in seconds
    val averageDuration: Long,
    val mostCalledNumber: String?
)

/**
 * UI Events for History Screen
 */
sealed class HistoryUiEvent {
    data class FilterSelected(val filter: CallFilter) : HistoryUiEvent()
    data class SearchQueryChanged(val query: String) : HistoryUiEvent()
    data class ViewModeChanged(val mode: HistoryViewMode) : HistoryUiEvent()
    data class BlockNumber(val phoneNumber: String) : HistoryUiEvent()
    data object RefreshLogs : HistoryUiEvent()
    data object DismissError : HistoryUiEvent()
    data object DismissSuccessMessage : HistoryUiEvent()

    data class ShowDeleteAllForNumber(val phoneNumber: String) : HistoryUiEvent()
    data object ConfirmDeleteAllForNumber : HistoryUiEvent()
    data object DismissDeleteAllForNumber : HistoryUiEvent()

    data class ShowBlockConfirmation(val callLog: CallLog) : HistoryUiEvent()
    data object ConfirmBlock : HistoryUiEvent()
    data object DismissBlockConfirmation : HistoryUiEvent()

    data object UndoBlock : HistoryUiEvent()
    data object DismissUndoSnackbar : HistoryUiEvent()
    
    // SIM call events
    data class PrepareCall(val phoneNumber: String) : HistoryUiEvent()
    data object ClearSimCallAction : HistoryUiEvent()

    data class GroupedRowLongPressed(val groupKey: String) : HistoryUiEvent()
    data class ToggleGroupedSelection(val groupKey: String) : HistoryUiEvent()
    data object ExitSelectionMode : HistoryUiEvent()
    data object SelectAllCallLogs : HistoryUiEvent()
    data object DeselectAllCallLogs : HistoryUiEvent()
    data object DeleteSelectedCallLogs : HistoryUiEvent()
    data object BlockSelectedCallLogs : HistoryUiEvent()
}
