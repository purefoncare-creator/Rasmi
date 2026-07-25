package com.rasmi.purevon.domain.model

/**
 * Domain model for Conversation (Message Thread)
 */
data class Conversation(
    val threadId: Long,
    val phoneNumber: String,
    val contactName: String?,
    val contactPhotoUri: String?,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val lastMessageType: String, // "sent" or "received"
    val unreadCount: Int,
    val messageCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isArchived: Boolean,
    val isGroup: Boolean = false,
    val groupParticipants: List<String> = emptyList()
) {
    // Aliases for backward compatibility
    val id: Long get() = threadId
    val lastMessageTime: Long get() = lastMessageTimestamp
}
