package com.rasmi.purevon.service

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.telecom.Call
import android.util.Log
import com.rasmi.purevon.presentation.screen.incall.InCallActivity
import com.rasmi.purevon.util.DebugLogger

object InCallServiceConstants {
    const val TAG = "PurevonInCallService"
    const val CHANNEL_ID_INCOMING = "purevon_incoming_calls_v2"
    const val CHANNEL_ID_ONGOING = "purevon_ongoing_calls_v4"
    const val CHANNEL_ID_INCOMING_SILENT = "purevon_incoming_silent_v1"
    const val CHANNEL_ID_ONGOING_ACTIVE = "purevon_ongoing_active_v2"
    const val NOTIFICATION_ID = 1
    const val NOTIFICATION_ID_ONGOING_ACTIVE = 2

    fun getStateName(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_RINGING -> "RINGING"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_ACTIVE -> "ACTIVE"
            Call.STATE_HOLDING -> "HOLDING"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_DISCONNECTING -> "DISCONNECTING"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
            else -> "UNKNOWN"
        }
    }
}

internal fun PurevonInCallService.isDeviceLocked(): Boolean {
    return try {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive
        val isLocked = keyguardManager.isKeyguardLocked
        Log.d("PurevonInCallService", "====== DEVICE STATE CHECK ======")
        Log.d("PurevonInCallService", "Screen ON: $isScreenOn")
        Log.d("PurevonInCallService", "Keyguard LOCKED: $isLocked")
        Log.d("PurevonInCallService", "Final Result: ${if (isLocked) "LOCKED" else "UNLOCKED"}")
        Log.d("PurevonInCallService", "================================")
        isLocked
    } catch (e: Exception) {
        Log.e("PurevonInCallService", "Error checking device lock state", e)
        true
    }
}

internal fun PurevonInCallService.lookupContactName(phoneNumber: String?): String? {
    if (phoneNumber.isNullOrBlank()) return null
    return try {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(phoneNumber)
        )
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                )
            } else null
        }
    } catch (e: Exception) {
        Log.e("PurevonInCallService", "Error looking up contact name for ${DebugLogger.maskPhoneNumber(phoneNumber)}", e)
        null
    }
}

internal fun PurevonInCallService.lookupContactPhotoUri(phoneNumber: String?): String? {
    if (phoneNumber.isNullOrBlank()) return null
    return try {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(phoneNumber)
        )
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_URI))
            } else null
        }
    } catch (e: Exception) {
        Log.e("PurevonInCallService", "Error looking up contact photo for ${DebugLogger.maskPhoneNumber(phoneNumber)}", e)
        null
    }
}

internal fun PurevonInCallService.launchInCallActivity() {
    try {
        Log.e("PurevonInCallService", "Launching InCall Activity")
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            Log.e("PurevonInCallService", "Screen is OFF - Waking up screen first")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Purevon:InCallWakeLock"
            )
            wakeLock.acquire(5000L)
        }
        val intent = android.content.Intent(this, InCallActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
        Log.e("PurevonInCallService", "InCall Activity launched successfully")
    } catch (e: Exception) {
        Log.e("PurevonInCallService", "Error launching InCall Activity: ${e.message}", e)
    }
}

internal fun PurevonInCallService.triggerHapticFeedback() {
    try {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        Log.d("PurevonInCallService", "Haptic feedback triggered for waiting call")
    } catch (e: Exception) {
        Log.e("PurevonInCallService", "Error triggering haptic feedback", e)
    }
}
