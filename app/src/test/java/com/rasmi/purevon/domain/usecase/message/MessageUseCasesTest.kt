package com.rasmi.purevon.domain.usecase.message

import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.MessageCategory
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.domain.repository.SyncRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for the 10 core message use cases in MessageUseCases.kt:
 *   GetAllConversations, GetMessagesByThread,
 *   GetMessagesByCategory, GetUnreadMessages, GetUnreadCount,
 *   SearchMessages, MarkThreadAsRead, DeleteConversation,
 *   ToggleConversationPin, SyncMessages
 */
class MessageUseCasesTest {

    private lateinit var messageRepo: MessageRepository
    private lateinit var syncRepo: SyncRepository

    @Before
    fun setUp() {
        messageRepo = mockk(relaxed = true)
        syncRepo = mockk(relaxed = true)
    }

    // ════════════════════════════════════════════════════════════
    // GetAllConversationsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetAllConversations returns flow from repository`() = runTest {
        val conversations = listOf(mockk<Conversation>())
        every { messageRepo.getAllConversations() } returns flowOf(conversations)

        val useCase = GetAllConversationsUseCase(messageRepo)
        val result = useCase().first()

        assertThat(result).hasSize(1)
    }

    @Test
    fun `GetAllConversations returns empty when no conversations`() = runTest {
        every { messageRepo.getAllConversations() } returns flowOf(emptyList())

        val useCase = GetAllConversationsUseCase(messageRepo)
        val result = useCase().first()

        assertThat(result).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // GetMessagesByThreadUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetMessagesByThread delegates threadId to repository`() = runTest {
        val messages = listOf(mockk<Message>())
        every { messageRepo.getMessagesByThread(5L) } returns flowOf(messages)

        val useCase = GetMessagesByThreadUseCase(messageRepo)
        val result = useCase(5L).first()

        assertThat(result).hasSize(1)
        verify(exactly = 1) { messageRepo.getMessagesByThread(5L) }
    }

    // ════════════════════════════════════════════════════════════
    // GetMessagesByCategoryUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetMessagesByCategory delegates category to repository`() = runTest {
        val messages = listOf(mockk<Message>())
        every { messageRepo.getMessagesByCategory(MessageCategory.OTP) } returns flowOf(messages)

        val useCase = GetMessagesByCategoryUseCase(messageRepo)
        val result = useCase(MessageCategory.OTP).first()

        assertThat(result).hasSize(1)
        verify(exactly = 1) { messageRepo.getMessagesByCategory(MessageCategory.OTP) }
    }

    // ════════════════════════════════════════════════════════════
    // GetUnreadMessagesUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetUnreadMessages returns unread messages flow`() = runTest {
        val messages = listOf(mockk<Message>(), mockk<Message>())
        every { messageRepo.getUnreadMessages() } returns flowOf(messages)

        val useCase = GetUnreadMessagesUseCase(messageRepo)
        val result = useCase().first()

        assertThat(result).hasSize(2)
    }

    // ════════════════════════════════════════════════════════════
    // GetUnreadCountUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetUnreadCount returns count from repository`() = runTest {
        every { messageRepo.getUnreadCount() } returns flowOf(5)

        val useCase = GetUnreadCountUseCase(messageRepo)
        val result = useCase().first()

        assertThat(result).isEqualTo(5)
    }

    @Test
    fun `GetUnreadCount returns zero when all read`() = runTest {
        every { messageRepo.getUnreadCount() } returns flowOf(0)

        val useCase = GetUnreadCountUseCase(messageRepo)
        val result = useCase().first()

        assertThat(result).isEqualTo(0)
    }

    // ════════════════════════════════════════════════════════════
    // SearchMessagesUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SearchMessages delegates query to repository`() = runTest {
        val messages = listOf(mockk<Message>())
        every { messageRepo.searchMessages("hello") } returns flowOf(messages)

        val useCase = SearchMessagesUseCase(messageRepo)
        val result = useCase("hello").first()

        assertThat(result).hasSize(1)
        verify(exactly = 1) { messageRepo.searchMessages("hello") }
    }

    // ════════════════════════════════════════════════════════════
    // MarkThreadAsReadUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `MarkThreadAsRead delegates threadId to repository`() = runTest {
        val useCase = MarkThreadAsReadUseCase(messageRepo)
        useCase(10L)

        coVerify(exactly = 1) { messageRepo.markThreadAsRead(10L) }
    }

    // ════════════════════════════════════════════════════════════
    // DeleteConversationUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `DeleteConversation delegates threadId to repository`() = runTest {
        val useCase = DeleteConversationUseCase(messageRepo)
        useCase(20L)

        coVerify(exactly = 1) { messageRepo.deleteThread(20L) }
    }

    // ════════════════════════════════════════════════════════════
    // ToggleConversationPinUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `ToggleConversationPin passes threadId and pin state`() = runTest {
        val useCase = ToggleConversationPinUseCase(messageRepo)
        useCase(30L, true)

        coVerify(exactly = 1) { messageRepo.setConversationPinned(30L, true) }
    }

    @Test
    fun `ToggleConversationPin can unpin`() = runTest {
        val useCase = ToggleConversationPinUseCase(messageRepo)
        useCase(30L, false)

        coVerify(exactly = 1) { messageRepo.setConversationPinned(30L, false) }
    }

    // ════════════════════════════════════════════════════════════
    // SyncMessagesUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SyncMessages invoke delegates to messageRepo syncWithSystemMessages`() = runTest {
        val useCase = SyncMessagesUseCase(messageRepo, syncRepo)
        useCase()

        coVerify(exactly = 1) { messageRepo.syncWithSystemMessages() }
    }

    @Test
    fun `SyncMessages performFullSync delegates to syncRepo`() = runTest {
        val useCase = SyncMessagesUseCase(messageRepo, syncRepo)
        useCase.performFullSync()

        coVerify(exactly = 1) { syncRepo.performFullSync() }
    }

    @Test
    fun `SyncMessages syncConversation delegates threadId`() = runTest {
        val useCase = SyncMessagesUseCase(messageRepo, syncRepo)
        useCase.syncConversation(5L)

        coVerify(exactly = 1) { syncRepo.syncConversation(5L) }
    }

    @Test
    fun `SyncMessages hasCompletedInitialSync returns value from syncRepo`() = runTest {
        coEvery { syncRepo.hasCompletedInitialSync() } returns true

        val useCase = SyncMessagesUseCase(messageRepo, syncRepo)
        val result = useCase.hasCompletedInitialSync()

        assertThat(result).isTrue()
    }
}
