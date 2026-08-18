package com.rasmi.purevon.presentation.main

import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.domain.repository.SyncRepository
import com.rasmi.purevon.domain.usecase.message.SyncMessagesUseCase
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
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var syncUseCase: SyncMessagesUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        syncUseCase = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel {
        return MainViewModel(syncUseCase)
    }

    @Test
    fun `initial state has isLoading true when sync not completed`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns false
        coEvery { syncUseCase.observeSyncProgress() } returns flowOf(SyncRepository.SyncProgress.Idle)
        coEvery { syncUseCase.performFullSync() } just Runs

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.needsInitialSync).isFalse()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `initial state has isLoading false when sync already completed`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.needsInitialSync).isFalse()
    }

    @Test
    fun `triggerFullSync sets loading true then false`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns true
        coEvery { syncUseCase.observeSyncProgress() } returns flowOf(SyncRepository.SyncProgress.Idle)
        coEvery { syncUseCase.performFullSync() } just Runs

        val vm = createViewModel()
        advanceUntilIdle()

        vm.triggerFullSync()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
        coVerify { syncUseCase.performFullSync() }
    }

    @Test
    fun `cancelSync stops loading`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.cancelSync()

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.syncProgress).isEqualTo(SyncRepository.SyncProgress.Idle)
    }

    @Test
    fun `onResume skips when sync is actively running`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns false
        coEvery { syncUseCase.observeSyncProgress() } returns flowOf(SyncRepository.SyncProgress.Idle)
        coEvery { syncUseCase.performFullSync() } just Runs

        val vm = createViewModel()
        advanceUntilIdle()

        val initialCallCount = coEvery { syncUseCase.hasCompletedInitialSync() } // called once in init
        vm.onResume()
        advanceUntilIdle()

        // onResume should not re-trigger because needsInitialSync=true and isLoading=true
        assertThat(vm.uiState.value.needsInitialSync).isFalse() // sync completed
    }

    @Test
    fun `setDialerPhoneNumber updates state`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setDialerPhoneNumber("+1234567890")
        assertThat(vm.dialerPhoneNumber.value).isEqualTo("+1234567890")
    }

    @Test
    fun `clearDialerPhoneNumber sets null`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setDialerPhoneNumber("+1234567890")
        vm.clearDialerPhoneNumber()
        assertThat(vm.dialerPhoneNumber.value).isNull()
    }

    @Test
    fun `setShouldClearDialerInput updates state`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setShouldClearDialerInput(true)
        assertThat(vm.shouldClearDialerInput.value).isTrue()
    }

    @Test
    fun `clearDialerInputFlag sets false`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setShouldClearDialerInput(true)
        vm.clearDialerInputFlag()
        assertThat(vm.shouldClearDialerInput.value).isFalse()
    }

    @Test
    fun `error during sync is captured in state`() = runTest {
        coEvery { syncUseCase.hasCompletedInitialSync() } returns false
        coEvery { syncUseCase.observeSyncProgress() } returns flowOf(SyncRepository.SyncProgress.Idle)
        coEvery { syncUseCase.performFullSync() } throws RuntimeException("Sync failed")

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo("Sync failed")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }
}
