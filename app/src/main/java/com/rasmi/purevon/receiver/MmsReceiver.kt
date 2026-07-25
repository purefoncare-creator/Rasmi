package com.rasmi.purevon.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receiver for incoming MMS messages
 * 
 * ✅ FIXED: Now properly downloads MMS using SmsManager.downloadMultimediaMessage()
 * 
 * When the app is the default SMS app:
 * 1. System sends WAP_PUSH_DELIVER with the notification PDU
 * 2. We extract the content-location (URL of MMS on MMSC server)
 * 3. We call SmsManager.downloadMultimediaMessage() to download the actual MMS
 * 4. MmsDownloadedReceiver handles the result and processes the downloaded MMS
 */
class MmsReceiver : BroadcastReceiver() {
    
    // Scope created per onReceive call, not class-level, to avoid leaks
    // The actual scope is created inside onReceive with goAsync()
    
    companion object {
        private const val TAG = "MmsReceiver"
        const val ACTION_MMS_RECEIVED = "android.provider.Telephony.WAP_PUSH_RECEIVED"
        
        // PDU Types from PduHeaders
        private const val MESSAGE_TYPE_NOTIFICATION_IND = 0x82
        private const val MESSAGE_TYPE_DELIVERY_IND = 0x86
        private const val MESSAGE_TYPE_READ_ORIG_IND = 0x88
        
        private const val CHANNEL_ID_MMS_ERROR = "mms_error_channel"
        private const val NOTIFICATION_ID_MMS_DOWNLOAD_FAILED = 99001
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "❌ onReceive called with null context=$context intent=$intent")
            return
        }
        
        val action = intent.action
        Log.w(TAG, "╔═══════════════════════════════════════════════════════╗")
        Log.w(TAG, "║  📩 MmsReceiver.onReceive() TRIGGERED                ║")
        Log.w(TAG, "╚═══════════════════════════════════════════════════════╝")
        Log.w(TAG, "🕐 Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}")
        Log.w(TAG, "📋 Action: $action")
        Log.w(TAG, "📋 Intent type: ${intent.type}")
        Log.w(TAG, "📋 Intent data: ${intent.data}")
        Log.w(TAG, "📋 Extras keys: ${intent.extras?.keySet()?.joinToString()}")
        
        // Log all intent extras
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                val value = try { extras.get(key) } catch (_: Exception) { "ERROR" }
                val display = when (value) {
                    is ByteArray -> "ByteArray[${value.size}]"
                    else -> value.toString().take(200)
                }
                Log.w(TAG, "   Extra[$key] = $display")
            }
        }
        
        if (action != ACTION_MMS_RECEIVED &&
            action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            Log.w(TAG, "⚠️ Ignoring unrelated action: $action")
            return
        }
        
        val pendingResult = goAsync()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        scope.launch {
            try {
                kotlinx.coroutines.withTimeout(25_000L) {
                    // ✅ Get subscription ID for correct SIM
                    val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        intent.getIntExtra("subscription", 
                            intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",
                                SmsManager.getDefaultSmsSubscriptionId()))
                    } else {
                        -1
                    }
                    
                    val pushData = intent.getByteArrayExtra("data")
                    val pduData = intent.getByteArrayExtra("pdu")
                    
                    Log.w(TAG, "📊 [STEP 1] RAW DATA:")
                    Log.w(TAG, "   Push data: ${if (pushData != null) "${pushData.size} bytes" else "NULL"}")
                    Log.w(TAG, "   PDU data: ${if (pduData != null) "${pduData.size} bytes" else "NULL"}")
                    Log.w(TAG, "   Subscription ID: $subId")
                    Log.w(TAG, "   Default SMS Sub: ${SmsManager.getDefaultSmsSubscriptionId()}")
                    Log.w(TAG, "   Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                    Log.w(TAG, "   Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    
                    // ✅ CRITICAL FIX: Download MMS from MMSC using system API
                    Log.w(TAG, "📊 [STEP 2] Calling downloadMmsFromNetwork(subId=$subId)...")
                    downloadMmsFromNetwork(context, intent, subId)
                    
                    // MmsDownloadedReceiver will handle notification and EventBus
                    // after the download completes — no need to duplicate here.
                    Log.w(TAG, "📊 [STEP 3] Download initiated — MmsDownloadedReceiver will handle notification")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing MMS", e)
            } finally {
                job.cancel()
                pendingResult.finish()
            }
        }
    }
    
    /**
     * ✅ CRITICAL FIX: Download MMS from MMSC via SmsManager.downloadMultimediaMessage()
     * 
     * When app is default SMS app, the system does NOT auto-download MMS.
     * We MUST call downloadMultimediaMessage() to fetch the actual MMS content.
     */
    private fun downloadMmsFromNetwork(context: Context, intent: Intent, subId: Int) {
        Log.w(TAG, "╔═══════════════════════════════════════════════════════╗")
        Log.w(TAG, "║  📥 downloadMmsFromNetwork() START                   ║")
        Log.w(TAG, "╚═══════════════════════════════════════════════════════╝")
        try {
            val pushData = intent.getByteArrayExtra("data") ?: intent.getByteArrayExtra("pdu")
            
            if (pushData == null) {
                Log.e(TAG, "❌ [DOWNLOAD STEP 1] FAILED: No push data! Both 'data' and 'pdu' extras are null")
                Log.e(TAG, "   Available extras: ${intent.extras?.keySet()?.joinToString()}")
                showDownloadFailureNotification(context, "No MMS data received")
                return
            }
            
            Log.w(TAG, "📊 [DOWNLOAD STEP 1] Got push data: ${pushData.size} bytes")
            Log.w(TAG, "   First 20 bytes hex: ${pushData.take(20).joinToString(" ") { "%02X".format(it) }}")
            
            // Parse the notification PDU to extract content-location
            var contentLocation: String? = null
            var transactionId: String? = null
            
            try {
                Log.w(TAG, "📊 [DOWNLOAD STEP 2] Parsing PDU with PduParser...")
                val parser = com.google.android.mms.pdu_alt.PduParser(pushData, true)
                val genericPdu = parser.parse()
                
                if (genericPdu != null) {
                    Log.w(TAG, "📊 [DOWNLOAD STEP 2] PDU parsed! Type: ${genericPdu.messageType} (0x${Integer.toHexString(genericPdu.messageType)})")
                    
                    when (genericPdu.messageType) {
                        MESSAGE_TYPE_NOTIFICATION_IND -> {
                            val notificationInd = genericPdu as com.google.android.mms.pdu_alt.NotificationInd
                            contentLocation = String(notificationInd.contentLocation ?: byteArrayOf())
                            transactionId = String(notificationInd.transactionId ?: byteArrayOf())
                            
                            Log.w(TAG, "✅ [DOWNLOAD STEP 2] NOTIFICATION_IND parsed successfully:")
                            Log.w(TAG, "   Content-Location: $contentLocation")
                            Log.w(TAG, "   Transaction-ID: $transactionId")
                            Log.w(TAG, "   Message Size: ${notificationInd.messageSize}")
                            Log.w(TAG, "   Expiry: ${notificationInd.expiry}")
                            Log.w(TAG, "   Content-Location empty? ${contentLocation.isNullOrBlank()}")
                        }
                        MESSAGE_TYPE_DELIVERY_IND -> {
                            Log.w(TAG, "ℹ️ [DOWNLOAD STEP 2] Received delivery indication (0x86) - no download needed")
                            return
                        }
                        MESSAGE_TYPE_READ_ORIG_IND -> {
                            Log.w(TAG, "Received read report - no download needed")
                            return
                        }
                        else -> {
                            Log.w(TAG, "Unknown PDU type: ${genericPdu.messageType}")
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ [DOWNLOAD STEP 2] PduParser returned null! Trying raw extraction...")
                    contentLocation = extractContentLocationRaw(pushData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [DOWNLOAD STEP 2] PduParser EXCEPTION: ${e.message}", e)
                contentLocation = extractContentLocationRaw(pushData)
            }
            
            if (contentLocation.isNullOrBlank()) {
                Log.e(TAG, "❌ [DOWNLOAD STEP 3] FATAL: Could not extract content-location!")
                Log.e(TAG, "   MMS cannot be downloaded without content-location URL")
                showDownloadFailureNotification(context, "Could not process MMS notification")
                return
            }
            
            Log.w(TAG, "📊 [DOWNLOAD STEP 3] Content-Location obtained: $contentLocation")
            
            // Create a temp file to receive the downloaded MMS PDU
            val fileName = "mms_download_${System.currentTimeMillis()}.dat"
            val downloadFile = java.io.File(context.cacheDir, fileName)
            Log.w(TAG, "📊 [DOWNLOAD STEP 4] Temp file: ${downloadFile.absolutePath}")
            
            val downloadUri = try {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    downloadFile
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ [DOWNLOAD STEP 4] FileProvider.getUriForFile FAILED!", e)
                Log.e(TAG, "   Package: ${context.packageName}")
                Log.e(TAG, "   Authority: ${context.packageName}.fileprovider")
                Log.e(TAG, "   File: ${downloadFile.absolutePath}")
                showDownloadFailureNotification(context, "MMS storage error")
                return
            }
            Log.w(TAG, "📊 [DOWNLOAD STEP 4] Download URI: $downloadUri")
            
            // ✅ FIX #40: Grant URI permissions to telephony process dynamically
            // On Samsung it's "com.samsung.android.phone.service", on Xiaomi it differs, etc.
            val telephonyPackages = mutableListOf<String>()
            // 1. Try to get the default SMS app package
            Telephony.Sms.getDefaultSmsPackage(context)?.let { telephonyPackages.add(it) }
            // 2. Common telephony process packages as fallback
            telephonyPackages.addAll(listOf(
                "com.android.phone",
                "com.android.mms",
                "com.android.mms.service"
            ))
            for (pkg in telephonyPackages.distinct()) {
                try {
                    context.grantUriPermission(
                        pkg,
                        downloadUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    Log.w(TAG, "📊 [DOWNLOAD STEP 5] URI permissions granted to $pkg")
                } catch (e: Exception) {
                    Log.d(TAG, "Could not grant URI permission to $pkg: ${e.message}")
                }
            }
            
            // Create PendingIntent for download completion callback
            // ✅ FIX M16: Use unique request code combining timestamp + hash to avoid collision
            val requestCode = (System.nanoTime() xor Thread.currentThread().id).toInt()
            val downloadedIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, MmsDownloadedReceiver::class.java).apply {
                    putExtra("content_location", contentLocation)
                    putExtra("transaction_id", transactionId ?: "")
                    putExtra("subscription_id", subId)
                    putExtra("file_path", downloadFile.absolutePath)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // Get SmsManager for the correct subscription
            val smsManager = getSmsManagerForSub(context, subId)
            
            // ✅ CRITICAL: Actually download the MMS content from MMSC
            Log.w(TAG, "╔═══════════════════════════════════════════════════════╗")
            Log.w(TAG, "║  🚀 [DOWNLOAD STEP 6] CALLING downloadMultimediaMessage()  ║")
            Log.w(TAG, "╚═══════════════════════════════════════════════════════╝")

            // ✅ FIX #9: Start foreground service to keep process alive during download
            com.rasmi.purevon.service.MmsForegroundService.startMmsService(context, "download")
            Log.w(TAG, "   Content-Location: $contentLocation")
            Log.w(TAG, "   Download URI: $downloadUri")
            Log.w(TAG, "   Subscription: $subId")
            Log.w(TAG, "   SmsManager class: ${smsManager.javaClass.name}")
            Log.w(TAG, "   PendingIntent target: MmsDownloadedReceiver")
            Log.w(TAG, "   File path passed: ${downloadFile.absolutePath}")
            
            smsManager.downloadMultimediaMessage(
                context,
                contentLocation,
                downloadUri,
                null, // let system use default APN
                downloadedIntent
            )
            
            Log.w(TAG, "✅ [DOWNLOAD STEP 6] downloadMultimediaMessage() called WITHOUT exception!")
            Log.w(TAG, "   ⏳ Waiting for MmsDownloadedReceiver callback...")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL: downloadMmsFromNetwork() EXCEPTION!", e)
            Log.e(TAG, "   Exception type: ${e.javaClass.name}")
            Log.e(TAG, "   Message: ${e.message}")
            Log.e(TAG, "   Cause: ${e.cause}")
            showDownloadFailureNotification(context, "MMS download error: ${e.message?.take(50) ?: "unknown"}")
        }
    }
    
    /**
     * Get appropriate SmsManager for subscription
     */
    private fun getSmsManagerForSub(context: Context, subId: Int): SmsManager =
        com.rasmi.purevon.util.mms.MmsUtils.getSmsManagerForSub(context, subId)
    
    /**
     * Extract content-location from raw WAP push data as fallback
     */
    private fun extractContentLocationRaw(data: ByteArray): String? {
        try {
            val dataStr = String(data, Charsets.ISO_8859_1)
            val httpIndex = dataStr.indexOf("http://")
            val httpsIndex = dataStr.indexOf("https://")
            
            val startIndex = when {
                httpIndex >= 0 && httpsIndex >= 0 -> minOf(httpIndex, httpsIndex)
                httpIndex >= 0 -> httpIndex
                httpsIndex >= 0 -> httpsIndex
                else -> return null
            }
            
            var endIndex = startIndex
            while (endIndex < dataStr.length && dataStr[endIndex].code > 0x20 && dataStr[endIndex].code < 0x7F) {
                endIndex++
            }
            
            val url = dataStr.substring(startIndex, endIndex)
            Log.w(TAG, "Extracted content-location from raw data: $url")
            return url
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting content-location from raw data", e)
            return null
        }
    }
    
    /**
     * Show a notification when MMS download fails, so the user is informed
     * instead of the message being silently lost.
     */
    private fun showDownloadFailureNotification(context: Context, reason: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create channel if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_MMS_ERROR,
                    "MMS Errors",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for MMS download failures"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // Tap opens the main conversation list
            val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_MMS_ERROR)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("MMS Download Failed")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Could not download multimedia message.\n$reason"
                ))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID_MMS_DOWNLOAD_FAILED, notification)
            Log.w(TAG, "📢 Shown MMS download failure notification: $reason")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing MMS download failure notification", e)
        }
    }
}
