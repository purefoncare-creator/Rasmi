package com.rasmi.purevon.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
import com.rasmi.purevon.data.local.dao.BlockedNumberDao
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.data.local.dao.WhitelistDao
import com.rasmi.purevon.data.preferences.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Call Screening Service for Android 10+ (API 29+)
 * Allows automatic call blocking and spam detection
 * 
 * IMPORTANT: This service MUST respond quickly (< 5 seconds) or it will cause ANR
 * 
 * ✅ Fixed: Whitelist Only Mode support
 */
@RequiresApi(Build.VERSION_CODES.Q)
@AndroidEntryPoint
class CallScreeningServiceImpl : CallScreeningService() {
    
    @Inject
    lateinit var blockedNumberDao: BlockedNumberDao
    
    @Inject
    lateinit var whitelistDao: WhitelistDao
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var simManager: com.rasmi.purevon.util.sim.SimManager
    
    companion object {
        private const val TAG = "CallScreeningService"
        private const val SCREENING_TIMEOUT_MS = 1500L // 1.5 seconds max to prevent ANR
    }
    
    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart

        Log.d(TAG, "onScreenCall called for: ${DebugLogger.maskPhoneNumber(phoneNumber ?: "")}")

        // ✅ FIX: Unknown / private numbers are now subject to the
        // `blockUnknownNumbers` setting instead of being silently allowed.
        // The actual decision is made inside checkIfShouldBlock() so that all
        // settings are read from a single place.

        // Screening is executed asynchronously on an IO worker so the binder
        // thread is never blocked. This avoids consuming the binder thread and
        // prevents the 1.5s runBlocking stall flagged in the review. The decision
        // is still bounded by SCREENING_TIMEOUT_MS to honour the <5s ANR window,
        // and respondToCall() is always invoked (block or allow) once decided.
        CoroutineScope(Dispatchers.IO).launch {
            val shouldBlock = try {
                val phoneAccountHandle = callDetails.accountHandle
                val incomingSubId = simManager.getSubscriptionIdForPhoneAccount(phoneAccountHandle)

                withTimeoutOrNull(SCREENING_TIMEOUT_MS) {
                    checkIfShouldBlock(phoneNumber, incomingSubId)
                } ?: false // If timeout, allow the call
            } catch (e: Exception) {
                Log.e(TAG, "Error during call screening, allowing call", e)
                false
            }

            if (shouldBlock) {
                Log.d(TAG, "BLOCKING call from: ${DebugLogger.maskPhoneNumber(phoneNumber ?: "<unknown>")}")
                respondToCall(callDetails, createBlockResponse())

                // ✅ FIX M32a: نافذة عائمة تفاصيل المتصل المحظور (40 ثانية)
                // تُطلق بعد الرد على النظام مباشرة حتى لا تؤخر الفحص (< 5s ANR limit)
                showBlockedCallBubble(phoneNumber)
            } else {
                Log.d(TAG, "Allowing call from: ${DebugLogger.maskPhoneNumber(phoneNumber ?: "<unknown>")}")
                respondToCall(callDetails, createAllowResponse())
            }
        }
    }
    
    /**
     * Blocking logic:
     * 1. 0) فحص نطاق SIM (هل الحظر مفعّل لهذه الشريحة تحديداً؟) إذا لا → ALLOW
     * 2. 1) Unknown / private number  → honor `blockUnknownNumbers`.
     * 3. 2) Blacklist (exact / normalized) → ALWAYS block.
     * 4. 3) Whitelist                  → ALWAYS allow.
     * 5. 4) `whitelistOnlyMode` ON     → BLOCK every number not whitelisted.
     * 6. 5) `callBlockingEnabled` OFF  → ALLOW.
     * 7. 6) Otherwise                  → BLOCK (general block).
     */
    private suspend fun checkIfShouldBlock(phoneNumber: String?, incomingSubId: Int?): Boolean {
        try {
            // ── Rule 0: SIM Scope check ──────────────────────────────────────
            val blockingScopeId = settingsDataStore.callBlockingSimSubscriptionId.first()
            if (blockingScopeId != -1 && incomingSubId != null && blockingScopeId != incomingSubId) {
                Log.d(TAG, "Call blocking not applied for SIM subId=$incomingSubId (Scope is $blockingScopeId) → ALLOW")
                return false
            }

            // ── Rule 1: Unknown / private number ─────────────────────────────
            if (phoneNumber.isNullOrBlank()) {
                val blockUnknown = settingsDataStore.blockUnknownNumbers.first()
                Log.d(TAG, "Unknown/private number → ${if (blockUnknown) "BLOCK" else "ALLOW"} (blockUnknownNumbers=$blockUnknown)")
                return blockUnknown
            }

            val normalizedIncoming = phoneNumber.replace(Regex("[^0-9]"), "").takeLast(9)

            // ── Rule 2: Blacklist always blocks ──────────────────────────────
            if (blockedNumberDao.isNumberBlocked(phoneNumber)) {
                Log.d(TAG, "Blacklisted (exact): ${DebugLogger.maskPhoneNumber(phoneNumber)} → BLOCK")
                return true
            }
            if (normalizedIncoming.length >= 9) {
                val blockedNumbers = blockedNumberDao.getAllBlockedPhoneNumbers()
                for (b in blockedNumbers) {
                    val norm = b.replace(Regex("[^0-9]"), "").takeLast(9)
                    if (norm.length >= 9 && norm == normalizedIncoming) {
                        Log.d(TAG, "Blacklisted (normalized): ${DebugLogger.maskPhoneNumber(phoneNumber)} → BLOCK")
                        return true
                    }
                }
            }

            // ── Rule 3: Whitelist → always allow ─────────────────────────────
            if (isNumberInWhitelist(phoneNumber)) {
                Log.d(TAG, "Whitelisted: ${DebugLogger.maskPhoneNumber(phoneNumber)} → ALLOW")
                return false
            }

            // ── Rule 4: Whitelist-only mode → block everything not whitelisted
            if (settingsDataStore.whitelistOnlyMode.first()) {
                Log.d(TAG, "Whitelist-only ON, not in whitelist: ${DebugLogger.maskPhoneNumber(phoneNumber)} → BLOCK")
                return true
            }

            // ── Rule 5: General block disabled → allow ───────────────────────
            if (!settingsDataStore.callBlockingEnabled.first()) {
                Log.d(TAG, "General block OFF → ALLOW")
                return false
            }

            // ── Rule 6: General block ON and not whitelisted → block ─────────
            Log.d(TAG, "General block ON, not whitelisted: ${DebugLogger.maskPhoneNumber(phoneNumber)} → BLOCK")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error in checkIfShouldBlock", e)
            return false
        }
    }
    
    /**
     * ✅ New: Check if number is in whitelist
     * Checks both exact match and normalized (last 9 digits) match
     */
    private suspend fun isNumberInWhitelist(phoneNumber: String): Boolean {
        try {
            // Check exact match first
            if (whitelistDao.isNumberWhitelisted(phoneNumber)) {
                return true
            }
            
            // Check with normalized number (last 9 digits)
            val normalizedNumber = phoneNumber.replace(Regex("[^0-9]"), "").takeLast(9)
            if (normalizedNumber.length >= 9) {
                // Get all whitelist numbers and check normalized match
                val whitelistNumbers = whitelistDao.getAllWhitelistNumbersSync()
                for (whitelisted in whitelistNumbers) {
                    val whitelistedNormalized = whitelisted.phoneNumber.replace(Regex("[^0-9]"), "").takeLast(9)
                    if (whitelistedNormalized.length >= 9 && whitelistedNormalized == normalizedNumber) {
                        Log.d(TAG, "Number matches whitelist (normalized): ${DebugLogger.maskPhoneNumber(phoneNumber)} -> ${DebugLogger.maskPhoneNumber(whitelisted.phoneNumber)}")
                        return true
                    }
                }
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking whitelist", e)
            return false // On error, treat as not whitelisted
        }
    }
    
    /**
     * ✅ FIX M32a: إظهار النافذة العائمة للمكالمات المحظورة.
     * fire-and-forget على IO — لا تحجب onScreenCall ولا تستهلك مهلة الفحص.
     * تعمل حتى للأرقام غير المعروفة (phoneNumber فارغ).
     */
    private fun showBlockedCallBubble(phoneNumber: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contactName = phoneNumber?.takeIf { it.isNotBlank() }?.let {
                    com.rasmi.purevon.data.repository.ContactResolver(applicationContext)
                        .resolveContactName(it)
                }
                val shown = BlockedCallBubbleService.show(
                    context = applicationContext,
                    phoneNumber = phoneNumber,
                    contactName = contactName
                )
                Log.d(TAG, "Blocked call bubble ${if (shown) "shown" else "NOT shown (no overlay permission?)"} for: ${DebugLogger.maskPhoneNumber(phoneNumber ?: "<unknown>")}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show blocked call bubble", e)
            }
        }
    }

    private fun createAllowResponse(): CallResponse {
        return CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
    }
    
    private fun createBlockResponse(): CallResponse {
        return CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false) // Keep in call log so user can see blocked calls
            .setSkipNotification(true) // Don't show notification for blocked calls
            .build()
    }
}
