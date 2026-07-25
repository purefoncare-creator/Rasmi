package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Local cache of messages to enable instant load/display
 * Synced with system SMS database
 */
@Entity(
    tableName = "cached_messages",
    indices = [
        Index(value = ["threadId"]),
        Index(value = ["timestamp"])
    ]
)
data class CachedMessageEntity(
    @PrimaryKey
    val id: Long,
    val threadId: Long,
    val phoneNumber: String,
    val contactName: String?,
    val body: String?,
    val timestamp: Long,
    val type: Int,
    val category: MessageCategory,
    val isRead: Boolean,
    val isSent: Boolean,
    val isDelivered: Boolean,
    val simSlot: Int?,
    val isSpam: Boolean,
    val spamScore: Float,
    val isMms: Boolean,
    val attachmentUris: List<String>,
    val attachmentTypes: List<String>,
    val status: String?, // Enum converted to String
    val isScheduled: Boolean,
    val scheduledTime: Long?,
    val scheduleId: Long?
)
