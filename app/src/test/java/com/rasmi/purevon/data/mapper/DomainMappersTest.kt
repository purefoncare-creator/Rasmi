package com.rasmi.purevon.data.mapper

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.entity.MessageTemplateEntity
import com.rasmi.purevon.domain.model.MessageTemplate
import com.rasmi.purevon.domain.model.RepeatInterval
import com.rasmi.purevon.data.local.entity.RepeatInterval as DataRepeatInterval
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class DomainMappersTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    // MessageTemplate mappers
    @Test
    fun `MessageTemplateEntity toDomain maps correctly`() {
        val entity = MessageTemplateEntity(
            id = 1L,
            title = "Quick Reply",
            content = "Thanks!",
            category = "work",
            emoji = "👍",
            useCount = 5,
            createdAt = 1000L,
            lastUsed = 2000L,
            isFavorite = true
        )

        val domain = entity.toDomain()

        assertThat(domain.id).isEqualTo(1L)
        assertThat(domain.title).isEqualTo("Quick Reply")
        assertThat(domain.content).isEqualTo("Thanks!")
        assertThat(domain.category).isEqualTo("work")
        assertThat(domain.emoji).isEqualTo("👍")
        assertThat(domain.useCount).isEqualTo(5)
        assertThat(domain.createdAt).isEqualTo(1000L)
        assertThat(domain.lastUsed).isEqualTo(2000L)
        assertThat(domain.isFavorite).isTrue()
    }

    @Test
    fun `MessageTemplate toEntity maps correctly`() {
        val domain = MessageTemplate(
            id = 2L,
            title = "Greeting",
            content = "Hello!",
            category = "personal",
            emoji = "👋",
            useCount = 10,
            createdAt = 3000L,
            lastUsed = null,
            isFavorite = false
        )

        val entity = domain.toEntity()

        assertThat(entity.id).isEqualTo(2L)
        assertThat(entity.title).isEqualTo("Greeting")
        assertThat(entity.content).isEqualTo("Hello!")
        assertThat(entity.category).isEqualTo("personal")
        assertThat(entity.emoji).isEqualTo("👋")
        assertThat(entity.useCount).isEqualTo(10)
        assertThat(entity.lastUsed).isNull()
        assertThat(entity.isFavorite).isFalse()
    }

    @Test
    fun `MessageTemplate round-trip entity toDomain toEntity`() {
        val original = MessageTemplateEntity(
            id = 3L, title = "T", content = "C",
            category = "gen", emoji = null, useCount = 0,
            createdAt = 0L, lastUsed = null, isFavorite = false
        )
        assertThat(original.toDomain().toEntity()).isEqualTo(original)
    }

    // RepeatInterval mappers
    @Test
    fun `RepeatInterval domain to entity round-trip`() {
        for (interval in RepeatInterval.entries) {
            val entity = interval.toEntity()
            assertThat(entity.toDomain()).isEqualTo(interval)
        }
    }

    @Test
    fun `RepeatInterval NONE maps correctly`() {
        assertThat(RepeatInterval.NONE.toEntity()).isEqualTo(DataRepeatInterval.NONE)
        assertThat(DataRepeatInterval.NONE.toDomain()).isEqualTo(RepeatInterval.NONE)
    }

    @Test
    fun `RepeatInterval DAILY maps correctly`() {
        assertThat(RepeatInterval.DAILY.toEntity()).isEqualTo(DataRepeatInterval.DAILY)
    }

    @Test
    fun `RepeatInterval WEEKLY maps correctly`() {
        assertThat(RepeatInterval.WEEKLY.toEntity()).isEqualTo(DataRepeatInterval.WEEKLY)
    }

    @Test
    fun `RepeatInterval MONTHLY maps correctly`() {
        assertThat(RepeatInterval.MONTHLY.toEntity()).isEqualTo(DataRepeatInterval.MONTHLY)
    }
}
