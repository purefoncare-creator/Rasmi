package com.rasmi.purevon.data.repository

import android.provider.Telephony
import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.CachedMessageDao
import com.rasmi.purevon.data.local.dao.MessageMetadataDao
import com.rasmi.purevon.data.local.entity.CachedMessageEntity
import com.rasmi.purevon.domain.model.MessageCategory
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Extended tests for [MessageSyncDelegate] — focusing on the invalidateAllCaches fix (M-02)
 * and buildSmsEntity edge cases.
 */
class MessageSyncDelegateExtendedTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
    }

    // ════════════════════════════════════════════════════════════
    // buildSmsEntity — edge cases
    // ════════════════════════════════════════════════════════════

    @Test
    fun `buildSmsEntity with null body`() {
        val entity = MessageSyncDelegate.buildSmsEntity(
            id = 1L,
            threadId = 10L,
            address = "+966500000000",
            contactName = null,
            body = null,
            date = 1_000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            isRead = false,
            simSlot = null,
            existing = null
        )

        assertThat(entity.body).isNull()
    }

    @Test
    fun `buildSmsEntity preserves existing attachment data`() {
        val existing = CachedMessageEntity(
            id = 1L, threadId = 10L, phoneNumber = "+966500000000",
            contactName = null, body = "old", timestamp = 1_000L,
            type = 1, category = MessageCategory.PERSONAL,
            isRead = false, isSent = false, isDelivered = true,
            simSlot = null, isSpam = false, spamScore = 0f,
            isMms = true,
            attachmentUris = listOf("content://mms/part/1"),
            attachmentTypes = listOf("image/jpeg"),
            status = null, isScheduled = false,
            scheduledTime = null, scheduleId = null
        )

        val entity = MessageSyncDelegate.buildSmsEntity(
            id = 1L, threadId = 10L, address = "+966500000000",
            contactName = "Ali", body = "new", date = 2_000L,
            type = Telephony.Sms.MESSAGE_TYPE_SENT, isRead = true,
            simSlot = 0, existing = existing
        )

        assertThat(entity.isMms).isTrue()
        assertThat(entity.attachmentUris).containsExactly("content://mms/part/1")
        assertThat(entity.attachmentTypes).containsExactly("image/jpeg")
    }

    @Test
    fun `buildSmsEntity sets isDelivered to true by default`() {
        val entity = MessageSyncDelegate.buildSmsEntity(
            id = 1L, threadId = 10L, address = "+966500000000",
            contactName = null, body = "test", date = 1_000L,
            type = 1, isRead = false, simSlot = null, existing = null
        )

        assertThat(entity.isDelivered).isTrue()
    }

    @Test
    fun `buildSmsEntity preserves existing isDelivered when false`() {
        val existing = CachedMessageEntity(
            id = 1L, threadId = 10L, phoneNumber = "+966500000000",
            contactName = null, body = "old", timestamp = 1_000L,
            type = 1, category = MessageCategory.PERSONAL,
            isRead = false, isSent = false, isDelivered = false,
            simSlot = null, isSpam = false, spamScore = 0f,
            isMms = false, attachmentUris = emptyList(),
            attachmentTypes = emptyList(), status = null,
            isScheduled = false, scheduledTime = null, scheduleId = null
        )

        val entity = MessageSyncDelegate.buildSmsEntity(
            id = 1L, threadId = 10L, address = "+966500000000",
            contactName = null, body = "new", date = 2_000L,
            type = 1, isRead = true, simSlot = null, existing = existing
        )

        assertThat(entity.isDelivered).isFalse()
    }

    // ════════════════════════════════════════════════════════════
    // buildSmsEntity — type handling
    // ════════════════════════════════════════════════════════════

    @Test
    fun `buildSmsEntity sets isSent true for sent type`() {
        val entity = MessageSyncDelegate.buildSmsEntity(
            id = 1L, threadId = 10L, address = "+966500000000",
            contactName = null, body = "test", date = 1_000L,
            type = Telephony.Sms.MESSAGE_TYPE_SENT, isRead = true,
            simSlot = null, existing = null
        )

        assertThat(entity.isSent).isTrue()
    }

    @Test
    fun `buildSmsEntity sets isSent false for inbox type`() {
        val entity = MessageSyncDelegate.buildSmsEntity(
            id = 1L, threadId = 10L, address = "+966500000000",
            contactName = null, body = "test", date = 1_000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX, isRead = false,
            simSlot = null, existing = null
        )

        assertThat(entity.isSent).isFalse()
    }

    // ════════════════════════════════════════════════════════════
    // buildSmsEntity — spam detection preservation
    // ════════════════════════════════════════════════════════════

    @Test
    fun `buildSmsEntity preserves spam score from existing`() {
        val existing = CachedMessageEntity(
            id = 1L, threadId = 10L, phoneNumber = "+966500000000",
            contactName = null, body = "old", timestamp = 1_000L,
            type = 1, category = MessageCategory.SPAM,
            isRead = false, isSent = false, isDelivered = true,
            simSlot = null, isSpam = true, spamScore = 0.95f,
            isMms = false, attachmentUris = emptyList(),
            attachmentTypes = emptyList(), status = null,
            isScheduled = false, scheduledTime = null, scheduleId = null
        )

        val entity = MessageSyncDelegate.buildSmsEntity(
            id = 1L, threadId = 10L, address = "+966500000000",
            contactName = null, body = "new", date = 2_000L,
            type = 1, isRead = true, simSlot = null, existing = existing
        )

        assertThat(entity.isSpam).isTrue()
        assertThat(entity.spamScore).isEqualTo(0.95f)
        assertThat(entity.category).isEqualTo(MessageCategory.SPAM)
    }
}
