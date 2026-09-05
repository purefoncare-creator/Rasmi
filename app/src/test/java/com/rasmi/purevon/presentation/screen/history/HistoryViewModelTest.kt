package com.rasmi.purevon.presentation.screen.history

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.model.ContactNote
import com.rasmi.purevon.domain.repository.ContactNoteRepository
import com.rasmi.purevon.domain.usecase.block.BlockNumberUseCase
import com.rasmi.purevon.domain.usecase.call.DeleteCallLogsByNumberUseCase
import com.rasmi.purevon.domain.usecase.call.DeleteCallLogsByNumbersUseCase
import com.rasmi.purevon.domain.usecase.call.GetAllCallLogsUseCase
import com.rasmi.purevon.domain.usecase.call.SyncCallLogsUseCase
import com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
import com.rasmi.purevon.util.sim.SimCallAction
import com.rasmi.purevon.util.sim.SimCallRouter
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
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var getAllCallLogsUseCase: GetAllCallLogsUseCase
    private lateinit var deleteCallLogsByNumberUseCase: DeleteCallLogsByNumberUseCase
    private lateinit var deleteCallLogsByNumbersUseCase: DeleteCallLogsByNumbersUseCase
    private lateinit var syncCallLogsUseCase: SyncCallLogsUseCase
    private lateinit var blockNumberUseCase: BlockNumberUseCase
    private lateinit var getAllContactsUseCase: GetAllContactsUseCase
    private lateinit var contactNoteRepository: ContactNoteRepository
    private lateinit var simCallRouter: SimCallRouter

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        getAllCallLogsUseCase = mockk(relaxed = true)
        deleteCallLogsByNumberUseCase = mockk(relaxed = true)
        deleteCallLogsByNumbersUseCase = mockk(relaxed = true)
        syncCallLogsUseCase = mockk(relaxed = true)
        blockNumberUseCase = mockk(relaxed = true)
        getAllContactsUseCase = mockk(relaxed = true)
        contactNoteRepository = mockk(relaxed = true)
        simCallRouter = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HistoryViewModel {
        return HistoryViewModel(
            getAllCallLogsUseCase,
            deleteCallLogsByNumberUseCase,
            deleteCallLogsByNumbersUseCase,
            syncCallLogsUseCase,
            blockNumberUseCase,
            getAllContactsUseCase,
            contactNoteRepository,
            simCallRouter
        )
    }

    private fun callLog(
        id: Long = 1L,
        number: String = "+966501234567",
        type: CallType = CallType.INCOMING,
        timestamp: Long = 1000L,
        duration: Long = 60,
        name: String? = "Alice",
        isBlocked: Boolean = false
    ) = CallLog(
        id = id,
        phoneNumber = number,
        contactName = name,
        contactPhotoUri = null,
        callType = type,
        timestamp = timestamp,
        duration = duration,
        simSlot = null,
        isSpam = false,
        spamScore = 0f,
        notes = null,
        isBlocked = isBlocked
    )

    private fun contact(
        id: Long = 1L,
        number: String = "+966501234567",
        name: String = "Alice",
        isFavorite: Boolean = false
    ) = Contact(
        id = id,
        displayName = name,
        phoneNumber = number,
        phoneType = null,
        photoUri = null,
        email = null,
        company = null,
        isFavorite = isFavorite,
        isBlocked = false,
        lastContactedTime = null,
        timesContacted = 0,
        preferredSimSlot = null
    )

    @Test
    fun `empty state has defaults`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.callLogs).isEmpty()
        assertThat(vm.uiState.value.contacts).isEmpty()
        assertThat(vm.uiState.value.statistics?.totalCalls).isEqualTo(0)
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `loads call logs and computes statistics`() = runTest {
        val logs = listOf(
            callLog(id = 1L, number = "+966500000001", type = CallType.INCOMING, timestamp = 100L, duration = 60, name = "A"),
            callLog(id = 2L, number = "+966500000001", type = CallType.INCOMING, timestamp = 200L, duration = 30, name = "A"),
            callLog(id = 3L, number = "+966500000002", type = CallType.MISSED, timestamp = 300L, duration = 0, name = "B")
        )
        every { getAllCallLogsUseCase() } returns flowOf(logs)
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.callLogs).hasSize(3)
        assertThat(state.statistics?.totalCalls).isEqualTo(3)
        assertThat(state.statistics?.incomingCalls).isEqualTo(2)
        assertThat(state.statistics?.missedCalls).isEqualTo(1)
        assertThat(state.statistics?.totalDuration).isEqualTo(90L)
        assertThat(state.statistics?.mostCalledNumber).isEqualTo("+966500000001")
    }

    @Test
    fun `filter selects missed calls only`() = runTest {
        val logs = listOf(
            callLog(id = 1L, number = "+966500000001", type = CallType.MISSED, timestamp = 100L, name = "A"),
            callLog(id = 2L, number = "+966500000002", type = CallType.INCOMING, timestamp = 200L, name = "B")
        )
        every { getAllCallLogsUseCase() } returns flowOf(logs)
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(HistoryUiEvent.FilterSelected(CallFilter.MISSED))
        advanceUntilIdle()

        assertThat(vm.uiState.value.selectedFilter).isEqualTo(CallFilter.MISSED)
        assertThat(vm.uiState.value.callLogs).hasSize(1)
        assertThat(vm.uiState.value.callLogs[0].callType).isEqualTo(CallType.MISSED)
    }

    @Test
    fun `search filters call logs by name`() = runTest {
        val logs = listOf(
            callLog(id = 1L, number = "+966501234567", type = CallType.INCOMING, timestamp = 100L, name = "Alice"),
            callLog(id = 2L, number = "+966509999999", type = CallType.INCOMING, timestamp = 200L, name = "Bob")
        )
        every { getAllCallLogsUseCase() } returns flowOf(logs)
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(HistoryUiEvent.SearchQueryChanged("ali"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.callLogs).hasSize(1)
        assertThat(vm.uiState.value.callLogs[0].contactName).isEqualTo("Alice")
    }

    @Test
    fun `block number sets success message`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(HistoryUiEvent.BlockNumber("+966501234567"))
        advanceUntilIdle()

        coVerify(exactly = 1) { blockNumberUseCase("+966501234567", any()) }
        assertThat(vm.uiState.value.successMessage).isEqualTo("Number blocked successfully")
    }

    @Test
    fun `prepare call with direct route sets make call action`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())
        coEvery { simCallRouter.resolveRoute() } returns SimCallRouter.CallRoute.Direct(2)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(HistoryUiEvent.PrepareCall("+966501234567"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.simCallAction)
            .isEqualTo(SimCallAction.MakeCall("+966501234567", 2))
    }

    @Test
    fun `prepare call with ask sim route shows picker`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())
        coEvery { simCallRouter.resolveRoute() } returns SimCallRouter.CallRoute.AskSim(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(HistoryUiEvent.PrepareCall("+966501234567"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.simCallAction)
            .isInstanceOf(SimCallAction.ShowSimPicker::class.java)
    }

    @Test
    fun `clear sim call action resets it`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())
        coEvery { simCallRouter.resolveRoute() } returns SimCallRouter.CallRoute.Direct(null)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(HistoryUiEvent.PrepareCall("+966501234567"))
        advanceUntilIdle()
        assertThat(vm.uiState.value.simCallAction).isNotNull()

        vm.onEvent(HistoryUiEvent.ClearSimCallAction)
        assertThat(vm.uiState.value.simCallAction).isNull()
    }

    @Test
    fun `confirm delete all for number deletes and clears pending state`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(HistoryUiEvent.ShowDeleteAllForNumber("+966501234567"))
        assertThat(vm.uiState.value.pendingDeleteAllPhoneNumber).isEqualTo("+966501234567")

        vm.onEvent(HistoryUiEvent.ConfirmDeleteAllForNumber)
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteCallLogsByNumberUseCase("+966501234567") }
        assertThat(vm.uiState.value.pendingDeleteAllPhoneNumber).isNull()
        assertThat(vm.uiState.value.showDeleteAllForNumberConfirmation).isFalse()
    }

    @Test
    fun `dismiss delete all for number resets pending`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(HistoryUiEvent.ShowDeleteAllForNumber("+966501234567"))
        vm.onEvent(HistoryUiEvent.DismissDeleteAllForNumber)

        assertThat(vm.uiState.value.pendingDeleteAllPhoneNumber).isNull()
    }

    @Test
    fun `undo block cancels pending block`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        val log = callLog()
        vm.onEvent(HistoryUiEvent.ShowBlockConfirmation(log))
        vm.onEvent(HistoryUiEvent.ConfirmBlock)
        assertThat(vm.uiState.value.showUndoSnackbar).isTrue()
        assertThat(vm.uiState.value.blockedNumber).isEqualTo("+966501234567")

        vm.onEvent(HistoryUiEvent.UndoBlock)
        advanceUntilIdle()

        assertThat(vm.uiState.value.showUndoSnackbar).isFalse()
        assertThat(vm.uiState.value.blockedNumber).isNull()
        coVerify(exactly = 0) { blockNumberUseCase(any(), any()) }
    }

    @Test
    fun `selection toggling updates selected group keys`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(listOf(
            callLog(id = 1L, number = "+966501234567", type = CallType.INCOMING, timestamp = 100L, name = "A")
        ))
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        val groupKey = vm.uiState.value.groupedContactCalls.first().groupKey

        vm.onEvent(HistoryUiEvent.GroupedRowLongPressed(groupKey))
        assertThat(vm.uiState.value.isSelectionMode).isTrue()
        assertThat(vm.uiState.value.selectedGroupKeys).containsExactly(groupKey)

        vm.onEvent(HistoryUiEvent.ToggleGroupedSelection(groupKey))
        assertThat(vm.uiState.value.isSelectionMode).isFalse()
        assertThat(vm.uiState.value.selectedGroupKeys).isEmpty()
    }

    @Test
    fun `delete selected call logs calls batch use case`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(listOf(
            callLog(id = 1L, number = "+966501234567", type = CallType.INCOMING, timestamp = 100L, name = "A"),
            callLog(id = 2L, number = "+966509999999", type = CallType.INCOMING, timestamp = 200L, name = "B")
        ))
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        val keys = vm.uiState.value.groupedContactCalls.map { it.groupKey }.toSet()
        vm.onEvent(HistoryUiEvent.GroupedRowLongPressed(keys.first()))
        keys.forEach { vm.onEvent(HistoryUiEvent.ToggleGroupedSelection(it)) }
        advanceUntilIdle()

        vm.onEvent(HistoryUiEvent.DeleteSelectedCallLogs)
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteCallLogsByNumbersUseCase(any()) }
        assertThat(vm.uiState.value.isSelectionMode).isFalse()
    }

    @Test
    fun `load notes for number populates notes`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())
        every { contactNoteRepository.getAllNotes() } returns flowOf(listOf(
            ContactNote(id = 1L, phoneNumber = "+966501234567", note = "hello")
        ))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.loadNotesForNumber("+966501234567")
        advanceUntilIdle()

        assertThat(vm.uiState.value.notesForSelectedNumber).hasSize(1)
        assertThat(vm.uiState.value.notesForSelectedNumber[0].note).isEqualTo("hello")
    }

    @Test
    fun `clear selected notes empties notes list`() = runTest {
        every { getAllCallLogsUseCase() } returns flowOf(emptyList())
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        every { contactNoteRepository.getPhoneNumbersWithNotes() } returns flowOf(emptyList())
        every { contactNoteRepository.getAllNotes() } returns flowOf(listOf(
            ContactNote(id = 1L, phoneNumber = "+966501234567", note = "hello")
        ))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.loadNotesForNumber("+966501234567")
        advanceUntilIdle()
        assertThat(vm.uiState.value.notesForSelectedNumber).isNotEmpty()

        vm.clearSelectedNotes()
        assertThat(vm.uiState.value.notesForSelectedNumber).isEmpty()
    }
}
