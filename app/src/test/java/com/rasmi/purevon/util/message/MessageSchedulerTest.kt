package com.rasmi.purevon.util.message

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.worker.ScheduledMessageWorker
import io.mockk.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class MessageSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: MessageScheduler

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        scheduler = MessageScheduler(context, workManager)
    }

    // ════════════════════════════════════════════════════════════
    // scheduleMessage
    // ════════════════════════════════════════════════════════════

    @Test
    fun `scheduleMessage returns success for future time`() {
        val futureTime = System.currentTimeMillis() + 3_600_000L

        val result = scheduler.scheduleMessage(
            messageId = 1L,
            recipient = "+966501234567",
            messageBody = "Hello",
            conversationId = 10L,
            scheduledTime = futureTime
        )

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { workManager.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun `scheduleMessage returns failure for past time`() {
        val pastTime = System.currentTimeMillis() - 3_600_000L

        val result = scheduler.scheduleMessage(
            messageId = 1L,
            recipient = "+966501234567",
            messageBody = "Hello",
            conversationId = 10L,
            scheduledTime = pastTime
        )

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { workManager.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun `scheduleMessage catches exceptions`() {
        coEvery { workManager.enqueue(any<WorkRequest>()) } throws RuntimeException("error")

        val futureTime = System.currentTimeMillis() + 3_600_000L
        val result = scheduler.scheduleMessage(1L, "+966501234567", "Hi", 10L, futureTime)

        assertThat(result.isFailure).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // cancelScheduledMessage
    // ════════════════════════════════════════════════════════════

    @Test
    fun `cancelScheduledMessage returns success`() {
        val result = scheduler.cancelScheduledMessage(42L)

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { workManager.cancelAllWorkByTag("message_42") }
    }

    @Test
    fun `cancelScheduledMessage handles exception`() {
        coEvery { workManager.cancelAllWorkByTag(any()) } throws RuntimeException("error")

        val result = scheduler.cancelScheduledMessage(42L)

        assertThat(result.isFailure).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // cancelAllScheduledMessages
    // ════════════════════════════════════════════════════════════

    @Test
    fun `cancelAllScheduledMessages returns success`() {
        val result = scheduler.cancelAllScheduledMessages()

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { workManager.cancelAllWorkByTag("scheduled_message") }
    }

    @Test
    fun `cancelAllScheduledMessages handles exception`() {
        coEvery { workManager.cancelAllWorkByTag(any()) } throws RuntimeException("error")

        val result = scheduler.cancelAllScheduledMessages()

        assertThat(result.isFailure).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // Companion constants
    // ════════════════════════════════════════════════════════════

    @Test
    fun `WORK_TAG_SCHEDULED_MESSAGE constant is correct`() {
        assertThat(MessageScheduler.WORK_TAG_SCHEDULED_MESSAGE).isEqualTo("scheduled_message")
    }

    @Test
    fun `key constants are correct`() {
        assertThat(MessageScheduler.KEY_MESSAGE_ID).isEqualTo("message_id")
        assertThat(MessageScheduler.KEY_RECIPIENT).isEqualTo("recipient")
        assertThat(MessageScheduler.KEY_MESSAGE_BODY).isEqualTo("message_body")
        assertThat(MessageScheduler.KEY_CONVERSATION_ID).isEqualTo("conversation_id")
    }
}
