package com.rasmi.purevon.service

import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.rasmi.purevon.receiver.SentStatusReceiver
import com.rasmi.purevon.receiver.DeliveryStatusReceiver

/**
 * Headless SMS Send Service
 * Required for app to be eligible as default SMS app
 * This service handles SMS sending when the app is not in foreground
 * Called for "Respond via message" from incoming call screen
 */
class HeadlessSmsSendService : Service() {
    
    companion object {
        private const val TAG = "HeadlessSmsSendService"
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "HeadlessSmsSendService started")
        
        if (intent == null) {
            Log.w(TAG, "Intent is null")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        
        try {
            // Parse the URI to extract phone number
            val dataString = intent.dataString
            Log.d(TAG, "Data string: $dataString")
            
            if (dataString != null) {
                // Remove scheme prefixes: sms:, smsto:, mms:, mmsto:
                val phoneNumber = Uri.decode(
                    dataString
                        .removePrefix("sms:")
                        .removePrefix("smsto:")
                        .removePrefix("mms:")
                        .removePrefix("mmsto:")
                        .trim()
                )
                
                // Get message text from intent extras
                val messageText = intent.getStringExtra(Intent.EXTRA_TEXT)
                
                if (com.rasmi.purevon.BuildConfig.ENABLE_LOGGING) {
                    Log.d(TAG, "Phone: $phoneNumber, Message: $messageText")
                }
                
                if (phoneNumber.isNotEmpty() && !messageText.isNullOrEmpty()) {
                    sendSmsMessage(phoneNumber, messageText)
                } else {
                    Log.w(TAG, "Invalid phone number or message text")
                }
            } else {
                Log.w(TAG, "Data string is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
        }
        
        stopSelf(startId)
        return START_NOT_STICKY
    }
    
    /**
     * Write outgoing SMS to system database (required for default SMS app)
     */
    private fun writeOutgoingSms(phoneNumber: String, messageText: String): Uri? {
        return try {
            val threadId = try {
                Telephony.Threads.getOrCreateThreadId(this, phoneNumber)
            } catch (_: Exception) { 0L }
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, phoneNumber)
                put(Telephony.Sms.BODY, messageText)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.DATE_SENT, now)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                if (threadId > 0) put(Telephony.Sms.THREAD_ID, threadId)
            }
            contentResolver.insert(Telephony.Sms.CONTENT_URI, values).also {
                Log.d(TAG, "SMS written to system DB: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write SMS to system DB", e)
            null
        }
    }
    
    private fun sendSmsMessage(phoneNumber: String, messageText: String) {
        try {
            // Write outgoing SMS to system database first
            val smsUri = writeOutgoingSms(phoneNumber, messageText)
            val messageId = smsUri?.lastPathSegment?.toLongOrNull() ?: System.currentTimeMillis()
            
            // Use same request code scheme as SmsSender for consistency
            val baseReqCode = (messageId and 0x7FFF_FFFFL).toInt()
            
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            // Send SMS (handle multipart if necessary)
            val parts = smsManager.divideMessage(messageText)
            
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()
                
                for (i in parts.indices) {
                    // 22-bit messageId << 8 | 8-bit partIndex (matches SmsSender scheme)
                    val sentCode = (baseReqCode and 0x3FFFFF shl 8) or (i and 0xFF)
                    val deliveryCode = sentCode or 0x40000000
                    sentIntents.add(createSentIntent(sentCode, messageId, phoneNumber, i, parts.size))
                    deliveredIntents.add(createDeliveryIntent(deliveryCode, messageId, phoneNumber))
                }
                
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    sentIntents,
                    deliveredIntents
                )
            } else {
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    messageText,
                    createSentIntent(baseReqCode, messageId, phoneNumber, 0, 1),
                    createDeliveryIntent(baseReqCode or 0x40000000, messageId, phoneNumber)
                )
            }
            
            Log.d(TAG, "SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
        }
    }
    
    private fun createSentIntent(requestCode: Int, messageId: Long, phoneNumber: String, partIndex: Int, totalParts: Int): PendingIntent {
        val intent = Intent(SentStatusReceiver.ACTION_SMS_SENT_STATUS).apply {
            setPackage(packageName)
            putExtra(SentStatusReceiver.EXTRA_MESSAGE_ID, messageId)
            putExtra(SentStatusReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(SentStatusReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SentStatusReceiver.EXTRA_TOTAL_PARTS, totalParts)
        }
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    private fun createDeliveryIntent(requestCode: Int, messageId: Long, phoneNumber: String): PendingIntent {
        val intent = Intent(DeliveryStatusReceiver.ACTION_SMS_DELIVERY_STATUS).apply {
            setPackage(packageName)
            putExtra(DeliveryStatusReceiver.EXTRA_MESSAGE_ID, messageId)
            putExtra(DeliveryStatusReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
