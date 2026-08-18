package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageDomainTest {

    private fun createMessage(
        id: Long = 1L,
        threadId: Long = 100L,
        phoneNumber: String = "+1234567890",
        contactName: String? = "Alice",
        body: String? = "Hello",
        timestamp: Long = 1000L,
        type: Int = 1,
        category: MessageCategory = MessageCategory.PERSONAL,
        isRead: Boolean = true,
        isSent: Boolean = false,
        isDelivered: Boolean = false,
        simSlot: Int? = 1,
        isSpam: Boolean = false,
        spamScore: Float = 0f,
        isMms: Boolean = false,
        attachmentUris: List<String> = emptyList(),
        attachmentTypes: List<String> = emptyList(),
        status: MessageStatus? = null,
        isScheduled: Boolean = false,
        scheduledTime: Long? = null,
        scheduleId: Long? = null,
        isStarred: Boolean = false
    ) = Message(
        id = id, threadId = threadId, phoneNumber = phoneNumber,
        contactName = contactName, body = body, timestamp = timestamp,
        type = type, category = category, isRead = isRead,
        isSent = isSent, isDelivered = isDelivered, simSlot = simSlot,
        isSpam = isSpam, spamScore = spamScore, isMms = isMms,
        attachmentUris = attachmentUris, attachmentTypes = attachmentTypes,
        status = status, isScheduled = isScheduled, scheduledTime = scheduledTime,
        scheduleId = scheduleId, isStarred = isStarred
    )

    @Test
    fun `address alias returns phoneNumber`() {
        val msg = createMessage(phoneNumber = "+19998887777")
        assertThat(msg.address).isEqualTo("+19998887777")
    }

    @Test
    fun `conversationId alias returns threadId`() {
        val msg = createMessage(threadId = 555L)
        assertThat(msg.conversationId).isEqualTo(555L)
    }

    @Test
    fun `equality works`() {
        val a = createMessage(id = 1L)
        val b = createMessage(id = 1L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `inequality on different id`() {
        val a = createMessage(id = 1L)
        val b = createMessage(id = 2L)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val original = createMessage(body = "Original", isRead = false)
        val copy = original.copy(body = "Modified")
        assertThat(copy.body).isEqualTo("Modified")
        assertThat(copy.isRead).isFalse()
    }

    @Test
    fun `default isScheduled is false`() {
        val msg = createMessage()
        assertThat(msg.isScheduled).isFalse()
    }

    @Test
    fun `default isStarred is false`() {
        val msg = createMessage()
        assertThat(msg.isStarred).isFalse()
    }

    @Test
    fun `scheduled message with scheduledTime`() {
        val msg = createMessage(isScheduled = true, scheduledTime = 5000L, scheduleId = 42L)
        assertThat(msg.isScheduled).isTrue()
        assertThat(msg.scheduledTime).isEqualTo(5000L)
        assertThat(msg.scheduleId).isEqualTo(42L)
    }

    @Test
    fun `message with attachments`() {
        val uris = listOf("content://file1", "content://file2")
        val types = listOf("image/jpeg", "video/mp4")
        val msg = createMessage(isMms = true, attachmentUris = uris, attachmentTypes = types)
        assertThat(msg.attachmentUris).hasSize(2)
        assertThat(msg.attachmentTypes).containsExactly("image/jpeg", "video/mp4")
    }

    @Test
    fun `null body is valid`() {
        val msg = createMessage(body = null)
        assertThat(msg.body).isNull()
    }

    @Test
    fun `null contactName is valid`() {
        val msg = createMessage(contactName = null)
        assertThat(msg.contactName).isNull()
    }

    @Test
    fun `null simSlot is valid`() {
        val msg = createMessage(simSlot = null)
        assertThat(msg.simSlot).isNull()
    }

    @Test
    fun `status enum values`() {
        assertThat(MessageStatus.SENDING).isNotNull()
        assertThat(MessageStatus.SENT).isNotNull()
        assertThat(MessageStatus.DELIVERED).isNotNull()
        assertThat(MessageStatus.FAILED).isNotNull()
    }

    @Test
    fun `message with every status`() {
        for (status in MessageStatus.values()) {
            val msg = createMessage(status = status)
            assertThat(msg.status).isEqualTo(status)
        }
    }

    @Test
    fun `spam message detection`() {
        val msg = createMessage(isSpam = true, spamScore = 0.95f)
        assertThat(msg.isSpam).isTrue()
        assertThat(msg.spamScore).isWithin(0.01f).of(0.95f)
    }

    @Test
    fun `hash is consistent`() {
        val msg = createMessage()
        assertThat(msg.hashCode()).isEqualTo(msg.hashCode())
    }

    @Test
    fun `toString contains id`() {
        val msg = createMessage(id = 42L)
        assertThat(msg.toString()).contains("42")
    }
}
