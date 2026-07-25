package com.rasmi.purevon.presentation.screen.contactdetail

import android.content.Context
import android.provider.CallLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rasmi.purevon.R
import com.rasmi.purevon.data.local.dao.ContactNoteDao
import com.rasmi.purevon.domain.model.ContactNote
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.repository.CallLogRepository
import com.rasmi.purevon.domain.repository.ContactRepository
import com.rasmi.purevon.domain.usecase.block.BlockNumberUseCase
import com.rasmi.purevon.domain.usecase.block.UnblockNumberUseCase
import com.rasmi.purevon.domain.usecase.contact.DeleteContactUseCase
import com.rasmi.purevon.domain.usecase.contact.ToggleFavoriteUseCase
import com.rasmi.purevon.presentation.navigation.Screen
import com.rasmi.purevon.util.sim.SimCallAction
import com.rasmi.purevon.util.sim.SimCallRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
    private val contactNoteDao: ContactNoteDao,
    private val callLogRepository: CallLogRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val deleteContactUseCase: DeleteContactUseCase,
    private val blockNumberUseCase: BlockNumberUseCase,
    private val unblockNumberUseCase: UnblockNumberUseCase,
    private val simCallRouter: SimCallRouter // ✅ لحل شريحة الاتصال
) : ViewModel() {
    
    private val contactId: Long = try {
        savedStateHandle.toRoute<Screen.ContactDetail>().contactId
    } catch (_: Exception) {
        // Fallback: try raw Long extraction from SavedStateHandle
        savedStateHandle.get<Long>("contactId") ?: 0L
    }
    
    private val _uiState = MutableStateFlow(ContactDetailUiState())
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()
    
    init {
        loadContact()
    }
    
    private fun loadContact() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Use efficient getContactById instead of loading all contacts
                val contact = contactRepository.getContactById(contactId)
                _uiState.update { 
                    it.copy(
                        contact = contact,
                        isLoading = false,
                        error = if (contact == null) "Contact not found" else null
                    )
                }
                
                // Load notes, call statistics for this contact
                contact?.let {
                    loadContactNotes(it.phoneNumber)
                    loadCallStatistics(it.phoneNumber)
                    loadRecentCalls(it.phoneNumber)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    /**
     * Load notes for this contact
     * Searches by both original and normalized phone number
     */
    private fun loadContactNotes(phoneNumber: String) {
        viewModelScope.launch {
            val normalized = normalizePhoneNumber(phoneNumber)
            val digitsOnly = phoneNumber.replace(Regex("[^0-9]"), "")
            // Build list of possible formats to query instead of loading ALL notes
            val possibleFormats = buildSet {
                add(phoneNumber)                          // original: "+966 50 123 4567"
                add(normalized)                           // last 10 digits: "0501234567"
                add(digitsOnly)                           // all digits: "966501234567"
                add("+$digitsOnly")                       // "+966501234567" (Telecom raw format)
                // Suffix variants (last 9 digits)
                if (normalized.length >= 9) add(normalized.takeLast(9))
                if (digitsOnly.length >= 9) add(digitsOnly.takeLast(9))
            }.toList()
            
            contactNoteDao.getNotesByPhoneNumbers(possibleFormats)
                .collect { matchingNotes ->
                    _uiState.update { it.copy(notes = matchingNotes.map { entity ->
                        ContactNote(
                            id = entity.id,
                            phoneNumber = entity.phoneNumber,
                            note = entity.note,
                            callDuration = entity.callDuration,
                            isIncoming = entity.isIncoming,
                            createdAt = entity.createdAt
                        )
                    }) }
                }
        }
    }
    
    /**
     * Load call statistics from system call log
     */
    private fun loadCallStatistics(phoneNumber: String) {
        viewModelScope.launch {
            val stats = getCallStatisticsFromSystem(phoneNumber)
            _uiState.update { it.copy(callStatistics = stats) }
        }
    }
    
    /**
     * Load recent calls for this contact
     */
    private fun loadRecentCalls(phoneNumber: String) {
        viewModelScope.launch {
            android.util.Log.e("ContactDetailVM", "=== RECENT CALLS === Loading for: '$phoneNumber'")
            callLogRepository.getCallLogsByNumber(phoneNumber)
                .catch { e -> android.util.Log.e("ContactDetailVM", "=== RECENT CALLS === Error: ${e.message}", e) }
                .collect { calls ->
                    android.util.Log.e("ContactDetailVM", "=== RECENT CALLS === Received ${calls.size} calls")
                    calls.forEachIndexed { i, call ->
                        if (i < 10) {
                            android.util.Log.e("ContactDetailVM", "  [$i] ${call.callType} | ${call.phoneNumber} | ${call.timestamp}")
                        }
                    }
                    // Sort by timestamp descending and take latest 20
                    val recentCalls = calls
                        .sortedByDescending { it.timestamp }
                        .take(20)
                    android.util.Log.e("ContactDetailVM", "=== RECENT CALLS === Showing ${recentCalls.size} calls")
                    _uiState.update { it.copy(recentCalls = recentCalls) }
                }
        }
    }
    
    /**
     * Get call statistics from system call log via repository
     */
    private suspend fun getCallStatisticsFromSystem(phoneNumber: String): CallStatistics {
        val stats = callLogRepository.getSystemCallStatisticsForNumber(phoneNumber)
        return CallStatistics(
            incomingCalls = stats.incomingCalls,
            outgoingCalls = stats.outgoingCalls,
            missedCalls = stats.missedCalls,
            totalCalls = stats.totalCalls,
            totalDurationSeconds = stats.totalDurationSeconds,
            lastCallTimestamp = stats.lastCallTimestamp
        )
    }
    
    /**
     * Normalize phone number for comparison
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
    }
    
    fun onEvent(event: ContactDetailUiEvent) {
        when (event) {
            is ContactDetailUiEvent.RefreshContact -> loadContact()
            is ContactDetailUiEvent.ToggleFavorite -> {
                viewModelScope.launch {
                    _uiState.value.contact?.let { contact ->
                        try {
                            val newFavoriteStatus = !contact.isFavorite
                            toggleFavoriteUseCase(contact.id, newFavoriteStatus)
                            
                            // Update UI immediately (optimistic update)
                            _uiState.update { state ->
                                state.copy(
                                    contact = state.contact?.copy(isFavorite = newFavoriteStatus),
                                    successMessage = if (newFavoriteStatus) "Added to favorites" else "Removed from favorites"
                                )
                            }
                        } catch (e: Exception) {
                            _uiState.update { it.copy(error = e.message) }
                        }
                    }
                }
            }
            
            is ContactDetailUiEvent.DeleteContact -> {
                viewModelScope.launch {
                    try {
                        deleteContactUseCase(contactId)
                        _uiState.update { it.copy(isDeleted = true) }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }
            
            is ContactDetailUiEvent.ToggleBlock -> {
                viewModelScope.launch {
                    _uiState.value.contact?.let { contact ->
                        try {
                            val newBlockedStatus = !contact.isBlocked
                            
                            if (contact.isBlocked) {
                                unblockNumberUseCase(contact.phoneNumber)
                            } else {
                                blockNumberUseCase(contact.phoneNumber, "Blocked by user")
                            }
                            
                            // Update UI immediately (optimistic update)
                            _uiState.update { state ->
                                state.copy(
                                    contact = state.contact?.copy(isBlocked = newBlockedStatus),
                                    successMessage = if (newBlockedStatus) "Contact blocked" else "Contact unblocked"
                                )
                            }
                        } catch (e: Exception) {
                            _uiState.update { it.copy(error = e.message) }
                        }
                    }
                }
            }
            
            is ContactDetailUiEvent.DeleteNote -> {
                viewModelScope.launch {
                    try {
                        contactNoteDao.deleteNote(event.noteId)
                        _uiState.update { it.copy(successMessage = "Note deleted") }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }
            
            is ContactDetailUiEvent.DismissError -> {
                _uiState.update { it.copy(error = null) }
            }
            
            is ContactDetailUiEvent.DismissSuccessMessage -> {
                _uiState.update { it.copy(successMessage = null) }
            }
            
            is ContactDetailUiEvent.DismissSnackbar -> {
                _uiState.update { it.copy(error = null, successMessage = null) }
            }
            
            is ContactDetailUiEvent.PrepareCall -> prepareCallWithSim(event.phoneNumber)
            
            is ContactDetailUiEvent.ClearSimCallAction -> {
                _uiState.update { it.copy(simCallAction = null) }
            }
            
            is ContactDetailUiEvent.ToggleShowAllCalls -> {
                _uiState.update { it.copy(showAllCalls = !it.showAllCalls) }
            }
            
            is ContactDetailUiEvent.SaveContact -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true) }
                    try {
                        _uiState.value.contact?.let { contact ->
                            // Construct display name from first and last name
                            val displayName = if (event.lastName.isNotBlank()) 
                                "${event.firstName} ${event.lastName}".trim() 
                            else 
                                event.firstName.trim()
                                
                            val updatedContact = contact.copy(
                                displayName = displayName,
                                phoneNumber = event.phoneNumber,
                                email = event.email,
                                company = event.company
                            )
                            
                            withContext(Dispatchers.IO) {
                                contactRepository.updateContact(updatedContact)
                            }
                            
                            // Reload contact data
                            loadContact()
                            
                            _uiState.update { it.copy(isLoading = false, successMessage = context.getString(R.string.success_contact_updated)) }
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.error_failed_update_contact, e.message ?: "")) }
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
}

/**
 * Call statistics for a contact
 */
data class CallStatistics(
    val incomingCalls: Int = 0,
    val outgoingCalls: Int = 0,
    val missedCalls: Int = 0,
    val totalCalls: Int = 0,
    val totalDurationSeconds: Long = 0,
    val lastCallTimestamp: Long? = null
) {
    /**
     * Format total duration as readable string
     */
    fun getFormattedDuration(): String {
        val hours = totalDurationSeconds / 3600
        val minutes = (totalDurationSeconds % 3600) / 60
        val seconds = totalDurationSeconds % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}

data class ContactDetailUiState(
    val contact: Contact? = null,
    val notes: List<ContactNote> = emptyList(),
    val callStatistics: CallStatistics = CallStatistics(),
    val recentCalls: List<com.rasmi.purevon.domain.model.CallLog> = emptyList(),
    val showAllCalls: Boolean = false,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val snackbarMessage: String? = null,
    val snackbarType: SnackbarType? = null,
    val simCallAction: SimCallAction? = null // ✅ إجراء تحديد الشريحة
)

enum class SnackbarType {
    SUCCESS, ERROR, INFO, WARNING
}

sealed class ContactDetailUiEvent {
    data object ToggleFavorite : ContactDetailUiEvent()
    data object DeleteContact : ContactDetailUiEvent()
    data object ToggleBlock : ContactDetailUiEvent()
    data class DeleteNote(val noteId: Long) : ContactDetailUiEvent()
    data object ToggleShowAllCalls : ContactDetailUiEvent()
    data object DismissError : ContactDetailUiEvent()
    data object DismissSuccessMessage : ContactDetailUiEvent()
    data object DismissSnackbar : ContactDetailUiEvent()
    data class PrepareCall(val phoneNumber: String) : ContactDetailUiEvent() // ✅ بدء تحديد شريحة الاتصال
    data object ClearSimCallAction : ContactDetailUiEvent()                  // ✅ امسح بعد المعالجة
    data class SaveContact(
        val firstName: String,
        val lastName: String,
        val phoneNumber: String,
        val email: String?,
        val company: String?
    ) : ContactDetailUiEvent()
    data object RefreshContact : ContactDetailUiEvent()
}
