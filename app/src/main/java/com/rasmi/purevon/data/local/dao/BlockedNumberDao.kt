package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.BlockedNumberEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Blocked Numbers
 */
@Dao
interface BlockedNumberDao {
    
    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAt DESC")
    fun getAllBlockedNumbers(): Flow<List<BlockedNumberEntity>>
    
    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAt DESC")
    suspend fun getAllBlockedNumbersSync(): List<BlockedNumberEntity>
    
    @Query("SELECT * FROM blocked_numbers WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getBlockedNumber(phoneNumber: String): BlockedNumberEntity?
    
    @Query("SELECT * FROM blocked_numbers WHERE isWildcard = 1")
    fun getWildcardBlocks(): Flow<List<BlockedNumberEntity>>
    
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE phoneNumber = :phoneNumber)")
    suspend fun isNumberBlocked(phoneNumber: String): Boolean

    @Query("SELECT phoneNumber FROM blocked_numbers")
    suspend fun getAllBlockedPhoneNumbers(): List<String>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedNumber(blockedNumber: BlockedNumberEntity): Long
    
    @Update
    suspend fun updateBlockedNumber(blockedNumber: BlockedNumberEntity)
    
    @Delete
    suspend fun deleteBlockedNumber(blockedNumber: BlockedNumberEntity)
    
    @Query("DELETE FROM blocked_numbers WHERE phoneNumber = :phoneNumber")
    suspend fun unblockNumber(phoneNumber: String)
    
    @Query("DELETE FROM blocked_numbers")
    suspend fun deleteAllBlockedNumbers()
}
