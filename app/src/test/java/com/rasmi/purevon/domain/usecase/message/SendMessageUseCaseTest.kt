package com.rasmi.purevon.domain.usecase.message

import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.util.SoundManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for SendMessageUseCase — the use case with the most logic (validation + sound).
 */
class SendMessageUseCaseTest {

    private lateinit var repository: MessageRepository
    private lateinit var soundManager: SoundManager
    private lateinit var useCase: SendMessageUseCase

    @Before
    fun setUp() {
        repository = mockk()
        soundManager = mockk(relaxed = true)
        useCase = SendMessageUseCase(repository, soundManager)
    }

    // ════════════════════════════════════════════════════════════
    // Validation tests
    // ════════════════════════════════════════════════════════════

    @Test
    fun `invoke with blank phone number returns InvalidNumberError`() = runTest {
        val result = useCase(phoneNumber = "", message = "Hello")

        assertThat(result.isFailure).isTrue()
        val error = result.errorOrNull()
        assertThat(error).isInstanceOf(MessageError.InvalidNumberError::class.java)
        coVerify(exactly = 0) { repository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `invoke with whitespace-only phone number returns failure`() = runTest {
        val result = useCase(phoneNumber = "   ", message = "Hello")

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { repository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `invoke with blank message returns failure`() = runTest {
        val result = useCase(phoneNumber = "+966500000000", message = "")

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { repository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `invoke with whitespace-only message returns failure`() = runTest {
        val result = useCase(phoneNumber = "+966500000000", message = "   ")

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { repository.sendMessage(any(), any(), any()) }
    }

    // ════════════════════════════════════════════════════════════
    // Success path
    // ════════════════════════════════════════════════════════════

    @Test
    fun `invoke with valid inputs delegates to repository`() = runTest {
        val expectedId = 42L
        coEvery { repository.sendMessage("+966500000000", "Hello", null) } returns
                MessageResult.Success(expectedId)

        val result = useCase(phoneNumber = "+966500000000", message = "Hello")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(expectedId)
        coVerify(exactly = 1) { repository.sendMessage("+966500000000", "Hello", null) }
    }

    @Test
    fun `invoke with simSlot passes it to repository`() = runTest {
        coEvery { repository.sendMessage(any(), any(), eq(2)) } returns
                MessageResult.Success(99L)

        val result = useCase(phoneNumber = "+966500000000", message = "Hi", simSlot = 2)

        assertThat(result.isSuccess).isTrue()
        coVerify { repository.sendMessage("+966500000000", "Hi", 2) }
    }

    @Test
    fun `plays sent sound on success`() = runTest {
        coEvery { repository.sendMessage(any(), any(), any()) } returns
                MessageResult.Success(1L)

        useCase(phoneNumber = "+966500000000", message = "Hello")

        verify(exactly = 1) { soundManager.playMessageSentSound() }
    }

    @Test
    fun `does not play sound on failure`() = runTest {
        coEvery { repository.sendMessage(any(), any(), any()) } returns
                MessageResult.Failure(MessageError.NetworkError("No network"))

        useCase(phoneNumber = "+966500000000", message = "Hello")

        verify(exactly = 0) { soundManager.playMessageSentSound() }
    }

    // ════════════════════════════════════════════════════════════
    // Failure path
    // ════════════════════════════════════════════════════════════

    @Test
    fun `invoke propagates repository failure`() = runTest {
        val error = MessageError.NetworkError("Timeout")
        coEvery { repository.sendMessage(any(), any(), any()) } returns
                MessageResult.Failure(error)

        val result = useCase(phoneNumber = "+966500000000", message = "Hello")

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isEqualTo(error)
    }
}
