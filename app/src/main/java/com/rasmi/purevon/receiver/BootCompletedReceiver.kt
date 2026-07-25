package com.rasmi.purevon.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import com.rasmi.purevon.MainActivity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rasmi.purevon.R
import com.rasmi.purevon.data.local.dao.ScheduledMessageDao
import com.rasmi.purevon.util.message.MessageScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Receiver for handling device boot completion
 * Reschedules pending scheduled messages after device restart
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var scheduledMessageDao: ScheduledMessageDao
    
    @Inject
    lateinit var messageScheduler: MessageScheduler
    
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON"
        )
        if (intent.action !in validActions) return
        
        // Check if dependencies are initialized
        if (!::scheduledMessageDao.isInitialized || !::messageScheduler.isInitialized) {
            Log.e(TAG, "Dependencies not initialized yet")
            return
        }
        
        Log.d(TAG, "Device boot completed, rescheduling pending messages...")
        
        // ✅ FIXED: Use goAsync() + withTimeout for proper BroadcastReceiver lifecycle
        val pendingResult = goAsync()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(25_000L) {
                // ✅ Fix #3: Only get PENDING messages (not SENT, FAILED, CANCELLED)
                val scheduledMessages = scheduledMessageDao.getPendingMessages().first()
                
                val currentTime = System.currentTimeMillis()
                var rescheduledCount = 0
                var expiredCount = 0
                
                scheduledMessages.forEach { message ->
                    if (message.scheduledTime > currentTime) {
                        // Message is still in the future, reschedule it
                        val result = messageScheduler.scheduleMessage(
                            messageId = message.id,
                            recipient = message.recipient,
                            messageBody = message.messageBody,
                            conversationId = 0L, // Will be determined when sent
                            scheduledTime = message.scheduledTime
                        )
                        
                        if (result.isSuccess) {
                            rescheduledCount++
                            Log.d(TAG, "Rescheduled message ${message.id} for ${message.recipient}")
                        } else {
                            Log.e(TAG, "Failed to reschedule message ${message.id}: ${result.exceptionOrNull()?.message}")
                        }
                    } else {
                        // Message was supposed to be sent in the past — notify user
                        scheduledMessageDao.deleteById(message.id)
                        expiredCount++
                        Log.d(TAG, "Deleted expired scheduled message ${message.id}")
                        
                        // Notify user about expired message
                        showExpiredNotification(context, message.recipient, message.messageBody)
                    }
                }
                
                Log.d(TAG, "Boot reschedule complete: $rescheduledCount rescheduled, $expiredCount expired")
                
                // ✅ إعادة جدولة الاتصالات الوهمية المعلقة بعد إعادة التشغيل
                rescheduleFakeCalls(context)
                
                } // withTimeout
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling messages after boot", e)
            } finally {
                job.cancel()
                pendingResult.finish()
            }
        }
    }
    
    /**
     * ✅ إعادة جدولة الاتصالات الوهمية المعلقة بعد إعادة تشغيل الجهاز
     * AlarmManager يفقد كل التنبيهات بعد الإطفاء، لذا نعيد جدولتها من SharedPreferences
     */
    private fun rescheduleFakeCalls(context: Context) {
        try {
            val fakeCalls = com.rasmi.purevon.util.FakeCallScheduleManager.getAll(context)
            if (fakeCalls.isEmpty()) {
                Log.d(TAG, "No pending fake calls to reschedule")
                return
            }
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            var rescheduled = 0
            
            fakeCalls.forEach { call ->
                try {
                    val broadcastIntent = Intent(context, FakeCallReceiver::class.java).apply {
                        putExtra(FakeCallReceiver.EXTRA_CALLER_NAME, call.callerName)
                        putExtra(FakeCallReceiver.EXTRA_REQUEST_CODE, call.requestCode)
                    }
                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        context,
                        call.requestCode,
                        broadcastIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(
                                android.app.AlarmManager.RTC_WAKEUP,
                                call.triggerTimeMs,
                                pendingIntent
                            )
                        } else {
                            alarmManager.setAndAllowWhileIdle(
                                android.app.AlarmManager.RTC_WAKEUP,
                                call.triggerTimeMs,
                                pendingIntent
                            )
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            call.triggerTimeMs,
                            pendingIntent
                        )
                    }
                    rescheduled++
                    Log.d(TAG, "Rescheduled fake call: ${call.callerName} at ${call.triggerTimeMs}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule fake call ${call.requestCode}", e)
                }
            }
            
            Log.d(TAG, "✅ Fake calls rescheduled: $rescheduled/${fakeCalls.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error rescheduling fake calls after boot", e)
        }
    }
    
    private fun showExpiredNotification(context: Context, recipient: String, messageBody: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "scheduled_failures",
                context.getString(R.string.scheduled_message_failures),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "new_conversation")
            putExtra("phone_number", recipient)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "scheduled_failures")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.scheduled_message_failed))
            .setContentText(
                context.getString(
                    R.string.scheduled_message_failed_body,
                    recipient,
                    messageBody.take(50)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
