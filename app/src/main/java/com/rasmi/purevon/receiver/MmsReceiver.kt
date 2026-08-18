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
import com.rasmi.purevon.util.DebugLogger

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
            DebugLogger.diagnostic(TAG, "❌ onReceive called with null context=$context intent=$intent")
            return
        }
        
        val action = intent.action
        DebugLogger.diagnostic(TAG, "╔═══════════════════════════════════════════════════════╗")
        DebugLogger.diagnostic(TAG, "║  📩 MmsReceiver.onReceive() TRIGGERED                ║")
        DebugLogger.diagnostic(TAG, "╚═══════════════════════════════════════════════════════╝")
        DebugLogger.diagnostic(TAG, "🕐 Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}")
        DebugLogger.diagnostic(TAG, "📋 Action: $action")
        DebugLogger.diagnostic(TAG, "📋 Intent type: ${intent.type}")
        DebugLogger.diagnostic(TAG, "📋 Intent data: ${intent.data}")
        DebugLogger.diagnostic(TAG, "📋 Extras keys: ${intent.extras?.keySet()?.joinToString()}")
        
        // Log all intent extras
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                val value = try { extras.get(key) } catch (_: Exception) { "ERROR" }
                val display = when (value) {
                    is ByteArray -> "ByteArray[${value.size}]"
                    else -> value.toString().take(200)
                }
                DebugLogger.diagnostic(TAG, "   Extra[$key] = $display")
            }
        }
        
        if (action != ACTION_MMS_RECEIVED &&
            action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            DebugLogger.diagnostic(TAG, "⚠️ Ignoring unrelated action: $action")
            return
        }
        
        val pendingResult = goAsync()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        scope.launch {
            try {
                kotlinx.coroutines.withTimeout(25_000L) {
                    // ✅ Get subscription ID for correct SIM
                    val subId = intent.getIntExtra("subscription",
                        intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",
                            SmsManager.getDefaultSmsSubscriptionId()))
                    
                    val pushData = intent.getByteArrayExtra("data")
                    val pduData = intent.getByteArrayExtra("pdu")
                    
                    DebugLogger.diagnostic(TAG, "📊 [STEP 1] RAW DATA:")
                    DebugLogger.diagnostic(TAG, "   Push data: ${if (pushData != null) "${pushData.size} bytes" else "NULL"}")
                    DebugLogger.diagnostic(TAG, "   PDU data: ${if (pduData != null) "${pduData.size} bytes" else "NULL"}")
                    DebugLogger.diagnostic(TAG, "   Subscription ID: $subId")
                    DebugLogger.diagnostic(TAG, "   Default SMS Sub: ${SmsManager.getDefaultSmsSubscriptionId()}")
                    DebugLogger.diagnostic(TAG, "   Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                    DebugLogger.diagnostic(TAG, "   Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    
                    // ✅ CRITICAL FIX: Download MMS from MMSC using system API
                    DebugLogger.diagnostic(TAG, "📊 [STEP 2] Calling downloadMmsFromNetwork(subId=$subId)...")
                    downloadMmsFromNetwork(context, intent, subId)
                    
                    // MmsDownloadedReceiver will handle notification and EventBus
                    // after the download completes — no need to duplicate here.
                    DebugLogger.diagnostic(TAG, "📊 [STEP 3] Download initiated — MmsDownloadedReceiver will handle notification")
                }
                
            } catch (e: Exception) {
                DebugLogger.diagnostic(TAG, "Error processing MMS", e)
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
        DebugLogger.diagnostic(TAG, "╔═══════════════════════════════════════════════════════╗")
        DebugLogger.diagnostic(TAG, "║  📥 downloadMmsFromNetwork() START                   ║")
        DebugLogger.diagnostic(TAG, "╚═══════════════════════════════════════════════════════╝")
        try {
            val pushData = intent.getByteArrayExtra("data") ?: intent.getByteArrayExtra("pdu")
            
            if (pushData == null) {
                DebugLogger.diagnostic(TAG, "❌ [DOWNLOAD STEP 1] FAILED: No push data! Both 'data' and 'pdu' extras are null")
                DebugLogger.diagnostic(TAG, "   Available extras: ${intent.extras?.keySet()?.joinToString()}")
                showDownloadFailureNotification(context, "No MMS data received")
                return
            }
            
            DebugLogger.diagnostic(TAG, "📊 [DOWNLOAD STEP 1] Got push data: ${pushData.size} bytes")
            DebugLogger.diagnostic(TAG, "   First 20 bytes hex: ${pushData.take(20).joinToString(" ") { "%02X".format(it) }}")
            
            // Parse the notification PDU to extract content-location
            var contentLocation: String? = null
            var transactionId: String? = null
            
            try {
                DebugLogger.diagnostic(TAG, "📊 [DOWNLOAD STEP 2] Parsing PDU with PduParser...")
                val parser = com.google.android.mms.pdu_alt.PduParser(pushData, true)
                val genericPdu = parser.parse()
                
                if (genericPdu != null) {
                    DebugLogger.diagnostic(TAG, "📊 [DOWNLOAD STEP 2] PDU parsed! Type: ${genericPdu.messageType} (0x${Integer.toHexString(genericPdu.messageType)})")
                    
                    when (genericPdu.messageType) {
                        MESSAGE_TYPE_NOTIFICATION_IND -> {
                            val notificationInd = genericPdu as com.google.android.mms.pdu_alt.NotificationInd
                            contentLocation = String(notificationInd.contentLocation ?: byteArrayOf())
                            transactionId = String(notificationInd.transactionId ?: byteArrayOf())
                            
                            DebugLogger.diagnostic(TAG, "✅ [DOWNLOAD STEP 2] NOTIFICATION_IND parsed successfully:")
                            DebugLogger.diagnostic(TAG, "   Content-Location: $contentLocation")
                            DebugLogger.diagnostic(TAG, "   Transaction-ID: $transactionId")
                            DebugLogger.diagnostic(TAG, "   Message Size: ${notificationInd.messageSize}")
                            DebugLogger.diagnostic(TAG, "   Expiry: ${notificationInd.expiry}")
                            DebugLogger.diagnostic(TAG, "   Content-Location empty? ${contentLocation.isNullOrBlank()}")
                        }
                        MESSAGE_TYPE_DELIVERY_IND -> {
                            DebugLogger.diagnostic(TAG, "ℹ️ [DOWNLOAD STEP 2] Received delivery indication (0x86) - no download needed")
                            return
                        }
                        MESSAGE_TYPE_READ_ORIG_IND -> {
                            DebugLogger.diagnostic(TAG, "Received read report - no download needed")
                            return
                        }
                        else -> {
                            DebugLogger.diagnostic(TAG, "Unknown PDU type: ${genericPdu.messageType}")
                        }
                    }
                } else {
                    DebugLogger.diagnostic(TAG, "⚠️ [DOWNLOAD STEP 2] PduParser returned null! Trying raw extraction...")
                    contentLocation = extractContentLocationRaw(pushData)
                }
            } catch (e: Exception) {
                DebugLogger.diagnostic(TAG, "❌ [DOWNLOAD STEP 2] PduParser EXCEPTION: ${e.message}", e)
                contentLocation = extractContentLocationRaw(pushData)
            }
            
            if (contentLocation.isNullOrBlank()) {
                DebugLogger.diagnostic(TAG, "❌ [DOWNLOAD STEP 3] FATAL: Could not extract content-location!")
                DebugLogger.diagnostic(TAG, "   MMS cannot be downloaded without content-location URL")
                showDownloadFailureNotification(context, "Could not process MMS notification")
                return
            }
            
            DebugLogger.diagnostic(TAG, "📊 [DOWNLOAD STEP 3] Content-Location obtained: $contentLocation")
            
            // Create a temp file to receive the downloaded MMS PDU
            val fileName = "mms_download_${System.currentTimeMillis()}.dat"
            val downloadFile = java.io.File(context.cacheDir, fileName)
            DebugLogger.diagnostic(TAG, "📊 [DOWNLOAD STEP 4] Temp file: ${downloadFile.absolutePath}")
            
            val downloadUri = try {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    downloadFile
                )
            } catch (e: Exception) {
                DebugLogger.diagnostic(TAG, "❌ [DOWNLOAD STEP 4] FileProvider.getUriForFile FAILED!", e)
                DebugLogger.diagnostic(TAG, "   Package: ${context.packageName}")
                DebugLogger.diagnostic(TAG, "   Authority: ${context.packageName}.fileprovider")
                DebugLogger.diagnostic(TAG, "   File: ${downloadFile.absolutePath}")
                showDownloadFailureNotification(context, "MMS storage error")
                return
            }
            DebugLogger.diagnostic(TAG, "📊 [DOWNLOAD STEP 4] Download URI: $downloadUri")
            
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
                    DebugLogger.diagnostic(TAG, "📊 [DOWNLOAD STEP 5] URI permissions granted to $pkg")
                } catch (e: Exception) {
                    DebugLogger.diagnostic(TAG, "Could not grant URI permission to $pkg: ${e.message}")
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
            DebugLogger.diagnostic(TAG, "╔═══════════════════════════════════════════════════════╗")
            DebugLogger.diagnostic(TAG, "║  🚀 [DOWNLOAD STEP 6] CALLING downloadMultimediaMessage()  ║")
            DebugLogger.diagnostic(TAG, "╚═══════════════════════════════════════════════════════╝")

            // ✅ FIX #9: Start foreground service to keep process alive during download
            com.rasmi.purevon.service.MmsForegroundService.startMmsService(context, "download")
            DebugLogger.diagnostic(TAG, "   Content-Location: $contentLocation")
            DebugLogger.diagnostic(TAG, "   Download URI: $downloadUri")
            DebugLogger.diagnostic(TAG, "   Subscription: $subId")
            DebugLogger.diagnostic(TAG, "   SmsManager class: ${smsManager.javaClass.name}")
            DebugLogger.diagnostic(TAG, "   PendingIntent target: MmsDownloadedReceiver")
            DebugLogger.diagnostic(TAG, "   File path passed: ${downloadFile.absolutePath}")
            
            smsManager.downloadMultimediaMessage(
                context,
                contentLocation,
                downloadUri,
                null, // let system use default APN
                downloadedIntent
            )
            
            DebugLogger.diagnostic(TAG, "✅ [DOWNLOAD STEP 6] downloadMultimediaMessage() called WITHOUT exception!")
            DebugLogger.diagnostic(TAG, "   ⏳ Waiting for MmsDownloadedReceiver callback...")
            
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "❌ FATAL: downloadMmsFromNetwork() EXCEPTION!", e)
            DebugLogger.diagnostic(TAG, "   Exception type: ${e.javaClass.name}")
            DebugLogger.diagnostic(TAG, "   Message: ${e.message}")
            DebugLogger.diagnostic(TAG, "   Cause: ${e.cause}")
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
            DebugLogger.diagnostic(TAG, "Extracted content-location from raw data: $url")
            return url
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "Error extracting content-location from raw data", e)
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
            
            val channel = NotificationChannel(
                CHANNEL_ID_MMS_ERROR,
                "MMS Errors",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for MMS download failures"
            }
            notificationManager.createNotificationChannel(channel)
            
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
            DebugLogger.diagnostic(TAG, "📢 Shown MMS download failure notification: $reason")
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "Error showing MMS download failure notification", e)
        }
    }
}
