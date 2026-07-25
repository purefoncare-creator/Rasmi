package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Metadata for messages (not the message data itself - read from system)
 * Stores spam scores, categories, and custom tags
 */
@Serializable
@Entity(tableName = "message_metadata")
data class MessageMetadataEntity(
    @PrimaryKey
    val systemMessageId: Long,
    val spamScore: Float = 0f,
    val category: MessageCategory = MessageCategory.PERSONAL,
    val customTag: String? = null,
    val multipartStatus: MultipartMessageStatus? = null,
    val isStarred: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
