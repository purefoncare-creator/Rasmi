package com.rasmi.purevon.domain.repository

import com.rasmi.purevon.domain.model.ContactNote
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Contact Notes operations.
 * Abstracts DAO access so ViewModels don't depend on the data layer directly.
 */
interface ContactNoteRepository {
    
    /**
     * Get all notes across all contacts.
     */
    fun getAllNotes(): Flow<List<ContactNote>>
    
    /**
     * Get distinct phone numbers that have notes — efficient for indicator display.
     */
    fun getPhoneNumbersWithNotes(): Flow<List<String>>
}
