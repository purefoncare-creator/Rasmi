package com.rasmi.purevon.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.rasmi.purevon.util.event.AppEvent
import com.rasmi.purevon.util.event.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Receiver for MMS sent status
 * Handles tracking when MMS messages are sent
 */
class MmsSentReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MmsSentReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        val messageId = intent.getLongExtra("message_id", -1L)
        val messageUriString = intent.getStringExtra("message_uri")
        val filePath = intent.getStringExtra("file_path") // ✅ Get temp file path
        val resultCode = resultCode
        
        Log.w(TAG, "═══════════════════════════════════════")
        Log.w(TAG, "📨 MMS SEND RESULT RECEIVED")
        Log.w(TAG, "═══════════════════════════════════════")
        Log.w(TAG, "Message ID: $messageId")
        Log.w(TAG, "Message URI: $messageUriString")
        Log.w(TAG, "Temp File: $filePath")
        Log.w(TAG, "Result Code: $resultCode")
        
        // ✅ Clean up temp file
        filePath?.let {
            try {
                val file = java.io.File(it)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.w(TAG, "🗑️ Temp file deleted: $deleted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting temp file", e)
            }
        }
        
        // ✅ FIXED: Use goAsync() + withTimeout for proper BroadcastReceiver lifecycle
        val pendingResult = goAsync()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(25_000L) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            Log.w(TAG, "═══════════════════════════════════════")
                            Log.w(TAG, "✅✅✅ MMS SENT SUCCESSFULLY ✅✅✅")
                            Log.w(TAG, "═══════════════════════════════════════")
                            
                            // Update message status to SENT
                            messageUriString?.let { uriStr ->
                                try {
                                    val messageUri = Uri.parse(uriStr)
                                    val values = ContentValues().apply {
                                        put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
                                    }
                                    context.contentResolver.update(messageUri, values, null, null)
                                    
                                    // Emit event for UI update
                                    EventBus.emit(AppEvent.SmsSent(
                                        phoneNumber = resolveMmsPhoneNumber(context, messageId) ?: "",
                                        success = true,
                                        messageUri = uriStr
                                    ))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error updating MMS status", e)
                                }
                            }
                        }
                        
                        // MMS_ERROR_RETRY: Temporary failure — keep in OUTBOX for system retry
                        SmsManager.MMS_ERROR_RETRY -> {
                            Log.w(TAG, "═══════════════════════════════════════")
                            Log.w(TAG, "🔄 MMS_ERROR_RETRY — Keeping in OUTBOX")
                            Log.w(TAG, "Message will be retried by system")
                            Log.w(TAG, "═══════════════════════════════════════")
                            // Do NOT mark as FAILED — leave in OUTBOX for automatic retry
                            // Emit event so UI knows it's still pending
                            messageUriString?.let { uriStr ->
                                try {
                                    EventBus.emit(AppEvent.SmsSent(
                                        phoneNumber = resolveMmsPhoneNumber(context, messageId) ?: "",
                                        success = false,
                                        messageUri = uriStr
                                    ))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error emitting retry event", e)
                                }
                            }
                        }
                        
                        else -> {
                            // Log all error code constants for debugging
                            Log.e(TAG, "═══════════════════════════════════════")
                            Log.e(TAG, "❌❌❌ MMS SEND FAILED ❌❌❌")
                            Log.e(TAG, "═══════════════════════════════════════")
                            Log.e(TAG, "Received Error Code: $resultCode")
                            Log.e(TAG, "---")
                            Log.e(TAG, "Error Code Constants:")
                            Log.e(TAG, "  MMS_ERROR_UNSPECIFIED = ${SmsManager.MMS_ERROR_UNSPECIFIED}")
                            Log.e(TAG, "  MMS_ERROR_INVALID_APN = ${SmsManager.MMS_ERROR_INVALID_APN}")
                            Log.e(TAG, "  MMS_ERROR_UNABLE_CONNECT_MMS = ${SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS}")
                            Log.e(TAG, "  MMS_ERROR_HTTP_FAILURE = ${SmsManager.MMS_ERROR_HTTP_FAILURE}")
                            Log.e(TAG, "  MMS_ERROR_IO_ERROR = ${SmsManager.MMS_ERROR_IO_ERROR}")
                            Log.e(TAG, "  MMS_ERROR_RETRY = ${SmsManager.MMS_ERROR_RETRY}")
                            Log.e(TAG, "  MMS_ERROR_CONFIGURATION_ERROR = ${SmsManager.MMS_ERROR_CONFIGURATION_ERROR}")
                            Log.e(TAG, "  MMS_ERROR_NO_DATA_NETWORK = ${SmsManager.MMS_ERROR_NO_DATA_NETWORK}")
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                Log.e(TAG, "  MMS_ERROR_DATA_DISABLED = ${SmsManager.MMS_ERROR_DATA_DISABLED}")
                            }
                            Log.e(TAG, "---")
                            
                            // Provide detailed error message
                            val errorMessage = when (resultCode) {
                                1 -> "MMS_ERROR_UNSPECIFIED - General unspecified error"
                                2 -> "MMS_ERROR_INVALID_APN - Invalid APN settings"
                                3 -> "MMS_ERROR_UNABLE_CONNECT_MMS - Unable to connect to MMS service"
                                4 -> "MMS_ERROR_HTTP_FAILURE - HTTP failure during MMS send"
                                5 -> "MMS_ERROR_IO_ERROR - I/O error reading/writing MMS data"
                                6 -> "MMS_ERROR_RETRY - Temporary failure - retry needed"
                                7 -> "MMS_ERROR_CONFIGURATION_ERROR - Configuration error"
                                8 -> "MMS_ERROR_NO_DATA_NETWORK - No data network available"
                                9 -> "MMS_ERROR_INVALID_SUBSCRIPTION_ID - Invalid subscription ID"
                                10 -> "MMS_ERROR_INACTIVE_SUBSCRIPTION - The SIM subscription is inactive/removed"
                                11 -> "MMS_ERROR_DATA_DISABLED - Mobile data is disabled"
                                else -> "Unknown error code: $resultCode"
                            }
                            Log.e(TAG, "Error Message: $errorMessage")
                            Log.e(TAG, "═══════════════════════════════════════")
                            
                            // Update message status to FAILED
                            messageUriString?.let { uriStr ->
                                try {
                                    val messageUri = Uri.parse(uriStr)
                                    val values = ContentValues().apply {
                                        put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED)
                                    }
                                    context.contentResolver.update(messageUri, values, null, null)
                                    
                                    // Emit failure event
                                    EventBus.emit(AppEvent.SmsSent(
                                        phoneNumber = resolveMmsPhoneNumber(context, messageId) ?: "",
                                        success = false,
                                        messageUri = uriStr
                                    ))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error updating MMS failure status", e)
                                }
                            }
                        }
                    }
                } // withTimeout
            } catch (e: Exception) {
                Log.e(TAG, "Error in MmsSentReceiver", e)
            } finally {
                job.cancel()
                pendingResult.finish()
            }
        }
    }

    private fun resolveMmsPhoneNumber(context: Context, mmsId: Long): String? {
        try {
            val addrUri = Uri.parse("content://mms").buildUpon().appendPath(mmsId.toString()).appendPath("addr").build()
            context.contentResolver.query(
                addrUri, arrayOf("address", "type"),
                null, null, null
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndex("address")
                val typeIndex = cursor.getColumnIndex("type")
                while (cursor.moveToNext()) {
                    val addrType = cursor.getInt(typeIndex)
                    if (addrType == 151 || addrType == 137) { // 151=PduHeaders.TO, 137=PduHeaders.FROM
                        return cursor.getString(addressIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving MMS phone number for event", e)
        }
        return null
    }
}
