package com.rasmi.purevon.data.repository

import android.util.Log
import com.rasmi.purevon.data.local.dao.BlockedNumberDao
import com.rasmi.purevon.data.local.dao.WhitelistDao
import com.rasmi.purevon.data.local.entity.WhitelistEntity
import com.rasmi.purevon.domain.model.WhitelistNumber
import com.rasmi.purevon.domain.repository.WhitelistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of WhitelistRepository
 * Ensures mutual exclusion with blocklist — whitelisting a number removes it from blocklist.
 */
class WhitelistRepositoryImpl @Inject constructor(
    private val whitelistDao: WhitelistDao,
    private val blockedNumberDao: BlockedNumberDao
) : WhitelistRepository {

    override fun getAllWhitelistNumbers(): Flow<List<WhitelistNumber>> {
        return whitelistDao.getAllWhitelistNumbers().map { entities ->
            entities.map { entity ->
                WhitelistNumber(
                    id = entity.id,
                    phoneNumber = entity.phoneNumber,
                    contactName = entity.contactName,
                    reason = entity.reason,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    override suspend fun addToWhitelist(phoneNumber: String, contactName: String?) {
        // Mutual exclusion: remove from blocklist first (best-effort)
        runCatching { blockedNumberDao.unblockNumber(phoneNumber) }
            .onFailure { Log.w("WhitelistRepository", "Failed to remove from blocklist while whitelisting", it) }
        
        // Always proceed with whitelisting even if blocklist removal failed
        whitelistDao.insertWhitelistNumber(
            WhitelistEntity(phoneNumber = phoneNumber, contactName = contactName)
        )
    }

    override suspend fun removeFromWhitelist(phoneNumber: String) {
        whitelistDao.removeFromWhitelist(phoneNumber)
    }
}
