package com.rasmi.purevon.presentation.screen.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.util.DefaultAppManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PermissionRequestViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var defaultAppManager: DefaultAppManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        defaultAppManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PermissionRequestViewModel {
        return PermissionRequestViewModel(context, defaultAppManager)
    }

    @Test
    fun `initial state reflects all permissions granted`() = runTest {
        every { defaultAppManager.isDefaultDialer() } returns true
        every { defaultAppManager.isDefaultSmsApp() } returns true
        every { defaultAppManager.areBothDefaultAppsSet() } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isDefaultDialer).isTrue()
        assertThat(vm.uiState.value.isDefaultSms).isTrue()
        assertThat(vm.uiState.value.isDefaultAppsSet).isTrue()
    }

    @Test
    fun `initial state reflects permissions not granted`() = runTest {
        every { defaultAppManager.isDefaultDialer() } returns false
        every { defaultAppManager.isDefaultSmsApp() } returns false
        every { defaultAppManager.areBothDefaultAppsSet() } returns false

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isDefaultDialer).isFalse()
        assertThat(vm.uiState.value.isDefaultSms).isFalse()
        assertThat(vm.uiState.value.isDefaultAppsSet).isFalse()
    }

    @Test
    fun `RefreshStatus event re-checks all permissions`() = runTest {
        every { defaultAppManager.isDefaultDialer() } returns false andThen true
        every { defaultAppManager.isDefaultSmsApp() } returns false
        every { defaultAppManager.areBothDefaultAppsSet() } returns false

        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isDefaultDialer).isFalse()

        vm.onEvent(PermissionRequestUiEvent.RefreshStatus)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isDefaultDialer).isTrue()
    }

    @Test
    fun `RequestDefaultApps event re-checks permissions`() = runTest {
        every { defaultAppManager.isDefaultDialer() } returns true
        every { defaultAppManager.isDefaultSmsApp() } returns true
        every { defaultAppManager.areBothDefaultAppsSet() } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(PermissionRequestUiEvent.RequestDefaultApps)
        advanceUntilIdle()

        verify(exactly = 2) { defaultAppManager.isDefaultDialer() }
    }

    @Test
    fun `mixed permission states are correctly reported`() = runTest {
        every { defaultAppManager.isDefaultDialer() } returns true
        every { defaultAppManager.isDefaultSmsApp() } returns false
        every { defaultAppManager.areBothDefaultAppsSet() } returns false

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isDefaultDialer).isTrue()
        assertThat(vm.uiState.value.isDefaultSms).isFalse()
        assertThat(vm.uiState.value.isDefaultAppsSet).isFalse()
    }
}
