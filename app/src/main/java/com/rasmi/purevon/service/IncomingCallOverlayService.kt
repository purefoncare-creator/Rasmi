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
import android.telecom.Call
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
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
import com.rasmi.purevon.domain.call.InCallServiceBridge
import com.rasmi.purevon.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * بطاقة علوية صغيرة تُعرض عند وصول مكالمة واردة أثناء استخدام المستخدم للتطبيق.
 * تتضمن أزرار: رد / رفض / إسكات — دون مغادرة الصفحة الحالية (كما في ديالر أندرويد).
 */
@AndroidEntryPoint
class IncomingCallOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "IncomingCallOverlay"
        private const val CHANNEL_ID = "incoming_call_overlay_channel"
        private const val NOTIFICATION_ID = 2004

        const val ACTION_SHOW = "com.rasmi.purevon.ACTION_SHOW_INCOMING_OVERLAY"
        const val ACTION_HIDE = "com.rasmi.purevon.ACTION_HIDE_INCOMING_OVERLAY"
        const val ACTION_SILENCED = "com.rasmi.purevon.ACTION_OVERLAY_SILENCED"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_CONTACT_PHOTO_URI = "extra_contact_photo_uri"

        private var instance: WeakReference<IncomingCallOverlayService>? = null

        fun getInstance(): IncomingCallOverlayService? = instance?.get()

        fun isRunning(): Boolean = instance?.get() != null

        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

        /**
         * عرض البطاقة العلوية. تُستدعى فقط عندما يكون الجهاز غير مقفل
         * والسماح بالرسم فوق التطبيقات مفعّل.
         */
        fun show(
            context: Context,
            phoneNumber: String?,
            contactName: String?,
            contactPhotoUri: String? = null
        ): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot show incoming overlay - overlay permission not granted")
                return false
            }
            if (isRunning()) {
                Log.d(TAG, "Overlay already running - updating content")
                return true
            }

            val intent = Intent(context, IncomingCallOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_CONTACT_PHOTO_URI, contactPhotoUri)
            }

            return try {
                ContextCompat_startService(context, intent)
                Log.d(TAG, "Incoming overlay dispatched for: ${DebugLogger.maskPhoneNumber(phoneNumber ?: "")}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start incoming overlay service", e)
                false
            }
        }

        fun hide(context: Context) {
            try {
                val intent = Intent(context, IncomingCallOverlayService::class.java).apply {
                    action = ACTION_HIDE
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching hide intent", e)
            }
        }

        fun notifySilenced() {
            instance?.get()?.let { svc ->
                svc.runOnMain { svc.notifySilencedInternal() }
            }
        }

        private fun ContextCompat_startService(context: Context, intent: Intent) {
            context.startForegroundService(intent)
        }
    }

    @Inject internal lateinit var inCallServiceBridge: InCallServiceBridge

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var isShowing = false
    private var isSilenced = false

    private var phoneNumber: String = ""
    private var contactName: String? = null
    private var contactPhotoUri: String? = null

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

        Log.d(TAG, "IncomingCallOverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
                contactPhotoUri = intent.getStringExtra(EXTRA_CONTACT_PHOTO_URI)
                showOverlay()
            }
            ACTION_HIDE -> {
                hideOverlay()
                stopSelf()
            }
            ACTION_SILENCED -> notifySilencedInternal()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (isShowing) {
            updateContent()
            return
        }

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val params = createWindowLayoutParams()

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@IncomingCallOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IncomingCallOverlayService)

            setContent {
                IncomingCallOverlayContent(
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    contactPhotoUri = contactPhotoUri,
                    isSilenced = isSilenced,
                    onAnswer = { answerCall() },
                    onReject = { rejectCall() },
                    onSilence = { silenceCall() }
                )
            }
        }

        try {
            windowManager?.addView(composeView, params)
            isShowing = true
            Log.d(TAG, "Incoming overlay shown for: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing incoming overlay", e)
            stopSelf()
        }
    }

    private fun updateContent() {
        composeView?.setContent {
            IncomingCallOverlayContent(
                phoneNumber = phoneNumber,
                contactName = contactName,
                contactPhotoUri = contactPhotoUri,
                isSilenced = isSilenced,
                onAnswer = { answerCall() },
                onReject = { rejectCall() },
                onSilence = { silenceCall() }
            )
        }
    }

    private fun hideOverlay() {
        if (!isShowing) return
        try {
            composeView?.let { windowManager?.removeView(it) }
            composeView = null
            isShowing = false
            isSilenced = false
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            Log.d(TAG, "Incoming overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding incoming overlay", e)
            composeView = null
            isShowing = false
        }
    }

    private fun answerCall() {
        Log.d(TAG, "Answer pressed from overlay")
        try {
            val call = inCallServiceBridge.getCurrentCall()
            if (call != null && call.state == Call.STATE_RINGING) {
                call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error answering from overlay", e)
        }
        hideAndStop()
    }

    private fun rejectCall() {
        Log.d(TAG, "Reject pressed from overlay")
        try {
            val call = inCallServiceBridge.getCurrentCall()
            if (call != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    call.reject(Call.REJECT_REASON_DECLINED)
                } else {
                    @Suppress("DEPRECATION")
                    call.reject(false, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting from overlay", e)
        }
        hideAndStop()
    }

    private fun hideAndStop() {
        hideOverlay()
        stopSelf()
    }

    private fun silenceCall() {
        Log.d(TAG, "Silence pressed from overlay")
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            telecomManager.silenceRinger()
            isSilenced = true
            updateContent()
        } catch (e: Exception) {
            Log.e(TAG, "Error silencing call", e)
        }
    }

    private fun notifySilencedInternal() {
        isSilenced = true
        updateContent()
    }

    private fun runOnMain(block: () -> Unit) {
        android.os.Handler(mainLooper).post(block)
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 24 // Offset from top - below status bar
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.incoming_overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.incoming_overlay_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.incoming_overlay_notification_title))
            .setContentText(getString(R.string.incoming_overlay_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "IncomingCallOverlayService destroyed")
        hideOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
