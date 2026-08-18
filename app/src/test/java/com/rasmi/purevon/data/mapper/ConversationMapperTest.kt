package com.rasmi.purevon.data.mapper

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.entity.CachedConversationEntity
import com.rasmi.purevon.data.local.entity.ConversationPreferencesEntity
import com.rasmi.purevon.domain.model.ConversationData
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class ConversationMapperTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    private fun conversationData(
        threadId: Long = 1L,
        phone: String = "+966501234567",
        body: String = "Hello",
        timestamp: Long = System.currentTimeMillis(),
        lastMessageType: Int = 1,
        messageCount: Int = 10,
        unreadCount: Int = 3
    ) = ConversationData(
        threadId = threadId,
        phoneNumber = phone,
        lastMessageBody = body,
        lastMessageTimestamp = timestamp,
        lastMessageType = lastMessageType,
        messageCount = messageCount,
        unreadCount = unreadCount
    )

    @Test
    fun `toConversation maps basic fields`() {
        val data = conversationData()
        val conv = ConversationMapper.toConversation(data)

        assertThat(conv.threadId).isEqualTo(1L)
        assertThat(conv.phoneNumber).isEqualTo("+966501234567")
        assertThat(conv.lastMessage).isEqualTo("Hello")
        assertThat(conv.messageCount).isEqualTo(10)
        assertThat(conv.unreadCount).isEqualTo(3)
        assertThat(conv.isPinned).isFalse()
        assertThat(conv.isMuted).isFalse()
        assertThat(conv.isArchived).isFalse()
    }

    @Test
    fun `toConversation maps type 1 to received`() {
        val data = conversationData(lastMessageType = 1)
        val conv = ConversationMapper.toConversation(data)
        assertThat(conv.lastMessageType).isEqualTo("received")
    }

    @Test
    fun `toConversation maps type 2 to sent`() {
        val data = conversationData(lastMessageType = 2)
        val conv = ConversationMapper.toConversation(data)
        assertThat(conv.lastMessageType).isEqualTo("sent")
    }

    @Test
    fun `toConversation applies preferences`() {
        val data = conversationData()
        val prefs = ConversationPreferencesEntity(
            systemThreadId = 1L,
            isPinned = true,
            isMuted = true,
            isArchived = true,
            customNotificationSound = null,
            customRingtone = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val conv = ConversationMapper.toConversation(data, prefs)
        assertThat(conv.isPinned).isTrue()
        assertThat(conv.isMuted).isTrue()
        assertThat(conv.isArchived).isTrue()
    }

    @Test
    fun `toConversations maps list with preferences map`() {
        val data1 = conversationData(threadId = 1L)
        val data2 = conversationData(threadId = 2L, phone = "+966509999999")
        val prefs = ConversationPreferencesEntity(
            systemThreadId = 1L, isPinned = true, isMuted = false,
            isArchived = false, customNotificationSound = null,
            customRingtone = null, createdAt = 0L, updatedAt = 0L
        )

        val result = ConversationMapper.toConversations(
            listOf(data1, data2),
            mapOf(1L to prefs)
        )

        assertThat(result).hasSize(2)
        assertThat(result[0].isPinned).isTrue()
        assertThat(result[1].isPinned).isFalse()
    }
}
