package com.rasmi.purevon.domain.repository

import com.rasmi.purevon.domain.model.Contact
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Contact operations
 */
interface ContactRepository {
    
    fun getAllContacts(): Flow<List<Contact>>
    
    fun getFavoriteContacts(): Flow<List<Contact>>
    
    fun getFrequentContacts(limit: Int = 10): Flow<List<Contact>>
    
    fun searchContacts(query: String): Flow<List<Contact>>
    
    suspend fun getContactByNumber(phoneNumber: String): Contact?
    
    /**
     * Get contact by ID - efficient lookup using system ContentProvider
     */
    suspend fun getContactById(contactId: Long): Contact?
    
    suspend fun updateContact(contact: Contact)
    
    suspend fun deleteContact(contactId: Long)
    
    suspend fun setFavorite(contactId: Long, isFavorite: Boolean)
}
