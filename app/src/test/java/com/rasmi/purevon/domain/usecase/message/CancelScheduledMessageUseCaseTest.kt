package com.rasmi.purevon.domain.usecase.message

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.util.message.MessageScheduler
import io.mockk.*
import org.junit.Before
import org.junit.Test

class CancelScheduledMessageUseCaseTest {

    private lateinit var messageScheduler: MessageScheduler
    private lateinit var useCase: CancelScheduledMessageUseCase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0

        messageScheduler = mockk(relaxed = true)
        useCase = CancelScheduledMessageUseCase(messageScheduler)
    }

    @Test
    fun `invoke with valid ID calls scheduler`() {
        coEvery { messageScheduler.cancelScheduledMessage(42L) } returns Result.success(Unit)

        val result = useCase(42L)

        assertThat(result).isInstanceOf(MessageResult.Success::class.java)
        coVerify(exactly = 1) { messageScheduler.cancelScheduledMessage(42L) }
    }

    @Test
    fun `invoke with zero ID returns failure`() {
        val result = useCase(0L)

        assertThat(result).isInstanceOf(MessageResult.Failure::class.java)
        coVerify(exactly = 0) { messageScheduler.cancelScheduledMessage(any()) }
    }

    @Test
    fun `invoke with negative ID returns failure`() {
        val result = useCase(-1L)

        assertThat(result).isInstanceOf(MessageResult.Failure::class.java)
        coVerify(exactly = 0) { messageScheduler.cancelScheduledMessage(any()) }
    }

    @Test
    fun `invoke propagates scheduler failure`() {
        coEvery { messageScheduler.cancelScheduledMessage(42L) } returns Result.failure(Exception("Worker error"))

        val result = useCase(42L)

        assertThat(result).isInstanceOf(MessageResult.Failure::class.java)
    }

    @Test
    fun `invoke with long max value succeeds`() {
        coEvery { messageScheduler.cancelScheduledMessage(Long.MAX_VALUE) } returns Result.success(Unit)

        val result = useCase(Long.MAX_VALUE)

        assertThat(result).isInstanceOf(MessageResult.Success::class.java)
    }
}
