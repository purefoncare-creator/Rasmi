package com.rasmi.purevon.presentation.screen.messages

import android.content.Context
import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.MessageCategory
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.model.MessageStatus
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.domain.usecase.conversation.ManageConversationUseCase
import com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
import com.rasmi.purevon.domain.usecase.message.ClearAllMessagesUseCase
import com.rasmi.purevon.domain.usecase.message.DeleteConversationUseCase
import com.rasmi.purevon.domain.usecase.message.GetAllConversationsUseCase
import com.rasmi.purevon.domain.usecase.message.GetArchivedConversationsUseCase
import com.rasmi.purevon.domain.usecase.message.GetStarredMessagesUseCase
import com.rasmi.purevon.domain.usecase.message.GetUnreadCountUseCase
import com.rasmi.purevon.domain.usecase.message.MarkThreadAsReadUseCase
import com.rasmi.purevon.domain.usecase.message.SearchConversationsUseCase
import com.rasmi.purevon.domain.usecase.message.SyncMessagesUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var deleteConversationUseCase: DeleteConversationUseCase
    private lateinit var clearAllMessagesUseCase: ClearAllMessagesUseCase
    private lateinit var markThreadAsReadUseCase: MarkThreadAsReadUseCase
    private lateinit var syncMessagesUseCase: SyncMessagesUseCase
    private lateinit var manageConversationUseCase: ManageConversationUseCase
    private lateinit var getAllConversationsUseCase: GetAllConversationsUseCase
    private lateinit var searchConversationsUseCase: SearchConversationsUseCase
    private lateinit var getStarredMessagesUseCase: GetStarredMessagesUseCase
    private lateinit var getArchivedConversationsUseCase: GetArchivedConversationsUseCase
    private lateinit var getUnreadCountUseCase: GetUnreadCountUseCase
    private lateinit var messageRepository: MessageRepository
    private lateinit var getAllContactsUseCase: GetAllContactsUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        deleteConversationUseCase = mockk(relaxed = true)
        clearAllMessagesUseCase = mockk(relaxed = true)
        markThreadAsReadUseCase = mockk(relaxed = true)
        syncMessagesUseCase = mockk(relaxed = true)
        manageConversationUseCase = mockk(relaxed = true)
        getAllConversationsUseCase = mockk(relaxed = true)
        searchConversationsUseCase = mockk(relaxed = true)
        getStarredMessagesUseCase = mockk(relaxed = true)
        getArchivedConversationsUseCase = mockk(relaxed = true)
        getUnreadCountUseCase = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        getAllContactsUseCase = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MessagesViewModel {
        every { getStarredMessagesUseCase() } returns flowOf(emptyList())
        every { getArchivedConversationsUseCase() } returns flowOf(emptyList())
        every { getUnreadCountUseCase() } returns flowOf(0)
        every { getAllContactsUseCase() } returns flowOf(emptyList())
        return MessagesViewModel(
            context,
            deleteConversationUseCase,
            clearAllMessagesUseCase,
            markThreadAsReadUseCase,
            syncMessagesUseCase,
            manageConversationUseCase,
            getAllConversationsUseCase,
            searchConversationsUseCase,
            getStarredMessagesUseCase,
            getArchivedConversationsUseCase,
            getUnreadCountUseCase,
            messageRepository,
            getAllContactsUseCase
        )
    }

    private fun conversation(
        threadId: Long = 1L,
        number: String = "+966501234567",
        unread: Int = 0
    ) = Conversation(
        threadId = threadId,
        phoneNumber = number,
        contactName = "Alice",
        contactPhotoUri = null,
        lastMessage = "hi",
        lastMessageTimestamp = 100L,
        lastMessageType = "received",
        unreadCount = unread,
        messageCount = 1,
        isPinned = false,
        isMuted = false,
        isArchived = false
    )

    private fun message(
        id: Long = 1L,
        threadId: Long = 1L,
        category: MessageCategory = MessageCategory.PERSONAL
    ) = Message(
        id = id,
        threadId = threadId,
        phoneNumber = "+966501234567",
        contactName = "Alice",
        body = "body",
        timestamp = 100L,
        type = 1,
        category = category,
        isRead = false,
        isSent = true,
        isDelivered = false,
        simSlot = 0,
        isSpam = false,
        spamScore = 0f,
        isMms = false,
        attachmentUris = emptyList(),
        attachmentTypes = emptyList(),
        status = MessageStatus.SENT
    )

    @Test
    fun `initial state is loading`() {
        val vm = createViewModel()
        assertThat(vm.uiState.value.isLoading).isTrue()
    }

    @Test
    fun `search query changed updates state`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.SearchQueryChanged("ali"))
        assertThat(vm.uiState.value.searchQuery).isEqualTo("ali")
    }

    @Test
    fun `tab changed updates selected tab`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.TabChanged(MessageTab.STARRED))
        assertThat(vm.uiState.value.selectedTab).isEqualTo(MessageTab.STARRED)
    }

    @Test
    fun `long press enters selection mode`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.ConversationLongPressed(5L))
        assertThat(vm.uiState.value.isSelectionMode).isTrue()
        assertThat(vm.uiState.value.selectedConversationIds).containsExactly(5L)
    }

    @Test
    fun `toggling selection adds and removes ids`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.ConversationLongPressed(1L))
        vm.onEvent(MessagesUiEvent.ToggleConversationSelection(2L))
        assertThat(vm.uiState.value.selectedConversationIds).containsExactly(1L, 2L)
        vm.onEvent(MessagesUiEvent.ToggleConversationSelection(1L))
        assertThat(vm.uiState.value.selectedConversationIds).containsExactly(2L)
    }

    @Test
    fun `toggling off last selection exits selection mode`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.ConversationLongPressed(1L))
        vm.onEvent(MessagesUiEvent.ToggleConversationSelection(1L))
        assertThat(vm.uiState.value.isSelectionMode).isFalse()
        assertThat(vm.uiState.value.selectedConversationIds).isEmpty()
    }

    @Test
    fun `exit selection mode clears ids`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.ConversationLongPressed(1L))
        vm.onEvent(MessagesUiEvent.ExitSelectionMode)
        assertThat(vm.uiState.value.isSelectionMode).isFalse()
        assertThat(vm.uiState.value.selectedConversationIds).isEmpty()
    }

    @Test
    fun `deselect all clears selection`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.ConversationLongPressed(1L))
        vm.onEvent(MessagesUiEvent.DeselectAllConversations)
        assertThat(vm.uiState.value.selectedConversationIds).isEmpty()
    }

    @Test
    fun `delete conversation calls use case`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.DeleteConversation(7L))
        advanceUntilIdle()
        coVerify(exactly = 1) { deleteConversationUseCase(7L) }
    }

    @Test
    fun `delete conversation error captured`() = runTest {
        coEvery { deleteConversationUseCase(7L) } throws RuntimeException("boom")
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.DeleteConversation(7L))
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isEqualTo("boom")
    }

    @Test
    fun `mark as read calls use case`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.MarkAsRead(3L))
        advanceUntilIdle()
        coVerify(exactly = 1) { markThreadAsReadUseCase(3L) }
    }

    @Test
    fun `archive conversation calls toggle archive`() = runTest {
        coEvery { manageConversationUseCase.toggleArchive(any()) } returns
            com.rasmi.purevon.domain.model.MessageResult.Success(true)
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.ArchiveConversation(9L))
        advanceUntilIdle()
        coVerify(exactly = 1) { manageConversationUseCase.toggleArchive(9L) }
    }

    @Test
    fun `clear all messages calls use case`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.ClearAllMessages)
        advanceUntilIdle()
        coVerify(exactly = 1) { clearAllMessagesUseCase() }
    }

    @Test
    fun `delete selected conversations calls use case for each id`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.ConversationLongPressed(1L))
        vm.onEvent(MessagesUiEvent.ToggleConversationSelection(2L))
        vm.onEvent(MessagesUiEvent.DeleteSelectedConversations)
        advanceUntilIdle()
        coVerify(exactly = 1) { deleteConversationUseCase(1L) }
        coVerify(exactly = 1) { deleteConversationUseCase(2L) }
        assertThat(vm.uiState.value.isSelectionMode).isFalse()
    }

    @Test
    fun `mark selected as read calls use case for each id`() = runTest {
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.ConversationLongPressed(1L))
        vm.onEvent(MessagesUiEvent.ToggleConversationSelection(2L))
        vm.onEvent(MessagesUiEvent.MarkSelectedAsRead)
        advanceUntilIdle()
        coVerify(exactly = 1) { markThreadAsReadUseCase(1L) }
        coVerify(exactly = 1) { markThreadAsReadUseCase(2L) }
    }

    @Test
    fun `refresh conversations toggles refreshing state`() = runTest {
        coEvery { syncMessagesUseCase.syncConversationsOnly() } coAnswers { delay(5000) }
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.RefreshConversations)
        runCurrent()
        assertThat(vm.uiState.value.isRefreshing).isTrue()
        advanceTimeBy(5000)
        runCurrent()
        assertThat(vm.uiState.value.isRefreshing).isFalse()
        coVerify(exactly = 1) { syncMessagesUseCase.syncConversationsOnly() }
    }

    @Test
    fun `dismiss error clears error`() = runTest {
        coEvery { deleteConversationUseCase(7L) } throws RuntimeException("boom")
        val vm = createViewModel()
        vm.onEvent(MessagesUiEvent.DeleteConversation(7L))
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isNotNull()
        vm.onEvent(MessagesUiEvent.DismissError)
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `observes unread count`() = runTest {
        val vm = createViewModel()
        every { getUnreadCountUseCase() } returns flowOf(5)
        advanceUntilIdle()
        assertThat(vm.uiState.value.unreadCount).isEqualTo(5)
    }

    @Test
    fun `observes starred messages`() = runTest {
        val starred = listOf(message(id = 1L), message(id = 2L))
        val vm = createViewModel()
        every { getStarredMessagesUseCase() } returns flowOf(starred)
        advanceUntilIdle()
        assertThat(vm.uiState.value.starredMessages).hasSize(2)
    }

    @Test
    fun `observes archived conversations`() = runTest {
        val archived = listOf(conversation(threadId = 1L), conversation(threadId = 2L))
        val vm = createViewModel()
        every { getArchivedConversationsUseCase() } returns flowOf(archived)
        advanceUntilIdle()
        assertThat(vm.uiState.value.archivedConversations).hasSize(2)
    }
}
