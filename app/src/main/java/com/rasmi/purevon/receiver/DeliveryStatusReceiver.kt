package com.rasmi.purevon.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.util.event.EventBus
import com.rasmi.purevon.util.event.AppEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for SMS delivery status
 * Updates message status when SMS is delivered to recipient's device
 */
class DeliveryStatusReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_SMS_DELIVERY_STATUS = "com.rasmi.purevon.SMS_DELIVERY_STATUS"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        private const val TAG = "DeliveryStatusReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        // ✅ FIX M42: Validate messageId
        if (messageId <= 0L) {
            Log.w(TAG, "⚠️ Invalid messageId: $messageId — ignoring")
            return
        }
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        
        Log.d(TAG, "=== SMS Delivery Status ===")
        Log.d(TAG, "Message ID: $messageId")
        Log.d(TAG, "Phone: ${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(phoneNumber)}")
        Log.d(TAG, "Result code: $resultCode")
        
        val code = resultCode
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (code) {
                    Activity.RESULT_OK -> {
                        Log.d(TAG, "✅✅ SMS delivered successfully")
                        updateMessageAsDelivered(context, messageId)
                        
                        // Emit delivery event using EventBus
                        EventBus.tryEmit(
                            AppEvent.SmsDelivered(
                                phoneNumber = phoneNumber,
                                success = true,
                                messageUri = messageId.toString()
                            )
                        )
                    }
                    
                    Activity.RESULT_CANCELED -> {
                        Log.w(TAG, "⚠️ SMS not delivered")
                        // ✅ FIX M18: Update STATUS in system DB for failed delivery
                        updateMessageDeliveryFailed(context, messageId)
                        // Emit delivery failure event using EventBus
                        EventBus.tryEmit(
                            AppEvent.SmsDelivered(
                                phoneNumber = phoneNumber,
                                success = false,
                                messageUri = messageId.toString()
                            )
                        )
                    }
                    
                    else -> {
                        Log.w(TAG, "⚠️ SMS delivery unknown status: $code")
                        // ✅ FIX M50: Also update for unknown status
                        updateMessageDeliveryFailed(context, messageId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling delivery status", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun updateMessageAsDelivered(context: Context, messageId: Long) {
        try {
            val values = ContentValues().apply {
                // Mark as delivered (type remains SENT, but we can track delivered status)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
            }
            
            val uri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms._ID} = ?"
            val selectionArgs = arrayOf(messageId.toString())
            
            val updated = context.contentResolver.update(uri, values, selection, selectionArgs)
            Log.d(TAG, "Updated $updated message(s) as delivered in database")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update delivery status", e)
        }
    }

    // ✅ FIX M18: Update STATUS for failed delivery
    private fun updateMessageDeliveryFailed(context: Context, messageId: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
            }
            val updated = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms._ID} = ?",
                arrayOf(messageId.toString())
            )
            Log.d(TAG, "Updated $updated message(s) as delivery-failed in database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update delivery failure status", e)
        }
    }
}
