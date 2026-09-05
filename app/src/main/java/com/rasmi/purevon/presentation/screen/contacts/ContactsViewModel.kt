package com.rasmi.purevon.presentation.screen.contacts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
import com.rasmi.purevon.domain.usecase.contact.GetFavoriteContactsUseCase
import com.rasmi.purevon.domain.usecase.contact.SearchContactsUseCase
import com.rasmi.purevon.domain.usecase.contact.ToggleFavoriteUseCase
import com.rasmi.purevon.domain.usecase.block.BlockNumberUseCase
import com.rasmi.purevon.domain.usecase.block.UnblockNumberUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getAllContactsUseCase: GetAllContactsUseCase,
    private val getFavoriteContactsUseCase: GetFavoriteContactsUseCase,
    private val searchContactsUseCase: SearchContactsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val deleteContactUseCase: com.rasmi.purevon.domain.usecase.contact.DeleteContactUseCase,
    private val blockNumberUseCase: BlockNumberUseCase,
    private val unblockNumberUseCase: UnblockNumberUseCase,
    // ✅ FIX M34: ملاحظات المعاينة العريضة
    private val contactNoteDao: com.rasmi.purevon.data.local.dao.ContactNoteDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(ContactFilter.ALL)
    
    private var deleteJob: Job? = null
    private var contactsJob: Job? = null

    init {
        observeContacts()
        observeSearch()
    }

    /** ✅ FIX M34: معرف جهة الاتصال في المعاينة العريضة */
    private var previewContactId: Long? = null

    /** ✅ FIX M34: تحميل ملاحظات جهة الاتصال للمعاينة العريضة */
    private fun loadPreviewNotes(contactId: Long?) {
        previewContactId = contactId
        if (contactId == null) {
            _uiState.update { it.copy(selectedContactNotes = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val contacts = getAllContactsUseCase().first()
                val phone = contacts.find { it.id == contactId }?.phoneNumber
                    ?.replace(Regex("[^0-9]"), "")?.takeLast(10)
                val notes = if (phone.isNullOrBlank()) emptyList()
                            else contactNoteDao.getNotesByPhoneNumberSync(phone)
                // ✅ تحويل Entity → نموذج النطاق كما في ContactDetailViewModel
                val mapped = notes.map { entity ->
                    com.rasmi.purevon.domain.model.ContactNote(
                        id = entity.id,
                        phoneNumber = entity.phoneNumber,
                        note = entity.note,
                        callDuration = entity.callDuration,
                        isIncoming = entity.isIncoming,
                        createdAt = entity.createdAt
                    )
                }
                _uiState.update { it.copy(selectedContactNotes = mapped) }
            } catch (e: Exception) {
                _uiState.update { it.copy(selectedContactNotes = emptyList()) }
            }
        }
    }

    /** ✅ FIX M34: حذف ملاحظة من المعاينة العريضة */
    private fun deletePreviewNote(noteId: Long) {
        viewModelScope.launch {
            try {
                contactNoteDao.deleteNote(noteId)
                loadPreviewNotes(previewContactId)
            } catch (_: Exception) { }
        }
    }

    private fun observeContacts() {
        contactsJob?.cancel()
        contactsJob = viewModelScope.launch {
            combine(
                getAllContactsUseCase(),
                getFavoriteContactsUseCase(),
                selectedFilter
            ) { allContacts, favorites, filter ->
                Triple(allContacts, favorites, filter)
            }.collect { (allContacts, favorites, filter) ->
                val blocked = allContacts.filter { it.isBlocked }
                _uiState.update { state ->
                    state.copy(
                        allContacts = allContacts,
                        favoriteContacts = favorites,
                        blockedContacts = blocked,
                        displayedContacts = if (searchQuery.value.isEmpty()) {
                            filterContacts(allContacts, favorites, filter)
                        } else {
                            state.displayedContacts
                        },
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isEmpty()) {
                        // Search cleared — restart contacts observation
                        observeContacts()
                    } else {
                        // Active search — cancel contacts observation to prevent race
                        contactsJob?.cancel()
                        searchContactsUseCase(query)
                            .collect { searchResults ->
                                _uiState.update { it.copy(displayedContacts = searchResults.take(50)) }
                            }
                    }
                }
        }
    }

    fun onEvent(event: ContactsUiEvent) {
        when (event) {
            is ContactsUiEvent.SearchQueryChanged -> {
                searchQuery.value = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
            }

            is ContactsUiEvent.FilterSelected -> {
                selectedFilter.value = event.filter
                _uiState.update { it.copy(selectedFilter = event.filter) }
            }

            is ContactsUiEvent.ContactSelected -> {
                // Navigate to contact details or call
            }
            
            is ContactsUiEvent.ContactLongPressed -> {
                // Enter selection mode and select this contact
                _uiState.update { 
                    it.copy(
                        isSelectionMode = true,
                        selectedContactIds = setOf(event.contactId)
                    ) 
                }
            }
            
            is ContactsUiEvent.ToggleContactSelection -> {
                val currentSelected = _uiState.value.selectedContactIds
                val newSelected = if (event.contactId in currentSelected) {
                    currentSelected - event.contactId
                } else {
                    currentSelected + event.contactId
                }
                
                // Exit selection mode if no items selected
                if (newSelected.isEmpty()) {
                    _uiState.update { 
                        it.copy(
                            isSelectionMode = false,
                            selectedContactIds = emptySet()
                        ) 
                    }
                } else {
                    _uiState.update { it.copy(selectedContactIds = newSelected) }
                }
            }
            
            is ContactsUiEvent.ExitSelectionMode -> {
                _uiState.update { 
                    it.copy(
                        isSelectionMode = false,
                        selectedContactIds = emptySet()
                    ) 
                }
            }
            
            is ContactsUiEvent.SelectAll -> {
                val allIds = _uiState.value.displayedContacts.map { it.id }.toSet()
                _uiState.update { it.copy(selectedContactIds = allIds) }
            }
            
            // ✅ FIX M34: ملاحظات المعاينة العريضة
            is ContactsUiEvent.ContactPreviewSelected -> loadPreviewNotes(event.contactId)
            is ContactsUiEvent.DeletePreviewNote -> deletePreviewNote(event.noteId)
            is ContactsUiEvent.DeselectAll -> {                _uiState.update { 
                    it.copy(
                        isSelectionMode = false,
                        selectedContactIds = emptySet()
                    ) 
                }
            }
            
            is ContactsUiEvent.DeleteSelectedContacts -> {
                val selectedIds = _uiState.value.selectedContactIds
                if (selectedIds.isEmpty()) return
                
                viewModelScope.launch {
                    try {
                        selectedIds.forEach { contactId ->
                            deleteContactUseCase(contactId)
                        }
                        _uiState.update { 
                            it.copy(
                                isSelectionMode = false,
                                selectedContactIds = emptySet(),
                                snackbarMessage = SnackbarMessage(
                                    message = "${selectedIds.size} contacts deleted",
                                    type = SnackbarType.SUCCESS
                                )
                            ) 
                        }
                    } catch (e: Exception) {
                        _uiState.update { 
                            it.copy(
                                snackbarMessage = SnackbarMessage(
                                    message = context.getString(R.string.error_failed_delete_contacts),
                                    type = SnackbarType.ERROR
                                )
                            ) 
                        }
                    }
                }
            }
            
            is ContactsUiEvent.BlockSelectedContacts -> {
                val selectedIds = _uiState.value.selectedContactIds
                if (selectedIds.isEmpty()) return
                
                viewModelScope.launch {
                    try {
                        val contacts = _uiState.value.allContacts.filter { it.id in selectedIds }
                        contacts.forEach { contact ->
                            blockNumberUseCase(contact.phoneNumber, "Blocked by user")
                        }
                        _uiState.update { 
                            it.copy(
                                isSelectionMode = false,
                                selectedContactIds = emptySet(),
                                snackbarMessage = SnackbarMessage(
                                    message = context.resources.getQuantityString(R.plurals.success_contacts_blocked, contacts.size, contacts.size),
                                    type = SnackbarType.SUCCESS
                                )
                            ) 
                        }
                    } catch (e: Exception) {
                        _uiState.update { 
                            it.copy(
                                snackbarMessage = SnackbarMessage(
                                    message = context.getString(R.string.error_failed_block_contacts),
                                    type = SnackbarType.ERROR
                                )
                            ) 
                        }
                    }
                }
            }
            
            is ContactsUiEvent.AddSelectedToFavorites -> {
                val selectedIds = _uiState.value.selectedContactIds
                if (selectedIds.isEmpty()) return
                
                viewModelScope.launch {
                    try {
                        selectedIds.forEach { contactId ->
                            toggleFavoriteUseCase(contactId, true)
                        }
                        _uiState.update { 
                            it.copy(
                                isSelectionMode = false,
                                selectedContactIds = emptySet(),
                                snackbarMessage = SnackbarMessage(
                                    message = context.resources.getQuantityString(R.plurals.success_contacts_added_favorites, selectedIds.size, selectedIds.size),
                                    type = SnackbarType.SUCCESS
                                )
                            ) 
                        }
                    } catch (e: Exception) {
                        _uiState.update { 
                            it.copy(
                                snackbarMessage = SnackbarMessage(
                                    message = context.getString(R.string.error_failed_add_favorites),
                                    type = SnackbarType.ERROR
                                )
                            ) 
                        }
                    }
                }
            }

            is ContactsUiEvent.ToggleFavorite -> {
                viewModelScope.launch {
                    try {
                        val contact = _uiState.value.allContacts.find { it.id == event.contactId }
                        contact?.let {
                            val newStatus = !it.isFavorite
                            toggleFavoriteUseCase(
                                it.id,
                                newStatus
                            )
                            _uiState.update { state ->
                                state.copy(
                                    snackbarMessage = SnackbarMessage(
                                        message = if (newStatus) context.getString(R.string.success_added_to_favorites) else context.getString(R.string.success_removed_from_favorites),
                                        type = SnackbarType.SUCCESS
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        _uiState.update { 
                            it.copy(
                                snackbarMessage = SnackbarMessage(
                                    message = context.getString(R.string.error_failed_update_favorite),
                                    type = SnackbarType.ERROR
                                )
                            ) 
                        }
                    }
                }
            }

            is ContactsUiEvent.DeleteContact -> {
                // Show pending delete with undo option
                val contact = _uiState.value.allContacts.find { it.id == event.contactId }
                contact?.let {
                    _uiState.update { state ->
                        state.copy(
                            pendingDeleteContact = contact,
                            snackbarMessage = SnackbarMessage(
                                message = context.getString(R.string.msg_contact_will_be_deleted, contact.displayName),
                                type = SnackbarType.WARNING,
                                actionLabel = "Undo",
                                action = SnackbarAction.UNDO_DELETE
                            )
                        )
                    }
                    
                    // Schedule actual deletion after 5 seconds
                    deleteJob?.cancel()
                    deleteJob = viewModelScope.launch {
                        delay(5000)
                        onEvent(ContactsUiEvent.ConfirmDeleteContact(event.contactId))
                    }
                }
            }
            
            is ContactsUiEvent.ConfirmDeleteContact -> {
                viewModelScope.launch {
                    try {
                        deleteContactUseCase(event.contactId)
                        _uiState.update { 
                            it.copy(
                                pendingDeleteContact = null,
                                snackbarMessage = SnackbarMessage(
                                    message = context.getString(R.string.success_contact_deleted),
                                    type = SnackbarType.SUCCESS
                                )
                            ) 
                        }
                    } catch (e: Exception) {
                        _uiState.update { 
                            it.copy(
                                pendingDeleteContact = null,
                                snackbarMessage = SnackbarMessage(
                                    message = context.getString(R.string.error_failed_delete_contact, e.message ?: ""),
                                    type = SnackbarType.ERROR
                                )
                            ) 
                        }
                    }
                }
            }
            
            is ContactsUiEvent.UndoDelete -> {
                deleteJob?.cancel()
                _uiState.update { 
                    it.copy(
                        pendingDeleteContact = null,
                        snackbarMessage = SnackbarMessage(
                            message = "Deletion cancelled",
                            type = SnackbarType.INFO
                        )
                    ) 
                }
            }

            is ContactsUiEvent.BlockContact -> {
                viewModelScope.launch {
                    try {
                        if (event.isBlocked) {
                            unblockNumberUseCase(event.phoneNumber)
                            _uiState.update { 
                                it.copy(
                                    snackbarMessage = SnackbarMessage(
                                        message = "Contact unblocked",
                                        type = SnackbarType.SUCCESS
                                    )
                                ) 
                            }
                        } else {
                            blockNumberUseCase(event.phoneNumber, "Blocked by user")
                            _uiState.update { 
                                it.copy(
                                    snackbarMessage = SnackbarMessage(
                                        message = "Contact blocked",
                                        type = SnackbarType.SUCCESS
                                    )
                                ) 
                            }
                        }
                    } catch (e: Exception) {
                        _uiState.update { 
                            it.copy(
                                snackbarMessage = SnackbarMessage(
                                    message = "Failed to ${if (event.isBlocked) "unblock" else "block"} contact",
                                    type = SnackbarType.ERROR
                                )
                            ) 
                        }
                    }
                }
            }

            is ContactsUiEvent.ScrollToLetter -> {
                val index = _uiState.value.displayedContacts.indexOfFirst {
                    val firstChar = it.displayName.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    firstChar == event.letter
                }
                if (index != -1) {
                    _uiState.update { it.copy(scrollToIndex = index) }
                }
            }
            
            is ContactsUiEvent.ClearScrollIndex -> {
                _uiState.update { it.copy(scrollToIndex = null) }
            }

            is ContactsUiEvent.DismissSnackbar -> {
                _uiState.update { it.copy(snackbarMessage = null) }
            }

            is ContactsUiEvent.DismissError -> {
                _uiState.update { it.copy(error = null) }
            }

            is ContactsUiEvent.AddContact -> {
                // Navigate to add contact screen
            }
        }
    }

    private fun filterContacts(
        allContacts: List<Contact>,
        favorites: List<Contact>,
        filter: ContactFilter
    ): List<Contact> {
        return when (filter) {
            // ✅ FIX M35: المفضلة أولاً ثم أبجدياً
            ContactFilter.ALL -> allContacts.sortedWith(
                compareByDescending<Contact> { it.isFavorite }
                    .thenBy { it.displayName.trim() }
            )
            ContactFilter.FAVORITES -> favorites.sortedBy { it.displayName.trim() }
            ContactFilter.BLOCKED -> allContacts.filter { it.isBlocked }
        }
    }

    fun getGroupedContacts(): List<ContactGroup> {
        val contacts = _uiState.value.displayedContacts
        return contacts
            .groupBy { 
                val name = it.displayName.trim()
                name.firstOrNull()?.uppercaseChar() ?: '#' 
            }
            .map { (letter, contacts) ->
                ContactGroup(letter, contacts.sortedBy { it.displayName.trim() })
            }
            .sortedBy { it.letter }
    }
}
