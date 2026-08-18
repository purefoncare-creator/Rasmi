package com.rasmi.purevon.worker

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.ScheduledMessageDao
import com.rasmi.purevon.data.local.entity.ScheduleStatus
import com.rasmi.purevon.data.local.entity.ScheduledMessageEntity
import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.MessageRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ScheduledMessageWorkerTest {

    private lateinit var context: Context
    private lateinit var params: WorkerParameters
    private lateinit var dao: ScheduledMessageDao
    private lateinit var messageRepository: MessageRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        context = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)

        params = mockk(relaxed = true)
    }

    private fun sampleMessage(
        id: Long = 1L,
        recipient: String = "+966501234567",
        body: String = "Hello!",
        status: ScheduleStatus = ScheduleStatus.PENDING,
        attachments: List<String> = emptyList()
    ) = ScheduledMessageEntity(
        id = id,
        recipient = recipient,
        messageBody = body,
        scheduledTime = System.currentTimeMillis(),
        status = status,
        attachmentUris = attachments
    )

    private fun workerWithInput(scheduleId: Long = 1L, attemptCount: Int = 0): ScheduledMessageWorker {
        val data = Data.Builder()
            .putLong(ScheduledMessageWorker.KEY_SCHEDULE_ID, scheduleId)
            .build()
        every { params.inputData } returns data
        every { params.runAttemptCount } returns attemptCount
        return ScheduledMessageWorker(context, params, dao, messageRepository)
    }

    // ════════════════════════════════════════════════════════════
    // doWork — missing / invalid input
    // ════════════════════════════════════════════════════════════

    @Test
    fun `doWork returns failure when schedule ID is -1`() = runTest {
        val data = Data.Builder().build()
        every { params.inputData } returns data
        every { params.runAttemptCount } returns 0

        val worker = ScheduledMessageWorker(context, params, dao, messageRepository)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }

    @Test
    fun `doWork returns failure when message not found in DB`() = runTest {
        coEvery { dao.getMessageById(1L) } returns null

        val worker = workerWithInput(1L)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }

    // ════════════════════════════════════════════════════════════
    // doWork — status checks
    // ════════════════════════════════════════════════════════════

    @Test
    fun `doWork returns success for already sent message`() = runTest {
        coEvery { dao.getMessageById(1L) } returns sampleMessage(status = ScheduleStatus.SENT)

        val worker = workerWithInput(1L)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify(exactly = 0) { messageRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `doWork returns success for cancelled message`() = runTest {
        coEvery { dao.getMessageById(1L) } returns sampleMessage(status = ScheduleStatus.CANCELLED)

        val worker = workerWithInput(1L)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
    }

    // ════════════════════════════════════════════════════════════
    // doWork — SMS sending
    // ════════════════════════════════════════════════════════════

    @Test
    fun `doWork sends SMS when no attachments`() = runTest {
        coEvery { dao.getMessageById(1L) } returns sampleMessage()
        coEvery { messageRepository.sendMessage(any(), any(), any()) } returns MessageResult.Success(42L)
        coEvery { dao.getPendingForRecipient(any(), any()) } returns null

        val worker = workerWithInput(1L)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify(exactly = 1) { messageRepository.sendMessage("+966501234567", "Hello!", null) }
    }

    @Test
    fun `doWork updates status to SENT on success`() = runTest {
        coEvery { dao.getMessageById(1L) } returns sampleMessage()
        coEvery { messageRepository.sendMessage(any(), any(), any()) } returns MessageResult.Success(42L)
        coEvery { dao.getPendingForRecipient(any(), any()) } returns null

        val worker = workerWithInput(1L)
        worker.doWork()

        coVerify(exactly = 1) { dao.updateStatus(1L, ScheduleStatus.SENT) }
    }

    // ════════════════════════════════════════════════════════════
    // doWork — MMS sending
    // ════════════════════════════════════════════════════════════

    @Test
    fun `doWork sends MMS when attachments exist`() = runTest {
        coEvery { dao.getMessageById(1L) } returns sampleMessage(
            attachments = listOf("content://media/1")
        )
        coEvery { messageRepository.sendMmsMessage(any(), any(), any(), any()) } returns MessageResult.Success(42L)
        coEvery { dao.getPendingForRecipient(any(), any()) } returns null

        val worker = workerWithInput(1L)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify(exactly = 1) {
            messageRepository.sendMmsMessage("+966501234567", "Hello!", listOf("content://media/1"), null)
        }
    }

    // ════════════════════════════════════════════════════════════
    // doWork — failure & retry
    // ════════════════════════════════════════════════════════════

    @Test
    fun `doWork retries when message send fails and attempt below max`() = runTest {
        coEvery { dao.getMessageById(1L) } returns sampleMessage()
        coEvery { messageRepository.sendMessage(any(), any(), any()) } returns
            MessageResult.Failure(MessageError.NetworkError())

        val worker = workerWithInput(1L, attemptCount = 1)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
    }

    @Test
    fun `doWork marks FAILED after max retries exceeded`() = runTest {
        coEvery { dao.getMessageById(1L) } returns sampleMessage()
        coEvery { messageRepository.sendMessage(any(), any(), any()) } returns
            MessageResult.Failure(MessageError.NetworkError())

        val worker = workerWithInput(1L, attemptCount = 3)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        coVerify(atLeast = 1) { dao.updateStatus(1L, ScheduleStatus.FAILED) }
    }

    // ════════════════════════════════════════════════════════════
    // doWork — exception handling
    // ════════════════════════════════════════════════════════════

    @Test
    fun `doWork retries on exception when attempt below max`() = runTest {
        coEvery { dao.getMessageById(1L) } returns sampleMessage()
        coEvery { messageRepository.sendMessage(any(), any(), any()) } throws RuntimeException("DB error")

        val worker = workerWithInput(1L, attemptCount = 2)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
    }

    @Test
    fun `doWork marks FAILED on exception after max retries`() = runTest {
        coEvery { dao.getMessageById(1L) } returns sampleMessage()
        coEvery { messageRepository.sendMessage(any(), any(), any()) } throws RuntimeException("DB error")

        val worker = workerWithInput(1L, attemptCount = 3)
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        coVerify(atLeast = 1) { dao.updateStatus(1L, ScheduleStatus.FAILED) }
    }

    // ════════════════════════════════════════════════════════════
    // Companion constants
    // ════════════════════════════════════════════════════════════

    @Test
    fun `KEY_SCHEDULE_ID constant is correct`() {
        assertThat(ScheduledMessageWorker.KEY_SCHEDULE_ID).isEqualTo("schedule_id")
    }

    @Test
    fun `WORK_NAME_PREFIX constant is correct`() {
        assertThat(ScheduledMessageWorker.WORK_NAME_PREFIX).isEqualTo("scheduled_message_")
    }
}
