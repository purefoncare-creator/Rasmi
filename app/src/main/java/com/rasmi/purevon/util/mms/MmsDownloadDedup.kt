package com.rasmi.purevon.util.mms

import android.content.Context
import android.provider.Telephony
import com.rasmi.purevon.util.DebugLogger
import org.json.JSONObject

/**
 * ✅ FIX M20: Three-layer duplicate guard for incoming MMS.
 *
 * When the MMSC never receives a proper NotifyRespInd it re-pushes the WAP
 * notification periodically (up to 30+ times over ~24-48h). Every push used to
 * trigger a full download + persist cycle, inserting another copy of the SAME
 * message into the inbox. This store backs the guards that stop the duplicates:
 *
 *  Layer A — transaction-id lookup against content://mms (authoritative, survives
 *            reinstalls of nothing but covers same-tr_id pushes) [MmsDownloadedReceiver]
 *  Layer B — Content-Location memory with a 48h TTL (covers carriers that rotate
 *            tr_id on every re-push, and pushes whose PDU failed to parse so no
 *            tr_id was extracted at all)
 *  Layer C — early skip in MmsReceiver BEFORE downloadMultimediaMessage() is
 *            invoked, so repeated pushes don't even consume data
 *  Layer D — ✅ FIX M29: Message-ID header lookup (DB by m_id + prefs TTL). The
 *            M-Message-ID is assigned by the MMSC and stays IDENTICAL across every
 *            redelivery of the same message, even when tr_id and Content-Location
 *            both rotate. Strongest identity available per OMA-MMS-ENC. Pattern
 *            adopted from QKSMS RetrieveTransaction.isDuplicateMessage.
 *
 * Deliberately NOT DuplicateMessageFilter: that one dedups by body+address+time,
 * which fits SMS. An MMS duplicate is defined by its retrieval URL / transaction,
 * and the body may not even be parsed yet when we must decide whether to insert.
 */
object MmsDownloadDedup {

    private const val TAG = "MmsDownloadDedup"
    private const val PREFS_NAME = "mms_download_dedup"
    private const val KEY_LOCATIONS = "processed_locations"
    private const val KEY_MESSAGE_IDS = "processed_message_ids"

    /** MMSCs typically expire unretrieved notifications within 24-48h. */
    private const val TTL_MS = 48L * 60 * 60 * 1000

    /**
     * Layer A helper: find an already-persisted MMS row carrying this transaction id.
     */
    fun findByTransactionId(context: Context, transactionId: String): Long? {
        if (transactionId.isBlank()) return null
        return try {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.TRANSACTION_ID} = ?",
                arrayOf(transactionId),
                "${Telephony.Mms.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
                } else null
            }
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "tr_id lookup failed for ${transactionId.take(8)}…", e)
            null
        }
    }

    /**
     * Layer B: has this Content-Location been downloaded & persisted recently?
     */
    fun wasLocationProcessed(context: Context, contentLocation: String): Boolean {
        if (contentLocation.isBlank()) return false
        val now = System.currentTimeMillis()
        return loadMap(context, KEY_LOCATIONS).let { map ->
            prune(map, now)
            val ts = map[contentLocation]
            ts != null && now - ts < TTL_MS
        }
    }

    /**
     * Layer B: remember that this Content-Location produced a persisted message.
     */
    fun markLocationProcessed(context: Context, contentLocation: String) {
        if (contentLocation.isBlank()) return
        upsert(context, KEY_LOCATIONS, contentLocation)
        DebugLogger.diagnostic(TAG, "Marked Content-Location as processed (${maskUrl(contentLocation)})")
    }

    /**
     * ✅ FIX M29 Layer D helper: find an already-persisted MMS row carrying this
     * Message-ID header. The MMSC keeps m_id constant across redeliveries, so a hit
     * here proves the SAME message is already in the DB regardless of rotated tr_id
     * or Content-Location.
     */
    fun findByMessageId(context: Context, messageIdHeader: String): Long? {
        if (messageIdHeader.isBlank()) return null
        return try {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.MESSAGE_ID} = ?",
                arrayOf(messageIdHeader),
                "${Telephony.Mms.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
                } else null
            }
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "m_id lookup failed for ${messageIdHeader.take(8)}…", e)
            null
        }
    }

    /**
     * ✅ FIX M29 Layer D: has this Message-ID been downloaded & persisted recently?
     * Prefs map covers the window where the first copy is still being persisted
     * concurrently (m_id row not queryable yet).
     */
    fun wasMessageIdProcessed(context: Context, messageIdHeader: String): Boolean {
        if (messageIdHeader.isBlank()) return false
        val now = System.currentTimeMillis()
        return loadMap(context, KEY_MESSAGE_IDS).let { map ->
            prune(map, now)
            val ts = map[messageIdHeader]
            ts != null && now - ts < TTL_MS
        }
    }

    /**
     * ✅ FIX M29 Layer D: remember that this Message-ID produced a persisted message.
     */
    fun markMessageIdProcessed(context: Context, messageIdHeader: String) {
        if (messageIdHeader.isBlank()) return
        upsert(context, KEY_MESSAGE_IDS, messageIdHeader)
        DebugLogger.diagnostic(TAG, "Marked Message-ID as processed (${messageIdHeader.take(8)}…)")
    }

    private fun upsert(context: Context, key: String, id: String) {
        synchronized(this) {
            val map = loadMap(context, key)
            prune(map, System.currentTimeMillis())
            map[id] = System.currentTimeMillis()
            saveMap(context, key, map)
        }
    }

    private fun maskUrl(url: String): String =
        url.substringBefore("?", missingDelimiterValue = url).take(60)

    private fun prune(map: MutableMap<String, Long>, now: Long) {
        map.entries.removeAll { now - it.value > TTL_MS }
    }

    private fun loadMap(context: Context, key: String): MutableMap<String, Long> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(key, null)
            if (raw.isNullOrBlank()) mutableMapOf()
            else {
                val json = JSONObject(raw)
                val keys = json.keys()
                val out = mutableMapOf<String, Long>()
                while (keys.hasNext()) {
                    val k = keys.next()
                    out[k] = json.getLong(k)
                }
                out
            }
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "Failed to load dedup map, starting fresh", e)
            mutableMapOf()
        }
    }

    private fun saveMap(context: Context, key: String, map: Map<String, Long>) {
        try {
            val json = JSONObject()
            map.forEach { (k, v) -> json.put(k, v) }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(key, json.toString()).apply()
        } catch (e: Exception) {
            DebugLogger.diagnostic(TAG, "Failed to persist dedup map", e)
        }
    }
}
