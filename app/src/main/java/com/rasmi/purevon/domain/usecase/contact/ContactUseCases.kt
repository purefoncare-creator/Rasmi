package com.rasmi.purevon.domain.usecase.contact

import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to get all contacts
 */
class GetAllContactsUseCase @Inject constructor(
    private val repository: ContactRepository
) {
    operator fun invoke(): Flow<List<Contact>> {
        return repository.getAllContacts()
    }
}

/**
 * Use case to get favorite contacts
 */
class GetFavoriteContactsUseCase @Inject constructor(
    private val repository: ContactRepository
) {
    operator fun invoke(): Flow<List<Contact>> {
        return repository.getFavoriteContacts()
    }
}

/**
 * Use case to search contacts
 */
class SearchContactsUseCase @Inject constructor(
    private val repository: ContactRepository
) {
    operator fun invoke(query: String): Flow<List<Contact>> {
        return repository.searchContacts(query)
    }
}

/**
 * Use case to get contact by phone number
 */
class GetContactByNumberUseCase @Inject constructor(
    private val repository: ContactRepository
) {
    suspend operator fun invoke(phoneNumber: String): Contact? {
        return repository.getContactByNumber(phoneNumber)
    }
}

/**
 * Use case to toggle favorite status
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val repository: ContactRepository
) {
    suspend operator fun invoke(contactId: Long, isFavorite: Boolean) {
        repository.setFavorite(contactId, isFavorite)
    }
}


