package com.rasmi.purevon.domain.usecase.message

import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.domain.repository.ReactionRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for MessageOperation use cases (Delete, Star, Retry, SendMms)
 * and Reaction use cases (Toggle, Remove).
 */
class MessageOperationUseCasesTest {

    private lateinit var messageRepo: MessageRepository
    private lateinit var reactionRepo: ReactionRepository

    @Before
    fun setUp() {
        messageRepo = mockk(relaxed = true)
        reactionRepo = mockk(relaxed = true)
    }

    // ════════════════════════════════════════════════════════════
    // DeleteMessageUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `DeleteMessage delegates messageId to repository`() = runTest {
        val useCase = DeleteMessageUseCase(messageRepo)

        useCase(42L)

        coVerify(exactly = 1) { messageRepo.deleteMessage(42L) }
    }

    // ════════════════════════════════════════════════════════════
    // ToggleMessageStarredUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `ToggleMessageStarred delegates messageId to repository`() = runTest {
        val useCase = ToggleMessageStarredUseCase(messageRepo)

        useCase(99L)

        coVerify(exactly = 1) { messageRepo.toggleMessageStarred(99L) }
    }

    // ════════════════════════════════════════════════════════════
    // RetryFailedMessageUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `RetryFailedMessage returns success from repository`() = runTest {
        coEvery { messageRepo.retryFailedMessage(7L) } returns MessageResult.Success(7L)

        val useCase = RetryFailedMessageUseCase(messageRepo)
        val result = useCase(7L)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(7L)
    }

    @Test
    fun `RetryFailedMessage returns failure from repository`() = runTest {
        val error = MessageError.NetworkError("No connection")
        coEvery { messageRepo.retryFailedMessage(7L) } returns MessageResult.Failure(error)

        val useCase = RetryFailedMessageUseCase(messageRepo)
        val result = useCase(7L)

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isEqualTo(error)
    }

    // ════════════════════════════════════════════════════════════
    // SendMmsMessageUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SendMmsMessage passes all parameters to repository`() = runTest {
        val attachments = listOf("content://media/1", "content://media/2")
        coEvery { messageRepo.sendMmsMessage(any(), any(), any(), any()) } returns
                MessageResult.Success(55L)

        val useCase = SendMmsMessageUseCase(messageRepo)
        val result = useCase("+966500000000", "Photo", attachments, 1)

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) {
            messageRepo.sendMmsMessage("+966500000000", "Photo", attachments, 1)
        }
    }

    @Test
    fun `SendMmsMessage passes null message`() = runTest {
        coEvery { messageRepo.sendMmsMessage(any(), any(), any(), any()) } returns
                MessageResult.Success(56L)

        val useCase = SendMmsMessageUseCase(messageRepo)
        val result = useCase("+966500000000", null, listOf("content://media/1"), null)

        assertThat(result.isSuccess).isTrue()
        coVerify { messageRepo.sendMmsMessage("+966500000000", null, listOf("content://media/1"), null) }
    }

    // ════════════════════════════════════════════════════════════
    // ToggleReactionUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `ToggleReaction delegates to repository`() = runTest {
        coEvery { reactionRepo.toggleReaction(10L, "👍") } returns Result.success(Unit)

        val useCase = ToggleReactionUseCase(reactionRepo)
        val result = useCase(10L, "👍")

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { reactionRepo.toggleReaction(10L, "👍") }
    }

    @Test
    fun `ToggleReaction propagates failure`() = runTest {
        coEvery { reactionRepo.toggleReaction(10L, "❤️") } returns
                Result.failure(RuntimeException("DB error"))

        val useCase = ToggleReactionUseCase(reactionRepo)
        val result = useCase(10L, "❤️")

        assertThat(result.isFailure).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // RemoveReactionUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `RemoveReaction delegates to repository`() = runTest {
        coEvery { reactionRepo.removeReaction(10L) } returns Result.success(Unit)

        val useCase = RemoveReactionUseCase(reactionRepo)
        val result = useCase(10L)

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { reactionRepo.removeReaction(10L) }
    }
}
