package com.rasmi.purevon.domain.usecase.block

import com.rasmi.purevon.domain.repository.BlockRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for Block/Unblock use cases.
 */
class BlockUseCasesTest {

    private lateinit var repository: BlockRepository

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
    }

    // ════════════════════════════════════════════════════════════
    // GetBlockedNumbersUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetBlockedNumbers returns flow from repository`() = runTest {
        val numbers = listOf("+966500000001", "+966500000002")
        every { repository.getAllBlockedNumbers() } returns flowOf(numbers)

        val useCase = GetBlockedNumbersUseCase(repository)
        val result = useCase().first()

        assertThat(result).isEqualTo(numbers)
        verify(exactly = 1) { repository.getAllBlockedNumbers() }
    }

    @Test
    fun `GetBlockedNumbers returns empty flow when none blocked`() = runTest {
        every { repository.getAllBlockedNumbers() } returns flowOf(emptyList())

        val useCase = GetBlockedNumbersUseCase(repository)
        val result = useCase().first()

        assertThat(result).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // BlockNumberUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `BlockNumber delegates to repository with reason`() = runTest {
        val useCase = BlockNumberUseCase(repository)

        useCase("+966500000000", "Spam caller")

        coVerify(exactly = 1) { repository.blockNumber("+966500000000", "Spam caller") }
    }

    @Test
    fun `BlockNumber passes null reason by default`() = runTest {
        val useCase = BlockNumberUseCase(repository)

        useCase("+966500000000")

        coVerify(exactly = 1) { repository.blockNumber("+966500000000", null) }
    }

    // ════════════════════════════════════════════════════════════
    // UnblockNumberUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `UnblockNumber delegates to repository`() = runTest {
        val useCase = UnblockNumberUseCase(repository)

        useCase("+966500000000")

        coVerify(exactly = 1) { repository.unblockNumber("+966500000000") }
    }

    // ════════════════════════════════════════════════════════════
    // IsNumberBlockedUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `IsNumberBlocked returns true when blocked`() = runTest {
        coEvery { repository.isNumberBlocked("+966500000000") } returns true

        val useCase = IsNumberBlockedUseCase(repository)
        val result = useCase("+966500000000")

        assertThat(result).isTrue()
    }

    @Test
    fun `IsNumberBlocked returns false when not blocked`() = runTest {
        coEvery { repository.isNumberBlocked("+966500000000") } returns false

        val useCase = IsNumberBlockedUseCase(repository)
        val result = useCase("+966500000000")

        assertThat(result).isFalse()
    }

    // ════════════════════════════════════════════════════════════
    // BlockWildcardUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `BlockWildcard delegates pattern to repository`() = runTest {
        val useCase = BlockWildcardUseCase(repository)

        useCase("+966500*", "Block all 0500 numbers")

        coVerify(exactly = 1) { repository.blockWildcard("+966500*", "Block all 0500 numbers") }
    }
}
