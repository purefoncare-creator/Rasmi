package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.ContactNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for contact notes
 */
@Dao
interface ContactNoteDao {
    
    /**
     * Get all notes for a phone number
     */
    @Query("SELECT * FROM contact_notes WHERE phoneNumber = :phoneNumber ORDER BY createdAt DESC")
    fun getNotesByPhoneNumber(phoneNumber: String): Flow<List<ContactNoteEntity>>
    
    /**
     * Get all notes for a phone number (suspend version)
     */
    @Query("SELECT * FROM contact_notes WHERE phoneNumber = :phoneNumber ORDER BY createdAt DESC")
    suspend fun getNotesByPhoneNumberSync(phoneNumber: String): List<ContactNoteEntity>
    
    /**
     * Get notes for multiple phone numbers (for contacts with multiple numbers)
     */
    @Query("SELECT * FROM contact_notes WHERE phoneNumber IN (:phoneNumbers) ORDER BY createdAt DESC")
    fun getNotesByPhoneNumbers(phoneNumbers: List<String>): Flow<List<ContactNoteEntity>>
    
    /**
     * Get latest note for a phone number
     */
    @Query("SELECT * FROM contact_notes WHERE phoneNumber = :phoneNumber ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestNote(phoneNumber: String): ContactNoteEntity?
    
    /**
     * Get count of notes for a phone number
     */
    @Query("SELECT COUNT(*) FROM contact_notes WHERE phoneNumber = :phoneNumber")
    suspend fun getNotesCount(phoneNumber: String): Int
    
    /**
     * Insert a new note
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: ContactNoteEntity): Long
    
    /**
     * Update an existing note
     */
    @Update
    suspend fun updateNote(note: ContactNoteEntity)
    
    /**
     * Delete a note by ID
     */
    @Query("DELETE FROM contact_notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long)
    
    /**
     * Delete all notes for a phone number
     */
    @Query("DELETE FROM contact_notes WHERE phoneNumber = :phoneNumber")
    suspend fun deleteNotesByPhoneNumber(phoneNumber: String)
    
    /**
     * Get all notes (for backup/export)
     */
    @Query("SELECT * FROM contact_notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<ContactNoteEntity>>

    /**
     * Get distinct phone numbers that have notes - efficient for indicator display
     */
    @Query("SELECT DISTINCT phoneNumber FROM contact_notes")
    fun getPhoneNumbersWithNotes(): Flow<List<String>>
    
    /**
     * Delete all notes
     */
    @Query("DELETE FROM contact_notes")
    suspend fun deleteAllNotes()
}

