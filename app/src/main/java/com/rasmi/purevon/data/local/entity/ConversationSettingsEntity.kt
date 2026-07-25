package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for conversation settings (pin, mute, archive)
 */
@Entity(tableName = "conversation_settings")
data class ConversationSettingsEntity(
    @PrimaryKey
    val threadId: Long,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val mutedUntil: Long? = null, // Timestamp when mute expires
    val pinnedAt: Long? = null, // Timestamp when pinned
    val archivedAt: Long? = null // Timestamp when archived
)
