package com.rasmi.purevon.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.util.event.AppEvent
import com.rasmi.purevon.util.event.EventBus
import com.rasmi.purevon.util.mms.MmsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Receiver for MMS download completion
 * 
 * Called after SmsManager.downloadMultimediaMessage() completes.
 * Handles:
 * - Parsing downloaded MMS PDU
 * - Persisting to system MMS database via PduPersister
 * - Sending acknowledgement (NotifyRespInd) to MMSC
 * - Showing notifications
 * - Updating UI via EventBus
 */
class MmsDownloadedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MmsDownloadedReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "❌ onReceive with null context=$context intent=$intent")
            return
        }
        
        val resultCode = resultCode
        val contentLocation = intent.getStringExtra("content_location") ?: ""
        val transactionId = intent.getStringExtra("transaction_id") ?: ""
        val subscriptionId = intent.getIntExtra("subscription_id", -1)
        val filePath = intent.getStringExtra("file_path") ?: ""
        
        Log.w(TAG, "╔═══════════════════════════════════════════════════════╗")
        Log.w(TAG, "║  📥 MmsDownloadedReceiver.onReceive() TRIGGERED      ║")
        Log.w(TAG, "╚═══════════════════════════════════════════════════════╝")
        Log.w(TAG, "🕐 Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}")
        Log.w(TAG, "📊 Result Code: $resultCode (RESULT_OK=${Activity.RESULT_OK})")
        Log.w(TAG, "📊 Content-Location: $contentLocation")
        Log.w(TAG, "📊 Transaction-ID: $transactionId")
        Log.w(TAG, "📊 Subscription ID: $subscriptionId")
        Log.w(TAG, "📊 File Path: $filePath")
        
        // Check if file exists and its size
        val downloadFile = java.io.File(filePath)
        Log.w(TAG, "📊 File exists: ${downloadFile.exists()}")
        Log.w(TAG, "📊 File size: ${if (downloadFile.exists()) "${downloadFile.length()} bytes" else "N/A"}")
        
        val pendingResult = goAsync()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(25_000L) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            Log.w(TAG, "✅ [RESULT] Download SUCCESS! Proceeding to handleSuccessfulDownload...")
                            handleSuccessfulDownload(context, filePath, transactionId, subscriptionId)
                        }
                        else -> {
                            val errorMessage = MmsUtils.getMmsErrorMessage(resultCode)
                            Log.e(TAG, "❌ [RESULT] Download FAILED!")
                            Log.e(TAG, "   Error: $errorMessage")
                            Log.e(TAG, "   Result code: $resultCode")
                            Log.e(TAG, "   This means the system could not download the MMS from MMSC")
                        }
                    }
                } // withTimeout
            } catch (e: Exception) {
                Log.e(TAG, "❌ EXCEPTION processing download result!", e)
                Log.e(TAG, "   Type: ${e.javaClass.name}")
                Log.e(TAG, "   Message: ${e.message}")
            } finally {
                // ✅ FIX #9: Stop foreground service when MMS download completes
                try {
                    com.rasmi.purevon.service.MmsForegroundService.stopMmsService(context)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to stop MMS foreground service", e)
                }
                cleanupTempFile(filePath)
                job.cancel()
                pendingResult.finish()
            }
        }
    }
    
    /**
     * Handle successful MMS download
     */
    private suspend fun handleSuccessfulDownload(
        context: Context, 
        filePath: String, 
        transactionId: String,
        subscriptionId: Int
    ) {
        Log.w(TAG, "╔═══════════════════════════════════════════════════════╗")
        Log.w(TAG, "║  📥 handleSuccessfulDownload() START                 ║")
        Log.w(TAG, "╚═══════════════════════════════════════════════════════╝")
        try {
            val downloadFile = java.io.File(filePath)
            
            Log.w(TAG, "📊 [HANDLE STEP 1] File check:")
            Log.w(TAG, "   Path: $filePath")
            Log.w(TAG, "   Exists: ${downloadFile.exists()}")
            Log.w(TAG, "   Size: ${if (downloadFile.exists()) "${downloadFile.length()} bytes" else "N/A"}")
            Log.w(TAG, "   Readable: ${downloadFile.canRead()}")
            
            if (!downloadFile.exists() || downloadFile.length() == 0L) {
                Log.e(TAG, "❌ [HANDLE STEP 1] Downloaded file is EMPTY or MISSING!")
                Log.w(TAG, "   Falling back to processSystemMms()...")
                processSystemMms(context, transactionId)
                return
            }
            
            // Read the downloaded PDU
            val pduData = downloadFile.readBytes()
            Log.w(TAG, "📊 [HANDLE STEP 2] Read ${pduData.size} bytes from file")
            Log.w(TAG, "   First 20 bytes hex: ${pduData.take(20).joinToString(" ") { "%02X".format(it) }}")
            
            // Parse the downloaded PDU
            try {
                Log.w(TAG, "📊 [HANDLE STEP 3] Getting PduPersister...")
                val pduPersister = com.google.android.mms.pdu_alt.PduPersister.getPduPersister(context)
                
                Log.w(TAG, "📊 [HANDLE STEP 4] Parsing PDU with PduParser...")
                val parser = com.google.android.mms.pdu_alt.PduParser(pduData, true)
                val retrieveConf = parser.parse()
                
                Log.w(TAG, "📊 [HANDLE STEP 4] Parse result: ${retrieveConf?.javaClass?.simpleName ?: "NULL"}")
                Log.w(TAG, "   Is RetrieveConf? ${retrieveConf is com.google.android.mms.pdu_alt.RetrieveConf}")
                
                if (retrieveConf != null && retrieveConf is com.google.android.mms.pdu_alt.RetrieveConf) {
                    Log.w(TAG, "✅ [HANDLE STEP 4] Parsed RetrieveConf successfully!")
                    Log.w(TAG, "   Subject: ${retrieveConf.subject}")
                    Log.w(TAG, "   From: ${retrieveConf.from}")
                    Log.w(TAG, "   Body parts: ${retrieveConf.body?.partsNum ?: 0}")
                    
                    // Persist to system MMS database
                    Log.w(TAG, "📊 [HANDLE STEP 5] Persisting to system DB...")
                    Log.w(TAG, "   Target URI: ${Telephony.Mms.Inbox.CONTENT_URI}")
                    Log.w(TAG, "   Subscription: $subscriptionId")
                    
                    val messageUri = pduPersister.persist(
                        retrieveConf,
                        Telephony.Mms.Inbox.CONTENT_URI,
                        true,  // createThreadId
                        true,  // groupMmsEnabled
                        null,  // preOpenedFiles
                        subscriptionId // subscription ID for correct SIM
                    )
                    
                    if (messageUri != null) {
                        Log.w(TAG, "✅ [HANDLE STEP 5] MMS persisted! URI: $messageUri")
                        
                        // Mark as read = false (new message)
                        val values = android.content.ContentValues().apply {
                            put(Telephony.Mms.READ, 0)
                            put(Telephony.Mms.SEEN, 0)
                            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionId >= 0) {
                                put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
                            }
                        }
                        context.contentResolver.update(messageUri, values, null, null)
                        Log.w(TAG, "📊 [HANDLE STEP 6] Updated READ=0, SEEN=0")
                        
                        // Extract MMS info for notification
                        val messageId = messageUri.lastPathSegment?.toLongOrNull() ?: 0L
                        Log.w(TAG, "📊 [HANDLE STEP 7] Notifying UI, messageId=$messageId")
                        notifyNewMms(context, messageId)
                    } else {
                        Log.e(TAG, "❌ [HANDLE STEP 5] persist() returned NULL!")
                        Log.w(TAG, "   Falling back to processSystemMms()...")
                        processSystemMms(context, transactionId)
                    }
                } else {
                    Log.w(TAG, "⚠️ [HANDLE STEP 4] Not a RetrieveConf, type: ${retrieveConf?.javaClass?.simpleName}")
                    Log.w(TAG, "   Falling back to processSystemMms()...")
                    processSystemMms(context, transactionId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [HANDLE] EXCEPTION during parse/persist!", e)
                Log.e(TAG, "   Type: ${e.javaClass.name}")
                Log.e(TAG, "   Message: ${e.message}")
                Log.e(TAG, "   Falling back to processSystemMms()...")
                processSystemMms(context, transactionId)
            }
            
            // Send acknowledgement (M-Acknowledge.ind) to MMSC
            sendAcknowledgement(context, transactionId, subscriptionId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling downloaded MMS", e)
        }
    }
    
    /**
     * Process the latest MMS from system database
     * Used as fallback when PDU parsing fails (system may have already saved it).
     * First tries to match by transactionId (most reliable), then falls back
     * to recency-based lookup with a wider time window.
     */
    private suspend fun processSystemMms(context: Context, transactionId: String = "") {
        try {
            // Strategy 1: Match by transaction ID if available (most reliable)
            if (transactionId.isNotBlank()) {
                context.contentResolver.query(
                    Telephony.Mms.CONTENT_URI,
                    arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE),
                    "${Telephony.Mms.TRANSACTION_ID} = ?",
                    arrayOf(transactionId),
                    "${Telephony.Mms.DATE} DESC LIMIT 1"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val messageId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
                        Log.w(TAG, "Found MMS by transaction ID: ID=$messageId, tr_id=$transactionId")
                        notifyNewMms(context, messageId)
                        return
                    }
                }
            }
            
            // Strategy 2: Fall back to most recent MMS within last 5 minutes
            // (widened from 2 min to handle slow network delivery)
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE),
                null,
                null,
                "${Telephony.Mms.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val messageId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE))
                    
                    val now = System.currentTimeMillis() / 1000
                    if (now - date < 300) {
                        Log.w(TAG, "Found recent MMS in system DB: ID=$messageId (${now - date}s ago)")
                        if (transactionId.isNotBlank()) {
                            Log.w(TAG, "⚠️ Could not match by transaction ID=$transactionId — using recency fallback")
                        }
                        notifyNewMms(context, messageId)
                    } else {
                        Log.w(TAG, "No recent MMS found (oldest is ${now - date}s ago)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing system MMS", e)
        }
    }
    
    /**
     * Send notification and update UI for a new MMS
     */
    private suspend fun notifyNewMms(context: Context, messageId: Long) {
        try {
            // Get sender address
            val phoneNumber = MmsUtils.getMmsAddress(context, messageId) ?: "Unknown"
            
            // Get text content
            val textContent = MmsUtils.getMmsText(context, messageId)
            
            // Get attachment count
            val attachmentCount = getMmsAttachmentCount(context, messageId)
            
            // Get thread ID
            val threadId = MmsUtils.getMmsThreadId(context, messageId)
            
            // Build display body
            val displayBody = buildString {
                if (!textContent.isNullOrBlank()) {
                    append(textContent)
                }
                if (attachmentCount > 0) {
                    if (isNotEmpty()) append(" ")
                    append("📎 ($attachmentCount ${if (attachmentCount == 1) "attachment" else "attachments"})")
                }
                if (isEmpty()) {
                    append("📎 MMS")
                }
            }
            
            Log.w(TAG, "✅ New MMS from: ${DebugLogger.maskPhoneNumber(phoneNumber)}, thread: $threadId, body: [${displayBody.length} chars]")
            
            // Emit event to update UI
            EventBus.tryEmit(
                AppEvent.SmsReceived(
                    phoneNumber = phoneNumber,
                    messageBody = displayBody,
                    timestamp = System.currentTimeMillis(),
                    threadId = threadId
                )
            )
            
            // Show notification
            showMmsNotification(context, phoneNumber, displayBody, threadId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying new MMS", e)
        }
    }
    
    /**
     * Send M-Acknowledge.ind to MMSC to confirm receipt
     */
    private fun sendAcknowledgement(context: Context, transactionId: String, subscriptionId: Int) {
        try {
            if (transactionId.isBlank()) {
                Log.w(TAG, "No transaction ID for acknowledgement")
                return
            }
            
            // Build acknowledge indication PDU
            val acknowledgeInd = com.google.android.mms.pdu_alt.AcknowledgeInd(
                com.google.android.mms.pdu_alt.PduHeaders.CURRENT_MMS_VERSION,
                transactionId.toByteArray()
            )
            
            val pduBytes = com.google.android.mms.pdu_alt.PduComposer(context, acknowledgeInd).make()
            
            if (pduBytes != null && pduBytes.isNotEmpty()) {
                // Write to temp file
                val ackFile = java.io.File(context.cacheDir, "mms_ack_${System.currentTimeMillis()}.dat")
                ackFile.writeBytes(pduBytes)
                
                val ackUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    ackFile
                )
                
                // Grant permissions
                context.grantUriPermission(
                    "com.android.phone",
                    ackUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                // Send acknowledgement
                val smsManager = MmsUtils.getSmsManagerForSub(context, subscriptionId)
                smsManager.sendMultimediaMessage(
                    context,
                    ackUri,
                    null,
                    null,
                    null // No need to track ack result
                )
                
                Log.w(TAG, "✅ MMS acknowledgement sent (transaction: $transactionId)")
                
                // Clean up ack file after a short delay to allow sendMultimediaMessage to read it.
                // Intentionally uses an independent scope: the cleanup needs to outlive the
                // receiver's goAsync() timeout (25s) since sendMultimediaMessage may still
                // be reading the file. The file lives in cacheDir so it is reclaimed on reboot.
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(30_000L)
                    try {
                        if (ackFile.exists()) ackFile.delete()
                    } catch (_: Exception) {}
                }
            } else {
                Log.w(TAG, "Failed to compose acknowledgement PDU")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending MMS acknowledgement (non-critical)", e)
            // Ack failure is non-critical - MMS is already received
        }
    }
    
    // ==========================================
    // Helper methods
    // ==========================================
    
    private fun getMmsAttachmentCount(context: Context, messageId: Long): Int {
        return try {
            context.contentResolver.query(
                Uri.parse("content://mms/$messageId/part"),
                arrayOf("_id", "ct"),
                null, null, null
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext()) {
                    val ct = cursor.getString(cursor.getColumnIndexOrThrow("ct"))
                    if (ct != "text/plain" && ct != "application/smil") {
                        count++
                    }
                }
                count
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error counting MMS attachments", e)
            0
        }
    }
    
    private fun cleanupTempFile(filePath: String) {
        try {
            if (filePath.isNotBlank()) {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    file.delete()
                    Log.w(TAG, "Temp file cleaned up: $filePath")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp file", e)
        }
    }
    
    /**
     * Show notification for incoming MMS
     */
    private suspend fun showMmsNotification(context: Context, phoneNumber: String, body: String, threadId: Long) {
        try {
            // Get contact name
            val contactName = try {
                if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val lookupUri = Uri.withAppendedPath(
                        android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(phoneNumber)
                    )
                    context.contentResolver.query(
                        lookupUri,
                        arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                } else null
            } catch (e: Exception) {
                null
            }
            
            // Get contact photo
            val contactPhoto = try {
                if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val lookupUri = Uri.withAppendedPath(
                        android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(phoneNumber)
                    )
                    val photoUri = context.contentResolver.query(
                        lookupUri,
                        arrayOf(android.provider.ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                    
                    photoUri?.let { uriStr ->
                        context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { inputStream ->
                            android.graphics.BitmapFactory.decodeStream(inputStream)
                        }
                    }
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Error loading MMS contact photo", e)
                null
            }

            // Get EnhancedNotificationManager via EntryPoint
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmsReceiverEntryPoint::class.java
            )
            val notificationManager = entryPoint.enhancedNotificationManager()
            val hideContent = entryPoint.settingsDataStore().hideSensitiveNotifications.first()
            
            // Check if group MMS
            val isGroup = try {
                val uri = Uri.parse("content://mms-sms/conversations?simple=true")
                context.contentResolver.query(
                    uri,
                    arrayOf("recipient_ids"),
                    "_id = ?",
                    arrayOf(threadId.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val recipientIds = cursor.getString(0) ?: ""
                        recipientIds.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size > 1
                    } else false
                } ?: false
            } catch (e: Exception) {
                false
            }
            
            notificationManager.showSmsNotification(
                phoneNumber = phoneNumber,
                senderName = contactName,
                messageBody = body,
                threadId = threadId,
                timestamp = System.currentTimeMillis(),
                contactPhoto = contactPhoto,
                hideContent = hideContent,
                isGroup = isGroup
            )
            
            Log.w(TAG, "✅ MMS notification shown for: ${contactName ?: phoneNumber}")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing MMS notification", e)
        }
    }
}
