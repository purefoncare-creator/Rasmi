package com.rasmi.purevon.presentation.screen.contacts

import android.content.Context
import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.ContactNoteDao
import com.rasmi.purevon.data.local.entity.ContactNoteEntity
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.usecase.block.BlockNumberUseCase
import com.rasmi.purevon.domain.usecase.block.UnblockNumberUseCase
import com.rasmi.purevon.domain.usecase.contact.DeleteContactUseCase
import com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
import com.rasmi.purevon.domain.usecase.contact.GetFavoriteContactsUseCase
import com.rasmi.purevon.domain.usecase.contact.SearchContactsUseCase
import com.rasmi.purevon.domain.usecase.contact.ToggleFavoriteUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var getAllContactsUseCase: GetAllContactsUseCase
    private lateinit var getFavoriteContactsUseCase: GetFavoriteContactsUseCase
    private lateinit var searchContactsUseCase: SearchContactsUseCase
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var deleteContactUseCase: DeleteContactUseCase
    private lateinit var blockNumberUseCase: BlockNumberUseCase
    private lateinit var unblockNumberUseCase: UnblockNumberUseCase
    private lateinit var contactNoteDao: ContactNoteDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        getAllContactsUseCase = mockk(relaxed = true)
        getFavoriteContactsUseCase = mockk(relaxed = true)
        searchContactsUseCase = mockk(relaxed = true)
        toggleFavoriteUseCase = mockk(relaxed = true)
        deleteContactUseCase = mockk(relaxed = true)
        blockNumberUseCase = mockk(relaxed = true)
        unblockNumberUseCase = mockk(relaxed = true)
        contactNoteDao = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ContactsViewModel {
        return ContactsViewModel(
            context,
            getAllContactsUseCase,
            getFavoriteContactsUseCase,
            searchContactsUseCase,
            toggleFavoriteUseCase,
            deleteContactUseCase,
            blockNumberUseCase,
            unblockNumberUseCase,
            contactNoteDao
        )
    }

    private fun contact(
        id: Long = 1L,
        name: String = "Alice",
        number: String = "+966501234567",
        isFavorite: Boolean = false,
        isBlocked: Boolean = false
    ) = Contact(
        id = id,
        displayName = name,
        phoneNumber = number,
        phoneType = null,
        photoUri = null,
        email = null,
        company = null,
        isFavorite = isFavorite,
        isBlocked = isBlocked,
        lastContactedTime = null,
        timesContacted = 0,
        preferredSimSlot = null
    )

    @Test
    fun `initial state has empty contacts and not loading`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.displayedContacts).isEmpty()
        assertThat(vm.uiState.value.selectedFilter).isEqualTo(ContactFilter.ALL)
    }

    @Test
    fun `loads and filters contacts by default filter`() = runTest {
        val contacts = listOf(
            contact(id = 1L, name = "Bob"),
            contact(id = 2L, name = "Alice", isFavorite = true)
        )
        every { getAllContactsUseCase() } returns flowOf(contacts)
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.allContacts).hasSize(2)
        assertThat(vm.uiState.value.displayedContacts).hasSize(2)
        assertThat(vm.uiState.value.displayedContacts[0].displayName).isEqualTo("Alice")
    }

    @Test
    fun `favorites filter shows only favorites`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(listOf(
            contact(id = 1L, name = "Alice"),
            contact(id = 2L, name = "Bob", isFavorite = true)
        ))
        every { getFavoriteContactsUseCase() } returns flowOf(
            listOf(contact(id = 2L, name = "Bob", isFavorite = true))
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.FilterSelected(ContactFilter.FAVORITES))
        advanceUntilIdle()

        assertThat(vm.uiState.value.selectedFilter).isEqualTo(ContactFilter.FAVORITES)
        assertThat(vm.uiState.value.displayedContacts).hasSize(1)
        assertThat(vm.uiState.value.displayedContacts[0].displayName).isEqualTo("Bob")
    }

    @Test
    fun `blocked filter shows only blocked contacts`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(listOf(
            contact(id = 1L, name = "Alice"),
            contact(id = 2L, name = "Bob", isBlocked = true)
        ))
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.FilterSelected(ContactFilter.BLOCKED))
        advanceUntilIdle()

        assertThat(vm.uiState.value.blockedContacts).hasSize(1)
        assertThat(vm.uiState.value.displayedContacts).hasSize(1)
        assertThat(vm.uiState.value.displayedContacts[0].displayName).isEqualTo("Bob")
    }

    @Test
    fun `long press enters selection mode`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.ContactLongPressed(5L))
        assertThat(vm.uiState.value.isSelectionMode).isTrue()
        assertThat(vm.uiState.value.selectedContactIds).containsExactly(5L)
    }

    @Test
    fun `toggle selection adds and removes ids`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.ContactLongPressed(1L))
        vm.onEvent(ContactsUiEvent.ToggleContactSelection(2L))
        assertThat(vm.uiState.value.selectedContactIds).containsExactly(1L, 2L)

        vm.onEvent(ContactsUiEvent.ToggleContactSelection(1L))
        assertThat(vm.uiState.value.selectedContactIds).containsExactly(2L)
    }

    @Test
    fun `toggling off last selection exits selection mode`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.ContactLongPressed(1L))
        vm.onEvent(ContactsUiEvent.ToggleContactSelection(1L))
        assertThat(vm.uiState.value.isSelectionMode).isFalse()
        assertThat(vm.uiState.value.selectedContactIds).isEmpty()
    }

    @Test
    fun `exit selection mode clears ids`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.ContactLongPressed(1L))
        vm.onEvent(ContactsUiEvent.ExitSelectionMode)
        assertThat(vm.uiState.value.isSelectionMode).isFalse()
        assertThat(vm.uiState.value.selectedContactIds).isEmpty()
    }

    @Test
    fun `select all selects displayed contacts`() = runTest {
        val contacts = listOf(contact(id = 1L, name = "A"), contact(id = 2L, name = "B"))
        every { getAllContactsUseCase() } returns flowOf(contacts)
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.SelectAll)
        assertThat(vm.uiState.value.selectedContactIds).containsExactly(1L, 2L)
    }

    @Test
    fun `search query updates state and searches`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        every { searchContactsUseCase("ali") } returns flowOf(
            listOf(contact(id = 1L, name = "Alice"))
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.SearchQueryChanged("ali"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.searchQuery).isEqualTo("ali")
    }

    @Test
    fun `delete selected contacts calls use case for each id`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.ContactLongPressed(1L))
        vm.onEvent(ContactsUiEvent.ToggleContactSelection(2L))
        vm.onEvent(ContactsUiEvent.DeleteSelectedContacts)
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteContactUseCase(1L) }
        coVerify(exactly = 1) { deleteContactUseCase(2L) }
        assertThat(vm.uiState.value.isSelectionMode).isFalse()
    }

    @Test
    fun `confirm delete contact calls use case`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.ConfirmDeleteContact(9L))
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteContactUseCase(9L) }
    }

    @Test
    fun `delete contact schedules pending deletion`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(
            listOf(contact(id = 1L, name = "Alice"))
        )
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.DeleteContact(1L))
        assertThat(vm.uiState.value.pendingDeleteContact).isNotNull()
        assertThat(vm.uiState.value.pendingDeleteContact?.displayName).isEqualTo("Alice")
    }

    @Test
    fun `undo delete cancels pending deletion`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(
            listOf(contact(id = 1L, name = "Alice"))
        )
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.DeleteContact(1L))
        assertThat(vm.uiState.value.pendingDeleteContact).isNotNull()

        vm.onEvent(ContactsUiEvent.UndoDelete)
        assertThat(vm.uiState.value.pendingDeleteContact).isNull()
    }

    @Test
    fun `toggle favorite updates use case`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(
            listOf(contact(id = 1L, name = "Alice", isFavorite = false))
        )
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.ToggleFavorite(1L))
        advanceUntilIdle()

        coVerify(exactly = 1) { toggleFavoriteUseCase(1L, true) }
    }

    @Test
    fun `block contact calls block use case`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.BlockContact("+966501234567", false))
        advanceUntilIdle()

        coVerify(exactly = 1) { blockNumberUseCase("+966501234567", any()) }
    }

    @Test
    fun `unblock contact calls unblock use case`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.BlockContact("+966501234567", true))
        advanceUntilIdle()

        coVerify(exactly = 1) { unblockNumberUseCase("+966501234567") }
    }

    @Test
    fun `scroll to letter sets index`() = runTest {
        val contacts = listOf(contact(id = 1L, name = "Bob"), contact(id = 2L, name = "Alice"))
        every { getAllContactsUseCase() } returns flowOf(contacts)
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.ScrollToLetter('A'))
        assertThat(vm.uiState.value.scrollToIndex).isNotNull()

        vm.onEvent(ContactsUiEvent.ClearScrollIndex)
        assertThat(vm.uiState.value.scrollToIndex).isNull()
    }

    @Test
    fun `dismiss snackbar clears message`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.DismissError)
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `delete selected contacts with empty selection does nothing`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.DeleteSelectedContacts)
        advanceUntilIdle()

        coVerify(exactly = 0) { deleteContactUseCase(any()) }
    }

    @Test
    fun `deselect all clears selection`() = runTest {
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { getFavoriteContactsUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ContactsUiEvent.ContactLongPressed(1L))
        vm.onEvent(ContactsUiEvent.DeselectAll)
        assertThat(vm.uiState.value.isSelectionMode).isFalse()
        assertThat(vm.uiState.value.selectedContactIds).isEmpty()
    }
}
