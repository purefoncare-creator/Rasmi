package com.rasmi.purevon.data.mapper

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.entity.CachedMessageEntity
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.MessageStatus
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class MessageMapperTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    private fun testEntity(
        id: Long = 1L,
        threadId: Long = 100L,
        status: String? = "SENT"
    ) = CachedMessageEntity(
        id = id,
        threadId = threadId,
        phoneNumber = "+966501234567",
        contactName = "Ali",
        body = "Hello",
        timestamp = System.currentTimeMillis(),
        type = 2,
        category = MessageCategory.PERSONAL,
        isRead = true,
        isSent = true,
        isDelivered = false,
        simSlot = null,
        isSpam = false,
        spamScore = 0f,
        isMms = false,
        attachmentUris = emptyList(),
        attachmentTypes = emptyList(),
        status = status,
        isScheduled = false,
        scheduledTime = null,
        scheduleId = null
    )

    @Test
    fun `toDomain maps basic fields`() {
        val entity = testEntity(id = 42L, threadId = 200L)
        val message = entity.toDomain()

        assertThat(message.id).isEqualTo(42L)
        assertThat(message.threadId).isEqualTo(200L)
        assertThat(message.phoneNumber).isEqualTo("+966501234567")
        assertThat(message.contactName).isEqualTo("Ali")
        assertThat(message.body).isEqualTo("Hello")
        assertThat(message.isRead).isTrue()
        assertThat(message.isSent).isTrue()
        assertThat(message.isSpam).isFalse()
    }

    @Test
    fun `toDomain maps status string to MessageStatus`() {
        val entity = testEntity(status = "DELIVERED")
        val message = entity.toDomain()
        assertThat(message.status).isEqualTo(MessageStatus.DELIVERED)
    }

    @Test
    fun `toDomain maps null status`() {
        val entity = testEntity(status = null)
        val message = entity.toDomain()
        assertThat(message.status).isNull()
    }

    @Test
    fun `toDomain maps invalid status to null`() {
        val entity = testEntity(status = "INVALID_STATUS")
        val message = entity.toDomain()
        assertThat(message.status).isNull()
    }

    @Test
    fun `toEntity maps status enum to string`() {
        val message = Message(
            id = 1L, threadId = 1L, phoneNumber = "+1",
            contactName = null, body = null, timestamp = 0L,
            type = 1, category = MessageCategory.PERSONAL,
            isRead = false, isSent = false, isDelivered = false,
            simSlot = null, isSpam = false, spamScore = 0f,
            isMms = false, attachmentUris = emptyList(),
            attachmentTypes = emptyList(), status = MessageStatus.SENDING
        )
        val entity = message.toEntity()
        assertThat(entity.status).isEqualTo("SENDING")
    }

    @Test
    fun `toEntity maps null status to null string`() {
        val message = Message(
            id = 1L, threadId = 1L, phoneNumber = "+1",
            contactName = null, body = null, timestamp = 0L,
            type = 1, category = MessageCategory.PERSONAL,
            isRead = false, isSent = false, isDelivered = false,
            simSlot = null, isSpam = false, spamScore = 0f,
            isMms = false, attachmentUris = emptyList(),
            attachmentTypes = emptyList(), status = null
        )
        val entity = message.toEntity()
        assertThat(entity.status).isNull()
    }

    @Test
    fun `round-trip entity toDomain toEntity preserves core fields`() {
        val original = testEntity(id = 99L, threadId = 300L, status = "FAILED")
        val message = original.toDomain()
        val restored = message.toEntity()

        assertThat(restored.id).isEqualTo(99L)
        assertThat(restored.threadId).isEqualTo(300L)
        assertThat(restored.status).isEqualTo("FAILED")
        assertThat(restored.phoneNumber).isEqualTo("+966501234567")
        assertThat(restored.body).isEqualTo("Hello")
        assertThat(restored.isRead).isTrue()
    }
}
