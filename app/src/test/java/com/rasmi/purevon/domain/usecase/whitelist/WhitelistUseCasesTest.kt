package com.rasmi.purevon.domain.usecase.whitelist

import com.rasmi.purevon.domain.model.WhitelistNumber
import com.rasmi.purevon.domain.repository.WhitelistRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for Whitelist use cases.
 */
class WhitelistUseCasesTest {

    private lateinit var repository: WhitelistRepository

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
    }

    // ════════════════════════════════════════════════════════════
    // GetWhitelistNumbersUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetWhitelistNumbers returns flow from repository`() = runTest {
        val entities = listOf(
            WhitelistNumber(id = 1, phoneNumber = "+966500000001", contactName = "Ahmed"),
            WhitelistNumber(id = 2, phoneNumber = "+966500000002", contactName = null)
        )
        every { repository.getAllWhitelistNumbers() } returns flowOf(entities)

        val useCase = GetWhitelistNumbersUseCase(repository)
        val result = useCase().first()

        assertThat(result).hasSize(2)
        assertThat(result[0].phoneNumber).isEqualTo("+966500000001")
        assertThat(result[1].contactName).isNull()
    }

    @Test
    fun `GetWhitelistNumbers returns empty list when none`() = runTest {
        every { repository.getAllWhitelistNumbers() } returns flowOf(emptyList())

        val useCase = GetWhitelistNumbersUseCase(repository)
        val result = useCase().first()

        assertThat(result).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // AddToWhitelistUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `AddToWhitelist delegates with contact name`() = runTest {
        val useCase = AddToWhitelistUseCase(repository)

        useCase("+966500000000", "Ahmed")

        coVerify(exactly = 1) { repository.addToWhitelist("+966500000000", "Ahmed") }
    }

    @Test
    fun `AddToWhitelist delegates with null contact name`() = runTest {
        val useCase = AddToWhitelistUseCase(repository)

        useCase("+966500000000", null)

        coVerify(exactly = 1) { repository.addToWhitelist("+966500000000", null) }
    }

    // ════════════════════════════════════════════════════════════
    // RemoveFromWhitelistUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `RemoveFromWhitelist delegates to repository`() = runTest {
        val useCase = RemoveFromWhitelistUseCase(repository)

        useCase("+966500000000")

        coVerify(exactly = 1) { repository.removeFromWhitelist("+966500000000") }
    }
}
