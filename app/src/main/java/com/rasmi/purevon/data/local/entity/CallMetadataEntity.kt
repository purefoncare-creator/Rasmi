package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Metadata for calls (not the call data itself - read from system)
 * Stores custom notes, spam scores, and labels
 */
@Serializable
@Entity(tableName = "call_metadata")
data class CallMetadataEntity(
    @PrimaryKey
    val systemCallLogId: Long,
    val notes: String? = null,
    val customLabel: String? = null,
    val spamScore: Float = 0f,
    val isMarkedAsSpam: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
