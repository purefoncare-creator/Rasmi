package com.rasmi.purevon.util

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Phone call utilities
 */
object PhoneUtil {
    
    /**
     * Format phone number for display
     */
    fun formatPhoneNumber(number: String): String {
        val hadPlus = number.trimStart().startsWith("+")
        val digits = number.filter { it.isDigit() }
        
        return when {
            digits.startsWith("00") || hadPlus -> {
                formatInternational(digits)
            }
            digits.length == 10 -> {
                "${digits.substring(0, 4)} ${digits.substring(4, 7)} ${digits.substring(7)}"
            }
            digits.length == 11 -> {
                "${digits.substring(0, 4)} ${digits.substring(4, 7)} ${digits.substring(7)}"
            }
            else -> number
        }
    }
    
    private fun formatInternational(digits: String): String {
        val clean = digits.removePrefix("00")
        return when {
            clean.length > 10 -> {
                "+${clean.substring(0, clean.length - 10)} ${clean.substring(clean.length - 10, clean.length - 7)} ${clean.substring(clean.length - 7, clean.length - 4)} ${clean.substring(clean.length - 4)}"
            }
            else -> "+$clean"
        }
    }
    
    /**
     * Check if the number is a USSD code (e.g., *1400#, *123*456#)
     */
    fun isUssdCode(number: String): Boolean {
        val trimmed = number.trim()
        return trimmed.startsWith("*") && trimmed.endsWith("#")
    }
    
    fun playClickSound(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
        } catch (e: Exception) {
            // Ignore sound errors
        }
    }

    /**
     * Initiate a phone call or USSD request
     * @param subscriptionId The subscription ID for dual SIM devices (-1 or null for default)
     */
    @SuppressLint("MissingPermission")
    fun makeCall(context: Context, phoneNumber: String, subscriptionId: Int? = null) {
        if (!hasCallPermission(context)) return
        
        try {
            // Check if this is a USSD code
            if (isUssdCode(phoneNumber)) {
                sendUssdRequest(context, phoneNumber, subscriptionId)
                return
            }

            // Resolve effective subscription ID: use provided value or fall back to system default voice SIM.
            // This prevents Android from showing the SIM-selection dialog on dual-SIM devices.
            val effectiveSubId: Int = if (subscriptionId != null && subscriptionId >= 0) {
                subscriptionId
            } else {
                val defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
                if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) defaultSubId else -1
            }
            
            // Use TelecomManager for Android M+ with a valid subscriptionId
            if (effectiveSubId >= 0) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val phoneAccountHandle = getPhoneAccountHandleForSubscription(context, effectiveSubId)
                
                if (phoneAccountHandle != null) {
                    val extras = android.os.Bundle().apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                    }
                    
                    val uri = Uri.fromParts("tel", phoneNumber, null)
                    telecomManager.placeCall(uri, extras)
                    return
                }
            }
            
            // Fallback to regular Intent
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.fromParts("tel", phoneNumber, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                // Add subscription ID as extras (some OEMs use these)
                if (effectiveSubId >= 0) {
                    putExtra("com.android.phone.extra.slot", getSlotForSubscription(context, effectiveSubId))
                    putExtra("slot", getSlotForSubscription(context, effectiveSubId))
                }
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PhoneUtil", "Error making call", e)
        }
    }
    
    /**
     * Send a USSD request (e.g., *1400# for balance check)
     * Uses TelecomManager.placeCall() with PhoneAccountHandle to avoid the system SIM picker
     * on dual-SIM devices — same approach as regular calls in [makeCall].
     */
    @SuppressLint("MissingPermission")
    fun sendUssdRequest(context: Context, ussdCode: String, subscriptionId: Int? = null) {
        if (!hasCallPermission(context)) return
        
        try {
            // Resolve effective subscription ID: use provided value or fall back to system default voice SIM.
            // This prevents Android from showing the SIM-selection dialog on dual-SIM devices.
            val effectiveSubId: Int = if (subscriptionId != null && subscriptionId >= 0) {
                subscriptionId
            } else {
                val defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
                if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) defaultSubId else -1
            }
            
            // USSD URI — Uri.fromParts encodes '#' as '%23' automatically
            val ussdUri = Uri.fromParts("tel", ussdCode, null)
            
            android.util.Log.d("PhoneUtil", "Sending USSD request: $ussdCode, effectiveSubId=$effectiveSubId")
            
            // Use TelecomManager for Android M+ with a valid subscriptionId
            if (effectiveSubId >= 0) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val phoneAccountHandle = getPhoneAccountHandleForSubscription(context, effectiveSubId)
                
                if (phoneAccountHandle != null) {
                    val extras = android.os.Bundle().apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                    }
                    telecomManager.placeCall(ussdUri, extras)
                    return
                }
            }
            
            // Fallback to regular Intent (older API or no matching PhoneAccountHandle)
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = ussdUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                if (effectiveSubId >= 0) {
                    putExtra("com.android.phone.extra.slot", getSlotForSubscription(context, effectiveSubId))
                    putExtra("slot", getSlotForSubscription(context, effectiveSubId))
                }
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PhoneUtil", "Error sending USSD request", e)
        }
    }
    
    /**
     * Public accessor to get PhoneAccountHandle for a subscription ID.
     * Used by NotificationHelper to attach SIM info to call-back intents.
     */
    fun getPhoneAccountForSubscription(context: Context, subscriptionId: Int): PhoneAccountHandle? {
        return getPhoneAccountHandleForSubscription(context, subscriptionId)
    }
    
    /**
     * Get PhoneAccountHandle for a subscription ID
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun getPhoneAccountHandleForSubscription(context: Context, subscriptionId: Int): PhoneAccountHandle? {
        
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val accounts = telecomManager.callCapablePhoneAccounts
            
            for (account in accounts) {
                val subId = telecomManager.getPhoneAccount(account)?.extras?.getInt(
                    "android.telecom.extra.SUBSCRIPTION_ID",
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                )
                
                if (subId == subscriptionId) {
                    return account
                }
            }
            
            // If no match found, try to return by index
            accounts.getOrNull(getSlotForSubscription(context, subscriptionId))
        } catch (e: Exception) {
            android.util.Log.e("PhoneUtil", "Error getting PhoneAccountHandle", e)
            null
        }
    }
    
    /**
     * Get SIM slot index for subscription ID
     */
    @SuppressLint("MissingPermission")
    private fun getSlotForSubscription(context: Context, subscriptionId: Int): Int {
        
        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val info = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
            info?.simSlotIndex ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Open dialer with number
     */
    fun openDialer(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.fromParts("tel", phoneNumber, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Open SMS app to send message
     */
    fun sendMessage(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.fromParts("sms", phoneNumber, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Check if app has call permission
     */
    fun hasCallPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Validate phone number or USSD code
     */
    fun isValidPhoneNumber(number: String): Boolean {
        // USSD codes are valid (e.g., *1400#, *123*456#)
        if (isUssdCode(number)) {
            return true
        }
        
        val digits = number.filter { it.isDigit() }
        return digits.length in 3..15
    }
    
    /**
     * Check if input can be dialed (phone number or USSD)
     */
    fun isDialable(number: String): Boolean {
        if (number.isBlank()) return false
        
        // USSD codes
        if (isUssdCode(number)) return true
        
        // Regular phone numbers
        val digits = number.filter { it.isDigit() }
        return digits.isNotEmpty()
    }
}
