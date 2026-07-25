package com.rasmi.purevon.domain.model

/**
 * Domain model for Contact
 * Clean representation for UI layer
 */
data class Contact(
    val id: Long,
    val displayName: String,
    val phoneNumber: String,
    val phoneType: String?,
    val photoUri: String?,
    val email: String?,
    val company: String?,
    val isFavorite: Boolean,
    val isBlocked: Boolean,
    val lastContactedTime: Long?,
    val timesContacted: Int,
    val preferredSimSlot: Int?
) {
    // Alias for backward compatibility
    val name: String get() = displayName
    val lastContactTime: Long? get() = lastContactedTime
}
