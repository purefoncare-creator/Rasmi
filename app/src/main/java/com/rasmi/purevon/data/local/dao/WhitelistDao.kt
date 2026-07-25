package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.WhitelistEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Whitelist Numbers
 */
@Dao
interface WhitelistDao {
    
    @Query("SELECT * FROM whitelist ORDER BY createdAt DESC")
    fun getAllWhitelistNumbers(): Flow<List<WhitelistEntity>>
    
    @Query("SELECT * FROM whitelist ORDER BY createdAt DESC")
    suspend fun getAllWhitelistNumbersSync(): List<WhitelistEntity>
    
    @Query("SELECT * FROM whitelist WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getWhitelistNumber(phoneNumber: String): WhitelistEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM whitelist WHERE phoneNumber = :phoneNumber)")
    suspend fun isNumberWhitelisted(phoneNumber: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhitelistNumber(whitelistNumber: WhitelistEntity): Long
    
    @Update
    suspend fun updateWhitelistNumber(whitelistNumber: WhitelistEntity)
    
    @Delete
    suspend fun deleteWhitelistNumber(whitelistNumber: WhitelistEntity)
    
    @Query("DELETE FROM whitelist WHERE phoneNumber = :phoneNumber")
    suspend fun removeFromWhitelist(phoneNumber: String)
    
    @Query("DELETE FROM whitelist")
    suspend fun deleteAllWhitelistNumbers()
}

