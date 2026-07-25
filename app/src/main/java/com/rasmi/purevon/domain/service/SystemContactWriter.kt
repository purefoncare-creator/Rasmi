package com.rasmi.purevon.domain.service

/**
 * Abstraction for updating contacts in the system Contacts ContentProvider.
 * Implementations live in the data layer (e.g., AndroidSystemContactWriter).
 */
interface SystemContactWriter {
    
    /**
     * Update a contact's phone number in the system contacts.
     *
     * @param contactId           The system contact ID
     * @param originalPhoneNumber The current phone number to match
     * @param newPhoneNumber      The normalized phone number to set
     */
    suspend fun updateContactPhoneNumber(
        contactId: Long,
        originalPhoneNumber: String,
        newPhoneNumber: String
    )
}
