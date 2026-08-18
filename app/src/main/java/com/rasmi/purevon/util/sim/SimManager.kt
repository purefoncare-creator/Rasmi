package com.rasmi.purevon.util.sim

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for handling dual SIM functionality
 */
@Singleton
class SimManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PHONE_ACCOUNT_EXTRA_SUBSCRIPTION_ID =
            "android.telecom.extra.SUBSCRIPTION_ID"
    }
    
    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    
    private var subscriptionListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    
    init {
        try {
            subscriptionListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() {
                    Log.d("SimManager", "Subscriptions changed — invalidating cache")
                    invalidateCache()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                subscriptionManager?.addOnSubscriptionsChangedListener(
                    context.mainExecutor,
                    subscriptionListener!!
                )
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager?.addOnSubscriptionsChangedListener(subscriptionListener)
            }
        } catch (e: Exception) {
            Log.w("SimManager", "Failed to register subscription listener", e)
        }
    }
    
    /**
     * Check if device has dual SIM
     */
    fun isDualSim(): Boolean {
        return try {
            val activeSubscriptions = getActiveSubscriptions()
            activeSubscriptions.size >= 2
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get available SIM cards
     */
    fun getAvailableSims(): List<SimInfo> {
        return try {
            val activeSubscriptions = getActiveSubscriptions()
            activeSubscriptions.mapIndexed { index, info ->
                // Try to get phone number from SubscriptionInfo
                val phoneNumber = getPhoneNumberForSubscription(info)
                
                SimInfo(
                    slotIndex = info.simSlotIndex,
                    subscriptionId = info.subscriptionId,
                    displayName = info.displayName?.toString() ?: "SIM ${index + 1}",
                    carrierName = info.carrierName?.toString() ?: "Unknown",
                    phoneNumber = phoneNumber ?: "SIM ${index + 1}",
                    isDefault = info.subscriptionId == getDefaultSubscriptionId()
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Try to get phone number for a subscription
     * Note: This may not work on all devices and requires READ_PHONE_NUMBERS permission
     */
    @SuppressLint("HardwareIds")
    @Suppress("DEPRECATION")
    private fun getPhoneNumberForSubscription(info: SubscriptionInfo): String? {
        return try {
            // First try getNumber() from SubscriptionInfo
            val numberFromInfo = info.number?.takeIf { it.isNotBlank() }
            if (numberFromInfo != null) {
                return formatPhoneNumberForDisplay(numberFromInfo)
            }
            
            // Try using TelephonyManager with subscription ID (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val teleManager = telephonyManager.createForSubscriptionId(info.subscriptionId)
                val line1Number = teleManager.line1Number?.takeIf { it.isNotBlank() }
                if (line1Number != null) {
                    return formatPhoneNumberForDisplay(line1Number)
                }
            } else {
                // For older versions, try line1Number from default TelephonyManager
                val line1Number = telephonyManager.line1Number?.takeIf { it.isNotBlank() }
                if (line1Number != null) {
                    return formatPhoneNumberForDisplay(line1Number)
                }
            }
            
            null
        } catch (e: SecurityException) {
            // Permission not granted
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Format phone number for display (mask middle digits for privacy if needed)
     */
    private fun formatPhoneNumberForDisplay(number: String): String {
        // Return the full number - users may want to see their actual number
        return number
    }
    
    /**
     * Get active subscriptions
     */
    private fun getActiveSubscriptions(): List<SubscriptionInfo> {
        return try {
            subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }
    
    /**
     * Get default subscription ID for voice calls
     */
    fun getDefaultSubscriptionId(): Int {
        return try {
            SubscriptionManager.getDefaultVoiceSubscriptionId()
        } catch (e: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }
    
    /**
     * Get SIM info by slot index
     */
    fun getSimBySlot(slotIndex: Int): SimInfo? {
        return getAvailableSims().firstOrNull { it.slotIndex == slotIndex }
    }
    
    /**
     * Get SIM info by subscription ID
     */
    fun getSimBySubscriptionId(subscriptionId: Int): SimInfo? {
        return getAvailableSims().firstOrNull { it.subscriptionId == subscriptionId }
    }

    /**
     * Cached mapping of subscriptionId → simSlotIndex.
     * Refreshed on first access and whenever invalidateCache() is called.
     * Using a cache avoids repetitive blocking IPC calls to the TelephonyService
     * (which were causing Choreographer "Skipped 700+ frames" / ANR when called per message row).
     */
    @Volatile
    private var subscriptionSlotCache: Map<Int, Int>? = null

    @SuppressLint("MissingPermission")
    private fun buildSubscriptionSlotCache(): Map<Int, Int> {
        return try {
            subscriptionManager?.activeSubscriptionInfoList
                ?.associate { it.subscriptionId to it.simSlotIndex }
                ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Invalidate the subscription→slot cache.
     * Call this when SIM configuration changes (insert/remove/swap).
     */
    fun invalidateCache() {
        subscriptionSlotCache = null
    }

    /**
     * Get SIM slot index (0-based) for a given subscription ID.
     * Uses a memory cache — O(1), no IPC. Safe to call from any thread or loop.
     * Returns: 0 = SIM1, 1 = SIM2, null = unknown/single-SIM
     */
    fun getSlotForSubscriptionId(subscriptionId: Int): Int? {
        if (subscriptionId <= 0) return null
        val cache = subscriptionSlotCache ?: buildSubscriptionSlotCache().also { subscriptionSlotCache = it }
        return cache[subscriptionId]
    }
    
    /**
     * Check if SIM is ready
     */
    fun isSimReady(): Boolean {
        return telephonyManager.simState == TelephonyManager.SIM_STATE_READY
    }
    
    /**
     * Get number of active SIMs
     */
    fun getActiveSimCount(): Int {
        return try {
            getActiveSubscriptions().size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * ✅ تحويل PhoneAccountHandle (المستخدم في CallScreeningService و InCallService)
     * إلى subscriptionId. يدعم API 30+ مباشرة، وللإصدارات الأقدم نبحث في extras
     * أو نطابق عبر id.
     *
     * @return subscriptionId أو null إذا تعذّر التحديد.
     */
    @SuppressLint("MissingPermission")
    fun getSubscriptionIdForPhoneAccount(handle: android.telecom.PhoneAccountHandle?): Int? {
        if (handle == null) return null
        return try {
            // API 30+: TelephonyManager.getSubscriptionId(PhoneAccountHandle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val subId = telephonyManager.getSubscriptionId(handle)
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return subId
            }
            // Fallback: قراءة extras الخاصة بالـ PhoneAccount
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                as? android.telecom.TelecomManager ?: return null
            val account = telecomManager.getPhoneAccount(handle) ?: return null
            val subFromExtras = account.extras?.getInt(
                PHONE_ACCOUNT_EXTRA_SUBSCRIPTION_ID,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            ) ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            if (subFromExtras != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return subFromExtras

            // Fallback أخير: مطابقة id برقم subscription لكل شريحة نشطة
            val handleId = handle.id
            getActiveSubscriptions().firstOrNull { sub ->
                val subIdStr = sub.subscriptionId.toString()
                // تطابق دقيق أو محاط بفواصل غير رقمية لتفادي 1 يطابق 21
                handleId == subIdStr ||
                    handleId.endsWith("/$subIdStr") ||
                    handleId.endsWith(";$subIdStr") ||
                    Regex("(^|[^0-9])$subIdStr([^0-9]|$)").containsMatchIn(handleId)
            }?.subscriptionId
        } catch (e: Exception) {
            Log.w("SimManager", "getSubscriptionIdForPhoneAccount failed", e)
            null
        }
    }
}

/**
 * SIM Card Information
 */
data class SimInfo(
    val slotIndex: Int,
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val phoneNumber: String,
    val isDefault: Boolean = false
)
