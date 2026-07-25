package com.rasmi.purevon.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rasmi.purevon.data.local.dao.*
import com.rasmi.purevon.data.local.entity.*

/**
 * Main Room Database for Purevon App
 * Version 8: Added CachedConversationEntity and CachedMessageEntity for faster message loading
 * Version 7: Added MessageTemplateEntity for message templates feature
 * Version 6: Added FTS (Full-Text Search) for fast message searching
 * - Metadata only (references system DB by ID)
 * - App-specific data (spam, blocked, whitelist, scheduled messages, conversation settings, contact notes, templates)
 * - Search index (FTS4 virtual table for fast text search)
 */
@Database(
    entities = [
        // Metadata only (references system DB by ID)
        CallMetadataEntity::class,
        MessageMetadataEntity::class,
        ConversationPreferencesEntity::class,
        
        // App-specific data
        SpamNumberEntity::class,
        BlockedNumberEntity::class,
        WhitelistEntity::class,
        ScheduledMessageEntity::class,
        ConversationSettingsEntity::class,
        ContactNoteEntity::class,
        MessageTemplateEntity::class,
        
        // Search index (FTS4)
        SearchMessageEntity::class,
        
        // ✅ NEW: Full content cache for "Google Messages" speed
        CachedConversationEntity::class,
        CachedMessageEntity::class,
        
        // Message reactions
        MessageReactionEntity::class
    ],
    version = 14, // ✅ FIX: Added multipartStatus column to message_metadata
    exportSchema = true
)
@TypeConverters(
    Converters::class
)
abstract class PurevonDatabase : RoomDatabase() {
    
    // Metadata DAOs
    abstract fun callMetadataDao(): CallMetadataDao
    abstract fun messageMetadataDao(): MessageMetadataDao
    abstract fun conversationPreferencesDao(): ConversationPreferencesDao
    
    // App-specific DAOs
    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun whitelistDao(): WhitelistDao
    
    // ✅ NEW: Cache DAOs
    abstract fun cachedConversationDao(): CachedConversationDao
    abstract fun cachedMessageDao(): CachedMessageDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun conversationSettingsDao(): ConversationSettingsDao
    abstract fun contactNoteDao(): ContactNoteDao
    abstract fun messageTemplateDao(): MessageTemplateDao
    
    // Reaction DAO
    abstract fun messageReactionDao(): MessageReactionDao
    
    // Search DAO
    abstract fun searchDao(): SearchDao
    
    companion object {
        const val DATABASE_NAME = "purevon_db"
    }
}
