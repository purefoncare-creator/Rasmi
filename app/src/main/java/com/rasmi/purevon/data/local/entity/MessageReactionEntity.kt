package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

/**
 * Message Reaction Entity
 * Stores emoji reactions for messages
 * 
 * Inspired by QKSMS + Modern messaging apps (WhatsApp, Telegram)
 * 
 * Features:
 * - Multiple reactions per message
 * - User identification (for group chats in future)
 * - Timestamp for reaction
 * - Sync capability
 */
@Entity(
    tableName = "message_reactions",
    indices = [
        Index(value = ["message_id"]),
        Index(value = ["emoji"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageReactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "message_id")
    val messageId: Long,
    
    @ColumnInfo(name = "emoji")
    val emoji: String, // ❤️, 👍, 😂, 😮, 😢, 🔥
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "user_id")
    val userId: String = "me", // For future group chat support
    
    @ColumnInfo(name = "synced")
    val synced: Boolean = false // For future cloud sync
)

/**
 * Aggregated reactions for a message
 */
data class MessageReactionSummary(
    val messageId: Long,
    val reactions: Map<String, Int>, // emoji -> count
    val myReaction: String? = null, // Current user's reaction
    val totalCount: Int
)

/**
 * Available reaction emojis
 */
object ReactionEmojis {
    const val HEART = "❤️"
    const val THUMBS_UP = "👍"
    const val LAUGH = "😂"
    const val WOW = "😮"
    const val SAD = "😢"
    const val FIRE = "🔥"
    const val LIKE = "👌"
    const val LOVE = "😍"
    
    val ALL = listOf(
        HEART, THUMBS_UP, LAUGH, WOW, SAD, FIRE, LIKE, LOVE
    )
    
    // Quick reactions (most common)
    val QUICK_REACTIONS = listOf(
        HEART, THUMBS_UP, LAUGH, FIRE
    )
}
