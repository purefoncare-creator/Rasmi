package com.rasmi.purevon.worker

import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.util.message.OtpManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Worker for automatically deleting OTP messages after a delay
 */
@HiltWorker
class OtpAutoDeleteWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val settingsDataStore: SettingsDataStore,
    private val otpManager: OtpManager
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "OtpAutoDeleteWorker"
        const val WORK_NAME = "otp_auto_delete_worker"
        private const val DEFAULT_DELAY_MINUTES = 5
    }
    
    override suspend fun doWork(): Result {
        return try {
            // Check if app is the default SMS app — deletion fails silently otherwise
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            if (defaultSmsPackage != context.packageName) {
                Log.d(TAG, "Not default SMS app, skipping OTP auto-delete")
                return Result.success()
            }
            
            // Check if auto-delete is enabled
            val autoDeleteEnabled = settingsDataStore.autoDeleteOtp.first()
            if (!autoDeleteEnabled) {
                Log.d(TAG, "Auto-delete OTP is disabled")
                return Result.success()
            }
            
            val delayMinutes = settingsDataStore.otpDeleteDelay.first()
            val deleteOlderThan = System.currentTimeMillis() - (delayMinutes.toLong() * 60 * 1000)
            
            Log.d(TAG, "Checking for OTP messages older than $delayMinutes minutes")
            
            var deletedCount = 0
            
            // Query inbox messages only (TYPE=1), limited to 500 most recent
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms.DATE} < ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(deleteOlderThan.toString(), "1"),
                "${Telephony.Sms.DATE} DESC LIMIT 500"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
                val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                
                // Validate indices
                if (idIndex == -1 || bodyIndex == -1 || dateIndex == -1) {
                    Log.e(TAG, "Invalid column indices for OTP auto-delete")
                    return@use
                }
                
                while (cursor.moveToNext()) {
                    try {
                        val messageId = cursor.getLong(idIndex)
                        val body = cursor.getString(bodyIndex) ?: continue
                        val timestamp = cursor.getLong(dateIndex)
                    
                        // Check if message is OTP
                        if (isOtpMessage(body)) {
                            // Delete the message
                            val deleted = context.contentResolver.delete(
                                Telephony.Sms.CONTENT_URI,
                                "${Telephony.Sms._ID} = ?",
                                arrayOf(messageId.toString())
                            )
                            
                            if (deleted > 0) {
                                deletedCount++
                                Log.d(TAG, "Deleted OTP message ID: $messageId (age: ${(System.currentTimeMillis() - timestamp) / 60000} min)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing message at position ${cursor.position}", e)
                    }
                }
            }
            
            Log.d(TAG, "Auto-deleted $deletedCount OTP messages")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-deleting OTP messages", e)
            Result.failure()
        }
    }
    
    // ✅ FIX #24: Use OtpManager.detectOtp() for consistent OTP detection
    // (previously used SpamDetectionRules.OTP_PATTERNS which had different criteria)
    private fun isOtpMessage(message: String): Boolean {
        return otpManager.detectOtp(message) != null
    }
}
