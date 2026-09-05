package com.rasmi.purevon.presentation.screen.contactdetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.ContactNoteDao
import com.rasmi.purevon.data.local.entity.ContactNoteEntity
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.repository.CallLogRepository
import com.rasmi.purevon.domain.repository.ContactRepository
import com.rasmi.purevon.domain.repository.SystemCallStatistics
import com.rasmi.purevon.domain.usecase.block.BlockNumberUseCase
import com.rasmi.purevon.domain.usecase.block.UnblockNumberUseCase
import com.rasmi.purevon.domain.usecase.contact.DeleteContactUseCase
import com.rasmi.purevon.domain.usecase.contact.ToggleFavoriteUseCase
import com.rasmi.purevon.util.sim.SimCallAction
import com.rasmi.purevon.util.sim.SimCallRouter
import com.rasmi.purevon.util.sim.SimInfo
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
class ContactDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var context: Context
    private lateinit var contactRepository: ContactRepository
    private lateinit var contactNoteDao: ContactNoteDao
    private lateinit var callLogRepository: CallLogRepository
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var deleteContactUseCase: DeleteContactUseCase
    private lateinit var blockNumberUseCase: BlockNumberUseCase
    private lateinit var unblockNumberUseCase: UnblockNumberUseCase
    private lateinit var simCallRouter: SimCallRouter

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = mockk(relaxed = true)
        context = mockk(relaxed = true)
        contactRepository = mockk(relaxed = true)
        contactNoteDao = mockk(relaxed = true)
        callLogRepository = mockk(relaxed = true)
        toggleFavoriteUseCase = mockk(relaxed = true)
        deleteContactUseCase = mockk(relaxed = true)
        blockNumberUseCase = mockk(relaxed = true)
        unblockNumberUseCase = mockk(relaxed = true)
        simCallRouter = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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

    private fun createViewModel(): ContactDetailViewModel {
        every { savedStateHandle.get<Long>("contactId") } returns 42L
        coEvery { callLogRepository.getSystemCallStatisticsForNumber(any()) } returns
            SystemCallStatistics()
        every { callLogRepository.getCallLogsByNumber(any()) } returns flowOf(emptyList())
        return ContactDetailViewModel(
            savedStateHandle,
            context,
            contactRepository,
            contactNoteDao,
            callLogRepository,
            toggleFavoriteUseCase,
            deleteContactUseCase,
            blockNumberUseCase,
            unblockNumberUseCase,
            simCallRouter
        )
    }

    private fun stubContact(value: Contact?) {
        coEvery { contactRepository.getContactById(42L) } returns value
    }

    @Test
    fun `loads contact on init`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.contact?.id).isEqualTo(42L)
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `contact not found sets error`() = runTest {
        stubContact(null)
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.contact).isNull()
        assertThat(vm.uiState.value.error).isEqualTo("Contact not found")
    }

    @Test
    fun `toggle favorite updates use case and state`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.ToggleFavorite)
        advanceUntilIdle()
        coVerify(exactly = 1) { toggleFavoriteUseCase(42L, true) }
        assertThat(vm.uiState.value.contact?.isFavorite).isTrue()
        assertThat(vm.uiState.value.successMessage).isEqualTo("Added to favorites")
    }

    @Test
    fun `delete contact sets is deleted`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.DeleteContact)
        advanceUntilIdle()
        coVerify(exactly = 1) { deleteContactUseCase(42L) }
        assertThat(vm.uiState.value.isDeleted).isTrue()
    }

    @Test
    fun `toggle block blocks contact`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.ToggleBlock)
        advanceUntilIdle()
        coVerify(exactly = 1) { blockNumberUseCase("+966501234567", any()) }
        assertThat(vm.uiState.value.contact?.isBlocked).isTrue()
        assertThat(vm.uiState.value.successMessage).isEqualTo("Contact blocked")
    }

    @Test
    fun `toggle block on blocked contact unblocks`() = runTest {
        stubContact(contact(id = 42L, isBlocked = true))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.ToggleBlock)
        advanceUntilIdle()
        coVerify(exactly = 1) { unblockNumberUseCase("+966501234567") }
        assertThat(vm.uiState.value.contact?.isBlocked).isFalse()
        assertThat(vm.uiState.value.successMessage).isEqualTo("Contact unblocked")
    }

    @Test
    fun `delete note calls dao`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.DeleteNote(7L))
        advanceUntilIdle()
        coVerify(exactly = 1) { contactNoteDao.deleteNote(7L) }
        assertThat(vm.uiState.value.successMessage).isEqualTo("Note deleted")
    }

    @Test
    fun `dismiss error clears error`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.DismissError)
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `toggle show all calls flips flag`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.showAllCalls).isFalse()
        vm.onEvent(ContactDetailUiEvent.ToggleShowAllCalls)
        assertThat(vm.uiState.value.showAllCalls).isTrue()
        vm.onEvent(ContactDetailUiEvent.ToggleShowAllCalls)
        assertThat(vm.uiState.value.showAllCalls).isFalse()
    }

    @Test
    fun `prepare call with direct route sets make call action`() = runTest {
        stubContact(contact(id = 42L))
        coEvery { simCallRouter.resolveRoute() } returns
            SimCallRouter.CallRoute.Direct(2)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.PrepareCall("+966501234567"))
        advanceUntilIdle()
        assertThat(vm.uiState.value.simCallAction)
            .isEqualTo(SimCallAction.MakeCall("+966501234567", 2))
    }

    @Test
    fun `prepare call with ask route shows picker`() = runTest {
        stubContact(contact(id = 42L))
        val sims = listOf(SimInfo(0, 1, "SIM 1", "carrier", "num"))
        coEvery { simCallRouter.resolveRoute() } returns
            SimCallRouter.CallRoute.AskSim(sims)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.PrepareCall("+966501234567"))
        advanceUntilIdle()
        assertThat(vm.uiState.value.simCallAction)
            .isEqualTo(SimCallAction.ShowSimPicker("+966501234567", sims))
    }

    @Test
    fun `clear sim call action resets it`() = runTest {
        stubContact(contact(id = 42L))
        coEvery { simCallRouter.resolveRoute() } returns
            SimCallRouter.CallRoute.Direct(1)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.PrepareCall("+966501234567"))
        advanceUntilIdle()
        assertThat(vm.uiState.value.simCallAction).isNotNull()
        vm.onEvent(ContactDetailUiEvent.ClearSimCallAction)
        assertThat(vm.uiState.value.simCallAction).isNull()
    }

    @Test
    fun `refresh reloads contact`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.RefreshContact)
        advanceUntilIdle()
        coVerify(exactly = 2) { contactRepository.getContactById(42L) }
    }

    @Test
    fun `loads notes for contact`() = runTest {
        stubContact(contact(id = 42L))
        val entity = mockk<ContactNoteEntity>(relaxed = true)
        every { entity.note } returns "hello"
        every { entity.phoneNumber } returns "0501234567"
        every { entity.id } returns 99L
        every { contactNoteDao.getNotesByPhoneNumbers(any()) } returns flowOf(listOf(entity))

        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.notes).hasSize(1)
        assertThat(vm.uiState.value.notes[0].note).isEqualTo("hello")
    }

    @Test
    fun `save contact updates repository`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ContactDetailUiEvent.SaveContact(
            firstName = "Alice",
            lastName = "Smith",
            phoneNumber = "+966501234567",
            email = "a@b.com",
            company = "ACME"
        ))
        advanceUntilIdle()
        coVerify(exactly = 1) { contactRepository.updateContact(any()) }
    }

    @Test
    fun `refresh with new id reloads new contact`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { contactRepository.getContactById(99L) } returns contact(id = 99L)
        vm.refreshWithId(99L)
        advanceUntilIdle()
        assertThat(vm.uiState.value.contact?.id).isEqualTo(99L)
    }

    @Test
    fun `refresh with same id is ignored`() = runTest {
        stubContact(contact(id = 42L))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.refreshWithId(42L)
        advanceUntilIdle()
        coVerify(exactly = 1) { contactRepository.getContactById(42L) }
    }
}
