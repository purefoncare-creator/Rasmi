package com.rasmi.purevon.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rasmi.purevon.R
import com.rasmi.purevon.data.local.dao.BlockedNumberDao
import com.rasmi.purevon.data.local.dao.WhitelistDao
import com.rasmi.purevon.data.local.entity.WhitelistEntity
import com.rasmi.purevon.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import javax.inject.Inject

@AndroidEntryPoint
class BlockedCallBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "BlockedCallBubble"
        private const val CHANNEL_ID = "blocked_call_bubble_channel"
        private const val NOTIFICATION_ID = 2003

        const val ACTION_SHOW = "com.rasmi.purevon.ACTION_SHOW_BLOCKED_CALL"
        const val ACTION_HIDE = "com.rasmi.purevon.ACTION_HIDE_BLOCKED_CALL"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_CONTACT_PHOTO_URI = "extra_contact_photo_uri"

        private var instance: WeakReference<BlockedCallBubbleService>? = null

        fun getInstance(): BlockedCallBubbleService? = instance?.get()

        fun isRunning(): Boolean = instance?.get() != null

        fun show(
            context: Context,
            phoneNumber: String?,
            contactName: String?,
            contactPhotoUri: String? = null
        ): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot show blocked call bubble - overlay permission not granted")
                return false
            }

            val intent = Intent(context, BlockedCallBubbleService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_CONTACT_PHOTO_URI, contactPhotoUri)
            }

            return try {
                context.startForegroundService(intent)
                Log.d(TAG, "Blocked call bubble dispatched for: ${DebugLogger.maskPhoneNumber(phoneNumber ?: "")}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start blocked call bubble service", e)
                false
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, BlockedCallBubbleService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    @Inject lateinit var blockedNumberDao: BlockedNumberDao
    @Inject lateinit var whitelistDao: WhitelistDao
    @Inject lateinit var soundManager: com.rasmi.purevon.util.SoundManager

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var isShowing = false

    private var phoneNumber: String = ""
    private var contactName: String? = null
    private var contactPhotoUri: String? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoDismissJob: Job? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        // ✅ FIX M38: shortService (مثل نافذة OTP) — نوع phoneCall يتطلب مكالمة نشطة
        // والمكالمة المحظورة مرفوضة أصلاً فيفشل البدء على Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        Log.d(TAG, "BlockedCallBubbleService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
                contactPhotoUri = intent.getStringExtra(EXTRA_CONTACT_PHOTO_URI)

                // Shown for unknown/private numbers too (phoneNumber may be blank).
                showBubble()
                scheduleAutoDismiss()
                soundManager.playBlockedCallSound()
            }
            ACTION_HIDE -> {
                hideBubble()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (isShowing) {
            updateBubbleContent()
            return
        }

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val params = createWindowLayoutParams()

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BlockedCallBubbleService)
            setViewTreeSavedStateRegistryOwner(this@BlockedCallBubbleService)

            setContent {
                BlockedCallBubbleContent(
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    contactPhotoUri = contactPhotoUri,
                    onCall = { callNumber() },
                    onAddToWhitelist = { addToWhitelistAndDismiss() },
                    onDismiss = {
                        hideBubble()
                        stopSelf()
                    }
                )
            }
        }

        try {
            windowManager?.addView(composeView, params)
            isShowing = true
            Log.d(TAG, "Blocked call bubble shown for: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing blocked call bubble", e)
            stopSelf()
        }
    }

    private fun updateBubbleContent() {
        composeView?.setContent {
            BlockedCallBubbleContent(
                phoneNumber = phoneNumber,
                contactName = contactName,
                contactPhotoUri = contactPhotoUri,
                onCall = { callNumber() },
                onAddToWhitelist = { addToWhitelistAndDismiss() },
                onDismiss = {
                    hideBubble()
                    stopSelf()
                }
            )
        }
    }

    private fun hideBubble() {
        if (!isShowing) return

        try {
            composeView?.let { view ->
                windowManager?.removeView(view)
            }
            composeView = null
            isShowing = false
            autoDismissJob?.cancel()
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            Log.d(TAG, "Blocked call bubble hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding blocked call bubble", e)
            composeView = null
            isShowing = false
        }
    }

    private fun callNumber() {
        if (phoneNumber.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", phoneNumber, null)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Log.d(TAG, "Opened dialer for: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening dialer for blocked number", e)
        }
        hideBubble()
        stopSelf()
    }

    private fun addToWhitelistAndDismiss() {
        serviceScope.launch {
            try {
                whitelistDao.insertWhitelistNumber(
                    WhitelistEntity(
                        phoneNumber = phoneNumber,
                        contactName = contactName,
                        reason = "Added from blocked call bubble"
                    )
                )
                blockedNumberDao.unblockNumber(phoneNumber)
                Log.d(TAG, "Added to whitelist: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add to whitelist", e)
            }
            Toast.makeText(
                this@BlockedCallBubbleService,
                getString(R.string.blocked_call_bubble_whitelisted),
                Toast.LENGTH_SHORT
            ).show()
            delay(300)
            hideBubble()
            stopSelf()
        }
    }

    private fun scheduleAutoDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = serviceScope.launch {
            // ✅ FIX M32a: 40 ثانية حسب طلب المستخدم
            delay(40_000)
            Log.d(TAG, "Auto-dismissing blocked call bubble after 40s")
            hideBubble()
            stopSelf()
        }
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Offset from top
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.blocked_call_bubble_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.blocked_call_bubble_channel_desc)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.blocked_call_bubble_notification_title))
            .setContentText(getString(R.string.blocked_call_bubble_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "BlockedCallBubbleService destroyed")

        hideBubble()
        autoDismissJob?.cancel()
        serviceScope.cancel()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
