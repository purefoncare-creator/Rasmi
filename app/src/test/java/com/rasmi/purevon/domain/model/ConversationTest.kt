package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConversationTest {

    private fun createConversation(
        threadId: Long = 1L,
        phoneNumber: String = "+1234567890",
        contactName: String? = "John",
        lastMessage: String = "Hello",
        lastMessageTimestamp: Long = 1000L,
        lastMessageType: String = "sent",
        unreadCount: Int = 0,
        messageCount: Int = 5,
        isPinned: Boolean = false,
        isMuted: Boolean = false,
        isArchived: Boolean = false,
        isGroup: Boolean = false,
        groupParticipants: List<String> = emptyList()
    ) = Conversation(
        threadId = threadId,
        phoneNumber = phoneNumber,
        contactName = contactName,
        contactPhotoUri = null,
        lastMessage = lastMessage,
        lastMessageTimestamp = lastMessageTimestamp,
        lastMessageType = lastMessageType,
        unreadCount = unreadCount,
        messageCount = messageCount,
        isPinned = isPinned,
        isMuted = isMuted,
        isArchived = isArchived,
        isGroup = isGroup,
        groupParticipants = groupParticipants
    )

    @Test
    fun `id alias returns threadId`() {
        val conv = createConversation(threadId = 42L)
        assertThat(conv.id).isEqualTo(42L)
    }

    @Test
    fun `lastMessageTime alias returns lastMessageTimestamp`() {
        val conv = createConversation(lastMessageTimestamp = 9999L)
        assertThat(conv.lastMessageTime).isEqualTo(9999L)
    }

    @Test
    fun `equality works`() {
        val a = createConversation(threadId = 1L)
        val b = createConversation(threadId = 1L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `inequality on different threadId`() {
        val a = createConversation(threadId = 1L)
        val b = createConversation(threadId = 2L)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `copy modifies only specified fields`() {
        val original = createConversation(unreadCount = 0)
        val copy = original.copy(unreadCount = 5)
        assertThat(copy.unreadCount).isEqualTo(5)
        assertThat(copy.threadId).isEqualTo(original.threadId)
    }

    @Test
    fun `hashCode is consistent`() {
        val conv = createConversation()
        assertThat(conv.hashCode()).isEqualTo(conv.hashCode())
    }

    @Test
    fun `toString contains threadId`() {
        val conv = createConversation(threadId = 123L)
        assertThat(conv.toString()).contains("123")
    }

    @Test
    fun `default isGroup is false`() {
        val conv = Conversation(
            threadId = 1L, phoneNumber = "+1", contactName = null,
            contactPhotoUri = null, lastMessage = "", lastMessageTimestamp = 0L,
            lastMessageType = "sent", unreadCount = 0, messageCount = 0,
            isPinned = false, isMuted = false, isArchived = false
        )
        assertThat(conv.isGroup).isFalse()
    }

    @Test
    fun `default groupParticipants is empty`() {
        val conv = Conversation(
            threadId = 1L, phoneNumber = "+1", contactName = null,
            contactPhotoUri = null, lastMessage = "", lastMessageTimestamp = 0L,
            lastMessageType = "sent", unreadCount = 0, messageCount = 0,
            isPinned = false, isMuted = false, isArchived = false
        )
        assertThat(conv.groupParticipants).isEmpty()
    }

    @Test
    fun `group conversation with participants`() {
        val participants = listOf("+1111", "+2222", "+3333")
        val conv = createConversation(isGroup = true, groupParticipants = participants)
        assertThat(conv.isGroup).isTrue()
        assertThat(conv.groupParticipants).hasSize(3)
    }

    @Test
    fun `isPinned true preserves all other fields`() {
        val conv = createConversation(isPinned = true)
        assertThat(conv.isPinned).isTrue()
        assertThat(conv.isMuted).isFalse()
        assertThat(conv.isArchived).isFalse()
    }

    @Test
    fun `null contactName is valid`() {
        val conv = createConversation(contactName = null)
        assertThat(conv.contactName).isNull()
    }
}
