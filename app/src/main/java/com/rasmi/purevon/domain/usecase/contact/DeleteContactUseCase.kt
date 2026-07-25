package com.rasmi.purevon.domain.usecase.contact

import com.rasmi.purevon.domain.repository.ContactRepository
import javax.inject.Inject

/**
 * Use case for deleting a contact
 */
class DeleteContactUseCase @Inject constructor(
    private val repository: ContactRepository
) {
    suspend operator fun invoke(contactId: Long) {
        repository.deleteContact(contactId)
    }
}
