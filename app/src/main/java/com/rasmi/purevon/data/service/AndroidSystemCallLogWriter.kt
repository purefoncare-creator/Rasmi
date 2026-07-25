package com.rasmi.purevon.data.service

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import android.util.Log
import com.rasmi.purevon.domain.service.SystemCallLogWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [SystemCallLogWriter].
 * Inserts entries into the system call log via ContentResolver.
 */
@Singleton
class AndroidSystemCallLogWriter @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemCallLogWriter {
    
    companion object {
        private const val TAG = "SystemCallLogWriter"
    }
    
    override suspend fun insertCallLogEntry(
        phoneNumber: String,
        timestamp: Long,
        duration: Long,
        callType: Int,
        simSlot: Int?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(CallLog.Calls.NUMBER, phoneNumber)
                put(CallLog.Calls.DATE, timestamp)
                put(CallLog.Calls.DURATION, duration)
                put(CallLog.Calls.TYPE, callType)
                put(CallLog.Calls.NEW, 1)
                
                if (simSlot != null) {
                    put(CallLog.Calls.PHONE_ACCOUNT_ID, simSlot)
                }
            }
            
            context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting call log entry", e)
            false
        }
    }
}
