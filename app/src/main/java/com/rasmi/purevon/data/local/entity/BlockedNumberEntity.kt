package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Blocked Number Entity - User's block list
 */
@Serializable
@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val phoneNumber: String,
    val pattern: String? = null, // For wildcard blocking (e.g., "05555*")
    
    val isWildcard: Boolean = false,
    val blockCalls: Boolean = true,
    val blockMessages: Boolean = true,
    
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val blockedAt: Long = System.currentTimeMillis()
)
