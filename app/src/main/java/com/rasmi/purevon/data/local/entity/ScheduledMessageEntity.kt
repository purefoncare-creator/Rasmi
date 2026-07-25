package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Repeat interval options for scheduled messages
 */
enum class RepeatInterval {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}

/**
 * Scheduled messages to be sent later.
 * Supports optional repeat via [repeatInterval].
 */
@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipient: String,
    val messageBody: String,
    val scheduledTime: Long,
    val status: ScheduleStatus = ScheduleStatus.PENDING,
    val simSlot: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Repeat mode — NONE means one-shot (default) */
    val repeatInterval: RepeatInterval = RepeatInterval.NONE,
    /** Attachment URIs for MMS — empty list means SMS only */
    val attachmentUris: List<String> = emptyList()
)

enum class ScheduleStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELLED
}
