package com.rasmi.purevon.data.repository

import com.rasmi.purevon.data.local.dao.ContactNoteDao
import com.rasmi.purevon.data.local.entity.ContactNoteEntity
import com.rasmi.purevon.domain.model.ContactNote
import com.rasmi.purevon.domain.repository.ContactNoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ContactNoteRepository] delegating to Room DAO.
 */
@Singleton
class ContactNoteRepositoryImpl @Inject constructor(
    private val contactNoteDao: ContactNoteDao
) : ContactNoteRepository {
    
    override fun getAllNotes(): Flow<List<ContactNote>> {
        return contactNoteDao.getAllNotes().map { list -> list.map { it.toDomain() } }
    }
    
    private fun ContactNoteEntity.toDomain() = ContactNote(
        id = id,
        phoneNumber = phoneNumber,
        note = note,
        callDuration = callDuration,
        isIncoming = isIncoming,
        createdAt = createdAt
    )
    
    override fun getPhoneNumbersWithNotes(): Flow<List<String>> {
        return contactNoteDao.getPhoneNumbersWithNotes()
    }
}
