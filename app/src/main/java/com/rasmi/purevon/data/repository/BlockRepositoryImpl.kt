package com.rasmi.purevon.data.repository

import android.telephony.PhoneNumberUtils
import android.util.Log
import com.rasmi.purevon.data.local.dao.BlockedNumberDao
import com.rasmi.purevon.data.local.dao.WhitelistDao
import com.rasmi.purevon.data.local.entity.BlockedNumberEntity
import com.rasmi.purevon.domain.repository.BlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of BlockRepository
 * Ensures mutual exclusion with whitelist — blocking a number removes it from whitelist.
 */
class BlockRepositoryImpl @Inject constructor(
    private val blockedNumberDao: BlockedNumberDao,
    private val whitelistDao: WhitelistDao
) : BlockRepository {
    
    override fun getAllBlockedNumbers(): Flow<List<String>> {
        return blockedNumberDao.getAllBlockedNumbers().map { entities ->
            entities.map { it.phoneNumber }
        }
    }
    
    override suspend fun blockNumber(phoneNumber: String, reason: String?) {
        // Mutual exclusion: remove from whitelist first (best-effort)
        runCatching { whitelistDao.removeFromWhitelist(phoneNumber) }
            .onFailure { Log.w("BlockRepository", "Failed to remove from whitelist while blocking", it) }
        
        // Always proceed with blocking even if whitelist removal failed
        blockedNumberDao.insertBlockedNumber(
            BlockedNumberEntity(
                phoneNumber = phoneNumber,
                reason = reason,
                blockCalls = true,
                blockMessages = true
            )
        )
    }
    
    override suspend fun unblockNumber(phoneNumber: String) {
        blockedNumberDao.unblockNumber(phoneNumber)
    }
    
    override suspend fun isNumberBlocked(phoneNumber: String): Boolean {
        if (blockedNumberDao.isNumberBlocked(phoneNumber)) return true
        // Normalized comparison fallback for different formats (e.g. +966564123456 vs 0564123456)
        val allBlocked = blockedNumberDao.getAllBlockedNumbers().first()
        return allBlocked.any { entity ->
            PhoneNumberUtils.compare(phoneNumber, entity.phoneNumber)
        }
    }
    
    override suspend fun blockWildcard(pattern: String, reason: String?) {
        blockedNumberDao.insertBlockedNumber(
            BlockedNumberEntity(
                phoneNumber = pattern,
                pattern = pattern,
                isWildcard = true,
                reason = reason,
                blockCalls = true,
                blockMessages = true
            )
        )
    }
    
    override suspend fun shouldBlockCall(phoneNumber: String): Boolean {
        // Check exact match
        if (blockedNumberDao.isNumberBlocked(phoneNumber)) {
            return true
        }
        
        // Check normalized match (e.g. +966564123456 vs 0564123456)
        val allBlocked = blockedNumberDao.getAllBlockedNumbers().first()
        if (allBlocked.any { it.blockCalls && PhoneNumberUtils.compare(phoneNumber, it.phoneNumber) }) {
            return true
        }
        
        // Check wildcard patterns — use .first() to get single snapshot instead of
        // .collect() which would suspend forever on a Room Flow.
        val blocks = blockedNumberDao.getWildcardBlocks().first()
        for (block in blocks) {
            if (block.blockCalls && matchesWildcard(phoneNumber, block.pattern ?: "")) {
                return true
            }
        }
        
        return false
    }
    
    override suspend fun shouldBlockMessage(phoneNumber: String): Boolean {
        // Check exact match
        val blocked = blockedNumberDao.getBlockedNumber(phoneNumber)
        if (blocked != null && blocked.blockMessages) {
            return true
        }
        
        // Check normalized match
        val allBlocked = blockedNumberDao.getAllBlockedNumbers().first()
        if (allBlocked.any { it.blockMessages && PhoneNumberUtils.compare(phoneNumber, it.phoneNumber) }) {
            return true
        }
        
        // Check wildcard patterns — use .first() to get single snapshot instead of
        // .collect() which would suspend forever on a Room Flow.
        val blocks = blockedNumberDao.getWildcardBlocks().first()
        for (block in blocks) {
            if (block.blockMessages && matchesWildcard(phoneNumber, block.pattern ?: "")) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Iterative glob matching — safe from ReDoS, O(n*m) worst case.
     * Supports only '*' wildcard (matches any sequence of characters).
     */
    private fun matchesWildcard(phoneNumber: String, pattern: String): Boolean {
        var si = 0
        var pi = 0
        var starIdx = -1
        var matchIdx = 0

        while (si < phoneNumber.length) {
            when {
                pi < pattern.length && pattern[pi] == phoneNumber[si] -> {
                    si++
                    pi++
                }
                pi < pattern.length && pattern[pi] == '*' -> {
                    starIdx = pi
                    matchIdx = si
                    pi++
                }
                starIdx != -1 -> {
                    pi = starIdx + 1
                    matchIdx++
                    si = matchIdx
                }
                else -> return false
            }
        }

        while (pi < pattern.length && pattern[pi] == '*') pi++
        return pi == pattern.length
    }
}
