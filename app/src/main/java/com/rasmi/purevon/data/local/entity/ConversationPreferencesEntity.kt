package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User preferences for conversations (thread_id from system)
 * Stores pinned, muted, archived states
 */
@Entity(tableName = "conversation_preferences")
data class ConversationPreferencesEntity(
    @PrimaryKey
    val systemThreadId: Long,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val customNotificationSound: String? = null,
    val customRingtone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
