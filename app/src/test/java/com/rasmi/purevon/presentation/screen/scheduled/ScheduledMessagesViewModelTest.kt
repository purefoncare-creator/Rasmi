package com.rasmi.purevon.presentation.screen.scheduled

import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.ScheduledMessageDao
import com.rasmi.purevon.data.local.entity.ScheduleStatus
import com.rasmi.purevon.data.local.entity.ScheduledMessageEntity
import com.rasmi.purevon.util.message.MessageScheduler
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
class ScheduledMessagesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scheduledMessageDao: ScheduledMessageDao
    private lateinit var messageScheduler: MessageScheduler

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        scheduledMessageDao = mockk(relaxed = true)
        messageScheduler = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ScheduledMessagesViewModel {
        return ScheduledMessagesViewModel(scheduledMessageDao, messageScheduler)
    }

    private fun createEntity(
        id: Long = 1L,
        recipient: String = "+1234567890",
        messageBody: String = "Hello",
        scheduledTime: Long = System.currentTimeMillis() + 3600000,
        status: ScheduleStatus = ScheduleStatus.PENDING
    ) = ScheduledMessageEntity(
        id = id, recipient = recipient, messageBody = messageBody,
        scheduledTime = scheduledTime, status = status,
        simSlot = 1, repeatInterval = com.rasmi.purevon.data.local.entity.RepeatInterval.NONE
    )

    @Test
    fun `initial state loads scheduled messages`() = runTest {
        val messages = listOf(createEntity(id = 1L), createEntity(id = 2L, recipient = "+9999"))
        every { scheduledMessageDao.getAllScheduledMessages() } returns flowOf(messages)

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.scheduledMessages).hasSize(2)
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `initial state with empty list`() = runTest {
        every { scheduledMessageDao.getAllScheduledMessages() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.scheduledMessages).isEmpty()
    }

    @Test
    fun `cancel message calls scheduler and dao`() = runTest {
        every { scheduledMessageDao.getAllScheduledMessages() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ScheduledMessagesUiEvent.CancelMessage(messageId = 42L))
        advanceUntilIdle()

        verify { messageScheduler.cancelScheduledMessage(42L) }
        coVerify { scheduledMessageDao.updateStatus(42L, ScheduleStatus.CANCELLED) }
    }

    @Test
    fun `cancel message handles error`() = runTest {
        every { scheduledMessageDao.getAllScheduledMessages() } returns flowOf(emptyList())
        every { messageScheduler.cancelScheduledMessage(any()) } returns Result.failure(RuntimeException("Cancel failed"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ScheduledMessagesUiEvent.CancelMessage(messageId = 1L))
        advanceUntilIdle()

        coVerify { scheduledMessageDao.updateStatus(1L, ScheduleStatus.CANCELLED) }
    }

    @Test
    fun `edit message sets editingMessage in state`() = runTest {
        val entity = createEntity(id = 5L, messageBody = "Test", scheduledTime = 1000L)
        every { scheduledMessageDao.getAllScheduledMessages() } returns flowOf(listOf(entity))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ScheduledMessagesUiEvent.EditMessage(messageId = 5L))

        assertThat(vm.uiState.value.editingMessage).isNotNull()
        assertThat(vm.uiState.value.editingMessage?.body).isEqualTo("Test")
        assertThat(vm.uiState.value.editingMessage?.scheduleId).isEqualTo(5L)
    }

    @Test
    fun `edit message for non-existent message does nothing`() = runTest {
        every { scheduledMessageDao.getAllScheduledMessages() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ScheduledMessagesUiEvent.EditMessage(messageId = 999L))

        assertThat(vm.uiState.value.editingMessage).isNull()
    }

    @Test
    fun `dismiss edit clears editingMessage`() = runTest {
        val entity = createEntity(id = 5L)
        every { scheduledMessageDao.getAllScheduledMessages() } returns flowOf(listOf(entity))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ScheduledMessagesUiEvent.EditMessage(messageId = 5L))
        assertThat(vm.uiState.value.editingMessage).isNotNull()

        vm.onEvent(ScheduledMessagesUiEvent.DismissEdit)
        assertThat(vm.uiState.value.editingMessage).isNull()
    }

    @Test
    fun `confirm edit updates message and reschedules`() = runTest {
        val entity = createEntity(id = 3L, recipient = "+1111", messageBody = "Old")
        every { scheduledMessageDao.getAllScheduledMessages() } returns flowOf(listOf(entity))
        coEvery { scheduledMessageDao.getMessageById(3L) } returns entity
        coEvery { scheduledMessageDao.update(any()) } just Runs
        every { messageScheduler.cancelScheduledMessage(3L) } returns Result.success(Unit)
        every { messageScheduler.scheduleMessage(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ScheduledMessagesUiEvent.ConfirmEdit(
            scheduleId = 3L, newBody = "New body", newTime = 2000L,
            newRepeat = com.rasmi.purevon.domain.model.RepeatInterval.NONE
        ))
        advanceUntilIdle()

        verify { messageScheduler.cancelScheduledMessage(3L) }
        coVerify { scheduledMessageDao.update(any()) }
        verify { messageScheduler.scheduleMessage(any(), any(), any(), any(), any()) }
        assertThat(vm.uiState.value.editingMessage).isNull()
    }

    @Test
    fun `message item data is correctly mapped`() = runTest {
        val entity = createEntity(
            id = 10L, recipient = "+5555", messageBody = "Meeting at 3",
            scheduledTime = 5000L, status = ScheduleStatus.PENDING
        )
        every { scheduledMessageDao.getAllScheduledMessages() } returns flowOf(listOf(entity))

        val vm = createViewModel()
        advanceUntilIdle()

        val item = vm.uiState.value.scheduledMessages[0]
        assertThat(item.id).isEqualTo(10L)
        assertThat(item.recipient).isEqualTo("+5555")
        assertThat(item.messageBody).isEqualTo("Meeting at 3")
        assertThat(item.scheduledTime).isEqualTo(5000L)
        assertThat(item.status).isEqualTo(ScheduleStatus.PENDING)
    }
}
