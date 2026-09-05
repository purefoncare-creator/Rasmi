package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for conversation settings (pin, mute, archive)
 */
@Entity(
    tableName = "conversation_settings",
    // ✅ FIX M25: declare the indices MIGRATION_2_3 creates so Room schema
    // validation passes for migrated installs and fresh installs match
    indices = [
        Index(value = ["isPinned"]),
        Index(value = ["isMuted"]),
        Index(value = ["isArchived"])
    ]
)
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
