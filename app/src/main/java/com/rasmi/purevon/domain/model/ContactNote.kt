package com.rasmi.purevon.domain.model

data class ContactNote(
    val id: Long = 0,
    val phoneNumber: String,
    val note: String,
    val callDuration: Long = 0,
    val isIncoming: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
