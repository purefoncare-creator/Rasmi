package com.rasmi.purevon.domain.model

/**
 * Internal data structure for building conversations from raw message data
 * Used during conversation aggregation before creating full Conversation objects
 */
data class ConversationData(
    val threadId: Long,
    val phoneNumber: String,
    val lastMessageBody: String?,
    val lastMessageTimestamp: Long,
    val lastMessageType: Int,
    val messageCount: Int,
    val unreadCount: Int
)


