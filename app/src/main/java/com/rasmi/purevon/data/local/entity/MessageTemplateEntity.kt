package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
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
@Entity(tableName = "message_templates")
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
