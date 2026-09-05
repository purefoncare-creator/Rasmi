package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Message Template Entity
 * Pre-written messages for quick sending
 * 
 * Examples:
 * - "في الطريق 🚗"
 * - "وصلت 👍"
 * - "بكلمك لاحقاً 📞"
 */
@Entity(
    tableName = "message_templates",
    // ✅ FIX M25: declare the indices MIGRATION_6_7 creates so Room schema
    // validation passes for migrated installs and fresh installs match
    indices = [
        Index(value = ["category"]),
        Index(value = ["is_favorite"])
    ]
)
data class MessageTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "title")
    val title: String, // "في الطريق"
    
    @ColumnInfo(name = "content")
    val content: String, // "في الطريق إليك الآن 🚗"
    
    @ColumnInfo(name = "category")
    val category: String = "general", // general, work, personal, etc.
    
    @ColumnInfo(name = "emoji")
    val emoji: String? = null, // 🚗
    
    @ColumnInfo(name = "use_count")
    val useCount: Int = 0,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_used")
    val lastUsed: Long? = null,
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
)

/**
 * Default templates — intentionally empty; user creates their own templates.
 */
object DefaultTemplates {
    val TEMPLATES: List<MessageTemplateEntity> = emptyList()
}
