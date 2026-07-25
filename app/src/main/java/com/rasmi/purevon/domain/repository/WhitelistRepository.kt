package com.rasmi.purevon.domain.repository

import com.rasmi.purevon.domain.model.WhitelistNumber
import kotlinx.coroutines.flow.Flow

/**
 * Repository for whitelist number operations.
 * Abstracts the data layer so ViewModels only depend on the domain layer.
 */
interface WhitelistRepository {
    fun getAllWhitelistNumbers(): Flow<List<WhitelistNumber>>
    suspend fun addToWhitelist(phoneNumber: String, contactName: String?)
    suspend fun removeFromWhitelist(phoneNumber: String)
}
