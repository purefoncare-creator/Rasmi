package com.rasmi.purevon.data.repository

import android.provider.Telephony
import com.rasmi.purevon.data.local.entity.CachedMessageEntity
import com.rasmi.purevon.domain.model.MessageCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression test for the metadata-preservation fix in [MessageSyncDelegate.buildSmsEntity]:
 * re-syncing an SMS from the system provider must not wipe out app-only fields
 * (category/spam/scheduling) that the system provider has no concept of.
 */
class MessageSyncDelegateTest {

    private val messageId = 101L
    private val threadId = 55L

    private fun existingEntity() = CachedMessageEntity(
        id = messageId,
        threadId = threadId,
        phoneNumber = "+966500000000",
        contactName = null,
        body = "old body",
        timestamp = 1_699_000_000_000L,
        type = Telephony.Sms.MESSAGE_TYPE_INBOX,
        category = MessageCategory.SPAM,
        isRead = false,
        isSent = false,
        isDelivered = true,
        simSlot = null,
        isSpam = true,
        spamScore = 0.87f,
        isMms = false,
        attachmentUris = emptyList(),
        attachmentTypes = emptyList(),
        status = "FAILED",
        isScheduled = true,
        scheduledTime = 1_800_000_000_000L,
        scheduleId = 42L
    )

    @Test
    fun `preserves existing category, spam and scheduling metadata`() {
        val saved = MessageSyncDelegate.buildSmsEntity(
            id = messageId,
            threadId = threadId,
            address = "+966500000000",
            contactName = null,
            body = "hello",
            date = 1_700_000_000_000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            isRead = true,
            simSlot = null,
            existing = existingEntity()
        )

        assertThat(saved.category).isEqualTo(MessageCategory.SPAM)
        assertThat(saved.isSpam).isTrue()
        assertThat(saved.spamScore).isEqualTo(0.87f)
        assertThat(saved.status).isEqualTo("FAILED")
        assertThat(saved.isScheduled).isTrue()
        assertThat(saved.scheduledTime).isEqualTo(1_800_000_000_000L)
        assertThat(saved.scheduleId).isEqualTo(42L)
        // Fields that DO come from the system provider must still be refreshed
        assertThat(saved.body).isEqualTo("hello")
        assertThat(saved.isRead).isTrue()
    }

    @Test
    fun `applies defaults when no cached row exists yet`() {
        val saved = MessageSyncDelegate.buildSmsEntity(
            id = messageId,
            threadId = threadId,
            address = "+966500000000",
            contactName = "Ali",
            body = "hello",
            date = 1_700_000_000_000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            isRead = true,
            simSlot = 0,
            existing = null
        )

        assertThat(saved.category).isEqualTo(MessageCategory.PERSONAL)
        assertThat(saved.isSpam).isFalse()
        assertThat(saved.spamScore).isEqualTo(0f)
        assertThat(saved.status).isNull()
        assertThat(saved.isScheduled).isFalse()
        assertThat(saved.scheduledTime).isNull()
        assertThat(saved.scheduleId).isNull()
        assertThat(saved.contactName).isEqualTo("Ali")
        assertThat(saved.simSlot).isEqualTo(0)
    }

    @Test
    fun `isSent reflects sent message type regardless of cached metadata`() {
        val saved = MessageSyncDelegate.buildSmsEntity(
            id = messageId,
            threadId = threadId,
            address = "+966500000000",
            contactName = null,
            body = "outgoing",
            date = 1_700_000_000_000L,
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            isRead = true,
            simSlot = null,
            existing = existingEntity()
        )

        assertThat(saved.isSent).isTrue()
    }
}
