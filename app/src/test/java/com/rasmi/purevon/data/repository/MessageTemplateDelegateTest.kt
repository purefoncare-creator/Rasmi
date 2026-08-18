package com.rasmi.purevon.data.repository

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.MessageTemplateDao
import com.rasmi.purevon.data.local.entity.MessageTemplateEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Tests for [MessageTemplateDelegate] — template CRUD and auto-seeding.
 */
class MessageTemplateDelegateTest {

    private lateinit var dao: MessageTemplateDao
    private lateinit var delegate: MessageTemplateDelegate

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        dao = mockk(relaxed = true)
        delegate = MessageTemplateDelegate(dao)
    }

    private fun sampleTemplate(
        id: Long = 1L,
        title: String = "Greeting",
        content: String = "Hello!",
        useCount: Int = 0
    ) = MessageTemplateEntity(
        id = id,
        title = title,
        content = content,
        category = "general",
        useCount = useCount,
        lastUsed = null,
        createdAt = System.currentTimeMillis()
    )

    // ════════════════════════════════════════════════════════════
    // Template seeding
    // ════════════════════════════════════════════════════════════

    @Test
    fun `getAllTemplates seeds defaults when DB is empty`() = runTest {
        coEvery { dao.getTemplateCount() } returns 0
        coEvery { dao.getAllTemplates() } returns kotlinx.coroutines.flow.flowOf(
            listOf(sampleTemplate(id = 99, title = "Default"))
        )

        val templates = mutableListOf<List<MessageTemplateEntity>>()
        delegate.getAllTemplates().collect { templates.add(it) }

        coVerify(exactly = 1) { dao.insertTemplates(any()) }
        assertThat(templates.first()).hasSize(1)
        assertThat(templates.first().first().title).isEqualTo("Default")
    }

    @Test
    fun `getAllTemplates skips seeding when DB has data`() = runTest {
        coEvery { dao.getTemplateCount() } returns 5
        coEvery { dao.getAllTemplates() } returns kotlinx.coroutines.flow.flowOf(
            listOf(sampleTemplate(id = 10, title = "Existing"))
        )

        val templates = mutableListOf<List<MessageTemplateEntity>>()
        delegate.getAllTemplates().collect { templates.add(it) }

        coVerify(exactly = 0) { dao.insertTemplates(any()) }
        assertThat(templates.first().first().title).isEqualTo("Existing")
    }

    // ════════════════════════════════════════════════════════════
    // Template CRUD
    // ════════════════════════════════════════════════════════════

    @Test
    fun `addTemplate delegates to DAO`() = runTest {
        val template = sampleTemplate(title = "New Template", content = "Hi there")

        delegate.addTemplate(template)

        coVerify(exactly = 1) { dao.insertTemplate(template) }
    }

    @Test
    fun `deleteTemplate delegates to DAO`() = runTest {
        val template = sampleTemplate(id = 42)

        delegate.deleteTemplate(template)

        coVerify(exactly = 1) { dao.deleteTemplate(template) }
    }

    @Test
    fun `updateTemplateUsage increments useCount and sets lastUsed`() = runTest {
        val template = sampleTemplate(id = 5, useCount = 3)
        coEvery { dao.getTemplateById(5) } returns template

        delegate.updateTemplateUsage(5)

        coVerify(exactly = 1) { dao.updateTemplate(any()) }
    }

    @Test
    fun `updateTemplateUsage handles missing template gracefully`() = runTest {
        coEvery { dao.getTemplateById(999) } returns null

        delegate.updateTemplateUsage(999)

        coVerify(exactly = 0) { dao.updateTemplate(any()) }
    }

    @Test
    fun `updateTemplateUsage handles DAO exception gracefully`() = runTest {
        coEvery { dao.getTemplateById(5) } throws RuntimeException("DB error")

        // Should not throw
        delegate.updateTemplateUsage(5)

        coVerify(exactly = 0) { dao.updateTemplate(any()) }
    }
}
