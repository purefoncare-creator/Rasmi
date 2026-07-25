package com.rasmi.purevon.domain.usecase.spam

import com.rasmi.purevon.domain.repository.SpamRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for Spam use cases: CheckIfSpam, ReportSpam, MarkAsNotSpam
 */
class SpamUseCasesTest {

    private lateinit var repo: SpamRepository

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
    }

    // ════════════════════════════════════════════════════════════
    // CheckIfSpamUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `CheckIfSpam returns spam result for known spam number`() = runTest {
        coEvery { repo.checkIfSpam("+201000000000") } returns Pair(true, 0.95f)
        coEvery { repo.getSpamScore("+201000000000") } returns 0.95f

        val useCase = CheckIfSpamUseCase(repo)
        val result = useCase("+201000000000")

        assertThat(result.isSpam).isTrue()
        assertThat(result.spamScore).isGreaterThan(0.8f)
        assertThat(result.riskLevel).isEqualTo(SpamRiskLevel.HIGH)
    }

    @Test
    fun `CheckIfSpam returns safe for clean number`() = runTest {
        coEvery { repo.checkIfSpam("+201234567890") } returns Pair(false, 0.1f)
        coEvery { repo.getSpamScore("+201234567890") } returns 0.1f

        val useCase = CheckIfSpamUseCase(repo)
        val result = useCase("+201234567890")

        assertThat(result.isSpam).isFalse()
        assertThat(result.riskLevel).isEqualTo(SpamRiskLevel.SAFE)
    }

    // ════════════════════════════════════════════════════════════
    // ReportSpamUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `ReportSpam delegates to repository`() = runTest {
        val useCase = ReportSpamUseCase(repo)
        useCase("+201000000000")

        coVerify(exactly = 1) { repo.reportSpam("+201000000000") }
    }

    // ════════════════════════════════════════════════════════════
    // MarkAsNotSpamUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `MarkAsNotSpam delegates to repository`() = runTest {
        val useCase = MarkAsNotSpamUseCase(repo)
        useCase("+201000000000")

        coVerify(exactly = 1) { repo.markAsNotSpam("+201000000000") }
    }
}
