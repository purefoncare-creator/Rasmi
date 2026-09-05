package com.rasmi.purevon.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
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
            DebugLogger.diagnostic(TAG, "❌ onReceive with null context=$context intent=$intent")
            return
        }
        
        val resultCode = resultCode
        val contentLocation = intent.getStringExtra("content_location") ?: ""
        val transactionId = intent.getStringExtra("transaction_id") ?: ""
        val subscriptionId = intent.getIntExtra("subscription_id", -1)
        val filePath = intent.getStringExtra("file_path") ?: ""
        
        DebugLogger.diagnostic(TAG, "╔═══════════════════════════════════════════════════════╗")
        DebugLogger.diagnostic(TAG, "║  📥 MmsDownloadedReceiver.onReceive() TRIGGERED      ║")
        DebugLogger.diagnostic(TAG, "╚═══════════════════════════════════════════════════════╝")
        DebugLogger.diagnostic(TAG, "🕐 Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}")
        DebugLogger.diagnostic(TAG, "📊 Result Code: $resultCode (RESULT_OK=${Activity.RESULT_OK})")
        DebugLogger.diagnostic(TAG, "📊 Content-Location: $contentLocation")
        DebugLogger.diagnostic(TAG, "📊 Transaction-ID: $transactionId")
        DebugLogger.diagnostic(TAG, "📊 Subscription ID: $subscriptionId")
        DebugLogger.diagnostic(TAG, "📊 File Path: $filePath")
        
        // Check if file exists and its size
        val downloadFile = java.io.File(filePath)
        DebugLogger.diagnostic(TAG, "📊 File exists: ${downloadFile.exists()}")
        DebugLogger.diagnostic(TAG, "📊 File size: ${if (downloadFile.exists()) "${downloadFile.length()} bytes" else "N/A"}")
        
        val pendingResult = goAsync()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(25_000L) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            DebugLogger.diagnostic(TAG, "✅ [RESULT] Download SUCCESS! Proceeding to handleSuccessfulDownload...")
                            handleSuccessfulDownload(context, filePath, transactionId, subscriptionId, contentLocation)
                        }
                        else -> {
                            val errorMessage = MmsUtils.getMmsErrorMessage(resultCode)
                            DebugLogger.diagnostic(TAG, "❌ [RESULT] Download FAILED!")
                            DebugLogger.diagnostic(TAG, "   Error: $errorMessage")
                            DebugLogger.diagnostic(TAG, "   Result code: $resultCode")
                            DebugLogger.diagnostic(TAG, "   This means the system could not download the MMS from MMSC")
                            // ✅ FIX M29 (pattern 1): permanent HTTP failures mean the message
                            // is gone from the MMSC forever — clean up any stale NotificationInd
                            // so nothing keeps re-pushing or retrying it.
                            val httpStatus = intent.getIntExtra(android.telephony.SmsManager.EXTRA_MMS_HTTP_STATUS, 0)
                            handleFailedDownload(context, httpStatus, contentLocation)
                        }
                    }
                } // withTimeout
            } catch (e: Exception) {
                DebugLogger.diagnostic(TAG, "❌ EXCEPTION processing download result!", e)
                DebugLogger.diagnostic(TAG, "   Type: ${e.javaClass.name}")
                DebugLogger.diagnostic(TAG, "   Message: ${e.message}")
            } finally {
                // ✅ FIX #9: Stop foreground service when MMS download completes
                try {
                    com.rasmi.purevon.service.MmsForegroundService.stopMmsService(context)
                } catch (e: Exception) {
                    DebugLogger.diagnostic(TAG, "Failed to stop MMS foreground service", e)
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
        subscriptionId: Int,
        contentLocation: String
    ) {
        DebugLogger.diagnostic(TAG, "╔═══════════════════════════════════════════════════════╗")
        DebugLogger.diagnostic(TAG, "║  📥 handleSuccessfulDownload() START                 ║")
        DebugLogger.diagnostic(TAG, "╚═══════════════════════════════════════════════════════╝")
        try {
            // ✅ FIX M20 (Layer A): same tr_id already persisted → notify, never re-insert
            if (transactionId.isNotBlank()) {
                com.rasmi.purevon.util.mms.MmsDownloadDedup.findByTransactionId(context, transactionId)?.let { existingId ->
                    DebugLogger.diagnostic(TAG, "MMS transaction already persisted: $transactionId")
                    notifyNewMms(context, existingId)
                    // ✅ FIX M29: still close the transaction so the MMSC stops re-pushing.
                    sendNotifyResponse(context, transactionId, subscriptionId, contentLocation)
                    return
                }
            }

            // ✅ FIX M20 (Layer B): carriers that rotate tr_id per re-push (or pushes
            // whose PDU failed to parse) share the SAME Content-Location URL.
            if (com.rasmi.purevon.util.mms.MmsDownloadDedup.wasLocationProcessed(context, contentLocation)) {
                DebugLogger.diagnostic(TAG, "⏭️ [DEDUP] Content-Location processed within TTL — skipping persist")
                return
            }

            val downloadFile = java.io.File(filePath)
            
            DebugLogger.diagnostic(TAG, "📊 [HANDLE STEP 1] File check:")
            DebugLogger.diagnostic(TAG, "   Path: $filePath")
            DebugLogger.diagnostic(TAG, "   Exists: ${downloadFile.exists()}")
            DebugLogger.diagnostic(TAG, "   Size: ${if (downloadFile.exists()) "${downloadFile.length()} bytes" else "N/A"}")
            DebugLogger.diagnostic(TAG, "   Readable: ${downloadFile.canRead()}")
            
            if (!downloadFile.exists() || downloadFile.length() == 0L) {
                DebugLogger.diagnostic(TAG, "❌ [HANDLE STEP 1] Downloaded file is EMPTY or MISSING!")
                // ✅ FIX M29 (pattern 2): download finished but produced no data —
                // mark the NotificationInd's retrieve-status as terminal error so
                // nothing treats it as pending retrieval anymore.
                markRetrieveStatusTerminal(context, contentLocation)
                DebugLogger.diagnostic(TAG, "   Falling back to processSystemMms()...")
                processSystemMms(context, transactionId)
                return
            }
            
            // Read the downloaded PDU
            val pduData = downloadFile.readBytes()
            DebugLogger.diagnostic(TAG, "📊 [HANDLE STEP 2] Read ${pduData.size} bytes from file")
            DebugLogger.diagnostic(TAG, "   First 20 bytes hex: ${pduData.take(20).joinToString(" ") { "%02X".format(it) }}")
            
            // Parse the downloaded PDU
            try {
                DebugLogger.diagnostic(TAG, "📊 [HANDLE STEP 3] Getting PduPersister...")
                val pduPersister = com.google.android.mms.pdu_alt.PduPersister.getPduPersister(context)
                
                DebugLogger.diagnostic(TAG, "📊 [HANDLE STEP 4] Parsing PDU with PduParser...")
                val parser = com.google.android.mms.pdu_alt.PduParser(pduData, true)
                val retrieveConf = parser.parse()
                
                DebugLogger.diagnostic(TAG, "📊 [HANDLE STEP 4] Parse result: ${retrieveConf?.javaClass?.simpleName ?: "NULL"}")
                DebugLogger.diagnostic(TAG, "   Is RetrieveConf? ${retrieveConf is com.google.android.mms.pdu_alt.RetrieveConf}")
                
                if (retrieveConf != null && retrieveConf is com.google.android.mms.pdu_alt.RetrieveConf) {
                    DebugLogger.diagnostic(TAG, "✅ [HANDLE STEP 4] Parsed RetrieveConf successfully!")
                    if (com.rasmi.purevon.BuildConfig.ENABLE_LOGGING) {
                        DebugLogger.diagnostic(TAG, "   Body parts: ${retrieveConf.body?.partsNum ?: 0}")
                    }

                    // ✅ FIX M29 (Layer D): the M-Message-ID header is identical across every
                    // redelivery of the same message, even when tr_id AND Content-Location
                    // both rotate. Strongest duplicate identity available.
                    val midHeader = retrieveConf.messageId?.toString(Charsets.UTF_8)?.trim().orEmpty()
                    val existingByMid = com.rasmi.purevon.util.mms.MmsDownloadDedup.findByMessageId(context, midHeader)
                    if (existingByMid != null || com.rasmi.purevon.util.mms.MmsDownloadDedup.wasMessageIdProcessed(context, midHeader)) {
                        DebugLogger.diagnostic(TAG, "⏭️ [DEDUP Layer D] Message-ID ${midHeader.take(8)}… already processed — skipping persist")
                        existingByMid?.let { notifyNewMms(context, it) }
                        sendNotifyResponse(context, transactionId, subscriptionId, contentLocation)
                        return
                    }

                    // Persist to system MMS database
                    DebugLogger.diagnostic(TAG, "📊 [HANDLE STEP 5] Persisting to system DB...")
                    DebugLogger.diagnostic(TAG, "   Target URI: ${Telephony.Mms.Inbox.CONTENT_URI}")
                    DebugLogger.diagnostic(TAG, "   Subscription: $subscriptionId")
                    
                    val messageUri = pduPersister.persist(
                        retrieveConf,
                        Telephony.Mms.Inbox.CONTENT_URI,
                        true,  // createThreadId
                        true,  // groupMmsEnabled
                        null,  // preOpenedFiles
                        subscriptionId // subscription ID for correct SIM
                    )
                    
                    if (messageUri != null) {
                        DebugLogger.diagnostic(TAG, "✅ [HANDLE STEP 5] MMS persisted! URI: $messageUri")
                        
                        // Mark as read = false (new message)
                        val values = android.content.ContentValues().apply {
                            put(Telephony.Mms.READ, 0)
                            put(Telephony.Mms.SEEN, 0)
                            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                            if (subscriptionId >= 0) {
                                put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
                            }
                        }
                        context.contentResolver.update(messageUri, values, null, null)
                        DebugLogger.diagnostic(TAG, "📊 [HANDLE STEP 6] Updated READ=0, SEEN=0")

                        // ✅ FIX M20 (Layer B): remember this URL so later re-pushes
                        // (even with rotated tr_id) can't insert a duplicate row
                        com.rasmi.purevon.util.mms.MmsDownloadDedup.markLocationProcessed(context, contentLocation)
                        // ✅ FIX M29 (Layer D): remember the Message-ID too — survives
                        // carriers that rotate BOTH tr_id and Content-Location on re-push
                        com.rasmi.purevon.util.mms.MmsDownloadDedup.markMessageIdProcessed(context, midHeader)
                        
                        // Extract MMS info for notification
                        val messageId = messageUri.lastPathSegment?.toLongOrNull() ?: 0L
                        DebugLogger.diagnostic(TAG, "📊 [HANDLE STEP 7] Notifying UI, messageId=$messageId")
                        notifyNewMms(context, messageId)
                    } else {
                        DebugLogger.diagnostic(TAG, "❌ [HANDLE STEP 5] persist() returned NULL!")
                        DebugLogger.diagnostic(TAG, "   Falling back to processSystemMms()...")
                        processSystemMms(context, transactionId)
                    }
                } else {
                    DebugLogger.diagnostic(TAG, "⚠️ [HANDLE STEP 4] Not a RetrieveConf, type: ${retrieveConf?.javaClass?.simpleName}")
                    DebugLogger.diagnostic(TAG, "   Falling back to processSystemMms()...")
                    processSystemMms(context, transactionId)
                }
            } catch (e: Exception) {
                DebugLogger.diagnostic(TAG, "❌ [HANDLE] EXCEPTION during parse/persist!", e)
                DebugLogger.diagnostic(TAG, "   Type: ${e.javaClass.name}")
                DebugLogger.diagnostic(TAG, "   Message: ${e.message}")
                DebugLogger.diagnostic(TAG, "   Falling back to processSystemMms()...")
                processSystemMms(context, transactionId)
            }
            
            // ✅ FIX M19: Send M-NotifyResp.ind (status=Retrieved) — NOT M-Acknowledge.ind.
            // The MMSC keeps re-pushing the WAP notification (→ every re-push downloads
            // and inserts ANOTHER copy of the message) until it receives a NotifyRespInd
            // with status Retrieved. AcknowledgeInd alone never closes the transaction,
            // which caused 30+ duplicate copies of one sent image on the receiver.
            sendNotifyResponse(context, transactionId, subscriptionId, contentLocation)
            
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "Error handling downloaded MMS", e)
        }
    }
    
    /**
     * ✅ FIX M29 (pattern 1): permanent download failures.
     *
     * HTTP 400/404 means the message no longer exists on the MMSC (expired or already
     * retrieved) — per QKSMS/AOSP practice we DELETE the stale NotificationInd row so
     * neither our Layer-C guard nor any OEM retry logic keeps acting on it.
     * Any other failure is treated as transient: logged only, nothing mutated.
     */
    private fun handleFailedDownload(context: Context, httpStatus: Int, contentLocation: String) {
        try {
            if (contentLocation.isBlank()) return
            if (httpStatus == 400 || httpStatus == 404) {
                val selection = "${Telephony.Mms.MESSAGE_TYPE} = ? AND ${Telephony.Mms.CONTENT_LOCATION} = ?"
                val args = arrayOf(
                    com.google.android.mms.pdu_alt.PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND.toString(),
                    contentLocation
                )
                val deleted = context.contentResolver.delete(Telephony.Mms.CONTENT_URI, selection, args)
                DebugLogger.diagnostic(TAG, "🧹 [FIX M29] HTTP $httpStatus → deleted $deleted stale NotificationInd row(s)")
            } else {
                DebugLogger.diagnostic(TAG, "⏳ [FIX M29] Transient failure (http=$httpStatus) — NotificationInd left untouched")
            }
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "handleFailedDownload cleanup failed (non-critical)", e)
        }
    }

    /**
     * ✅ FIX M29 (pattern 2): mark the NotificationInd's retrieve-status as a terminal
     * error (RETRIEVE_STATUS_ERROR_END = 0xFF), mirroring AOSP DownloadRequest.persist
     * behavior for empty responses. Prevents the row from looking like a pending
     * retrieval to OEM stacks that scan pending messages.
     */
    private fun markRetrieveStatusTerminal(context: Context, contentLocation: String) {
        try {
            if (contentLocation.isBlank()) return
            val values = android.content.ContentValues().apply {
                put(Telephony.Mms.RETRIEVE_STATUS, com.google.android.mms.pdu_alt.PduHeaders.RETRIEVE_STATUS_ERROR_END)
            }
            val selection = "${Telephony.Mms.MESSAGE_TYPE} = ? AND ${Telephony.Mms.CONTENT_LOCATION} = ?"
            val args = arrayOf(
                com.google.android.mms.pdu_alt.PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND.toString(),
                contentLocation
            )
            val updated = context.contentResolver.update(Telephony.Mms.CONTENT_URI, values, selection, args)
            DebugLogger.diagnostic(TAG, "🏁 [FIX M29] Marked $updated NotificationInd row(s) retrieve-status = ERROR_END")
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "markRetrieveStatusTerminal failed (non-critical)", e)
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
                        DebugLogger.diagnostic(TAG, "Found MMS by transaction ID: ID=$messageId, tr_id=$transactionId")
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
                        DebugLogger.diagnostic(TAG, "Found recent MMS in system DB: ID=$messageId (${now - date}s ago)")
                        if (transactionId.isNotBlank()) {
                            DebugLogger.diagnostic(TAG, "⚠️ Could not match by transaction ID=$transactionId — using recency fallback")
                        }
                        notifyNewMms(context, messageId)
                    } else {
                        DebugLogger.diagnostic(TAG, "No recent MMS found (oldest is ${now - date}s ago)")
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "Error processing system MMS", e)
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
            
            DebugLogger.diagnostic(TAG, "✅ New MMS from: ${DebugLogger.maskPhoneNumber(phoneNumber)}, thread: $threadId, body: [${displayBody.length} chars]")
            
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
            DebugLogger.diagnostic(TAG, "Error notifying new MMS", e)
        }
    }
    
    /**
     * ✅ FIX M19: Send M-NotifyResp.ind (status=Retrieved) to close the MMS transaction.
     *
     * Per OMA-MMS-ENC the response to a successful retrieval of a NotificationInd is
     * NotifyRespInd with status Retrieved (132). The previous implementation sent
     * AcknowledgeInd (M-Acknowledge.ind) — only valid in deferred-retrieval AFTER a
     * NotifyResp — and posted it with locationUrl=null, i.e. to the default MMSC send
     * endpoint instead of the message's own Content-Location. The MMSC therefore never
     * marked the notification as retrieved and kept re-pushing it, so every push
     * triggered another download+persist cycle (the 30+ duplicates bug).
     */
    private fun sendNotifyResponse(
        context: Context,
        transactionId: String,
        subscriptionId: Int,
        contentLocation: String
    ) {
        try {
            if (transactionId.isBlank()) {
                DebugLogger.diagnostic(TAG, "No transaction ID for notify response")
                return
            }

            // Build M-NotifyResp.ind with status = Retrieved (132)
            val notifyRespInd = com.google.android.mms.pdu_alt.NotifyRespInd(
                com.google.android.mms.pdu_alt.PduHeaders.CURRENT_MMS_VERSION,
                transactionId.toByteArray(),
                com.google.android.mms.pdu_alt.PduHeaders.STATUS_RETRIEVED
            )

            val pduBytes = com.google.android.mms.pdu_alt.PduComposer(context, notifyRespInd).make()

            if (pduBytes != null && pduBytes.isNotEmpty()) {
                // Write to temp file
                val mmsDir = java.io.File(context.cacheDir, "mms").apply { mkdirs() }
                val respFile = java.io.File(mmsDir, "mms_notify_resp_${System.currentTimeMillis()}.dat")
                respFile.writeBytes(pduBytes)

                val respUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    respFile
                )

                // Grant permissions
                context.grantUriPermission(
                    "com.android.phone",
                    respUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // Post the NotifyRespInd. Prefer the message's own Content-Location as
                // the target URL; fall back to null (= platform default MMSC URL).
                val locationUrl = contentLocation.trim().takeIf { it.isNotBlank() && it.startsWith("http") }
                val smsManager = MmsUtils.getSmsManagerForSub(context, subscriptionId)
                smsManager.sendMultimediaMessage(
                    context,
                    respUri,
                    locationUrl,
                    null,
                    null // No need to track notify-resp result
                )

                DebugLogger.diagnostic(TAG, "✅ MMS NotifyRespInd (Retrieved) sent (transaction: $transactionId, url: ${locationUrl ?: "default MMSC"})")

                // Clean up response file after a short delay to allow sendMultimediaMessage to read it.
                // Intentionally uses an independent scope: the cleanup needs to outlive the
                // receiver's goAsync() timeout (25s) since sendMultimediaMessage may still
                // be reading the file. The file lives in cacheDir so it is reclaimed on reboot.
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(30_000L)
                    try {
                        if (respFile.exists()) respFile.delete()
                    } catch (_: Exception) {}
                }
            } else {
                DebugLogger.diagnostic(TAG, "Failed to compose NotifyRespInd PDU")
            }
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "Error sending MMS notify response (non-critical)", e)
            // NotifyResp failure is non-critical for local persistence - MMS is already received.
            // NOTE: if this keeps failing on some carriers the MMSC may re-push the
            // notification; the dedup guard added in FIX M20 prevents duplicate rows then.
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
            DebugLogger.diagnostic(TAG, "Error counting MMS attachments", e)
            0
        }
    }
    
    private fun cleanupTempFile(filePath: String) {
        try {
            if (filePath.isNotBlank()) {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    file.delete()
                    DebugLogger.diagnostic(TAG, "Temp file cleaned up: $filePath")
                }
            }
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "Error cleaning up temp file", e)
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
                DebugLogger.diagnostic(TAG, "Error loading MMS contact photo", e)
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
            
            DebugLogger.diagnostic(TAG, "✅ MMS notification shown for: ${contactName ?: phoneNumber}")
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "Error showing MMS notification", e)
        }
    }
}
