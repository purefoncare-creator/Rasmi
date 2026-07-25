package com.rasmi.purevon.util.mms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * APN Manager for MMS Configuration
 * 
 * Priority order for finding MMS APN:
 * 1. System MMS APN (type contains "mms" in Android Telephony database)
 * 2. Preferred system APN (may have MMSC even if type is not "mms")
 * 3. Klinker library auto-detected APN (SharedPreferences from apns.xml with 1000+ carriers)
 * 4. Hardcoded fallbacks (GCC + Saudi — last resort if system DB + klinker prefs fail)
 */
@Singleton
class ApnManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ApnManager"

        // Fallback APNs when SIM numeric matches; operators change MMS endpoints — verify if MMS fails.
        private val FALLBACK_APNS = mapOf(
            // Saudi Arabia
            "420:01" to ApnSettings(mmsc = "http://mms.net.sa:8002/", proxy = "10.1.1.1", port = "8080", carrier = "STC"),
            "420:03" to ApnSettings(mmsc = "http://10.3.3.133:8080/was", proxy = "10.3.2.133", port = "8080", carrier = "Mobily"),
            "420:04" to ApnSettings(mmsc = "http://10.122.200.12:8002", proxy = "10.122.200.10", port = "8080", carrier = "Zain SA"),
            "420:05" to ApnSettings(mmsc = "http://mms.virginmobile.sa", proxy = "10.9.1.40", port = "8080", carrier = "Virgin SA"),
            // UAE
            "424:02" to ApnSettings(mmsc = "http://mms.du.ae/", proxy = "10.13.0.13", port = "8080", carrier = "du UAE"),
            "424:03" to ApnSettings(mmsc = "http://mms.etisalat.ae/", proxy = "10.12.0.32", port = "8080", carrier = "Etisalat UAE"),
            // Kuwait
            "419:02" to ApnSettings(mmsc = "http://mms.zain.com/", proxy = "10.13.2.16", port = "8080", carrier = "Zain KW"),
            "419:03" to ApnSettings(mmsc = "http://mms.ooredoo.com.kw/", proxy = "10.1.1.1", port = "8080", carrier = "Ooredoo KW"),
            "419:04" to ApnSettings(mmsc = "http://mms.stc.com.kw/", proxy = "172.28.31.77", port = "8080", carrier = "STC KW"),
            // Qatar
            "427:01" to ApnSettings(mmsc = "http://mms.ooredoo.qa/", proxy = "10.23.9.7", port = "8080", carrier = "Ooredoo QA"),
            "427:02" to ApnSettings(mmsc = "http://mms.vodafone.qa/", proxy = "192.168.168.30", port = "8080", carrier = "Vodafone QA"),
            // Bahrain
            "426:01" to ApnSettings(mmsc = "http://mms.batelco.com/", proxy = "192.168.192.168", port = "80", carrier = "Batelco BH"),
            "426:02" to ApnSettings(mmsc = "http://mmsc.zain.bh/", proxy = "10.42.0.41", port = "8080", carrier = "Zain BH"),
            // Oman
            "422:03" to ApnSettings(mmsc = "http://mms.omantel.om/", proxy = "192.168.206.34", port = "8080", carrier = "Omantel"),
            // Jordan
            "416:01" to ApnSettings(mmsc = "http://mmsc.zain.jo/", proxy = "10.43.0.29", port = "9201", carrier = "Zain JO"),
            "416:77" to ApnSettings(mmsc = "http://mms.orange.jo/", proxy = "10.143.5.31", port = "8080", carrier = "Orange JO"),
            // Egypt (common MNCs)
            "602:01" to ApnSettings(mmsc = "http://mms.orange.eg/", proxy = "10.81.4.98", port = "8080", carrier = "Orange EG"),
            "602:02" to ApnSettings(mmsc = "http://mms.vodafone.com.eg/", proxy = "196.205.90.33", port = "8080", carrier = "Vodafone EG"),
            "602:03" to ApnSettings(mmsc = "http://mms.etisalat.com.eg/", proxy = "10.71.82.7", port = "8080", carrier = "Etisalat EG")
        )
    }

    data class ApnSettings(
        val mmsc: String,
        val proxy: String?,
        val port: String?,
        val carrier: String = "Unknown",
        val mmsUA: String? = null,
        val mmsUAProfUrl: String? = null,
        val authType: Int = -1,
        val user: String? = null,
        val password: String? = null
    ) {
        val hasProxy: Boolean get() = !proxy.isNullOrBlank()
        val proxyAddress: String? get() = if (hasProxy) "$proxy:${port ?: "80"}" else null
        // ✅ FIX #61: Override toString() to hide password from logs
        override fun toString(): String = "ApnSettings(mmsc=$mmsc, proxy=$proxy, port=$port, carrier=$carrier, user=$user, password=***)"
    }

    /** Whether klinker auto-detect has already run this session */
    private val autoDetectRan = AtomicBoolean(false)

    /** Latch shared across concurrent callers of autoDetectApnBlocking */
    @Volatile
    private var detectLatch: CountDownLatch? = null

    /** Result of the last blocking auto-detect */
    @Volatile
    private var detectResult = false

    /**
     * Get MMS APN settings with full priority chain.
     * This is the main entry point.
     */
    fun getApnSettings(subId: Int? = null): ApnSettings? {
        Log.w(TAG, "🔍 getApnSettings(subId=$subId) - searching for MMS APN...")

        // 1. Query system for MMS-type APN (most reliable)
        getMmsTypeApn(subId)?.let {
            Log.w(TAG, "✅ [1] Found system MMS APN: carrier=${it.carrier}, mmsc=${it.mmsc}, proxy=${it.proxy}:${it.port}")
            return it
        }

        // 2. Query preferred APN (may have MMSC set even for default/internet APN)
        getPreferredApn(subId)?.let {
            Log.w(TAG, "✅ [2] Found preferred APN with MMSC: carrier=${it.carrier}, mmsc=${it.mmsc}")
            return it
        }

        // 3. Klinker SharedPreferences (populated by a previous autoDetectApn() run)
        getKlinkerApn()?.let {
            Log.w(TAG, "✅ [3] Found klinker cached APN: mmsc=${it.mmsc}")
            return it
        }

        // 4. Klinker blocking auto-detect — triggers live lookup from apns.xml (1000+ global carriers).
        // Runs synchronously (up to 5 s) so the result is immediately usable.
        // This replaces the old Gulf-only hardcoded fallback map with a truly global lookup.
        Log.w(TAG, "⏳ [4] Klinker cache miss — running blocking auto-detect (≤5 s)...")
        val detected = autoDetectApnBlocking()
        if (detected) {
            getKlinkerApn()?.let {
                Log.w(TAG, "✅ [4] Klinker auto-detect succeeded: mmsc=${it.mmsc}")
                return it
            }
        }

        // 5. Last-resort: Gulf/MENA hardcoded fallbacks (kept for offline/roaming edge cases
        //    where the system DB and klinker both fail on known Gulf carriers).
        getFallbackApn(subId)?.let {
            Log.w(TAG, "✅ [5] Using hardcoded regional fallback: carrier=${it.carrier}, mmsc=${it.mmsc}")
            return it
        }

        Log.e(TAG, "❌ NO MMS APN FOUND after all tiers! MMS will likely fail.")
        return null
    }

    /**
     * Auto-detect APN using klinker android-smsmms library.
     * Reads from apns.xml with 1000+ carriers worldwide.
     * Saves results to SharedPreferences.
     */
    fun autoDetectApn(onComplete: ((success: Boolean) -> Unit)? = null) {
        try {
            Log.w(TAG, "🔄 Starting klinker APN auto-detection...")
            com.klinker.android.send_message.ApnUtils.initDefaultApns(
                context,
                object : com.klinker.android.send_message.ApnUtils.OnApnFinishedListener {
                    override fun onFinished() {
                        autoDetectRan.set(true)
                        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                        val mmsc = prefs.getString("mmsc_url", "")
                        val proxy = prefs.getString("mms_proxy", "")
                        val port = prefs.getString("mms_port", "")
                        Log.w(TAG, "✅ Klinker auto-detect completed: mmsc=$mmsc, proxy=$proxy, port=$port")
                        onComplete?.invoke(!mmsc.isNullOrBlank())
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Klinker APN auto-detection failed", e)
            autoDetectRan.set(true)
            onComplete?.invoke(false)
        }
    }

    /**
     * Blocking auto-detect for use in coroutines.
     * Waits up to 5 seconds for klinker to finish.
     * Thread-safe: only the first caller triggers detection; others wait on the same latch.
     */
    fun autoDetectApnBlocking(): Boolean {
        if (autoDetectRan.get()) return true

        // Only one thread triggers auto-detect; others piggyback on the shared latch.
        val latch = CountDownLatch(1)
        val shouldRun = synchronized(this) {
            if (autoDetectRan.get()) return true
            val existing = detectLatch
            if (existing != null) {
                // Another thread is already running — wait on its latch.
                existing.await(5, TimeUnit.SECONDS)
                return detectResult
            }
            // We are the first caller — register our latch.
            detectLatch = latch
            true
        }

        // First caller reaches here; run auto-detect and wait for result.
        autoDetectApn { result ->
            detectResult = result
            autoDetectRan.set(true)
            synchronized(this@ApnManager) { detectLatch = null }
            latch.countDown()
        }

        try {
            latch.await(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "APN auto-detect interrupted", e)
            return false
        }
        return detectResult
    }

    // ─── SYSTEM MMS APN (type contains "mms") ───

    /**
     * Query the system APN table for entries whose TYPE column contains "mms".
     * This is the correct way to find the MMS APN.
     */
    private fun getMmsTypeApn(subId: Int?): ApnSettings? {
        return try {
            val mccMnc = getCarrierMccMncRaw(subId)
            Log.w(TAG, "   [1] Querying system MMS APNs for MCC/MNC=$mccMnc, subId=$subId")

            // Query all current APNs, then filter for MMS type
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && subId != null) {
                Uri.parse("content://telephony/carriers/subId/$subId")
            } else {
                Telephony.Carriers.CONTENT_URI
            }

            val columns = arrayOf(
                Telephony.Carriers.MMSC,
                Telephony.Carriers.MMSPROXY,
                Telephony.Carriers.MMSPORT,
                Telephony.Carriers.NAME,
                Telephony.Carriers.TYPE,
                Telephony.Carriers.AUTH_TYPE,
                Telephony.Carriers.USER,
                Telephony.Carriers.PASSWORD,
                Telephony.Carriers.NUMERIC
            )

            // Build selection: type contains "mms" AND matches carrier numeric (MCC+MNC)
            val selection: String?
            val selectionArgs: Array<String>?
            if (mccMnc != null) {
                selection = "${Telephony.Carriers.NUMERIC} = ? AND ${Telephony.Carriers.CURRENT} IS NOT NULL"
                selectionArgs = arrayOf(mccMnc)
            } else {
                selection = "${Telephony.Carriers.CURRENT} IS NOT NULL"
                selectionArgs = null
            }

            context.contentResolver.query(uri, columns, selection, selectionArgs, null)?.use { cursor ->
                val results = mutableListOf<ApnSettings>()
                val typeIndex = cursor.getColumnIndex(Telephony.Carriers.TYPE)

                while (cursor.moveToNext()) {
                    val type = cursor.getString(typeIndex) ?: ""
                    // MMS APN has type containing "mms" (e.g., "mms", "default,mms", "mms,supl")
                    if (type.contains("mms", ignoreCase = true) || type == "*") {
                        extractApnFromCursor(cursor)?.let { apn ->
                            Log.w(TAG, "   [1] Candidate MMS APN: name=${apn.carrier}, type=$type, mmsc=${apn.mmsc}")
                            results.add(apn)
                        }
                    }
                }
                Log.w(TAG, "   [1] Found ${results.size} MMS-type APNs")
                // Prefer APN with proxy (more specific), then first found
                results.firstOrNull { it.hasProxy } ?: results.firstOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error querying MMS-type APNs", e)
            null
        }
    }

    // ─── PREFERRED APN (may have MMSC) ───

    private fun getPreferredApn(subId: Int?): ApnSettings? {
        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && subId != null) {
                Uri.parse("content://telephony/carriers/preferapn/subId/$subId")
            } else {
                Uri.parse("content://telephony/carriers/preferapn")
            }

            Log.w(TAG, "   [2] Querying preferred APN from $uri")

            context.contentResolver.query(
                uri,
                arrayOf(
                    Telephony.Carriers.MMSC,
                    Telephony.Carriers.MMSPROXY,
                    Telephony.Carriers.MMSPORT,
                    Telephony.Carriers.NAME,
                    Telephony.Carriers.TYPE,
                    Telephony.Carriers.AUTH_TYPE,
                    Telephony.Carriers.USER,
                    Telephony.Carriers.PASSWORD
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val typeIndex = cursor.getColumnIndex(Telephony.Carriers.TYPE)
                    val type = if (typeIndex >= 0) cursor.getString(typeIndex) ?: "" else ""
                    val apn = extractApnFromCursor(cursor)
                    if (apn != null) {
                        Log.w(TAG, "   [2] Preferred APN: name=${apn.carrier}, type=$type, mmsc=${apn.mmsc}")
                    } else {
                        Log.w(TAG, "   [2] Preferred APN has no MMSC")
                    }
                    apn
                } else {
                    Log.w(TAG, "   [2] No preferred APN found")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading preferred APN", e)
            null
        }
    }

    // ─── KLINKER AUTO-DETECT (SharedPreferences) ───

    private fun getKlinkerApn(): ApnSettings? {
        // If auto-detect hasn't run yet, run it now (blocking)
        if (!autoDetectRan.get()) {
            Log.w(TAG, "   [3] Klinker auto-detect hasn't run yet, running now...")
            autoDetectApnBlocking()
        }

        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val mmsc = prefs.getString("mmsc_url", "") ?: ""
        if (mmsc.isBlank()) {
            Log.w(TAG, "   [3] Klinker SharedPreferences: no MMSC found")
            return null
        }
        val proxy = prefs.getString("mms_proxy", "")
        val port = prefs.getString("mms_port", "")
        return ApnSettings(
            mmsc = mmsc,
            proxy = proxy?.takeIf { it.isNotBlank() },
            port = port?.takeIf { it.isNotBlank() },
            carrier = "Klinker-AutoDetected"
        )
    }

    // ─── HARDCODED FALLBACK ───

    private fun getFallbackApn(subId: Int?): ApnSettings? {
        val mccMnc = getCarrierMccMnc(subId)
        return FALLBACK_APNS[mccMnc]?.also {
            Log.w(TAG, "   [4] Hardcoded fallback for $mccMnc: ${it.carrier}")
        }
    }

    // ─── UTILITY ───

    /**
     * Extract ApnSettings from a cursor row. Returns null if MMSC is empty.
     */
    private fun extractApnFromCursor(cursor: Cursor): ApnSettings? {
        val mmscIndex = cursor.getColumnIndex(Telephony.Carriers.MMSC)
        val proxyIndex = cursor.getColumnIndex(Telephony.Carriers.MMSPROXY)
        val portIndex = cursor.getColumnIndex(Telephony.Carriers.MMSPORT)
        val nameIndex = cursor.getColumnIndex(Telephony.Carriers.NAME)
        val authIndex = cursor.getColumnIndex(Telephony.Carriers.AUTH_TYPE)
        val userIndex = cursor.getColumnIndex(Telephony.Carriers.USER)
        val passIndex = cursor.getColumnIndex(Telephony.Carriers.PASSWORD)

        val mmsc = if (mmscIndex >= 0) cursor.getString(mmscIndex) else null
        if (mmsc.isNullOrBlank()) return null

        return ApnSettings(
            mmsc = mmsc,
            proxy = if (proxyIndex >= 0) cursor.getString(proxyIndex)?.takeIf { it.isNotBlank() } else null,
            port = if (portIndex >= 0) cursor.getString(portIndex)?.takeIf { it.isNotBlank() } else null,
            carrier = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "System" else "System",
            authType = if (authIndex >= 0) cursor.getInt(authIndex) else -1,
            user = if (userIndex >= 0) cursor.getString(userIndex) else null,
            password = if (passIndex >= 0) cursor.getString(passIndex) else null
        )
    }

    /**
     * Get raw MCC+MNC numeric string (e.g., "42003") for APN table queries.
     */
    private fun getCarrierMccMncRaw(subId: Int?): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val operator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && subId != null) {
                val tm = telephonyManager.createForSubscriptionId(subId)
                tm.simOperator
            } else {
                telephonyManager.simOperator
            }
            operator?.takeIf { it.length >= 5 }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting raw MCC+MNC", e)
            null
        }
    }

    /**
     * Get carrier MCC:MNC code (formatted as "420:03")
     */
    private fun getCarrierMccMnc(subId: Int?): String? {
        return getCarrierMccMncRaw(subId)?.let { raw ->
            "${raw.substring(0, 3)}:${raw.substring(3)}"
        }
    }

    fun validateApn(apn: ApnSettings?): Boolean {
        if (apn == null) return false
        val isValid = apn.mmsc.isNotBlank() && apn.mmsc.startsWith("http")
        if (!isValid) Log.w(TAG, "⚠️ Invalid APN: ${apn.mmsc}")
        return isValid
    }

    fun getAvailableSubscriptions(): List<SubscriptionInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return emptyList()
        // ✅ FIX #55: Check READ_PHONE_STATE permission before accessing subscriptions
        if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted — cannot list subscriptions")
            return emptyList()
        }
        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subscriptionManager.activeSubscriptionInfoList?.map { info ->
                SubscriptionInfo(
                    subscriptionId = info.subscriptionId,
                    displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                    carrierName = info.carrierName?.toString() ?: "Unknown",
                    simSlotIndex = info.simSlotIndex
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscriptions", e)
            emptyList()
        }
    }

    data class SubscriptionInfo(
        val subscriptionId: Int,
        val displayName: String,
        val carrierName: String,
        val simSlotIndex: Int
    )

    fun getApnDebugInfo(subId: Int? = null): String {
        val apn = getApnSettings(subId)
        return buildString {
            appendLine("APN Debug Info:")
            appendLine("SubId: $subId")
            appendLine("MCC+MNC: ${getCarrierMccMncRaw(subId) ?: "Unknown"}")
            appendLine("Carrier: ${apn?.carrier ?: "Unknown"}")
            appendLine("MMSC: ${apn?.mmsc ?: "NOT FOUND"}")
            appendLine("Proxy: ${apn?.proxy ?: "None"}")
            appendLine("Port: ${apn?.port ?: "None"}")
        }
    }
}
