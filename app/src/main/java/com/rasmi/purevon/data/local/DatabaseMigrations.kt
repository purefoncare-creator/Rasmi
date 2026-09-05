package com.rasmi.purevon.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 1 to 2
 * Removes full data storage and keeps only metadata
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop old tables (full data duplication)
        db.execSQL("DROP TABLE IF EXISTS call_logs")
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS contacts")
        db.execSQL("DROP TABLE IF EXISTS conversations")
        
        // Create new metadata tables
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS call_metadata (
                systemCallLogId INTEGER PRIMARY KEY NOT NULL,
                notes TEXT,
                customLabel TEXT,
                spamScore REAL NOT NULL DEFAULT 0,
                isMarkedAsSpam INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message_metadata (
                systemMessageId INTEGER PRIMARY KEY NOT NULL,
                spamScore REAL NOT NULL DEFAULT 0,
                category TEXT NOT NULL DEFAULT 'PERSONAL',
                customTag TEXT,
                isStarred INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS conversation_preferences (
                systemThreadId INTEGER PRIMARY KEY NOT NULL,
                isPinned INTEGER NOT NULL DEFAULT 0,
                isMuted INTEGER NOT NULL DEFAULT 0,
                isArchived INTEGER NOT NULL DEFAULT 0,
                customNotificationSound TEXT,
                customRingtone TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS scheduled_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recipient TEXT NOT NULL,
                messageBody TEXT NOT NULL,
                scheduledTime INTEGER NOT NULL,
                status TEXT NOT NULL,
                simSlot INTEGER,
                createdAt INTEGER NOT NULL
            )
        """)
        
        // Keep spam_numbers and blocked_numbers as they are app-specific
        // No changes needed for these tables
    }
}

/**
 * Migration from version 2 to 3
 * Adds conversation_settings table for pin/mute/archive features
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create conversation_settings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS conversation_settings (
                threadId INTEGER PRIMARY KEY NOT NULL,
                isPinned INTEGER NOT NULL DEFAULT 0,
                isMuted INTEGER NOT NULL DEFAULT 0,
                isArchived INTEGER NOT NULL DEFAULT 0,
                mutedUntil INTEGER,
                pinnedAt INTEGER,
                archivedAt INTEGER
            )
        """)
        
        // Create indexes for better query performance
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_conversation_settings_isPinned 
            ON conversation_settings(isPinned)
        """)
        
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_conversation_settings_isMuted 
            ON conversation_settings(isMuted)
        """)
        
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_conversation_settings_isArchived 
            ON conversation_settings(isArchived)
        """)
    }
}

/**
 * Migration from version 3 to 4
 * Adds whitelist table for call blocking bypass
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create whitelist table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS whitelist (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                phoneNumber TEXT NOT NULL,
                contactName TEXT,
                reason TEXT,
                createdAt INTEGER NOT NULL
            )
        """)
        
        // Create index for better query performance
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_whitelist_phoneNumber 
            ON whitelist(phoneNumber)
        """)
    }
}

/**
 * Migration from version 4 to 5
 * Adds contact_notes table for storing call notes
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create contact_notes table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                phoneNumber TEXT NOT NULL,
                note TEXT NOT NULL,
                callDuration INTEGER NOT NULL DEFAULT 0,
                isIncoming INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL
            )
        """)
        
        // Create index for better query performance
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_contact_notes_phoneNumber 
            ON contact_notes(phoneNumber)
        """)
    }
}

/**
 * Migration from version 5 to 6
 * Adds FTS4 (Full-Text Search) virtual table for fast message searching
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create FTS4 virtual table for message search
        // FTS4 automatically creates 'rowid' column
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS search_messages_fts 
            USING fts4(
                address TEXT,
                contactName TEXT,
                body TEXT,
                threadId INTEGER,
                date INTEGER,
                type INTEGER
            )
        """)
    }
}

/**
 * Migration from version 6 to 7
 * Adds message_templates table for quick reply templates
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create table for message templates
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_templates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                category TEXT NOT NULL,
                emoji TEXT,
                use_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                last_used INTEGER,
                is_favorite INTEGER NOT NULL DEFAULT 0
            )
            """
        )

        // Index to speed up category filtering and favorites
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_templates_category ON message_templates(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_templates_is_favorite ON message_templates(is_favorite)")
    }
}

/**
 * Migration from version 7 to 8
 * Drops old cache tables (if any) and recreates with correct schema
 * matching CachedConversationEntity and CachedMessageEntity
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Try to preserve existing cached data before rebuilding
        try {
            db.execSQL("ALTER TABLE cached_conversations RENAME TO cached_conversations_backup")
        } catch (_: Exception) { /* table may not exist */ }
        try {
            db.execSQL("ALTER TABLE cached_messages RENAME TO cached_messages_backup")
        } catch (_: Exception) { /* table may not exist */ }

        db.execSQL("DROP TABLE IF EXISTS cached_conversations")
        db.execSQL("DROP TABLE IF EXISTS cached_messages")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cached_conversations (
                threadId INTEGER PRIMARY KEY NOT NULL,
                phoneNumber TEXT NOT NULL,
                contactName TEXT,
                contactPhotoUri TEXT,
                lastMessage TEXT NOT NULL,
                lastMessageTimestamp INTEGER NOT NULL,
                lastMessageType TEXT NOT NULL,
                unreadCount INTEGER NOT NULL,
                messageCount INTEGER NOT NULL,
                isPinned INTEGER NOT NULL DEFAULT 0,
                isMuted INTEGER NOT NULL DEFAULT 0,
                isArchived INTEGER NOT NULL DEFAULT 0,
                isGroup INTEGER NOT NULL DEFAULT 0,
                groupParticipants TEXT NOT NULL DEFAULT '[]'
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cached_messages (
                id INTEGER PRIMARY KEY NOT NULL,
                threadId INTEGER NOT NULL,
                phoneNumber TEXT NOT NULL,
                contactName TEXT,
                body TEXT,
                timestamp INTEGER NOT NULL,
                type INTEGER NOT NULL,
                category TEXT NOT NULL DEFAULT 'PERSONAL',
                isRead INTEGER NOT NULL DEFAULT 0,
                isSent INTEGER NOT NULL DEFAULT 0,
                isDelivered INTEGER NOT NULL DEFAULT 0,
                simSlot INTEGER,
                isSpam INTEGER NOT NULL DEFAULT 0,
                spamScore REAL NOT NULL DEFAULT 0,
                isMms INTEGER NOT NULL DEFAULT 0,
                attachmentUris TEXT NOT NULL DEFAULT '[]',
                attachmentTypes TEXT NOT NULL DEFAULT '[]',
                status TEXT,
                isScheduled INTEGER NOT NULL DEFAULT 0,
                scheduledTime INTEGER,
                scheduleId INTEGER
            )
        """)

        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_threadId ON cached_messages(threadId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_timestamp ON cached_messages(timestamp)")

        // Restore data from backups where possible
        try {
            db.execSQL("""
                INSERT OR IGNORE INTO cached_conversations
                SELECT threadId, phoneNumber, contactName, contactPhotoUri,
                       lastMessage, lastMessageTimestamp, lastMessageType,
                       unreadCount, messageCount, isPinned, isMuted, isArchived,
                       isGroup, groupParticipants
                FROM cached_conversations_backup
            """)
        } catch (_: Exception) { /* column mismatch, skip */ }
        try {
            db.execSQL("""
                INSERT OR IGNORE INTO cached_messages
                SELECT id, threadId, phoneNumber, contactName, body, timestamp,
                       type, category, isRead, isSent, isDelivered, simSlot,
                       isSpam, spamScore, isMms, attachmentUris, attachmentTypes,
                       status, isScheduled, scheduledTime, scheduleId
                FROM cached_messages_backup
            """)
        } catch (_: Exception) { /* column mismatch, skip */ }

        db.execSQL("DROP TABLE IF EXISTS cached_conversations_backup")
        db.execSQL("DROP TABLE IF EXISTS cached_messages_backup")
    }
}

/**
 * Migration from version 8 to 9
 * Adds message_reactions table for emoji reactions feature
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message_reactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                message_id INTEGER NOT NULL,
                emoji TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                user_id TEXT NOT NULL DEFAULT 'me',
                synced INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_reactions_message_id ON message_reactions(message_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_reactions_emoji ON message_reactions(emoji)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_reactions_timestamp ON message_reactions(timestamp)")
    }
}

/**
 * Migration 9 → 10
 * Adds indices on cached_conversations for faster queries
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_conversations_lastMessageTimestamp ON cached_conversations(lastMessageTimestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_conversations_isArchived ON cached_conversations(isArchived)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_conversations_isPinned ON cached_conversations(isPinned)")
    }
}

/**
 * Migration 10 → 11
 * Repairs corrupted message_reactions table.
 * On some devices MIGRATION_8_9 was silently skipped because an empty
 * message_reactions table already existed (CREATE TABLE IF NOT EXISTS).
 * This migration drops and recreates the table unconditionally.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Back up existing reactions data before rebuilding
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS message_reactions_backup AS SELECT * FROM message_reactions")
        } catch (_: Exception) { /* table may not exist or be empty */ }

        db.execSQL("DROP TABLE IF EXISTS message_reactions")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message_reactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                message_id INTEGER NOT NULL,
                emoji TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                user_id TEXT NOT NULL DEFAULT 'me',
                synced INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_reactions_message_id ON message_reactions(message_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_reactions_emoji ON message_reactions(emoji)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_reactions_timestamp ON message_reactions(timestamp)")

        // Restore data from backup
        try {
            db.execSQL("""
                INSERT OR IGNORE INTO message_reactions (message_id, emoji, timestamp, user_id, synced)
                SELECT message_id, emoji, timestamp, user_id, synced
                FROM message_reactions_backup
            """)
        } catch (_: Exception) { /* backup may not exist */ }
        db.execSQL("DROP TABLE IF EXISTS message_reactions_backup")
    }
}

/**
 * Migration 11 → 12
 * Adds repeatInterval column to scheduled_messages for repeating messages.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scheduled_messages ADD COLUMN repeatInterval TEXT NOT NULL DEFAULT 'NONE'")
    }
}

/**
 * Migration 12 → 13
 * Adds attachmentUris column to scheduled_messages for MMS support.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scheduled_messages ADD COLUMN attachmentUris TEXT NOT NULL DEFAULT '[]'")
    }
}

/**
 * Migration 13 → 14
 * ✅ FIX: Adds multipartStatus column to message_metadata.
 * MessageMetadataEntity defines this column but no previous migration added it,
 * causing Room schema validation to crash on upgrade.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Check if column already exists before adding to avoid "duplicate column" crash
        val cursor = db.query("PRAGMA table_info(message_metadata)")
        var hasColumn = false
        while (cursor.moveToNext()) {
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex >= 0 && cursor.getString(nameIndex) == "multipartStatus") {
                hasColumn = true
                break
            }
        }
        cursor.close()
        if (!hasColumn) {
            db.execSQL("ALTER TABLE message_metadata ADD COLUMN multipartStatus TEXT DEFAULT NULL")
        }
    }
}

/**
 * ✅ FIX M25: Migration from version 14 to 15
 *
 * The entities conversation_settings, whitelist and message_templates previously
 * did NOT declare their indices in @Entity, while older migrations created them.
 * Consequences before this fix:
 *  - Fresh installs had no indices at all (slow queries)
 *  - Installs that migrated through v2/v3/v6 carried indices the entity schema
 *    didn't declare → Room schema validation mismatch risk
 * Now the indices are declared in @Entity; this migration adds them for existing
 * fresh-install users upgrading to v15. IF NOT EXISTS makes it a no-op on
 * installs that already created them via the old migration chain.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_settings_isPinned ON conversation_settings(isPinned)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_settings_isMuted ON conversation_settings(isMuted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_settings_isArchived ON conversation_settings(isArchived)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_whitelist_phoneNumber ON whitelist(phoneNumber)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_templates_category ON message_templates(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_templates_is_favorite ON message_templates(is_favorite)")
    }
}