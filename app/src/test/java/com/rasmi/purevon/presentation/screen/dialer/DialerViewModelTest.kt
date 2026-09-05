package com.rasmi.purevon.presentation.screen.dialer

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.call.InCallServiceBridge
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.model.CallType
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.repository.BlockRepository
import com.rasmi.purevon.domain.repository.CallLogRepository
import com.rasmi.purevon.domain.repository.RecentSystemCall
import com.rasmi.purevon.domain.usecase.contact.SearchContactsUseCase
import com.rasmi.purevon.util.sim.SimInfo
import com.rasmi.purevon.util.sim.SimManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class DialerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var searchContactsUseCase: SearchContactsUseCase
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var simManager: SimManager
    private lateinit var callLogRepository: CallLogRepository
    private lateinit var blockRepository: BlockRepository
    private lateinit var inCallBridge: InCallServiceBridge

    private val askModeFlow = MutableStateFlow(false)
    private val simIdFlow = MutableStateFlow(-1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        searchContactsUseCase = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        simManager = mockk(relaxed = true)
        callLogRepository = mockk(relaxed = true)
        blockRepository = mockk(relaxed = true)
        inCallBridge = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sim(subscriptionId: Int, slot: Int, name: String) =
        SimInfo(slotIndex = slot, subscriptionId = subscriptionId, displayName = name,
            carrierName = "carrier", phoneNumber = "050000000$subscriptionId")

    private fun createViewModel(): DialerViewModel {
        every { settingsDataStore.isSimAskMode } answers { askModeFlow }
        every { settingsDataStore.defaultSimSubscriptionId } answers { simIdFlow }
        every { simManager.getAvailableSims() } returns listOf(sim(1, 0, "SIM 1"), sim(2, 1, "SIM 2"))
        every { inCallBridge.muteState } returns MutableStateFlow(false)
        every { inCallBridge.speakerState } returns MutableStateFlow(false)
        every { inCallBridge.callState } returns MutableStateFlow(null)
        every { inCallBridge.currentPhoneNumber } returns null
        every { inCallBridge.currentContactName } returns null
        every { inCallBridge.getCurrentCall() } returns null
        return DialerViewModel(
            searchContactsUseCase,
            settingsDataStore,
            simManager,
            callLogRepository,
            blockRepository,
            inCallBridge
        )
    }

    private fun contactOf(name: String, number: String) = Contact(
        id = 1L, displayName = name, phoneNumber = number,
        phoneType = null, photoUri = null, email = null, company = null,
        isFavorite = false, isBlocked = false, lastContactedTime = null,
        timesContacted = 0, preferredSimSlot = null
    )

    @Test
    fun `initial state has default values`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.dialedNumber).isEmpty()
        assertThat(vm.uiState.value.showCallButton).isFalse()
        assertThat(vm.uiState.value.availableSims).hasSize(2)
    }

    @Test
    fun `default sim label is SIM1`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.currentSimLabel).isEqualTo("SIM1")
    }

    @Test
    fun `number changed updates dialed number and shows call button`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.NumberChanged("501234567"))
        assertThat(vm.uiState.value.dialedNumber).isEqualTo("501234567")
        assertThat(vm.uiState.value.showCallButton).isTrue()
        vm.onEvent(DialerUiEvent.NumberChanged(""))
        assertThat(vm.uiState.value.showCallButton).isFalse()
    }

    @Test
    fun `digit pressed appends to dialed number`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.DigitPressed("5"))
        vm.onEvent(DialerUiEvent.DigitPressed("0"))
        assertThat(vm.uiState.value.dialedNumber).isEqualTo("50")
        assertThat(vm.uiState.value.showCallButton).isTrue()
    }

    @Test
    fun `backspace removes last digit`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.NumberChanged("5012"))
        vm.onEvent(DialerUiEvent.BackspacePressed)
        assertThat(vm.uiState.value.dialedNumber).isEqualTo("501")
    }

    @Test
    fun `backspace long pressed clears input`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.DigitPressed("9"))
        vm.onEvent(DialerUiEvent.BackspaceLongPressed)
        assertThat(vm.uiState.value.dialedNumber).isEmpty()
        assertThat(vm.uiState.value.showCallButton).isFalse()
    }

    @Test
    fun `clear input resets state`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.DigitPressed("5"))
        vm.onEvent(DialerUiEvent.ClearInput)
        assertThat(vm.uiState.value.dialedNumber).isEmpty()
        assertThat(vm.uiState.value.searchResults).isEmpty()
    }

    @Test
    fun `contact selected fills number and marks selected`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.ContactSelected(contactOf("Alice", "+966501234567")))
        assertThat(vm.uiState.value.dialedNumber).isEqualTo("+966501234567")
        assertThat(vm.uiState.value.isContactSelected).isTrue()
        assertThat(vm.uiState.value.selectedContactName).isEqualTo("Alice")
    }

    @Test
    fun `contact selected without real name is not marked selected`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.ContactSelected(contactOf("-", "+966501234567")))
        assertThat(vm.uiState.value.isContactSelected).isFalse()
    }

    @Test
    fun `initiate call with permission emits make phone call`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.NumberChanged("501234567"))
        vm.onEvent(DialerUiEvent.InitiateCall(true))
        val action = vm.uiAction.tryReceive().getOrNull()
        assertThat(action).isEqualTo(DialerUiAction.MakePhoneCall("501234567", 1))
    }

    @Test
    fun `initiate call without permission emits request permission`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.NumberChanged("501234567"))
        vm.onEvent(DialerUiEvent.InitiateCall(false))
        val action = vm.uiAction.tryReceive().getOrNull()
        assertThat(action).isEqualTo(DialerUiAction.RequestPermission)
    }

    @Test
    fun `initiate call in ask mode shows sim picker`() = runTest {
        askModeFlow.value = true
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.NumberChanged("501234567"))
        vm.onEvent(DialerUiEvent.InitiateCall(true))
        val action = vm.uiAction.tryReceive().getOrNull()
        assertThat(action).isEqualTo(DialerUiAction.ShowSimPickerForCall("501234567"))
    }

    @Test
    fun `initiate call with empty number fetches last outgoing call`() = runTest {
        coEvery { callLogRepository.getLastOutgoingCall() } returns
            CallLog(
                id = 1L, phoneNumber = "+966501234567", contactName = "Alice",
                contactPhotoUri = null, callType = CallType.OUTGOING,
                timestamp = 1000L, duration = 60, simSlot = null, isSpam = false,
                spamScore = 0f, notes = null, isBlocked = false
            )
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.InitiateCall(true))
        advanceUntilIdle()
        assertThat(vm.uiState.value.dialedNumber).isEqualTo("+966501234567")
        assertThat(vm.uiState.value.isContactSelected).isTrue()
    }

    @Test
    fun `request call permission emits request permission action`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.RequestCallPermission)
        val action = vm.uiAction.tryReceive().getOrNull()
        assertThat(action).isEqualTo(DialerUiAction.RequestPermission)
    }

    @Test
    fun `sim selected for call emits make phone call`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.SimSelectedForCall(2, "+966501234567"))
        val action = vm.uiAction.tryReceive().getOrNull()
        assertThat(action).isEqualTo(DialerUiAction.MakePhoneCall("+966501234567", 2))
    }

    @Test
    fun `sim switch cycles to sim2`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.SimSwitchPressed)
        advanceUntilIdle()
        assertThat(vm.uiState.value.currentSimLabel).isEqualTo("SIM2")
    }

    @Test
    fun `sim switch cycles sim2 to ask mode`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.SimSwitchPressed)
        vm.onEvent(DialerUiEvent.SimSwitchPressed)
        advanceUntilIdle()
        assertThat(vm.uiState.value.currentSimLabel).isEqualTo("ASK")
    }

    @Test
    fun `toggle mute calls bridge`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.ToggleMuteCall)
        verify(exactly = 1) { inCallBridge.toggleMute() }
    }

    @Test
    fun `toggle speaker calls bridge`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(DialerUiEvent.ToggleSpeakerCall)
        verify(exactly = 1) { inCallBridge.toggleSpeaker() }
    }

    @Test
    fun `recent contacts loaded from call log`() = runTest {
        coEvery { callLogRepository.getRecentUniqueSystemCalls(any()) } returns listOf(
            RecentSystemCall("+966501234567", "Alice", 2, 1000L, null),
            RecentSystemCall("+966500000002", "Bob", 2, 2000L, null)
        )
        coEvery { blockRepository.shouldBlockCall(any()) } returns false
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.recentContacts).hasSize(2)
        assertThat(vm.uiState.value.recentContacts[0].displayName).isEqualTo("Alice")
    }

    @Test
    fun `recent contacts reloaded on refresh suggestions`() = runTest {
        coEvery { callLogRepository.getRecentUniqueSystemCalls(any()) } returns
            listOf(RecentSystemCall("+966501234567", "Alice", 2, 1000L, null))
        coEvery { blockRepository.shouldBlockCall(any()) } returns false
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.recentContacts).hasSize(1)

        coEvery { callLogRepository.getRecentUniqueSystemCalls(any()) } returns emptyList()
        vm.refreshSuggestions()
        advanceUntilIdle()
        assertThat(vm.uiState.value.recentContacts).isEmpty()
    }
}
