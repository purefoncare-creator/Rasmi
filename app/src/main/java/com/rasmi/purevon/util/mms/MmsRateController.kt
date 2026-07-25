package com.rasmi.purevon.util.mms

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Rate limiter for outgoing MMS messages.
 * Inspired by QKSMS RateController but simplified for Purefon:
 * - Tracks MMS sends in the system Telephony.Mms.Rate content provider
 * - Limits to [MAX_MMS_PER_HOUR] messages per rolling 1-hour window
 * - Returns structured error instead of prompting user (cleaner for MVVM)
 *
 * Thread-safe: all public methods are synchronized.
 */
object MmsRateController {

    private const val TAG = "MmsRateController"

    /** Maximum MMS messages allowed per hour */
    private const val MAX_MMS_PER_HOUR = 100

    /** Rolling time window (1 hour in milliseconds) */
    private val ONE_HOUR_MS = TimeUnit.HOURS.toMillis(1)

    /**
     * Check if the user is allowed to send another MMS.
     * Queries the system Rate content provider for sends within the last hour.
     *
     * @return null if allowed, or [MmsRateLimitInfo] with details if limit is reached
     */
    @Synchronized
    fun checkRateLimit(context: Context): MmsRateLimitInfo? {
        val oneHourAgo = System.currentTimeMillis() - ONE_HOUR_MS
        val rateUri = Telephony.Mms.Rate.CONTENT_URI

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                rateUri,
                arrayOf("COUNT(*) AS rate_count"),
                "${Telephony.Mms.Rate.SENT_TIME} > ?",
                arrayOf(oneHourAgo.toString()),
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val count = cursor.getInt(0)
                if (count >= MAX_MMS_PER_HOUR) {
                    val info = MmsRateLimitInfo(
                        messagesSent = count,
                        limit = MAX_MMS_PER_HOUR,
                        windowMs = ONE_HOUR_MS
                    )
                    Log.w(TAG, "⚠️ MMS rate limit reached: $count/$MAX_MMS_PER_HOUR in last hour")
                    return info
                }
            }
        } catch (e: Exception) {
            // Rate table might not exist on some custom ROMs — fail open (allow send)
            Log.w(TAG, "Could not query rate table, allowing send", e)
        } finally {
            cursor?.close()
        }

        return null // Allowed
    }

    /**
     * Record a successful MMS send in the rate table.
     * Called after the message is confirmed sent by the MMSC or system SmsManager.
     */
    @Synchronized
    fun recordSend(context: Context) {
        val values = ContentValues(1).apply {
            put(Telephony.Mms.Rate.SENT_TIME, System.currentTimeMillis())
        }
        try {
            context.contentResolver.insert(Telephony.Mms.Rate.CONTENT_URI, values)
            Log.d(TAG, "✅ Recorded MMS send in rate table")
        } catch (e: Exception) {
            // Non-critical — don't block sending if rate tracking fails
            Log.w(TAG, "Could not record MMS send in rate table", e)
        }
    }

    /**
     * Get the number of MMS messages sent in the current hour window.
     * Useful for UI display (e.g., "87/100 MMS sent this hour").
     */
    @Synchronized
    fun getSendCount(context: Context): Int {
        val oneHourAgo = System.currentTimeMillis() - ONE_HOUR_MS
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                Telephony.Mms.Rate.CONTENT_URI,
                arrayOf("COUNT(*) AS rate_count"),
                "${Telephony.Mms.Rate.SENT_TIME} > ?",
                arrayOf(oneHourAgo.toString()),
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query send count", e)
        } finally {
            cursor?.close()
        }
        return 0
    }

    /**
     * Details about a rate limit violation.
     */
    data class MmsRateLimitInfo(
        val messagesSent: Int,
        val limit: Int,
        val windowMs: Long
    ) {
        val windowMinutes: Int get() = (windowMs / 60_000).toInt()
    }
}
