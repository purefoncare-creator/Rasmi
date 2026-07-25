package com.rasmi.purevon.domain.model

/**
 * Domain model for message templates.
 * Decoupled from data layer (Room entity).
 */
data class MessageTemplate(
    val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "general",
    val emoji: String? = null,
    val useCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null,
    val isFavorite: Boolean = false
)
