package com.rasmi.purevon.di

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.room.Room
import com.rasmi.purevon.data.local.MIGRATION_1_2
import com.rasmi.purevon.data.local.MIGRATION_2_3
import com.rasmi.purevon.data.local.MIGRATION_3_4
import com.rasmi.purevon.data.local.MIGRATION_4_5
import com.rasmi.purevon.data.local.MIGRATION_5_6
import com.rasmi.purevon.data.local.MIGRATION_6_7
import com.rasmi.purevon.data.local.MIGRATION_7_8
import com.rasmi.purevon.data.local.MIGRATION_8_9
import com.rasmi.purevon.data.local.MIGRATION_9_10
import com.rasmi.purevon.data.local.MIGRATION_10_11
import com.rasmi.purevon.data.local.MIGRATION_11_12
import com.rasmi.purevon.data.local.MIGRATION_12_13
import com.rasmi.purevon.data.local.MIGRATION_13_14
import com.rasmi.purevon.data.local.MIGRATION_14_15
import com.rasmi.purevon.data.local.PurevonDatabase
import com.rasmi.purevon.data.local.dao.*
import com.rasmi.purevon.util.security.DatabasePassphraseManager
import com.rasmi.purevon.util.security.DataEncryptionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

/**
 * Hilt module for Database dependencies
 * Updated for metadata-only storage (Version 6)
 * Added SearchDao for FTS (Full-Text Search)
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): PurevonDatabase {
        // Load native SQLCipher library BEFORE any SupportOpenHelperFactory usage.
        // Otherwise on a fresh install (where migrateToEncrypted/rekeyToRawKey return early
        // because the DB file doesn't exist yet) the .so is never loaded and Room will
        // crash with: UnsatisfiedLinkError: No implementation found for ...nativeOpen(...)
        System.loadLibrary("sqlcipher")

        val passphrase = DatabasePassphraseManager.getPassphrase(context)
        try {
            // Run the one-time encryption migration on an IO-friendly policy. The database is
            // created directly with a raw 256-bit key (hex key) below, so no separate re-key
            // step is needed.
            val prefs = context.getSharedPreferences("purevon_db_cipher", Context.MODE_PRIVATE)
            val needsMigration = !prefs.getBoolean("migration_done", false)

            if (needsMigration) {
                val oldPolicy = android.os.StrictMode.allowThreadDiskWrites()
                try {
                    check(migrateToEncrypted(context, passphrase)) {
                        "Database encryption migration failed"
                    }
                    @SuppressLint("ApplySharedPref")
                    prefs.edit().putBoolean("migration_done", true).commit()
                } finally {
                    android.os.StrictMode.setThreadPolicy(oldPolicy)
                }
            }

            // Use raw 256-bit key format — skips PBKDF2 entirely
            val hexKey = "x'" + passphrase.joinToString("") { "%02x".format(it) } + "'"
            // ✅ FIX M36: الترحيل إلى sqlcipher-android (دعم صفحات 16KB لمتطلب Google Play)
            val factory = SupportOpenHelperFactory(hexKey.toByteArray(Charsets.US_ASCII))
            
            return Room.databaseBuilder(
                context,
                PurevonDatabase::class.java,
                PurevonDatabase.DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2, 
                    MIGRATION_2_3, 
                    MIGRATION_3_4, 
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15
                )
                // ✅ FIX #3: Removed fallbackToDestructiveMigrationOnDowngrade()
                // Downgrading now throws IllegalStateException instead of silently deleting all data.
                // Users who downgrade will see a crash, which is preferable to losing all messages/data.
                .build()
        } finally {
            // Zero out the primary decrypted secret to secure memory
            passphrase.fill(0)
        }
    }
    
    /**
     * Migrate an existing unencrypted database to SQLCipher encrypted format.
     * This runs once — on subsequent launches the DB is already encrypted.
     */
    private fun migrateToEncrypted(context: Context, passphrase: ByteArray): Boolean {
        val dbFile = context.getDatabasePath(PurevonDatabase.DATABASE_NAME)
        if (!dbFile.exists()) return true // Fresh install, nothing to migrate
        
        // Quick check: try to open as unencrypted SQLite
        // If it succeeds, the DB needs encryption migration
        try {
            val testDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            testDb.close()
        } catch (e: Exception) {
            // Already encrypted or corrupt — skip migration
            return true
        }
        
        Log.d("DatabaseModule", "Migrating unencrypted database to SQLCipher...")
        
        val tempFile = context.getDatabasePath("${PurevonDatabase.DATABASE_NAME}_encrypted")
        try {
            tempFile.delete() // Clean up any previous failed attempt
            
            // Open the unencrypted DB with SQLCipher (empty passphrase)
            // ✅ FIX M36: واجهة sqlcipher-android الجديدة (كلمة مرور byte[] + hook)
            System.loadLibrary("sqlcipher")
            val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                ByteArray(0),
                null,
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE,
                null
            )
            
            // Export to new encrypted DB
            val hexKey = "x'" + passphrase.joinToString("") { "%02x".format(it) } + "'"
            db.execSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS encrypted KEY $hexKey")
            db.execSQL("SELECT sqlcipher_export('encrypted')")
            db.execSQL("DETACH DATABASE encrypted")
            db.close()
            
            // Swap files
            val walFile = java.io.File(dbFile.absolutePath + "-wal")
            val shmFile = java.io.File(dbFile.absolutePath + "-shm")
            walFile.delete()
            shmFile.delete()
            dbFile.delete()
            check(tempFile.renameTo(dbFile)) {
                "Could not replace the database with the encrypted copy"
            }
            
            Log.d("DatabaseModule", "Database encryption migration completed successfully")
            return true
        } catch (e: Exception) {
            Log.e("DatabaseModule", "Database encryption migration failed", e)
            tempFile.delete()
            // ✅ FIX #7: Do NOT delete original dbFile on migration failure.
            // Keep the original file for a retry, but do not allow the app to open it as plaintext.
            return false
        }
    }
    
    // Metadata DAOs
    @Provides
    @Singleton
    fun provideCallMetadataDao(database: PurevonDatabase): CallMetadataDao {
        return database.callMetadataDao()
    }
    
    @Provides
    @Singleton
    fun provideMessageMetadataDao(database: PurevonDatabase): MessageMetadataDao {
        return database.messageMetadataDao()
    }
    
    @Provides
    @Singleton
    fun provideConversationPreferencesDao(database: PurevonDatabase): ConversationPreferencesDao {
        return database.conversationPreferencesDao()
    }

    // ✅ NEW: Cache DAOs
    @Provides
    @Singleton
    fun provideCachedConversationDao(database: PurevonDatabase): com.rasmi.purevon.data.local.dao.CachedConversationDao {
        return database.cachedConversationDao()
    }

    @Provides
    @Singleton
    fun provideCachedMessageDao(database: PurevonDatabase): com.rasmi.purevon.data.local.dao.CachedMessageDao {
        return database.cachedMessageDao()
    }
    
    // App-specific DAOs
    @Provides
    @Singleton
    fun provideSpamNumberDao(database: PurevonDatabase): SpamNumberDao {
        return database.spamNumberDao()
    }
    
    @Provides
    @Singleton
    fun provideBlockedNumberDao(database: PurevonDatabase): BlockedNumberDao {
        return database.blockedNumberDao()
    }
    
    @Provides
    @Singleton
    fun provideWhitelistDao(database: PurevonDatabase): WhitelistDao {
        return database.whitelistDao()
    }
    
    @Provides
    @Singleton
    fun provideScheduledMessageDao(database: PurevonDatabase): ScheduledMessageDao {
        return database.scheduledMessageDao()
    }
    
    @Provides
    @Singleton
    fun provideContactNoteDao(database: PurevonDatabase): ContactNoteDao {
        return database.contactNoteDao()
    }
    
    @Provides
    @Singleton
    fun provideMessageTemplateDao(database: PurevonDatabase): MessageTemplateDao {
        return database.messageTemplateDao()
    }
    
    // Reaction DAO
    @Provides
    @Singleton
    fun provideMessageReactionDao(database: PurevonDatabase): MessageReactionDao {
        return database.messageReactionDao()
    }
    
    // Search DAO (FTS4)
    @Provides
    @Singleton
    fun provideSearchDao(database: PurevonDatabase): SearchDao {
        return database.searchDao()
    }

    @Provides
    @Singleton
    fun provideDataEncryptionManager(@ApplicationContext context: Context): DataEncryptionManager {
        return DataEncryptionManager(context)
    }
}
