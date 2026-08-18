package com.rasmi.purevon.util.mms

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

/**
 * Shared MMS utility methods.
 * Extracted from MmsReceiver and MmsDownloadedReceiver to eliminate code duplication.
 */
object MmsUtils {
    
    private const val TAG = "MmsUtils"
    
    /**
     * Get the sender address from an MMS message.
     * Queries the MMS address table for the FROM address (type 137).
     * Filters out the common "insert-address-token" placeholder.
     */
    fun getMmsAddress(context: Context, messageId: Long): String? {
        return try {
            context.contentResolver.query(
                Uri.parse("content://mms/$messageId/addr"),
                arrayOf("address", "type"),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow("type"))
                    if (type == 137) { // FROM
                        val address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
                        if (!address.isNullOrBlank() && address != "insert-address-token") {
                            return@use address
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS address", e)
            null
        }
    }
    
    /**
     * Get the text content of an MMS message.
     * Queries the MMS parts table for text/plain content.
     */
    fun getMmsText(context: Context, messageId: Long): String? {
        return try {
            context.contentResolver.query(
                Uri.parse("content://mms/$messageId/part"),
                arrayOf("_id", "ct", "text"),
                "ct = ?",
                arrayOf("text/plain"),
                null
            )?.use { cursor ->
                // ✅ FIX #21: Read ALL text parts, not just the first one
                val parts = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    val text = cursor.getString(cursor.getColumnIndexOrThrow("text"))
                    if (!text.isNullOrBlank()) {
                        parts.add(text)
                    }
                }
                parts.joinToString("\n").ifBlank { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS text", e)
            null
        }
    }
    
    /**
     * Get the correct SmsManager instance for a given subscription ID.
     * Handles API level differences across Android versions.
     */
    fun getSmsManagerForSub(context: Context, subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (subId >= 0) {
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subId)
            } else {
                context.getSystemService(SmsManager::class.java)
            }
        } else if (subId >= 0) {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
    
    /**
     * Get the thread ID for an MMS message.
     */
    fun getMmsThreadId(context: Context, messageId: Long): Long {
        return try {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms.THREAD_ID),
                "${Telephony.Mms._ID} = ?",
                arrayOf(messageId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS thread ID", e)
            0L
        }
    }
    
    /**
     * Get a human-readable error message for MMS error codes.
     */
    fun getMmsErrorMessage(resultCode: Int): String {
        return when (resultCode) {
            1 -> "MMS_ERROR_UNSPECIFIED - General unspecified error"
            2 -> "MMS_ERROR_INVALID_APN - Invalid APN settings"
            3 -> "MMS_ERROR_UNABLE_CONNECT_MMS - Unable to connect to MMS service"
            4 -> "MMS_ERROR_HTTP_FAILURE - HTTP failure during MMS download"
            5 -> "MMS_ERROR_IO_ERROR - I/O error"
            6 -> "MMS_ERROR_RETRY - Temporary failure, retry needed"
            7 -> "MMS_ERROR_CONFIGURATION_ERROR - Configuration error"
            8 -> "MMS_ERROR_NO_DATA_NETWORK - No data network available"
            10 -> "MMS_ERROR_DATA_DISABLED - Mobile data is disabled"
            else -> "Unknown error code: $resultCode"
        }
    }
}
