package com.rasmi.purevon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rasmi.purevon.R

/**
 * Foreground service that keeps the process alive during long MMS operations
 * (send with retries, download). Without this, Android may kill the process
 * during exponential backoff delays or slow network transfers.
 *
 * Usage: start via [startMmsService] / stop via [stopMmsService].
 */
class MmsForegroundService : Service() {

    companion object {
        private const val TAG = "MmsForegroundService"
        private const val CHANNEL_ID = "mms_operation_channel"
        private const val NOTIFICATION_ID = 99002

        private const val ACTION_START = "com.rasmi.purevon.action.MMS_START"
        private const val ACTION_STOP = "com.rasmi.purevon.action.MMS_STOP"
        const val EXTRA_OPERATION = "mms_operation" // "send" or "download"
        const val EXTRA_PHONE_NUMBER = "mms_phone_number"

        fun startMmsService(context: Context, operation: String, phoneNumber: String? = null) {
            val intent = Intent(context, MmsForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OPERATION, operation)
                phoneNumber?.let { putExtra(EXTRA_PHONE_NUMBER, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopMmsService(context: Context) {
            val intent = Intent(context, MmsForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val operation = intent.getStringExtra(EXTRA_OPERATION) ?: "MMS"
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                val title = when (operation) {
                    "send" -> getString(R.string.mms_fg_sending)
                    "download" -> getString(R.string.mms_fg_downloading)
                    else -> getString(R.string.mms_fg_processing)
                }
                val text = if (!phone.isNullOrBlank()) {
                    getString(R.string.mms_fg_to, phone)
                } else {
                    getString(R.string.mms_fg_in_progress)
                }
                startForeground(NOTIFICATION_ID, buildNotification(title, text))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.mms_fg_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.mms_fg_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
