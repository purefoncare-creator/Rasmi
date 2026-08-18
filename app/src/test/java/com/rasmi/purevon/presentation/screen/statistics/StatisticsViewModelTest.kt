package com.rasmi.purevon.presentation.screen.statistics

import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.model.CallType
import com.rasmi.purevon.domain.repository.CallLogRepository
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.util.CallGrouper
import com.rasmi.purevon.util.CallStatistics
import com.rasmi.purevon.util.ContactFrequency
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var callRepository: CallLogRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var callGrouper: CallGrouper

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        callRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        callGrouper = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): StatisticsViewModel {
        return StatisticsViewModel(callRepository, messageRepository, callGrouper)
    }

    @Test
    fun `initial state has default values`() = runTest {
        every { callRepository.getAllCallLogs() } returns emptyFlow()
        every { messageRepository.getTotalMessageCount() } returns flowOf(0)
        every { messageRepository.getMessageCountByType(1) } returns flowOf(0)
        every { messageRepository.getMessageCountByType(2) } returns flowOf(0)

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.totalMessages).isEqualTo(0)
        assertThat(vm.uiState.value.receivedMessages).isEqualTo(0)
        assertThat(vm.uiState.value.sentMessages).isEqualTo(0)
        assertThat(vm.uiState.value.frequentContacts).isEmpty()
    }

    @Test
    fun `loads call statistics from repository`() = runTest {
        val stats = CallStatistics(
            totalCalls = 100, incomingCalls = 40, outgoingCalls = 50,
            missedCalls = 10, blockedCalls = 0, totalDuration = 5000L, averageDuration = 50L
        )
        every { callRepository.getAllCallLogs() } returns flowOf(emptyList())
        every { callGrouper.getCallStatistics(any()) } returns stats
        every { callGrouper.getMostFrequentContacts(any(), 5) } returns emptyList()
        every { messageRepository.getTotalMessageCount() } returns flowOf(0)
        every { messageRepository.getMessageCountByType(1) } returns flowOf(0)
        every { messageRepository.getMessageCountByType(2) } returns flowOf(0)

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.callStats).isEqualTo(stats)
        assertThat(vm.uiState.value.callStats?.totalCalls).isEqualTo(100)
    }

    @Test
    fun `loads message counts from repository`() = runTest {
        every { callRepository.getAllCallLogs() } returns emptyFlow()
        every { messageRepository.getTotalMessageCount() } returns flowOf(250)
        every { messageRepository.getMessageCountByType(1) } returns flowOf(120) // RECEIVED
        every { messageRepository.getMessageCountByType(2) } returns flowOf(130) // SENT

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.totalMessages).isEqualTo(250)
        assertThat(vm.uiState.value.receivedMessages).isEqualTo(120)
        assertThat(vm.uiState.value.sentMessages).isEqualTo(130)
    }

    @Test
    fun `loads most frequent contacts`() = runTest {
        val freq = listOf(
            ContactFrequency("+1234", "Alice", 15, 3000L, System.currentTimeMillis()),
            ContactFrequency("+5678", "Bob", 10, 2000L, System.currentTimeMillis())
        )
        every { callRepository.getAllCallLogs() } returns flowOf(emptyList())
        every { callGrouper.getCallStatistics(any()) } returns mockk()
        every { callGrouper.getMostFrequentContacts(any(), 5) } returns freq
        every { messageRepository.getTotalMessageCount() } returns flowOf(0)
        every { messageRepository.getMessageCountByType(1) } returns flowOf(0)
        every { messageRepository.getMessageCountByType(2) } returns flowOf(0)

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.frequentContacts).hasSize(2)
        assertThat(vm.uiState.value.frequentContacts[0].name).isEqualTo("Alice")
    }

    @Test
    fun `handles call logs error gracefully`() = runTest {
        every { callRepository.getAllCallLogs() } returns flowOf(emptyList())
        every { callGrouper.getCallStatistics(any()) } throws RuntimeException("DB error")
        every { messageRepository.getTotalMessageCount() } returns flowOf(0)
        every { messageRepository.getMessageCountByType(1) } returns flowOf(0)
        every { messageRepository.getMessageCountByType(2) } returns flowOf(0)

        val vm = createViewModel()
        advanceUntilIdle()

        // Should not crash, callStats remains null
        assertThat(vm.uiState.value.callStats).isNull()
    }

    @Test
    fun `handles message count error gracefully`() = runTest {
        every { callRepository.getAllCallLogs() } returns emptyFlow()
        every { messageRepository.getTotalMessageCount() } returns flow { throw RuntimeException("DB error") }
        every { messageRepository.getMessageCountByType(1) } returns flowOf(0)
        every { messageRepository.getMessageCountByType(2) } returns flowOf(0)

        val vm = createViewModel()
        advanceUntilIdle()

        // Should not crash, counts stay at default 0
        assertThat(vm.uiState.value.totalMessages).isEqualTo(0)
    }

    @Test
    fun `state updates when call logs flow emits`() = runTest {
        val calls = listOf(
            mockk<CallLog> { every { this@mockk.type } returns CallType.INCOMING.toSystemType() }
        )
        every { callRepository.getAllCallLogs() } returns flowOf(calls)
        every { callGrouper.getCallStatistics(any()) } returns mockk()
        every { callGrouper.getMostFrequentContacts(any(), 5) } returns emptyList()
        every { messageRepository.getTotalMessageCount() } returns flowOf(0)
        every { messageRepository.getMessageCountByType(1) } returns flowOf(0)
        every { messageRepository.getMessageCountByType(2) } returns flowOf(0)

        val vm = createViewModel()
        advanceUntilIdle()

        verify { callGrouper.getCallStatistics(calls) }
        verify { callGrouper.getMostFrequentContacts(calls, 5) }
    }
}
