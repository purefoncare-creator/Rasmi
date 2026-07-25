package com.rasmi.purevon.domain.usecase.call

import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.model.CallType
import com.rasmi.purevon.domain.repository.CallLogRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for Call use cases:
 *   GetAllCallLogs, GetCallLogsByType, GetCallLogsByNumber,
 *   SearchCallLogs, DeleteCallLog, SyncCallLogs,
 *   GetCallStatistics, ClearAllCallLogs
 */
class CallUseCasesTest {

    private lateinit var repo: CallLogRepository

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
    }

    // ════════════════════════════════════════════════════════════
    // GetAllCallLogsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetAllCallLogs returns flow from repository`() = runTest {
        val logs = listOf(mockk<CallLog>(), mockk<CallLog>())
        every { repo.getAllCallLogs() } returns flowOf(logs)

        val useCase = GetAllCallLogsUseCase(repo)
        val result = useCase().first()

        assertThat(result).hasSize(2)
    }

    @Test
    fun `GetAllCallLogs returns empty when no logs`() = runTest {
        every { repo.getAllCallLogs() } returns flowOf(emptyList())

        val useCase = GetAllCallLogsUseCase(repo)
        val result = useCase().first()

        assertThat(result).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // GetCallLogsByTypeUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetCallLogsByType filters by incoming`() = runTest {
        val logs = listOf(mockk<CallLog>())
        every { repo.getCallLogsByType(CallType.INCOMING) } returns flowOf(logs)

        val useCase = GetCallLogsByTypeUseCase(repo)
        val result = useCase(CallType.INCOMING).first()

        assertThat(result).hasSize(1)
        verify(exactly = 1) { repo.getCallLogsByType(CallType.INCOMING) }
    }

    @Test
    fun `GetCallLogsByType filters by missed`() = runTest {
        every { repo.getCallLogsByType(CallType.MISSED) } returns flowOf(emptyList())

        val useCase = GetCallLogsByTypeUseCase(repo)
        val result = useCase(CallType.MISSED).first()

        assertThat(result).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // GetCallLogsByNumberUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetCallLogsByNumber delegates phoneNumber to repository`() = runTest {
        val logs = listOf(mockk<CallLog>())
        every { repo.getCallLogsByNumber("+201234567890") } returns flowOf(logs)

        val useCase = GetCallLogsByNumberUseCase(repo)
        val result = useCase("+201234567890").first()

        assertThat(result).hasSize(1)
    }

    // ════════════════════════════════════════════════════════════
    // SearchCallLogsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SearchCallLogs delegates query to repository`() = runTest {
        val logs = listOf(mockk<CallLog>())
        every { repo.searchCallLogs("01234") } returns flowOf(logs)

        val useCase = SearchCallLogsUseCase(repo)
        val result = useCase("01234").first()

        assertThat(result).hasSize(1)
        verify(exactly = 1) { repo.searchCallLogs("01234") }
    }

    // ════════════════════════════════════════════════════════════
    // DeleteCallLogUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `DeleteCallLog delegates callLogId to repository`() = runTest {
        val useCase = DeleteCallLogUseCase(repo)
        useCase(55L)

        coVerify(exactly = 1) { repo.deleteCallLog(55L) }
    }

    // ════════════════════════════════════════════════════════════
    // SyncCallLogsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SyncCallLogs delegates to repository`() = runTest {
        val useCase = SyncCallLogsUseCase(repo)
        useCase()

        coVerify(exactly = 1) { repo.syncWithSystemCallLog() }
    }

    // ════════════════════════════════════════════════════════════
    // GetCallStatisticsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetCallStatistics returns computed statistics`() = runTest {
        coEvery { repo.getCallCountByType(CallType.INCOMING) } returns 10
        coEvery { repo.getCallCountByType(CallType.OUTGOING) } returns 20
        coEvery { repo.getCallCountByType(CallType.MISSED) } returns 3
        coEvery { repo.getTotalDuration(any()) } returns 3600L

        val useCase = GetCallStatisticsUseCase(repo)
        val stats = useCase()

        assertThat(stats.incomingCount).isEqualTo(10)
        assertThat(stats.outgoingCount).isEqualTo(20)
        assertThat(stats.missedCount).isEqualTo(3)
        assertThat(stats.totalDurationSeconds).isEqualTo(3600L)
    }

    // ════════════════════════════════════════════════════════════
    // ClearAllCallLogsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `ClearAllCallLogs delegates to repository`() = runTest {
        val useCase = ClearAllCallLogsUseCase(repo)
        useCase()

        coVerify(exactly = 1) { repo.clearAllCallLogs() }
    }
}
