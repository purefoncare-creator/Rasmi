package com.rasmi.purevon.domain.usecase.block

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.domain.repository.BlockRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ShouldBlockUseCasesTest {

    private lateinit var blockRepository: BlockRepository
    private lateinit var shouldBlockCallUseCase: ShouldBlockCallUseCase
    private lateinit var shouldBlockMessageUseCase: ShouldBlockMessageUseCase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        blockRepository = mockk()
        shouldBlockCallUseCase = ShouldBlockCallUseCase(blockRepository)
        shouldBlockMessageUseCase = ShouldBlockMessageUseCase(blockRepository)
    }

    @Test
    fun `ShouldBlockCallUseCase returns true when blocked`() = runTest {
        coEvery { blockRepository.shouldBlockCall("+966501234567") } returns true
        assertThat(shouldBlockCallUseCase("+966501234567")).isTrue()
    }

    @Test
    fun `ShouldBlockCallUseCase returns false when not blocked`() = runTest {
        coEvery { blockRepository.shouldBlockCall("+966501234567") } returns false
        assertThat(shouldBlockCallUseCase("+966501234567")).isFalse()
    }

    @Test
    fun `ShouldBlockMessageUseCase returns true when blocked`() = runTest {
        coEvery { blockRepository.shouldBlockMessage("+966509999999") } returns true
        assertThat(shouldBlockMessageUseCase("+966509999999")).isTrue()
    }

    @Test
    fun `ShouldBlockMessageUseCase returns false when not blocked`() = runTest {
        coEvery { blockRepository.shouldBlockMessage("+966509999999") } returns false
        assertThat(shouldBlockMessageUseCase("+966509999999")).isFalse()
    }
}
