package com.rasmi.purevon.domain.model

/**
 * Domain model for a whitelisted phone number.
 * Decoupled from Room entity — used in Use Cases, ViewModels, and UI.
 */
data class WhitelistNumber(
    val id: Long = 0,
    val phoneNumber: String,
    val contactName: String? = null,
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
