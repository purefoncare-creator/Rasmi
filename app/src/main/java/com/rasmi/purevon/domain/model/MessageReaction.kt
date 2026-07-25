package com.rasmi.purevon.domain.model

data class MessageReaction(
    val id: Long = 0,
    val messageId: Long,
    val emoji: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String = "self",
    val synced: Boolean = false
)

data class ReactionSummary(
    val messageId: Long,
    val reactions: Map<String, Int> = emptyMap(),
    val myReaction: String? = null,
    val totalCount: Int = 0
)
