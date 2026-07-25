package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.MessageTemplateEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Message Templates
 */
@Dao
interface MessageTemplateDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: MessageTemplateEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<MessageTemplateEntity>)
    
    @Update
    suspend fun updateTemplate(template: MessageTemplateEntity)
    
    @Delete
    suspend fun deleteTemplate(template: MessageTemplateEntity)
    
    @Query("SELECT * FROM message_templates ORDER BY use_count DESC, last_used DESC")
    fun getAllTemplates(): Flow<List<MessageTemplateEntity>>
    
    @Query("SELECT * FROM message_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: Long): MessageTemplateEntity?
    
    @Query("SELECT * FROM message_templates WHERE category = :category ORDER BY use_count DESC")
    fun getTemplatesByCategory(category: String): Flow<List<MessageTemplateEntity>>
    
    @Query("SELECT * FROM message_templates WHERE is_favorite = 1 ORDER BY use_count DESC")
    fun getFavoriteTemplates(): Flow<List<MessageTemplateEntity>>
    
    @Query("SELECT * FROM message_templates ORDER BY use_count DESC LIMIT :limit")
    suspend fun getMostUsedTemplates(limit: Int = 5): List<MessageTemplateEntity>
    
    @Query("UPDATE message_templates SET use_count = use_count + 1, last_used = :timestamp WHERE id = :id")
    suspend fun incrementUseCount(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE message_templates SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)
    
    @Query("SELECT COUNT(*) FROM message_templates")
    suspend fun getTemplateCount(): Int
}
