package com.rasmi.purevon.data.repository

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.util.ErrorHandler
import com.rasmi.purevon.util.mms.ApnManager
import com.rasmi.purevon.util.mms.MmsConfiguration
import com.rasmi.purevon.util.mms.MobileDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Handles MMS message sending operations.
 * Extracted from MessageRepositoryImpl to reduce class size (~530 lines).
 *
 * The mmsSendInProgress flag is communicated back to the parent via [setSendInProgress] callback,
 * which allows the parent's ContentObserver to skip during send.
 */
internal class MmsSender(
    private val context: Context,
    private val imageCompressor: com.rasmi.purevon.util.ImageCompressor,
    private val apnManager: ApnManager,
    private val setSendInProgress: (Boolean) -> Unit
) {

    companion object {
        private const val TAG = "MmsSender"
        
        // MMS PDU constants (from OMA-WAP-MMS spec)
        private const val MESSAGE_TYPE_SEND_REQ = 128  // m-send-req
        private const val PRIORITY_NORMAL = 129
        private const val MMS_VERSION_1_2 = 18  // 0x12
        private const val MMS_YES = 128  // Yes for delivery/read report
        private const val MMS_NO = 129   // No for delivery/read report

        /**
         * Returns true when the device is likely running a custom ROM (MIUI, ColorOS, etc.)
         * where the system MMS service is known to be unreliable.
         * On stock/near-stock Android (Pixel, Samsung One UI, etc.) the system path is preferred.
         */
        fun isLikelyCustomRom(): Boolean {
            val manufacturer = android.os.Build.MANUFACTURER.lowercase()
            // Xiaomi / Redmi / POCO run MIUI which breaks system MMS service
            if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) return true
            // OnePlus OxygenOS (early versions) had MMS issues; later near-stock
            if (manufacturer.contains("oneplus")) {
                val version = android.os.Build.VERSION.RELEASE.split(".").firstOrNull()?.toIntOrNull() ?: 0
                return version < 11 // OxygenOS pre-Android 11 had issues
            }
            // OPPO / Realme ColorOS
            if (manufacturer.contains("oppo") || manufacturer.contains("realme")) return true
            // Vivo FuntouchOS
            if (manufacturer.contains("vivo")) return true
            return false
        }
    }

    /**
     * Send an MMS message.
     * @param getOrCreateThreadId function to get/create thread ID for the phone number
     * @param onComplete callback with threadId for post-send sync (syncMessages + syncConversations)
     */
    suspend fun sendMms(
        phoneNumber: String,
        message: String?,
        attachmentUris: List<String>,
        simSlot: Int?,
        getOrCreateThreadId: (String) -> Long,
        onComplete: suspend (threadId: Long) -> Unit
    ): MessageResult<Long> {
        // Validate permissions
        val permissionCheck = ErrorHandler.checkPermission(context, Manifest.permission.SEND_SMS)
        if (permissionCheck.isFailure) {
            return MessageResult.Failure(permissionCheck.errorOrNull() ?: MessageError.PermissionError(Manifest.permission.SEND_SMS, "Permission denied"))
        }

        // Validate phone number
        val numberValidation = ErrorHandler.validatePhoneNumber(phoneNumber)
        if (numberValidation.isFailure) {
            return MessageResult.Failure(numberValidation.errorOrNull() ?: MessageError.InvalidNumberError(phoneNumber, "Invalid phone number"))
        }

        // Check network for MMS
        val networkCheck = ErrorHandler.checkNetwork(context, requiresWifi = false)
        if (networkCheck.isFailure) {
            return MessageResult.Failure(networkCheck.errorOrNull() ?: MessageError.NetworkError("No network connection"))
        }

        // Check MMS rate limit (max 100 MMS per hour, tracked in system Rate table)
        val rateLimitInfo = com.rasmi.purevon.util.mms.MmsRateController.checkRateLimit(context)
        if (rateLimitInfo != null) {
            return MessageResult.Failure(
                MessageError.RateLimitError(
                    retryAfterSeconds = 60,
                    message = "${rateLimitInfo.messagesSent}/${rateLimitInfo.limit} MMS sent in last ${rateLimitInfo.windowMinutes} minutes"
                )
            )
        }

        return withContext(Dispatchers.IO) { var messageUri: Uri? = null; try {
            val threadId = getOrCreateThreadId(phoneNumber)

            // Dual SIM support — validate subscription is still active
            val subscriptionId = run {
                val requestedSubId = simSlot ?: SmsManager.getDefaultSmsSubscriptionId()
                // ✅ FIX: Reject INVALID_SUBSCRIPTION_ID (Integer.MAX_VALUE) immediately
                val safeSubId = if (requestedSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || requestedSubId <= 0) -1 else requestedSubId
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeSubIds = try {
                    subManager?.activeSubscriptionInfoList?.map { it.subscriptionId } ?: emptyList()
                } catch (e: SecurityException) {
                    Log.d(TAG, "Cannot read active subscriptions, using default", e)
                    emptyList()
                }
                if (activeSubIds.isNotEmpty() && safeSubId !in activeSubIds) {
                    val fallback = SmsManager.getDefaultSmsSubscriptionId()
                        .takeIf { it > 0 && it != SubscriptionManager.INVALID_SUBSCRIPTION_ID && it in activeSubIds }
                        ?: activeSubIds.first()
                    Log.d(TAG, "⚠️ Saved subscription ID $safeSubId is INACTIVE! Active: $activeSubIds. Falling back to $fallback")
                    fallback
                } else {
                    safeSubId
                }
            }

            Log.d(TAG, "══════ MMS SEND START ══════")
            Log.d(TAG, "📤 Sending MMS:")
            Log.d(TAG, "  - Phone: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
            Log.d(TAG, "  - Message length: ${message?.length ?: 0}")
            Log.d(TAG, "  - Attachments: ${attachmentUris.size}")
            Log.d(TAG, "  - SIM Slot: $simSlot")
            Log.d(TAG, "  - Subscription ID: $subscriptionId")

            // Suppress content observer only during network send phase, not during compression
            // ✅ FIX M11: Moved from before DB insert to before network send to reduce suppression window

            // Insert MMS message into system database
            val timestamp = System.currentTimeMillis()
            val mmsUri = Uri.parse("content://mms")

            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "📤 Starting MMS Creation Process")
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "📍 Phone: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
            Log.d(TAG, "📍 Thread ID: $threadId")
            Log.d(TAG, "📍 Subscription ID: $subscriptionId")
            Log.d(TAG, "📍 Message: [${message?.length ?: 0} chars]")
            Log.d(TAG, "📍 Attachments count: ${attachmentUris.size}")
            attachmentUris.forEachIndexed { index, uri ->
                Log.d(TAG, "   Attachment $index: $uri")
            }

            // Insert MMS message part
            val mmsValues = ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, timestamp / 1000)
                put(Telephony.Mms.DATE_SENT, timestamp / 1000)
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
                put(Telephony.Mms.MESSAGE_TYPE, MESSAGE_TYPE_SEND_REQ)
                put(Telephony.Mms.MESSAGE_CLASS, "personal")
                // Delivery/read reports are disabled by default: many carriers worldwide
                // reject MMS if these flags are set, or generate unwanted reply PDUs.
                // Gulf carriers that require them can still receive via system MMS service.
                put(Telephony.Mms.DELIVERY_REPORT, MMS_NO)
                put(Telephony.Mms.READ_REPORT, MMS_NO)

                // CRITICAL: Set Subject - MMSC (e.g., Mobily) requires this header
                // Limit to 40 bytes (not chars) to stay within MMSC byte-length limits for Arabic/CJK
                val mmsSubject = run {
                    val raw = message?.ifBlank { "MMS" } ?: "MMS"
                    val bytes = raw.toByteArray(Charsets.UTF_8)
                    if (bytes.size <= 40) raw
                    else String(bytes, 0, 40, Charsets.UTF_8).trimEnd('\uFFFD')
                }
                put(Telephony.Mms.SUBJECT, mmsSubject)
                put(Telephony.Mms.SUBJECT_CHARSET, 106) // UTF-8

                // CRITICAL: Set Expiry - MMSC requires this to deliver (7 days)
                put(Telephony.Mms.EXPIRY, 604800L)

                put(Telephony.Mms.MMS_VERSION, MMS_VERSION_1_2)
                put(Telephony.Mms.PRIORITY, PRIORITY_NORMAL)

                // CRITICAL: Set Transaction-ID - required by PduComposer
                val transactionId = "T${System.currentTimeMillis()}"
                put(Telephony.Mms.TRANSACTION_ID, transactionId)

                // Set Content-Type for MMS
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")

                // Content-Location: omit rather than set empty to avoid carrier issues

                // ✅ FIX #37: Use openAssetFileDescriptor for accurate size estimation
                // Note: This is a pre-compression estimate; actual size may differ after compression
                var totalSize = message?.toByteArray()?.size?.toLong() ?: 0L
                attachmentUris.forEach { uriString ->
                    try {
                        val uri = Uri.parse(uriString)
                        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                            afd.length.takeIf { it >= 0 }
                        } ?: context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
                        totalSize += size
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to get size for attachment", e)
                    }
                }
                put(Telephony.Mms.MESSAGE_SIZE, totalSize.toInt())
                Log.d(TAG, "📏 MMS estimated size: $totalSize bytes (pre-compression)")

                // Dual SIM subscription_id
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
                }
            }

            Log.d(TAG, "📝 Inserting MMS into database...")
            messageUri = context.contentResolver.insert(mmsUri, mmsValues)
            if (messageUri == null) {
                // Retry insert once after short delay
                Log.w(TAG, "⚠️ MMS insert failed, retrying once after 500ms...")
                delay(500)
                messageUri = context.contentResolver.insert(mmsUri, mmsValues)
            }
            if (messageUri == null) {
                Log.e(TAG, "❌ Failed to insert MMS into database after retry")
                return@withContext MessageResult.Failure(
                    MessageError.DatabaseError("insert", "Failed to insert MMS into database")
                )
            }

            val messageId = messageUri.lastPathSegment?.toLongOrNull()
            if (messageId == null) {
                return@withContext MessageResult.Failure(
                    MessageError.DatabaseError("parse", "Failed to parse MMS ID")
                )
            }
            Log.d(TAG, "✅ MMS inserted successfully! URI: $messageUri  ID: $messageId")

            // Insert recipient address
            val addrUri = Uri.parse("content://mms/$messageId/addr")
            val addrValues = ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, phoneNumber)
                put(Telephony.Mms.Addr.CHARSET, 106)
                put(Telephony.Mms.Addr.TYPE, 151) // TO
            }
            val addrInsertResult = context.contentResolver.insert(addrUri, addrValues)
            if (addrInsertResult == null) {
                Log.e(TAG, "❌ Failed to insert recipient address for MMS $messageId")
                throw Exception("Failed to insert recipient address")
            }

            // Insert FROM address using insert-address-token
            val fromValues = ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, "insert-address-token")
                put(Telephony.Mms.Addr.CHARSET, 106)
                put(Telephony.Mms.Addr.TYPE, 137) // FROM
            }
            context.contentResolver.insert(addrUri, fromValues)
            Log.d(TAG, "📬 FROM address inserted (type=137, address=insert-address-token)")

            // Insert text part if exists
            val partUri = Uri.parse("content://mms/$messageId/part")
            var partIndex = 0
            if (!message.isNullOrBlank()) {
                val textPartValues = ContentValues().apply {
                    put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                    put(Telephony.Mms.Part.CHARSET, 106) // UTF-8
                    put(Telephony.Mms.Part.FILENAME, "text.txt")
                    put(Telephony.Mms.Part.NAME, "text.txt")
                    put(Telephony.Mms.Part.CONTENT_LOCATION, "text_${partIndex}.txt")
                    put(Telephony.Mms.Part.CONTENT_ID, "<text_$partIndex>")
                    put(Telephony.Mms.Part.TEXT, message)
                }
                context.contentResolver.insert(partUri, textPartValues)
                partIndex++
            }

            // Track SMIL parts — uses SmilBuilder.SmilPart for smart slide grouping
            val smilParts = mutableListOf<com.rasmi.purevon.util.mms.SmilBuilder.SmilPart>()
            if (!message.isNullOrBlank()) {
                smilParts.add(com.rasmi.purevon.util.mms.SmilBuilder.SmilPart("text_0.txt", "text/plain"))
            }

            // Process attachments
            Log.d(TAG, "📎 Processing ${attachmentUris.size} attachments...")
            attachmentUris.forEachIndexed { index, uriString ->
                Log.d(TAG, "   Processing attachment $index: $uriString")
                try {
                    var uri = Uri.parse(uriString)
                    val contentResolverType = context.contentResolver.getType(uri)

                    var mimeType = if (contentResolverType == null || contentResolverType == "application/octet-stream") {
                        val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uriString)
                        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
                    } else {
                        contentResolverType
                    }

                    if (uriString.lowercase().endsWith(".m4a") && (mimeType == "application/octet-stream" || mimeType == null)) {
                        mimeType = "audio/mp4"
                    }

                    // Compress images
                    if (mimeType.startsWith("image/")) {
                        val mmsConfig = MmsConfiguration.getMmsConfig(context, subscriptionId)
                        // ✅ FIX: Use carrier's actual max size with reasonable floor/ceiling
                        // Most modern carriers support 1MB+, use 80% of carrier limit for image
                        // but ensure at least 600KB and respect carrier max
                        val carrierMaxBytes = mmsConfig.maxMessageSize
                        val maxImageBytes = (carrierMaxBytes * 0.80).toInt()
                            .coerceIn(600 * 1024, 1200 * 1024) // 600KB min, 1.2MB max
                        // ✅ FIX: Use carrier's actual image dimensions, don't cap to tiny values
                        val maxW = mmsConfig.maxImageWidth.coerceIn(1024, 1920)
                        val maxH = mmsConfig.maxImageHeight.coerceIn(1024, 1920)
                        Log.d(TAG, "🖼️ Compressing image for MMS (target: ${maxImageBytes / 1024}KB, maxDim: ${maxW}x${maxH})...")
                        val compressedUri = imageCompressor.compressImageForMms(
                            uri,
                            maxSizeBytes = maxImageBytes,
                            maxWidth = maxW,
                            maxHeight = maxH
                        )
                        if (compressedUri != null) {
                            uri = compressedUri
                            Log.d(TAG, "✅ Image compressed for MMS, new URI: $compressedUri")
                        } else {
                            Log.d(TAG, "⚠️ MMS image compression failed, using original")
                        }
                    }

                    // Compress videos
                    if (mimeType.startsWith("video/")) {
                        Log.d(TAG, "📹 Compressing video before sending...")
                        try {
                            val videoCompressor = com.rasmi.purevon.util.VideoCompressor(context)
                            val compressedVideoUri = videoCompressor.compressVideoForMms(uri)
                            if (compressedVideoUri != null && compressedVideoUri != uri) {
                                uri = compressedVideoUri
                                mimeType = "video/mp4" // ✅ FIX: Re-encoded as H.264/MP4, not 3gpp
                                Log.d(TAG, "✅ Video compressed, new URI: $compressedVideoUri")
                            } else if (compressedVideoUri != null) {
                                // Video was already small enough, keep original MIME
                                Log.d(TAG, "✅ Video already within limits, sending as-is")
                            } else {
                                Log.d(TAG, "⚠️ Video compression failed, sending original")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error compressing video", e)
                        }
                    }

                    // ✅ FIX #52: Use use{} block to prevent InputStream resource leak
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        // ✅ FIX: Use openAssetFileDescriptor for accurate file size
                        // inputStream.available() can return incorrect values for content:// URIs
                        val fileSize = try {
                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                afd.length.takeIf { it >= 0 } ?: inputStream.available().toLong()
                            } ?: inputStream.available().toLong()
                        } catch (e: Exception) {
                            inputStream.available().toLong()
                        }
                        Log.d(TAG, "     Type: $mimeType, Size: $fileSize bytes")

                        val fileExtension = when {
                            mimeType.startsWith("image/") -> when (mimeType) {
                                "image/jpeg", "image/jpg" -> ".jpg"
                                "image/png" -> ".png"
                                "image/gif" -> ".gif"
                                else -> ".img"
                            }
                            mimeType.startsWith("video/") -> ".mp4"
                            mimeType.startsWith("audio/") -> {
                                // ✅ FIX: Support .m4a files (AAC encoded)
                                if (uriString.lowercase().endsWith(".m4a")) ".m4a"
                                else ".mp3"
                            }
                            else -> ""
                        }
                        val filename = "attachment_${partIndex}$fileExtension"
                        val contentId = "attachment_$partIndex"

                        val attachmentValues = ContentValues().apply {
                            put(Telephony.Mms.Part.CONTENT_TYPE, mimeType)
                            put(Telephony.Mms.Part.FILENAME, filename)
                            put(Telephony.Mms.Part.NAME, filename)
                            put(Telephony.Mms.Part.CONTENT_LOCATION, filename)
                            put(Telephony.Mms.Part.CONTENT_ID, "<$contentId>")
                            if (mimeType.startsWith("text/")) {
                                put(Telephony.Mms.Part.CHARSET, 106)
                            }
                        }

                        val partInsertUri = context.contentResolver.insert(partUri, attachmentValues)
                        if (partInsertUri != null) {
                            Log.d(TAG, "     ✅ Part inserted: $partInsertUri")
                            context.contentResolver.openOutputStream(partInsertUri)?.use { output ->
                                val bytesCopied = inputStream.copyTo(output)
                                Log.d(TAG, "     ✅ Copied $bytesCopied bytes to part")
                            }
                            smilParts.add(com.rasmi.purevon.util.mms.SmilBuilder.SmilPart(filename, mimeType))
                            partIndex++
                        } else {
                            Log.e(TAG, "     ❌ Failed to insert part")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error attaching file $index: ${e.message}", e)
                }
            }

            // Add SMIL presentation — uses SmilBuilder for smart slide grouping
            try {
                val smilContent = com.rasmi.purevon.util.mms.SmilBuilder.buildSmil(smilParts)
                Log.d(TAG, "📋 SMIL: $smilContent")
                val smilPartValues = ContentValues().apply {
                    put(Telephony.Mms.Part.CONTENT_TYPE, "application/smil")
                    put(Telephony.Mms.Part.CONTENT_ID, "<smil>")
                    put(Telephony.Mms.Part.CONTENT_LOCATION, "smil.xml")
                    put(Telephony.Mms.Part.FILENAME, "smil.xml")
                    put(Telephony.Mms.Part.NAME, "smil.xml")
                    put(Telephony.Mms.Part.TEXT, smilContent)
                }
                context.contentResolver.insert(partUri, smilPartValues)
                Log.d(TAG, "✅ SMIL part added (${smilParts.size} refs)")
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ Failed to add SMIL part", e)
            }

            // Move to outbox
            val updateValues = ContentValues().apply {
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            }
            context.contentResolver.update(messageUri, updateValues, null, null)

            // ✅ FIX #37 (continued): Update MESSAGE_SIZE with actual post-compression PDU size
            // The pre-insert estimate may differ significantly after image/video compression
            try {
                val actualParts = context.contentResolver.query(
                    partUri, arrayOf(Telephony.Mms.Part._DATA, Telephony.Mms.Part.TEXT),
                    null, null, null
                )
                var actualSize = 0L
                actualParts?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val data = cursor.getString(0)
                        val text = cursor.getString(1)
                        if (data != null) {
                            actualSize += try { java.io.File(data).length() } catch (_: Exception) { 0L }
                        } else if (text != null) {
                            actualSize += text.toByteArray().size
                        }
                    }
                }
                if (actualSize > 0) {
                    val sizeUpdate = ContentValues().apply { put(Telephony.Mms.MESSAGE_SIZE, actualSize.toInt()) }
                    context.contentResolver.update(messageUri, sizeUpdate, null, null)
                    Log.d(TAG, "📏 MMS actual size (post-compression): $actualSize bytes")
                }
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ Failed to update MESSAGE_SIZE post-compression", e)
            }

            // ✅ FIXED: Removed automatic mobile data toggle — it was dangerous because:
            // 1) If user manually enables data during the 30s window, code would turn it OFF
            // 2) Modifying connectivity state without user consent is bad UX
            // Instead, just check and warn the user
            val isMobileDataEnabled = MobileDataManager.isMobileDataEnabled(context)
            Log.d(TAG, "📶 Mobile data enabled: $isMobileDataEnabled")
            if (isMobileDataEnabled == false) {
                Log.d(TAG, "⚠️ Mobile data is disabled — MMS may fail. User should enable it.")
            }
            if (MobileDataManager.isAirplaneModeOn(context)) {
                Log.e(TAG, "❌ Device is in airplane mode — MMS cannot be sent")
            }

            // Build PDU
            Log.d(TAG, "📝 Building MMS PDU...")
            val pduBytes: ByteArray
            try {
                val pduPersister = com.google.android.mms.pdu_alt.PduPersister.getPduPersister(context)
                val sendReq = pduPersister.load(messageUri) as? com.google.android.mms.pdu_alt.SendReq
                    ?: throw Exception("Failed to load MMS PDU")

                Log.d(TAG, "📋 SendReq headers:")
                Log.d(TAG, "   Subject: [${sendReq.subject?.string?.length ?: 0} chars]")
                Log.d(TAG, "   From: ${DebugLogger.maskPhoneNumber(sendReq.from?.string ?: "NULL")}")
                Log.d(TAG, "   To: ${sendReq.to?.joinToString { DebugLogger.maskPhoneNumber(it.string) } ?: "NULL"}")
                Log.d(TAG, "   TransactionId: ${String(sendReq.transactionId ?: byteArrayOf())}")
                Log.d(TAG, "   ContentType: ${String(sendReq.contentType ?: byteArrayOf())}")

                pduBytes = com.google.android.mms.pdu_alt.PduComposer(context, sendReq).make()
                    ?: throw Exception("Failed to compose MMS PDU")
                Log.d(TAG, "✅ PDU composed: ${pduBytes.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error building PDU", e)
                throw e
            }

            // Resolve APN settings
            val apnSettings = apnManager.getApnSettings(subscriptionId)
            val mmscUrl = apnSettings?.mmsc
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "📡 APN RESOLUTION:")
            Log.d(TAG, "   Carrier: ${apnSettings?.carrier ?: "NONE"}")
            Log.d(TAG, "   MMSC URL: ${mmscUrl ?: "NULL (system default)"}")
            Log.d(TAG, "   Proxy: ${apnSettings?.proxy ?: "none"}:${apnSettings?.port ?: "-"}")
            Log.d(TAG, "═══════════════════════════════════════")

            // ========================================
            // SEND WITH EXPONENTIAL BACKOFF RETRY
            // Strategy 1 (Direct HTTP) → Strategy 2 (System) → retry with backoff
            // ========================================
            setSendInProgress(true)
            var directSendSuccess = false
            val preferDirectHttp = isLikelyCustomRom()
            Log.d(TAG, "🔍 ROM check: preferDirectHttp=$preferDirectHttp (manufacturer=${android.os.Build.MANUFACTURER})")

            // Wrap entire send logic in exponential backoff retry scheduler
            val operationKey = "mms-send-${messageId}"
            val retryResult = com.rasmi.purevon.util.mms.MmsRetryScheduler.executeWithRetry(
                operationKey = operationKey,
                isRetryable = { e ->
                    // Retry on network/IO errors, but not on permission or invalid number errors
                    e !is SecurityException &&
                    !e.message.orEmpty().contains("permission", ignoreCase = true) &&
                    !e.message.orEmpty().contains("invalid", ignoreCase = true)
                }
            ) {
                // Reset for each attempt
                var attemptDirectSuccess = false

                // STRATEGY 1: Direct HTTP POST to MMSC (custom ROMs only)
                if (!mmscUrl.isNullOrBlank() && preferDirectHttp) {
                    try {
                        Log.d(TAG, "🚀 STRATEGY 1: Direct HTTP POST to MMSC: $mmscUrl")
                        val httpResult = sendMmsViaDirectHttp(
                            pduBytes = pduBytes,
                            mmscUrl = mmscUrl,
                            proxy = apnSettings?.proxy,
                            port = apnSettings?.port?.toIntOrNull() ?: 80,
                            messageUri = messageUri,
                            subscriptionId = subscriptionId
                        )
                        if (httpResult.accepted) {
                            attemptDirectSuccess = true
                            if (httpResult.confirmed) {
                                Log.d(TAG, "✅✅✅ DIRECT HTTP: confirmed by MMSC SendConf ✅✅✅")
                                val sentValues = ContentValues().apply {
                                    put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
                                }
                                context.contentResolver.update(messageUri, sentValues, null, null)
                            } else {
                                Log.w(TAG, "⚠️ DIRECT HTTP: ambiguous 200 — not marking SENT until confirmed")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ Direct HTTP POST failed: ${e.message}", e)
                        if (!isRetryableException(e)) throw e // Re-throw non-retryable
                    }
                }

                // STRATEGY 2: System SmsManager (fallback)
                if (!attemptDirectSuccess) {
                    Log.d(TAG, "🔄 STRATEGY 2: System sendMultimediaMessage() fallback...")
                    val sendFile = java.io.File(context.cacheDir, "send.${System.currentTimeMillis()}.dat")
                    try {
                        java.io.FileOutputStream(sendFile).use { it.write(pduBytes) }

                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", sendFile
                        )

                        try {
                            context.grantUriPermission(
                                "com.android.mms.service", fileUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            Log.d(TAG, "✅ URI permission granted to com.android.mms.service")
                        } catch (e: Exception) {
                            Log.d(TAG, "⚠️ URI permission grant failed (non-fatal): ${e.message}")
                        }

                        val sentIntent = PendingIntent.getBroadcast(
                            context,
                            (messageId and 0x7FFF_FFFFL).toInt(),
                            Intent(context, com.rasmi.purevon.receiver.MmsSentReceiver::class.java).apply {
                                putExtra("message_id", messageId)
                                putExtra("message_uri", messageUri.toString())
                                putExtra("file_path", sendFile.absolutePath)
                            },
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )

                        val validSubForSms = subscriptionId.takeIf { it > 0 && it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            if (validSubForSms != null) context.getSystemService(SmsManager::class.java).createForSubscriptionId(validSubForSms)
                            else context.getSystemService(SmsManager::class.java)
                        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1 && validSubForSms != null) {
                            @Suppress("DEPRECATION")
                            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsManager.getDefault()
                        }

                        Log.d(TAG, "📤 Calling sendMultimediaMessage() with system-default MMSC...")
                        smsManager.sendMultimediaMessage(
                            context,
                            fileUri,
                            null,
                            null,
                            sentIntent
                        )
                        Log.d(TAG, "✅ MMS handed to system for sending")
                        attemptDirectSuccess = true
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ sendMultimediaMessage failed: ${e.message}", e)
                        sendFile.delete()
                        if (!isRetryableException(e)) throw e // Re-throw non-retryable
                        throw e // Re-throw for retry scheduler
                    }
                }

                if (!attemptDirectSuccess) {
                    throw Exception("All send strategies failed — will retry with backoff")
                }

                attemptDirectSuccess
            }

            // Handle final result after all retries
            if (retryResult.isFailure) {
                directSendSuccess = false
                Log.e(TAG, "❌ MMS send failed after all retry attempts: ${retryResult.exceptionOrNull()?.message}")
                val failValues = ContentValues().apply {
                    put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED)
                }
                context.contentResolver.update(messageUri, failValues, null, null)
            } else {
                directSendSuccess = retryResult.getOrDefault(false)
            }

            // ✅ FIXED: Removed orphan CoroutineScope for mobile data restore
            // (mobile data toggle was removed entirely — see above)

            // ✅ FIXED: Use withContext instead of orphan CoroutineScope
            if (retryResult.isSuccess) {
                try {
                    onComplete(threadId)
                    Log.d(TAG, "✅ Synced after MMS send for thread $threadId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing after MMS send", e)
                }
            } else {
                Log.d(TAG, "⏭️ Skipping onComplete — send failed, no sync needed")
            }

            // Clean up old temp image files after successful send
            try {
                imageCompressor.cleanupTempFiles()
            } catch (e: Exception) {
                Log.w(TAG, "Temp file cleanup failed", e)
            }

            // Record successful send in rate table (max 100 MMS/hour)
            try {
                com.rasmi.purevon.util.mms.MmsRateController.recordSend(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record MMS send in rate table", e)
            }

            Log.d(TAG, "══════ MMS SEND COMPLETE ══════ messageId=$messageId")
            MessageResult.Success(messageId)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for MMS", e)
            // Mark MMS as FAILED so it doesn't stay in OUTBOX forever
            try {
                val failValues = android.content.ContentValues().apply {
                    put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED)
                }
                messageUri?.let { context.contentResolver.update(it, failValues, null, null) }
            } catch (cleanup: Exception) {
                Log.w(TAG, "Failed to mark MMS as FAILED", cleanup)
            }
            MessageResult.Failure(MessageError.PermissionError(Manifest.permission.SEND_SMS, e.message ?: ""))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send MMS", e)
            // Mark MMS as FAILED so it doesn't stay in OUTBOX forever
            try {
                val failValues = android.content.ContentValues().apply {
                    put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED)
                }
                messageUri?.let { context.contentResolver.update(it, failValues, null, null) }
            } catch (cleanup: Exception) {
                Log.w(TAG, "Failed to mark MMS as FAILED", cleanup)
            }
            MessageResult.Failure(MessageError.MmsError(e.message ?: "Unknown MMS error"))
        } finally {
            // ✅ FIX #19: Single finally block guarantees flag reset on all paths
            setSendInProgress(false)
        } }
    }

    /**
     * Result of a direct HTTP MMS send attempt.
     * @param accepted  HTTP-level acceptance (2xx). Prevents double-send via system path.
     * @param confirmed MMSC returned a valid SendConf with RESPONSE_STATUS_OK — safe to mark SENT immediately.
     *                  When false and accepted is true, keep msg_box in OUTBOX until confirmed externally.
     */
    private data class DirectHttpResult(val accepted: Boolean, val confirmed: Boolean) {
        companion object {
            val REJECTED = DirectHttpResult(accepted = false, confirmed = false)
        }
    }

    /**
     * Determines if an exception is retryable (network/IO issues) vs permanent (permission/invalid).
     * Used by [MmsRetryScheduler] to decide whether to attempt another retry.
     */
    private fun isRetryableException(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: return true
        // Non-retryable: permission, invalid number, security
        if (e is SecurityException) return false
        if (msg.contains("permission")) return false
        if (msg.contains("invalid number")) return false
        if (msg.contains("invalid subscription")) return false
        // Retryable: network, IO, connection, timeout
        return true
    }

    /**
     * Send MMS via direct HTTP POST to MMSC.
     * This bypasses the system MMS service which may silently fail on MIUI/custom ROMs.
     * Uses ConnectivityManager to acquire a cellular MMS network before sending.
     *
     * @return DirectHttpResult indicating whether the MMSC accepted and/or confirmed the message.
     */
    private suspend fun sendMmsViaDirectHttp(
        pduBytes: ByteArray,
        mmscUrl: String,
        proxy: String?,
        port: Int,
        messageUri: Uri,
        subscriptionId: Int
    ): DirectHttpResult {
        var network: android.net.Network? = null
        var connection: java.net.HttpURLConnection? = null
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

        try {
            // ✅ Request MMS-capable cellular network (non-blocking via suspendCancellableCoroutine)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val networkRequest = android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_MMS)
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build()

                val acquiredNetwork = try {
                    kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                        kotlinx.coroutines.suspendCancellableCoroutine<android.net.Network?> { cont ->
                            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                                override fun onAvailable(net: android.net.Network) {
                                    // ✅ FIX 2.13: Unregister callback on success path too
                                    try { connectivityManager.unregisterNetworkCallback(this) } catch (_: Exception) {}
                                    if (cont.isActive) cont.resume(net) {}
                                }
                                override fun onUnavailable() {
                                    try { connectivityManager.unregisterNetworkCallback(this) } catch (_: Exception) {}
                                    if (cont.isActive) cont.resume(null) {}
                                }
                            }

                            cont.invokeOnCancellation {
                                try { connectivityManager.unregisterNetworkCallback(callback) } catch (_: Exception) {}
                            }

                            try {
                                connectivityManager.requestNetwork(networkRequest, callback)
                            } catch (e: SecurityException) {
                                Log.d(TAG, "⚠️ Cannot request MMS network (SecurityException): ${e.message}")
                                if (cont.isActive) cont.resume(null) {}
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "⚠️ Error acquiring MMS network: ${e.message}")
                    null
                }

                if (acquiredNetwork == null) {
                    Log.d(TAG, "⚠️ Could not acquire MMS network in 30s, trying with active network")
                } else {
                    network = acquiredNetwork
                    Log.d(TAG, "✅ MMS cellular network acquired: $network")
                }
            }

            // ✅ HTTP POST to MMSC
            Log.d(TAG, "📤 HTTP POST to MMSC: $mmscUrl (proxy=${proxy ?: "none"}:$port)")
            val url = java.net.URL(mmscUrl)

            connection = if (!proxy.isNullOrBlank() && proxy != "none") {
                val proxyAddr = java.net.InetSocketAddress(proxy, port)
                val httpProxy = java.net.Proxy(java.net.Proxy.Type.HTTP, proxyAddr)
                if (network != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    // Open connection through MMS network
                    network.openConnection(url, httpProxy) as java.net.HttpURLConnection
                } else {
                    url.openConnection(httpProxy) as java.net.HttpURLConnection
                }
            } else {
                if (network != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    network.openConnection(url) as java.net.HttpURLConnection
                } else {
                    url.openConnection() as java.net.HttpURLConnection
                }
            }

            connection.doOutput = true
            connection.doInput = true
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/vnd.wap.mms-message")
            connection.setRequestProperty("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic")
            connection.setRequestProperty("Content-Length", pduBytes.size.toString())
            connection.connectTimeout = 60_000
            connection.readTimeout = 60_000

            // Add carrier-specific User-Agent
            val mmsConfig = MmsConfiguration.getMmsConfig(context, subscriptionId)
            if (mmsConfig.userAgent.isNotEmpty()) {
                connection.setRequestProperty("User-Agent", mmsConfig.userAgent)
            }
            if (mmsConfig.uaProfUrl.isNotEmpty()) {
                connection.setRequestProperty("x-wap-profile", mmsConfig.uaProfUrl)
            }

            Log.d(TAG, "📤 Sending ${pduBytes.size} bytes to MMSC...")
            connection.outputStream.use { it.write(pduBytes) }

            val responseCode = connection.responseCode
            Log.d(TAG, "📬 MMSC HTTP response: $responseCode")

            if (responseCode in 200..299) {
                val responseBytes = try {
                    connection.inputStream.readBytes()
                } catch (e: Exception) {
                    Log.d(TAG, "⚠️ Could not read response body: ${e.message}")
                    null
                }

                if (responseBytes != null && responseBytes.isNotEmpty()) {
                    Log.d(TAG, "📬 MMSC response: ${responseBytes.size} bytes")
                    try {
                        val responsePdu = com.google.android.mms.pdu_alt.PduParser(responseBytes, false).parse()
                        if (responsePdu is com.google.android.mms.pdu_alt.SendConf) {
                            val status = responsePdu.responseStatus
                            Log.d(TAG, "📬 MMSC SendConf status: $status (OK=${com.google.android.mms.pdu_alt.PduHeaders.RESPONSE_STATUS_OK})")
                            return if (status == com.google.android.mms.pdu_alt.PduHeaders.RESPONSE_STATUS_OK) {
                                DirectHttpResult(accepted = true, confirmed = true)
                            } else {
                                Log.e(TAG, "❌ MMSC rejected with status: $status")
                                DirectHttpResult.REJECTED
                            }
                        } else {
                            // MMSC returned HTTP 200 but not a proper SendConf PDU.
                            // Accept to prevent double-send; do NOT mark SENT yet.
                            Log.w(TAG, "📬 MMSC returned non-SendConf body — accepted but unconfirmed")
                            return DirectHttpResult(accepted = true, confirmed = false)
                        }
                    } catch (e: Exception) {
                        // Response body present but unparseable with HTTP 200.
                        // Accept to prevent double-send; confirmation pending.
                        Log.w(TAG, "📬 Could not parse MMSC response (HTTP 200) — accepted, unconfirmed")
                        return DirectHttpResult(accepted = true, confirmed = false)
                    }
                } else {
                    // Empty body with HTTP 200 — many carriers behave this way.
                    // Accept to prevent double-send; confirmation pending.
                    Log.w(TAG, "📬 Empty MMSC body with HTTP 200 — accepted, unconfirmed")
                    return DirectHttpResult(accepted = true, confirmed = false)
                }
            } else {
                Log.e(TAG, "❌ MMSC returned HTTP $responseCode")
                try {
                    val errorBody = connection.errorStream?.readBytes()?.let { String(it).take(200) }
                    Log.e(TAG, "   Error body: $errorBody")
                } catch (_: Exception) {}
            }

            return DirectHttpResult.REJECTED
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "❌ MMSC connection timed out", e)
            return DirectHttpResult.REJECTED
        } catch (e: java.io.IOException) {
            Log.e(TAG, "❌ MMSC I/O error: ${e.message}", e)
            return DirectHttpResult.REJECTED
        } catch (e: Exception) {
            Log.e(TAG, "❌ Direct HTTP send error: ${e.message}", e)
            return DirectHttpResult.REJECTED
        } finally {
            // ✅ FIXED: Always disconnect to prevent socket/connection pool leaks
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }
}
