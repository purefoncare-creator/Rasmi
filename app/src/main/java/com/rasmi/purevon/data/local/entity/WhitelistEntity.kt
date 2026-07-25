package com.rasmi.purevon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Whitelist Entity - Allowed numbers that bypass call blocking
 */
@Serializable
@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val phoneNumber: String,
    val contactName: String? = null,
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

