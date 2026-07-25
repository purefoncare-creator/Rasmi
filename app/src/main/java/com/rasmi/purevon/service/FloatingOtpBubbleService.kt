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
import androidx.annotation.RequiresApi
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
import com.rasmi.purevon.util.SoundManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * Floating OTP Bubble Service
 * Shows a floating bubble with OTP code when SMS arrives
 */
@RequiresApi(Build.VERSION_CODES.M)
@AndroidEntryPoint
class FloatingOtpBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingOtpBubble"
        private const val CHANNEL_ID = "floating_otp_bubble_channel"
        private const val NOTIFICATION_ID = 2002
        
        const val ACTION_SHOW_OTP = "com.rasmi.purevon.ACTION_SHOW_OTP"
        const val ACTION_HIDE_OTP = "com.rasmi.purevon.ACTION_HIDE_OTP"
        const val EXTRA_OTP_CODE = "extra_otp_code"
        const val EXTRA_SENDER = "extra_sender"
        
        private var instance: WeakReference<FloatingOtpBubbleService>? = null
        
        fun getInstance(): FloatingOtpBubbleService? = instance?.get()
        
        fun isRunning(): Boolean = instance?.get() != null
        
        /**
         * Show the floating OTP bubble
         * @return true if the bubble was actually dispatched (overlay permission granted),
         *         false if the permission is missing and the bubble could NOT be shown.
         *         Callers MUST fall-back to a regular notification when false is returned.
         */
        fun show(
            context: Context,
            otpCode: String,
            sender: String
        ): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot show OTP bubble - overlay permission not granted, will show notification instead")
                return false
            }
            
            val intent = Intent(context, FloatingOtpBubbleService::class.java).apply {
                action = ACTION_SHOW_OTP
                putExtra(EXTRA_OTP_CODE, otpCode)
                putExtra(EXTRA_SENDER, sender)
            }
            
            return try {
                context.startForegroundService(intent)
                Log.d(TAG, "OTP bubble dispatched for sender: $sender")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start OTP bubble service", e)
                false
            }
        }
        
        /**
         * Hide the floating OTP bubble
         */
        fun hide(context: Context) {
            val intent = Intent(context, FloatingOtpBubbleService::class.java).apply {
                action = ACTION_HIDE_OTP
            }
            context.startService(intent)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    @Inject lateinit var soundManager: SoundManager
    
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var isShowing = false
    
    private var otpCode: String = ""
    private var sender: String = ""
    
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        Log.d(TAG, "FloatingOtpBubbleService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OTP -> {
                otpCode = intent.getStringExtra(EXTRA_OTP_CODE) ?: ""
                sender = intent.getStringExtra(EXTRA_SENDER) ?: ""
                
                if (otpCode.isNotEmpty()) {
                    showBubble()
                    scheduleAutoDismiss()
                    // The SMS notification is suppressed when the OTP bubble shows
                    // (see SmsReceiver.handleOtpAutoCopy), so play the dedicated
                    // OTP sound here.
                    soundManager.playOtpSound()
                }
            }
            ACTION_HIDE_OTP -> {
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
            setViewTreeLifecycleOwner(this@FloatingOtpBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOtpBubbleService)
            
            setContent {
                FloatingOtpBubbleContent(
                    otpCode = otpCode,
                    sender = sender,
                    onCopyClick = {
                        copyOtpToClipboard()
                    },
                    onDismissClick = {
                        hideBubble()
                        stopSelf()
                    }
                )
            }
        }
        
        try {
            windowManager?.addView(composeView, params)
            isShowing = true
            Log.d(TAG, "OTP Bubble shown for sender: ${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(sender)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing OTP bubble", e)
            stopSelf()
        }
    }

    private fun updateBubbleContent() {
        composeView?.setContent {
            FloatingOtpBubbleContent(
                otpCode = otpCode,
                sender = sender,
                onCopyClick = {
                    copyOtpToClipboard()
                },
                onDismissClick = {
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
            composeView = null   // prevent memory leak — matches FloatingCallService pattern
            isShowing = false
            autoDismissJob?.cancel()
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            Log.d(TAG, "OTP Bubble hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding OTP bubble", e)
            composeView = null
            isShowing = false
        }
    }

    private fun copyOtpToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("OTP", otpCode).apply {
            // Mark as sensitive so Android 13+ hides it from clipboard history and other apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = android.os.PersistableBundle().apply {
                    putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)
        
        // Show a generic confirmation toast — never expose the raw OTP code in UI text
        android.widget.Toast.makeText(
            this,
            getString(R.string.otp_copied_generic),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        // ✅ FIXED: Don't log OTP codes — sensitive data
        Log.d(TAG, "OTP copied to clipboard")
        
        // Auto-hide after copying
        serviceScope.launch {
            delay(500)
            hideBubble()
            stopSelf()
        }
    }

    private fun scheduleAutoDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = serviceScope.launch {
            delay(30_000) // 30 seconds
            Log.d(TAG, "Auto-dismissing OTP bubble after 30s")
            // Show a fallback notification so the user doesn't miss the OTP
            // if they weren't looking at the screen during the 30s window.
            showMissedOtpNotification()
            hideBubble()
            stopSelf()
        }
    }

    /**
     * Shows a silent notification after the bubble auto-dismisses.
     * The notification does NOT contain the OTP code — it only tells the user
     * to check their SMS inbox.
     */
    private fun showMissedOtpNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val maskedSender = if (sender.length > 3) "${sender.take(3)}***" else sender
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.otp_missed_notification_title))
            .setContentText(getString(R.string.otp_missed_notification_text, maskedSender))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID + 1, notification)
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
            getString(R.string.otp_bubble_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.otp_bubble_channel_desc)
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.otp_bubble_notification_title))
            .setContentText(getString(R.string.otp_bubble_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "FloatingOtpBubbleService destroyed")
        
        hideBubble()
        autoDismissJob?.cancel()
        serviceScope.cancel()
        
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
