package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Entity for storing notes about contacts/phone numbers
 * Notes are associated with phone numbers so they can be displayed
 * in contact details screen
 */
@Serializable
@Entity(
    tableName = "contact_notes",
    indices = [Index(value = ["phoneNumber"])]
)
data class ContactNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val note: String,
    val callDuration: Long = 0, // Duration of the call this note was made for (in seconds)
    val isIncoming: Boolean = true, // Whether it was an incoming or outgoing call
    val createdAt: Long = System.currentTimeMillis()
)

