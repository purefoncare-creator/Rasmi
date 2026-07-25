package com.rasmi.purevon.domain.model

/**
 * Domain model for Call Log
 */
data class CallLog(
    val id: Long,
    val phoneNumber: String, // Phone number
    val contactName: String?, // Contact name
    val contactPhotoUri: String?,
    val callType: CallType, // Converted type
    val timestamp: Long, // Timestamp
    val duration: Long,
    val simSlot: Int?,
    val isSpam: Boolean,
    val spamScore: Float,
    val notes: String?,
    val isBlocked: Boolean = false // True if number is in blocked list or call type is BLOCKED
) {
    // Aliases for system field names
    val number: String get() = phoneNumber
    val name: String? get() = contactName
    val type: Int get() = callType.toSystemType()
    val date: Long get() = timestamp
}
