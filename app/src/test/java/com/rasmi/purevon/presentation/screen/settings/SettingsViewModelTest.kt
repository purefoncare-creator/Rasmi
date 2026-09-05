package com.rasmi.purevon.presentation.screen.settings

import android.content.Context
import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.model.WhitelistNumber
import com.rasmi.purevon.domain.repository.CallLogRepository
import com.rasmi.purevon.domain.usecase.block.BlockNumberUseCase
import com.rasmi.purevon.domain.usecase.block.GetBlockedNumbersUseCase
import com.rasmi.purevon.domain.usecase.block.UnblockNumberUseCase
import com.rasmi.purevon.domain.usecase.call.ClearAllCallLogsUseCase
import com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
import com.rasmi.purevon.domain.usecase.whitelist.AddToWhitelistUseCase
import com.rasmi.purevon.domain.usecase.whitelist.GetWhitelistNumbersUseCase
import com.rasmi.purevon.domain.usecase.whitelist.RemoveFromWhitelistUseCase
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
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var simManager: SimManager
    private lateinit var getBlockedNumbersUseCase: GetBlockedNumbersUseCase
    private lateinit var blockNumberUseCase: BlockNumberUseCase
    private lateinit var unblockNumberUseCase: UnblockNumberUseCase
    private lateinit var getWhitelistNumbersUseCase: GetWhitelistNumbersUseCase
    private lateinit var addToWhitelistUseCase: AddToWhitelistUseCase
    private lateinit var removeFromWhitelistUseCase: RemoveFromWhitelistUseCase
    private lateinit var clearAllCallLogsUseCase: ClearAllCallLogsUseCase
    private lateinit var getAllContactsUseCase: GetAllContactsUseCase
    private lateinit var callLogRepository: CallLogRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        settingsDataStore = mockk(relaxed = true)
        simManager = mockk(relaxed = true)
        getBlockedNumbersUseCase = mockk(relaxed = true)
        blockNumberUseCase = mockk(relaxed = true)
        unblockNumberUseCase = mockk(relaxed = true)
        getWhitelistNumbersUseCase = mockk(relaxed = true)
        addToWhitelistUseCase = mockk(relaxed = true)
        removeFromWhitelistUseCase = mockk(relaxed = true)
        clearAllCallLogsUseCase = mockk(relaxed = true)
        getAllContactsUseCase = mockk(relaxed = true)
        callLogRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(): SettingsViewModel {
        every { settingsDataStore.defaultSimSlot } returns flowOf(0)
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(-1)
        every { settingsDataStore.defaultSmsSimSubscriptionId } returns flowOf(-1)
        every { settingsDataStore.isRtlEnabled } returns flowOf(false)
        every { settingsDataStore.appLanguage } returns flowOf("system")
        every { settingsDataStore.hideSensitiveNotifications } returns flowOf(false)
        every { settingsDataStore.callBlockingEnabled } returns flowOf(false)
        every { settingsDataStore.callBlockingSimSubscriptionId } returns flowOf(-1)
        every { settingsDataStore.blockUnknownNumbers } returns flowOf(false)
        every { settingsDataStore.whitelistOnlyMode } returns flowOf(false)
        every { settingsDataStore.otpAutoCopyEnabled } returns flowOf(true)
        every { settingsDataStore.myCardFirstName } returns flowOf("")
        every { settingsDataStore.myCardLastName } returns flowOf("")
        every { settingsDataStore.myCardPhone } returns flowOf("")
        every { settingsDataStore.myCardEmail } returns flowOf("")
        every { settingsDataStore.myCardCompany } returns flowOf("")
        every { simManager.getAvailableSims() } returns emptyList()
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        coEvery { callLogRepository.getRecentUniqueSystemCalls(any()) } returns emptyList()
        return SettingsViewModel(
            context,
            settingsDataStore,
            simManager,
            getBlockedNumbersUseCase,
            blockNumberUseCase,
            unblockNumberUseCase,
            getWhitelistNumbersUseCase,
            addToWhitelistUseCase,
            removeFromWhitelistUseCase,
            clearAllCallLogsUseCase,
            getAllContactsUseCase,
            callLogRepository
        )
    }

    @Test
    fun `initial state has default settings`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isRtlEnabled).isFalse()
        assertThat(vm.uiState.value.appLanguage).isEqualTo("system")
        assertThat(vm.uiState.value.otpEnabled).isTrue()
        assertThat(vm.uiState.value.blockedNumbers).isEmpty()
        assertThat(vm.uiState.value.callBlockingSimSubscriptionId).isEqualTo(-1)
    }

    @Test
    fun `rtl toggled persists to data store`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.RtlToggled(true))
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setRtlEnabled(true) }
    }

    @Test
    fun `default sim changed persists subscription id`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.DefaultSimChanged(5))
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setDefaultSimSubscriptionId(5) }
        assertThat(vm.uiState.value.showSimSelectorDialog).isFalse()
    }

    @Test
    fun `show and hide sim selector toggles dialog`() = runTest {
        every { getBlockedNumbersUseCase() } returns flowOf(emptyList())
        every { getWhitelistNumbersUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.ShowSimSelector)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showSimSelectorDialog).isTrue()
        vm.onEvent(SettingsUiEvent.HideSimSelector)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showSimSelectorDialog).isFalse()
    }

    @Test
    fun `default sms sim changed persists and closes dialog`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.DefaultSmsSimChanged(7))
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setDefaultSmsSimSubscriptionId(7) }
        assertThat(vm.uiState.value.showSmsSimSelectorDialog).isFalse()
    }

    @Test
    fun `hide sensitive notifications toggled persists`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.HideSensitiveNotificationsToggled(true))
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setHideSensitiveNotifications(true) }
    }

    @Test
    fun `call blocking toggled persists`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.CallBlockingToggled(true))
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setCallBlockingEnabled(true) }
    }

    @Test
    fun `save call blocking settings persists all values`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.SaveCallBlockingSettings(true, true, true, 3))
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setCallBlockingEnabled(true) }
        coVerify(exactly = 1) { settingsDataStore.setBlockUnknownNumbers(true) }
        coVerify(exactly = 1) { settingsDataStore.setWhitelistOnlyMode(true) }
        coVerify(exactly = 1) { settingsDataStore.setCallBlockingSimSubscriptionId(3) }
        assertThat(vm.uiState.value.showCallBlockingSettingsDialog).isFalse()
    }

    @Test
    fun `otp toggled persists`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.OtpToggled(false))
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setOtpAutoCopyEnabled(false) }
    }

    @Test
    fun `save my card persists card fields`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.SaveMyCard("John", "Doe", "+9665", "j@d.com", "ACME"))
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.saveMyCard("John", "Doe", "+9665", "j@d.com", "ACME") }
        assertThat(vm.uiState.value.successMessage).isNotNull()
    }

    @Test
    fun `show blocked list opens dialog`() = runTest {
        every { getBlockedNumbersUseCase() } returns flowOf(emptyList())
        every { getWhitelistNumbersUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.ShowBlockedList)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showBlockedListDialog).isTrue()
        vm.onEvent(SettingsUiEvent.HideBlockedList)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showBlockedListDialog).isFalse()
    }

    @Test
    fun `show whitelist opens dialog`() = runTest {
        every { getBlockedNumbersUseCase() } returns flowOf(emptyList())
        every { getWhitelistNumbersUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.ShowWhitelist)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showWhitelistDialog).isTrue()
        vm.onEvent(SettingsUiEvent.HideWhitelist)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showWhitelistDialog).isFalse()
    }

    @Test
    fun `add to blocked list calls use case`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.AddToBlockedList("+966501234567", "Alice"))
        advanceUntilIdle()
        coVerify(exactly = 1) { blockNumberUseCase("+966501234567", "Alice") }
    }

    @Test
    fun `remove from blocked list calls use case`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.RemoveFromBlockedList("+966501234567"))
        advanceUntilIdle()
        coVerify(exactly = 1) { unblockNumberUseCase("+966501234567") }
    }

    @Test
    fun `add to whitelist calls use case`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.AddToWhitelist("+966501234567", "Alice"))
        advanceUntilIdle()
        coVerify(exactly = 1) { addToWhitelistUseCase("+966501234567", "Alice") }
    }

    @Test
    fun `remove from whitelist calls use case`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.RemoveFromWhitelist("+966501234567"))
        advanceUntilIdle()
        coVerify(exactly = 1) { removeFromWhitelistUseCase("+966501234567") }
    }

    @Test
    fun `observes blocked numbers`() = runTest {
        every { getBlockedNumbersUseCase() } returns flowOf(listOf("+966501234567"))
        every { getWhitelistNumbersUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.blockedNumbers).containsExactly("+966501234567")
    }

    @Test
    fun `observes whitelist numbers`() = runTest {
        every { getBlockedNumbersUseCase() } returns flowOf(emptyList())
        every { getWhitelistNumbersUseCase() } returns flowOf(
            listOf(WhitelistNumber(id = 1L, phoneNumber = "+966501234567", contactName = "Alice"))
        )
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.whitelistNumbers).hasSize(1)
        assertThat(vm.uiState.value.whitelistNumbers[0].phoneNumber)
            .isEqualTo("+966501234567")
    }

    @Test
    fun `picker search query updates state`() = runTest {
        every { getBlockedNumbersUseCase() } returns flowOf(emptyList())
        every { getWhitelistNumbersUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.PickerSearchChanged("ali"))
        advanceUntilIdle()
        assertThat(vm.uiState.value.pickerSearchQuery).isEqualTo("ali")
    }

    @Test
    fun `show fake calls opens dialog`() = runTest {
        every { getBlockedNumbersUseCase() } returns flowOf(emptyList())
        every { getWhitelistNumbersUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.ShowFakeCalls)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showFakeCallsDialog).isTrue()
        vm.onEvent(SettingsUiEvent.HideFakeCalls)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showFakeCallsDialog).isFalse()
    }

    @Test
    fun `show callback reminders opens dialog`() = runTest {
        every { getBlockedNumbersUseCase() } returns flowOf(emptyList())
        every { getWhitelistNumbersUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.ShowCallbackReminders)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showCallbackRemindersDialog).isTrue()
        vm.onEvent(SettingsUiEvent.HideCallbackReminders)
        advanceUntilIdle()
        assertThat(vm.uiState.value.showCallbackRemindersDialog).isFalse()
    }

    @Test
    fun `clear message clears success message`() = runTest {
        every { getBlockedNumbersUseCase() } returns flowOf(emptyList())
        every { getWhitelistNumbersUseCase() } returns flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(SettingsUiEvent.SaveMyCard("A", "B", "C", "D", "E"))
        advanceUntilIdle()
        assertThat(vm.uiState.value.successMessage).isNotNull()
        vm.onEvent(SettingsUiEvent.ClearMessage)
        advanceUntilIdle()
        assertThat(vm.uiState.value.successMessage).isNull()
    }
}
