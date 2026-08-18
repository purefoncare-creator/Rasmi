package com.rasmi.purevon.worker

import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.domain.model.MessageResult
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
class DelayedMessageWorkerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var messageRepository: MessageRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)

        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { context.getString(any()) } returns "Test"
        every { context.applicationContext } returns context
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createWorker(
        inputData: Data = Data.EMPTY,
        runAttemptCount: Int = 0
    ): DelayedMessageWorker {
        every { workerParams.inputData } returns inputData
        every { workerParams.runAttemptCount } returns runAttemptCount
        return DelayedMessageWorker(context, workerParams, messageRepository, settingsDataStore)
    }

    @Test
    fun `success returns Result success`() = runTest {
        val inputData = Data.Builder()
            .putString(DelayedMessageWorker.KEY_PHONE_NUMBER, "+1234567890")
            .putString(DelayedMessageWorker.KEY_MESSAGE_TEXT, "Hello")
            .putInt(DelayedMessageWorker.KEY_SIM_SLOT, -1)
            .build()

        coEvery { settingsDataStore.defaultSmsSimSubscriptionId } returns flowOf(1)
        coEvery { messageRepository.sendMessage(any(), any(), any()) } returns MessageResult.Success(1L)

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `failure returns Result retry`() = runTest {
        val inputData = Data.Builder()
            .putString(DelayedMessageWorker.KEY_PHONE_NUMBER, "+1234567890")
            .putString(DelayedMessageWorker.KEY_MESSAGE_TEXT, "Hello")
            .putInt(DelayedMessageWorker.KEY_SIM_SLOT, -1)
            .build()

        coEvery { settingsDataStore.defaultSmsSimSubscriptionId } returns flowOf(1)
        coEvery { messageRepository.sendMessage(any(), any(), any()) } returns
            MessageResult.Failure(com.rasmi.purevon.domain.model.MessageError.NetworkError())

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test
    fun `missing phone number returns failure`() = runTest {
        val inputData = Data.Builder()
            .putString(DelayedMessageWorker.KEY_MESSAGE_TEXT, "Hello")
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test
    fun `missing message text returns failure`() = runTest {
        val inputData = Data.Builder()
            .putString(DelayedMessageWorker.KEY_PHONE_NUMBER, "+1234567890")
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test
    fun `max retries returns failure`() = runTest {
        val inputData = Data.Builder()
            .putString(DelayedMessageWorker.KEY_PHONE_NUMBER, "+1234567890")
            .putString(DelayedMessageWorker.KEY_MESSAGE_TEXT, "Hello")
            .putInt(DelayedMessageWorker.KEY_SIM_SLOT, -1)
            .build()

        val worker = createWorker(inputData, runAttemptCount = 3)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test
    fun `sends MMS when attachments present`() = runTest {
        val inputData = Data.Builder()
            .putString(DelayedMessageWorker.KEY_PHONE_NUMBER, "+1234567890")
            .putString(DelayedMessageWorker.KEY_MESSAGE_TEXT, "Photo")
            .putStringArray(DelayedMessageWorker.KEY_ATTACHMENT_URIS, arrayOf("content://file1"))
            .putInt(DelayedMessageWorker.KEY_SIM_SLOT, -1)
            .build()

        coEvery { settingsDataStore.defaultSmsSimSubscriptionId } returns flowOf(1)
        coEvery { messageRepository.sendMmsMessage(any(), any(), any(), any()) } returns MessageResult.Success(1L)

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify { messageRepository.sendMmsMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `uses saved SIM slot from input data`() = runTest {
        val inputData = Data.Builder()
            .putString(DelayedMessageWorker.KEY_PHONE_NUMBER, "+1234567890")
            .putString(DelayedMessageWorker.KEY_MESSAGE_TEXT, "Hi")
            .putInt(DelayedMessageWorker.KEY_SIM_SLOT, 2)
            .build()

        coEvery { messageRepository.sendMessage("+1234567890", "Hi", 2) } returns MessageResult.Success(1L)

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify { messageRepository.sendMessage("+1234567890", "Hi", 2) }
    }

    @Test
    fun `exception returns retry`() = runTest {
        val inputData = Data.Builder()
            .putString(DelayedMessageWorker.KEY_PHONE_NUMBER, "+1234567890")
            .putString(DelayedMessageWorker.KEY_MESSAGE_TEXT, "Hello")
            .putInt(DelayedMessageWorker.KEY_SIM_SLOT, -1)
            .build()

        coEvery { settingsDataStore.defaultSmsSimSubscriptionId } throws RuntimeException("DB crash")

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }
}
