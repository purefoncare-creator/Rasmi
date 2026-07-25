package com.rasmi.purevon.presentation.screen.contacts

import com.rasmi.purevon.domain.model.Contact

/**
 * UI State for Contacts Screen
 */
data class ContactsUiState(
    val allContacts: List<Contact> = emptyList(),
    val favoriteContacts: List<Contact> = emptyList(),
    val blockedContacts: List<Contact> = emptyList(),
    val displayedContacts: List<Contact> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ContactFilter = ContactFilter.ALL,
    val isLoading: Boolean = false,
    val error: String? = null,
    val scrollToIndex: Int? = null,
    val snackbarMessage: SnackbarMessage? = null,
    val pendingDeleteContact: Contact? = null,
    val isSelectionMode: Boolean = false,
    val selectedContactIds: Set<Long> = emptySet()
)

/**
 * Snackbar message with type and action
 */
data class SnackbarMessage(
    val message: String,
    val type: SnackbarType = SnackbarType.INFO,
    val actionLabel: String? = null,
    val action: SnackbarAction? = null
)

/**
 * Serializable snackbar actions — avoids lambdas in data class (breaks StateFlow distinctUntilChanged)
 */
enum class SnackbarAction {
    UNDO_DELETE
}

enum class SnackbarType {
    SUCCESS,
    ERROR,
    INFO,
    WARNING
}

/**
 * Contact Filter Options
 */
enum class ContactFilter {
    ALL,
    FAVORITES,
    BLOCKED
}

/**
 * UI Events for Contacts Screen
 */
sealed class ContactsUiEvent {
    data class SearchQueryChanged(val query: String) : ContactsUiEvent()
    data class FilterSelected(val filter: ContactFilter) : ContactsUiEvent()
    data class ContactSelected(val contact: Contact) : ContactsUiEvent()
    data class ContactLongPressed(val contactId: Long) : ContactsUiEvent()
    data class ToggleContactSelection(val contactId: Long) : ContactsUiEvent()
    data object ExitSelectionMode : ContactsUiEvent()
    data object SelectAll : ContactsUiEvent()
    data object DeselectAll : ContactsUiEvent()
    data class ToggleFavorite(val contactId: Long) : ContactsUiEvent()
    data class DeleteContact(val contactId: Long) : ContactsUiEvent()
    data class ConfirmDeleteContact(val contactId: Long) : ContactsUiEvent()
    data object DeleteSelectedContacts : ContactsUiEvent()
    data object BlockSelectedContacts : ContactsUiEvent()
    data object AddSelectedToFavorites : ContactsUiEvent()
    data object UndoDelete : ContactsUiEvent()
    data class BlockContact(val phoneNumber: String, val isBlocked: Boolean) : ContactsUiEvent()
    data class ScrollToLetter(val letter: Char) : ContactsUiEvent()
    data object ClearScrollIndex : ContactsUiEvent()
    data object DismissError : ContactsUiEvent()
    data object DismissSnackbar : ContactsUiEvent()
    data object AddContact : ContactsUiEvent()
}

/**
 * Grouped contacts by first letter
 */
data class ContactGroup(
    val letter: Char,
    val contacts: List<Contact>
)
