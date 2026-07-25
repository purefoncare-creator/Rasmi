package com.rasmi.purevon.domain.usecase.message

import androidx.paging.PagingData
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.repository.MessageRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for MessageQuery use cases:
 *   GetMessagesByThreadPaged, GetAddressFromThreadId,
 *   GetOrCreateThreadId, GetMessageChangeEvents,
 *   SyncThreadMessages, SaveScheduledMessage
 */
class MessageQueryUseCasesTest {

    private lateinit var repo: MessageRepository

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
    }

    // ════════════════════════════════════════════════════════════
    // GetMessagesByThreadPagedUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetMessagesByThreadPaged delegates threadId to repository`() = runTest {
        val pagingFlow = flowOf(PagingData.empty<Message>())
        every { repo.getMessagesByThreadPaged(5L) } returns pagingFlow

        val useCase = GetMessagesByThreadPagedUseCase(repo)
        val result = useCase(5L)

        assertThat(result).isSameInstanceAs(pagingFlow)
        verify(exactly = 1) { repo.getMessagesByThreadPaged(5L) }
    }

    // ════════════════════════════════════════════════════════════
    // GetAddressFromThreadIdUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetAddressFromThreadId returns address`() = runTest {
        coEvery { repo.getAddressFromThreadId(10L) } returns "+201234567890"

        val useCase = GetAddressFromThreadIdUseCase(repo)
        val result = useCase(10L)

        assertThat(result).isEqualTo("+201234567890")
    }

    @Test
    fun `GetAddressFromThreadId returns null for unknown thread`() = runTest {
        coEvery { repo.getAddressFromThreadId(999L) } returns null

        val useCase = GetAddressFromThreadIdUseCase(repo)
        val result = useCase(999L)

        assertThat(result).isNull()
    }

    // ════════════════════════════════════════════════════════════
    // GetOrCreateThreadIdUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetOrCreateThreadId returns existing thread id`() = runTest {
        coEvery { repo.getOrCreateThreadIdForNumber("+201234567890") } returns 42L

        val useCase = GetOrCreateThreadIdUseCase(repo)
        val result = useCase("+201234567890")

        assertThat(result).isEqualTo(42L)
    }

    @Test
    fun `GetOrCreateThreadId creates new thread for unknown number`() = runTest {
        coEvery { repo.getOrCreateThreadIdForNumber("+201111111111") } returns 100L

        val useCase = GetOrCreateThreadIdUseCase(repo)
        val result = useCase("+201111111111")

        assertThat(result).isEqualTo(100L)
    }

    // ════════════════════════════════════════════════════════════
    // GetMessageChangeEventsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetMessageChangeEvents returns flow from repository`() = runTest {
        val eventsFlow = flowOf(Unit)
        every { repo.getMessageChangeEvents() } returns eventsFlow

        val useCase = GetMessageChangeEventsUseCase(repo)
        val result = useCase()

        assertThat(result).isSameInstanceAs(eventsFlow)
    }

    // ════════════════════════════════════════════════════════════
    // SyncThreadMessagesUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SyncThreadMessages delegates threadId to repository`() = runTest {
        val useCase = SyncThreadMessagesUseCase(repo)
        useCase(15L)

        coVerify(exactly = 1) { repo.syncMessages(15L) }
    }

    // ════════════════════════════════════════════════════════════
    // SaveScheduledMessageUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SaveScheduledMessage passes all parameters`() = runTest {
        val scheduledTime = System.currentTimeMillis() + 60_000
        coEvery {
            repo.scheduleMessage("+201234567890", "Hello", scheduledTime, emptyList(), null)
        } returns 77L

        val useCase = SaveScheduledMessageUseCase(repo)
        val result = useCase("+201234567890", "Hello", scheduledTime)

        assertThat(result).isEqualTo(77L)
    }

    @Test
    fun `SaveScheduledMessage passes attachments and simSlot`() = runTest {
        val scheduledTime = System.currentTimeMillis() + 120_000
        val uris = listOf("content://media/1")
        coEvery {
            repo.scheduleMessage("+201234567890", "Photo", scheduledTime, uris, 1)
        } returns 88L

        val useCase = SaveScheduledMessageUseCase(repo)
        val result = useCase("+201234567890", "Photo", scheduledTime, uris, 1)

        assertThat(result).isEqualTo(88L)
    }
}
