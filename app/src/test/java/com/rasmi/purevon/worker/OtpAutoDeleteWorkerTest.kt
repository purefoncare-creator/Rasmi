package com.rasmi.purevon.worker

import android.content.Context
import android.provider.Telephony
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.util.message.OtpManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
class OtpAutoDeleteWorkerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var otpManager: OtpManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        otpManager = mockk(relaxed = true)

        every { context.packageName } returns "com.rasmi.purevon"

        mockkStatic(Telephony.Sms::class)
        every { Telephony.Sms.getDefaultSmsPackage(any()) } returns "com.rasmi.purevon"
    }

    @After
    fun tearDown() {
        unmockkStatic(Telephony.Sms::class)
        Dispatchers.resetMain()
    }

    private fun createWorker(): OtpAutoDeleteWorker {
        return OtpAutoDeleteWorker(context, workerParams, settingsDataStore, otpManager)
    }

    @Test
    fun `returns success when not default SMS app`() = runTest {
        every { Telephony.Sms.getDefaultSmsPackage(any()) } returns "com.other.app"

        val worker = createWorker()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `returns success when auto-delete is disabled`() = runTest {
        every { settingsDataStore.autoDeleteOtp } returns flowOf(false)

        val worker = createWorker()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `returns success when auto-delete is enabled but no OTP messages found`() = runTest {
        every { settingsDataStore.autoDeleteOtp } returns flowOf(true)
        every { settingsDataStore.otpDeleteDelay } returns flowOf(5)
        every { context.contentResolver } returns mockk(relaxed = true)
        every { context.contentResolver.query(any(), any(), any(), any(), any()) } returns null

        val worker = createWorker()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `returns failure on exception`() = runTest {
        every { settingsDataStore.autoDeleteOtp } returns flow {
            throw RuntimeException("DataStore crash")
        }

        val worker = createWorker()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test
    fun `WORK_NAME constant is correct`() {
        assertThat(OtpAutoDeleteWorker.WORK_NAME).isEqualTo("otp_auto_delete_worker")
    }
}
