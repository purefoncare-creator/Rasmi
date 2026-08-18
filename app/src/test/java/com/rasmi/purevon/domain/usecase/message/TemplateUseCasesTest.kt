package com.rasmi.purevon.domain.usecase.message

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.domain.model.MessageTemplate
import com.rasmi.purevon.domain.repository.MessageRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TemplateUseCasesTest {

    private lateinit var repository: MessageRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        repository = mockk()
    }

    @Test
    fun `GetAllTemplatesUseCase returns flow`() = runTest {
        val templates = listOf(
            MessageTemplate(id = 1, title = "Hello", content = "Hi there!")
        )
        coEvery { repository.getAllTemplates() } returns flowOf(templates)

        val useCase = GetAllTemplatesUseCase(repository)
        val result = useCase().first()
        assertThat(result).hasSize(1)
        assertThat(result[0].title).isEqualTo("Hello")
    }

    @Test
    fun `AddTemplateUseCase calls repository`() = runTest {
        val template = MessageTemplate(title = "Test", content = "Test content")
        coEvery { repository.addTemplate(template) } just Runs

        val useCase = AddTemplateUseCase(repository)
        useCase(template)
        coVerify { repository.addTemplate(template) }
    }

    @Test
    fun `UpdateTemplateUsageUseCase calls repository`() = runTest {
        coEvery { repository.updateTemplateUsage(42L) } just Runs

        val useCase = UpdateTemplateUsageUseCase(repository)
        useCase(42L)
        coVerify { repository.updateTemplateUsage(42L) }
    }

    @Test
    fun `DeleteTemplateUseCase calls repository`() = runTest {
        val template = MessageTemplate(id = 1, title = "Del", content = "Delete me")
        coEvery { repository.deleteTemplate(template) } just Runs

        val useCase = DeleteTemplateUseCase(repository)
        useCase(template)
        coVerify { repository.deleteTemplate(template) }
    }
}
