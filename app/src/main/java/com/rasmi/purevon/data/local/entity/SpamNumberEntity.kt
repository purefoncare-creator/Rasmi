package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Spam Number Entity - Local spam database
 */
@Serializable
@Entity(
    tableName = "spam_numbers",
    indices = [
        Index(value = ["phoneNumber"], unique = true),
        Index(value = ["spamScore"]),
        Index(value = ["reportCount"])
    ]
)
data class SpamNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val phoneNumber: String,
    val countryCode: String? = null,
    
    val spamScore: Float, // 0-1 score (1 = definitely spam)
    val spamType: SpamType,
    
    val reportCount: Int = 0,
    val lastReportedAt: Long = System.currentTimeMillis(),
    
    val name: String? = null, // Known spam name (e.g., "Telemarketer")
    val category: String? = null, // e.g., "Scam", "Robocall", "Marketing"
    
    val isUserBlocked: Boolean = false,
    val isWhitelisted: Boolean = false,
    
    val notes: String? = null
)

enum class SpamType {
    TELEMARKETER,
    SCAM,
    ROBOCALL,
    FRAUD,
    HARASSMENT,
    PROMOTIONAL,
    POLL,
    UNKNOWN,
    TELEMARKETING  // Alias for TELEMARKETER
}
