package com.rasmi.purevon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.screen.incall.InCallActivity
import com.rasmi.purevon.service.InCallServiceConstants.CHANNEL_ID_INCOMING
import com.rasmi.purevon.service.InCallServiceConstants.CHANNEL_ID_INCOMING_SILENT
import com.rasmi.purevon.service.InCallServiceConstants.CHANNEL_ID_ONGOING
import com.rasmi.purevon.service.InCallServiceConstants.NOTIFICATION_ID
import com.rasmi.purevon.service.InCallServiceConstants.getStateName

class InCallNotificationManager(
    private val context: Context,
    private val bridge: InCallServiceBridgeImpl
) {
    companion object {
        private const val TAG = "InCallNotificationManager"
    }

    val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val incomingChannel = NotificationChannel(
                CHANNEL_ID_INCOMING,
                context.getString(R.string.notification_channel_incoming_calls),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_incoming_calls_desc)
                setSound(null, null)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
                enableVibration(false)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(incomingChannel)
            Log.d(TAG, "Created incoming calls notification channel with IMPORTANCE_HIGH")

            runCatching {
                notificationManager.deleteNotificationChannel("purevon_ongoing_calls_v3")
            }

            val ongoingChannel = NotificationChannel(
                CHANNEL_ID_ONGOING,
                context.getString(R.string.service_ongoing_calls_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.service_status_bar_chip_desc)
                setSound(null, null)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(ongoingChannel)
            Log.d(TAG, "Created ongoing calls notification channel with IMPORTANCE_LOW to prevent heads-up banners")

            val incomingSilentChannel = NotificationChannel(
                CHANNEL_ID_INCOMING_SILENT,
                context.getString(R.string.notification_channel_incoming_call_unlocked),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.notification_channel_incoming_call_unlocked_desc)
                setSound(null, null)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(incomingSilentChannel)
            Log.d(TAG, "Created silent incoming channel with IMPORTANCE_MIN")
        }
    }

    @Suppress("DEPRECATION")
    fun buildAndPostNotification(
        call: Call,
        useHighPriority: Boolean,
        startForeground: (Int, Notification) -> Unit,
        stopForeground: (Int) -> Unit
    ) {
        val callState = call.details?.state ?: Call.STATE_NEW
        val phoneNumber = call.details?.handle?.schemeSpecificPart ?: bridge.currentPhoneNumber ?: ""
        val displayName = bridge.currentContactName?.takeIf { it.isNotBlank() } ?: phoneNumber

        val activityIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isIncoming = callState == Call.STATE_RINGING || useHighPriority

        if (!isIncoming) {
            runCatching {
                stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
                notificationManager.cancel(NOTIFICATION_ID)
            }.onFailure { e ->
                Log.e(TAG, "Failed to remove non-incoming call notification", e)
            }
            Log.d(TAG, "[NOTIF] Removed notification for non-incoming call state=${getStateName(callState)}")
            return
        }

        val isDeviceLocked = isDeviceLocked(context)
        val channelId = when {
            isDeviceLocked -> CHANNEL_ID_INCOMING
            else -> CHANNEL_ID_INCOMING_SILENT
        }

        val title = when (callState) {
            Call.STATE_RINGING    -> context.getString(R.string.service_incoming_call)
            Call.STATE_DIALING,
            Call.STATE_CONNECTING,
            Call.STATE_SELECT_PHONE_ACCOUNT -> context.getString(R.string.service_calling)
            Call.STATE_HOLDING    -> context.getString(R.string.incall_on_hold)
            else                  -> context.getString(R.string.service_ongoing_call)
        }

        val priority = if (isIncoming && isDeviceLocked) NotificationCompat.PRIORITY_MAX
                       else NotificationCompat.PRIORITY_MIN

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(displayName)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)

        if (isIncoming && isDeviceLocked) {
            val canUse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationManager.canUseFullScreenIntent().also { granted ->
                    Log.d(TAG, "canUseFullScreenIntent = $granted (API ${Build.VERSION.SDK_INT})")
                }
            } else {
                true
            }
            if (canUse) {
                builder.setFullScreenIntent(contentIntent, true)
                Log.d(TAG, "[NOTIF] fullScreenIntent attached (device is LOCKED)")
            } else {
                Log.w(TAG, "fullScreenIntent not available. Falling back to direct launch.")
            }
        } else if (isIncoming) {
            Log.d(TAG, "[NOTIF] Skipping fullScreenIntent - device is UNLOCKED, activity launched directly")
        }

        val notification = builder.build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "[NOTIF] startForeground posted on channel=$channelId (locked=$isDeviceLocked)")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}", e)
        }
    }

    private fun isDeviceLocked(context: Context): Boolean {
        return try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isScreenOn = powerManager.isInteractive
            val isLocked = keyguardManager.isKeyguardLocked
            Log.d(TAG, "Device locked: $isLocked (screen=$isScreenOn)")
            isLocked
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device lock state", e)
            true
        }
    }
}
