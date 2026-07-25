package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.rasmi.purevon.data.local.Converters

/**
 * Local cache of conversations to enable instant load/display
 * Synced with system SMS database
 */
@Entity(
    tableName = "cached_conversations",
    indices = [
        Index("lastMessageTimestamp"),
        Index("isArchived"),
        Index("isPinned")
    ]
)
data class CachedConversationEntity(
    @PrimaryKey
    val threadId: Long,
    val phoneNumber: String,
    val contactName: String?,
    val contactPhotoUri: String?,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val lastMessageType: String,
    val unreadCount: Int,
    val messageCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isArchived: Boolean,
    val isGroup: Boolean,
    // Using simple string storage for list via helper or just ignore for now if converters not ready
    // Assuming Converters class handles List<String>
    val groupParticipants: List<String>
)
