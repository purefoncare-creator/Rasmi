package com.rasmi.purevon.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.screen.fakecall.FakeInCallActivity
import com.rasmi.purevon.util.FakeCallScheduleManager

class FakeCallReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "FakeCallReceiver"
        const val EXTRA_CALLER_NAME = "fake_caller_name"
        const val EXTRA_REQUEST_CODE = "fake_request_code"
        private const val NOTIFICATION_ID = 9001
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Fake call triggered!")
        
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        
        if (requestCode != -1) {
            FakeCallScheduleManager.remove(context, requestCode)
        }
        FakeCallScheduleManager.removeExpired(context)
        
        Log.d(TAG, "Opening fake incoming call screen for: $callerName")
        
        val fakeCallIntent = Intent(context, FakeInCallActivity::class.java).apply {
            putExtra(FakeInCallActivity.EXTRA_CALLER_NAME, callerName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fake_call_channel",
                context.getString(R.string.notification_channel_fake_call),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_fake_call_desc)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, requestCode, fakeCallIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, "fake_call_channel")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(callerName)
                .setContentText(context.getString(R.string.notification_fake_call_incoming))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
            try {
                context.startActivity(fakeCallIntent)
            } catch (_: Exception) {}
        } else {
            context.startActivity(fakeCallIntent)
        }
    }
}
