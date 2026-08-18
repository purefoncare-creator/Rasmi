package com.rasmi.purevon.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rasmi.purevon.R
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.call.InCallServiceBridge
import com.rasmi.purevon.presentation.overlay.FloatingCallOverlay
import com.rasmi.purevon.presentation.theme.PurevonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Service لإظهار الشريط العائم للمكالمات فوق جميع التطبيقات
 * ✅ تم إصلاح: Memory Leak, Background Service kill, Battery Drain
 */
@AndroidEntryPoint
class FloatingCallService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    
    @Inject
    lateinit var inCallServiceBridge: InCallServiceBridge

    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    companion object {
        private const val TAG = "FloatingCallService"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_CALL_START_TIME = "call_start_time" // ✅ Changed from Duration String
        const val EXTRA_IS_MUTED = "is_muted"
        const val EXTRA_IS_SPEAKER_ON = "is_speaker_on"
        const val EXTRA_IS_RINGING = "is_ringing"
        const val EXTRA_IS_DIALING = "is_dialing"
        // ✅ Multi-call extras
        const val EXTRA_SECOND_CALL_NAME = "second_call_name"
        const val EXTRA_SECOND_CALL_NUMBER = "second_call_number"
        const val EXTRA_SECOND_CALL_STATE = "second_call_state" // "waiting", "held", or null
        const val EXTRA_CURRENT_AUDIO_ROUTE = "current_audio_route"
        
        const val ACTION_UPDATE = "com.rasmi.purevon.ACTION_UPDATE_OVERLAY"
        const val ACTION_STOP = "com.rasmi.purevon.ACTION_STOP_OVERLAY"
        const val ACTION_HIDE = "com.rasmi.purevon.ACTION_HIDE_OVERLAY"
        const val ACTION_SHOW = "com.rasmi.purevon.ACTION_SHOW_OVERLAY"
        
        private const val NOTIFICATION_ID = 4567
        private const val CHANNEL_ID = "floating_call_overlay_channel"
        
        // ✅ Static flag: يمنع إعادة إنشاء overlay بعد إنهاء المكالمة من الشريط
        // لا يعتمد على instance لأن Android قد يُنشئ instance جديد
        @Volatile
        var isPendingStop = false
        
        fun start(context: Context, contactName: String?, phoneNumber: String) {
            isPendingStop = false // ✅ مكالمة جديدة: إعادة تفعيل الـ overlay
            val intent = Intent(context, FloatingCallService::class.java).apply {
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            }
            context.startForegroundService(intent)
        }
        
        fun update(
            context: Context,
            contactName: String?,
            phoneNumber: String,
            callStartTime: Long,
            isMuted: Boolean,
            isSpeakerOn: Boolean,
            isRinging: Boolean,
            isDialing: Boolean = false,
            secondCallName: String? = null,
            secondCallNumber: String? = null,
            secondCallState: String? = null, // "waiting", "held", or null
            currentAudioRoute: Int = 0
        ) {
            val intent = Intent(context, FloatingCallService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CALL_START_TIME, callStartTime)
                putExtra(EXTRA_IS_MUTED, isMuted)
                putExtra(EXTRA_IS_SPEAKER_ON, isSpeakerOn)
                putExtra(EXTRA_IS_RINGING, isRinging)
                putExtra(EXTRA_IS_DIALING, isDialing)
                putExtra(EXTRA_SECOND_CALL_NAME, secondCallName)
                putExtra(EXTRA_SECOND_CALL_NUMBER, secondCallNumber)
                putExtra(EXTRA_SECOND_CALL_STATE, secondCallState)
                putExtra(EXTRA_CURRENT_AUDIO_ROUTE, currentAudioRoute)
            }
            // ✅ Only start if needed, otherwise send command. 
            // In Android 8+, starting service from bg is restricted, but if it is already FG service it is fine.
            // ✅ لا ترسل UPDATE إذا كان الشريط في طور الإيقاف (يمنع القفزة)
            if (isPendingStop) {
                Log.d(TAG, "update() skipped - isPendingStop=true")
                return
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update overlay service", e)
            }
        }
        
        fun hide(context: Context) {
            val intent = Intent(context, FloatingCallService::class.java).apply {
                action = ACTION_HIDE
            }
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {}
        }
        
        fun show(context: Context) {
            val intent = Intent(context, FloatingCallService::class.java).apply {
                action = ACTION_SHOW
            }
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {}
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, FloatingCallService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {}
        }
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var currentParams: WindowManager.LayoutParams? = null
    
    // Lifecycle components
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    // State
    private var contactName by mutableStateOf<String?>(null)
    private var phoneNumber by mutableStateOf("")
    private var callStartTime by mutableLongStateOf(0L)
    private var isMuted by mutableStateOf(false)
    private var isSpeakerOn by mutableStateOf(false)
    private var currentAudioRoute by mutableIntStateOf(0) // CallAudioState.ROUTE_*
    private var isRinging by mutableStateOf(false)
    private var isDialing by mutableStateOf(false)
    private var isVisible by mutableStateOf(true)
    // ✅ Collapsed/Bubble state
    private var isCollapsed by mutableStateOf(false)
    // ✅ Multi-call state
    private var secondCallName by mutableStateOf<String?>(null)
    private var secondCallNumber by mutableStateOf<String?>(null)
    private var secondCallState by mutableStateOf<String?>(null) // "waiting", "held", or null
    private var darkThemeOverride by mutableStateOf<Boolean?>(null)
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
    
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        observeThemeSettings()
        startForegroundSelf()
        Log.d(TAG, "FloatingCallService created")
    }

    private fun observeThemeSettings() {
        scope.launch {
            combine(
                settingsDataStore.isDarkMode,
                settingsDataStore.isAutoTheme
            ) { isDarkMode, autoTheme ->
                Pair(isDarkMode, autoTheme)
            }.collect { (isDarkMode, autoTheme) ->
                darkThemeOverride = if (autoTheme) null else isDarkMode
            }
        }
    }
    
    private fun startForegroundSelf() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(com.rasmi.purevon.R.string.notification_channel_call_overlay),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(com.rasmi.purevon.R.string.notification_channel_call_overlay_desc)
            setSound(null, null)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.rasmi.purevon.R.string.notification_active_call_title))
            .setContentText(getString(com.rasmi.purevon.R.string.notification_active_call_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isPendingStop = true
                hideOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HIDE -> {
                hideOverlay()
                return START_STICKY
            }
            ACTION_SHOW -> {
                showOverlay()
                return START_STICKY
            }
            ACTION_UPDATE -> {
                updateFromIntent(intent)
                return START_STICKY
            }
        }
        
        // ✅ F5: عند إعادة تشغيل النظام للخدمة (START_STICKY)، intent قد يكون null
        // نتحقق من bridge إذا كانت المكالمة لا تزال نشطة
        if (intent == null) {
            val bridgeCall = inCallServiceBridge.getCurrentCall()
            val bridgePhone = inCallServiceBridge.currentPhoneNumber
            if (bridgeCall != null && bridgePhone != null) {
                contactName = inCallServiceBridge.currentContactName
                phoneNumber = bridgePhone
                callStartTime = inCallServiceBridge.getCallStartTime()
                Log.d(TAG, "Service restarted by system, restored state from bridge")
            } else {
                Log.w(TAG, "Service restarted but no active call, stopping")
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
            phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
            
            if (phoneNumber.isEmpty()) {
                Log.e(TAG, "No phone number provided, stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        // تأخير لمدة 500ms للسماح للنشاط بالظهور أولاً
        if (overlayView == null && isVisible) {
            scope.launch {
                delay(500)
                if (isVisible && overlayView == null && !isActivityInForeground()) {
                    showOverlay()
                }
            }
        }
        
        return START_STICKY
    }
    
    private fun updateFromIntent(intent: Intent) {
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        callStartTime = intent.getLongExtra(EXTRA_CALL_START_TIME, 0L)
        isMuted = intent.getBooleanExtra(EXTRA_IS_MUTED, false)
        isSpeakerOn = intent.getBooleanExtra(EXTRA_IS_SPEAKER_ON, false)
        currentAudioRoute = intent.getIntExtra(EXTRA_CURRENT_AUDIO_ROUTE, 0)
        isRinging = intent.getBooleanExtra(EXTRA_IS_RINGING, false)
        isDialing = intent.getBooleanExtra(EXTRA_IS_DIALING, false)
        // ✅ Multi-call data
        secondCallName = intent.getStringExtra(EXTRA_SECOND_CALL_NAME)
        secondCallNumber = intent.getStringExtra(EXTRA_SECOND_CALL_NUMBER)
        secondCallState = intent.getStringExtra(EXTRA_SECOND_CALL_STATE)
        
        if (isVisible && overlayView == null && !isActivityInForeground()) {
             showOverlay()
        }
    }
    
    // ✅ Fix Memory Leak: Cleanup in onDestroy
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        store.clear()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
        Log.d(TAG, "FloatingCallService destroyed and cleaned up")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (!isVisible || overlayView != null || isPendingStop) return
        
        // ✅ F2: فحص إذن الرسم فوق التطبيقات قبل إضافة الـ overlay
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted, skipping overlay")
            return
        }
        
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = getStatusBarHeight()
            }
            
            currentParams = params
            // ✅ إعادة تعيين حالة الطي عند إظهار الشريط
            isCollapsed = false
            
            overlayView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingCallService)
                setViewTreeViewModelStoreOwner(this@FloatingCallService)
                setViewTreeSavedStateRegistryOwner(this@FloatingCallService)
                
                setContent {
                    val useDarkTheme = darkThemeOverride ?: isSystemInDarkTheme()
                    PurevonTheme(darkTheme = useDarkTheme) {
                        FloatingCallOverlay(
                            contactName = contactName,
                            phoneNumber = phoneNumber,
                            callStartTime = callStartTime,
                            isMuted = isMuted,
                            isSpeakerOn = isSpeakerOn,
                            currentAudioRoute = currentAudioRoute,
                            isRinging = isRinging,
                            isDialing = isDialing,
                            secondCallName = secondCallName,
                            secondCallNumber = secondCallNumber,
                            secondCallState = secondCallState,
                            isCollapsed = isCollapsed,
                            onCollapsedChange = { collapsed ->
                                isCollapsed = collapsed
                                if (collapsed) {
                                    // التصغير: أخّر تغيير حجم النافذة حتى تنتهي أنيميشن الطيّ (300ms)
                                    // بدون تأخير: الشريط يُقطع مفاجئاً إلى WRAP_CONTENT بينما هو لا يزال ظاهراً
                                    scope.launch {
                                        delay(250)
                                        updateLayoutForMode(true)
                                    }
                                } else {
                                    // التوسيع: غيّر التخطيط فوراً حتى تستوعب الأنيميشن الكامل عرض الشاشة
                                    updateLayoutForMode(false)
                                }
                            },
                            onDrag = { dx, dy ->
                                handleDrag(dx, dy)
                            },
                            onDragEnd = {
                                snapToEdge()
                            },
                            onToggleMute = { toggleMute() },
                            onToggleSpeaker = { toggleSpeaker() },
                            onAnswer = { answerCall() },
                            onReject = { rejectCall() },
                            onSilence = { silenceCall() },
                            onEndCall = { endCall() },
                            onAnswerWaiting = { answerWaitingCall() },
                            onRejectWaiting = { rejectWaitingCall() },
                            onSwapCalls = { swapCalls() },
                            onTap = { openCallScreen() },
                            darkThemeOverride = useDarkTheme
                        )
                    }
                }
            }
            
            windowManager?.addView(overlayView, params)
            // ✅ F3: الانتقال عبر STARTED قبل RESUMED لاحترام عقد LifecycleRegistry
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            Log.d(TAG, "Overlay shown successfully")
            
        } catch (e: Exception) {
            // ✅ F6: لا نقتل الخدمة عند فشل الـ overlay — المكالمة لا تزال نشطة
            Log.e(TAG, "Error showing overlay (service continues running)", e)
            overlayView = null
        }
    }
    
    /**
     * ✅ تحديث تخطيط النافذة عند التبديل بين الشريط والفقاعة
     */
    private fun updateLayoutForMode(collapsed: Boolean) {
        try {
            val params = currentParams ?: return
            val view = overlayView ?: return
            
            if (collapsed) {
                // ✅ وضع الفقاعة: WRAP_CONTENT + محاذاة لليمين
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.TOP or Gravity.END
                // الموقع الأولي: أعلى يمين الشاشة
                params.x = 0
                params.y = getStatusBarHeight()
            } else {
                // ✅ وضع الشريط: MATCH_PARENT + مركز أفقي
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = 0
                params.y = getStatusBarHeight()
            }
            
            windowManager?.updateViewLayout(view, params)
            Log.d(TAG, "Layout updated for mode: ${if (collapsed) "BUBBLE" else "BAR"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating layout for mode", e)
        }
    }
    
    /**
     * ✅ معالجة السحب لتحريك الفقاعة
     */
    private fun handleDrag(dx: Float, dy: Float) {
        try {
            val params = currentParams ?: return
            val view = overlayView ?: return
            
            // تحديث الموضع بناءً على الجاذبية
            // عندما تكون الجاذبية END، x موجب يعني للداخل (لليسار)
            // عندما تكون الجاذبية START، x موجب يعني لليمين
            if (params.gravity and Gravity.END == Gravity.END) {
                params.x -= dx.toInt()
            } else {
                params.x += dx.toInt()
            }
            params.y += dy.toInt()
            
            // ✅ حدود الشاشة
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            params.x = params.x.coerceIn(0, screenWidth - 200)
            params.y = params.y.coerceIn(0, screenHeight - 200)
            
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling drag", e)
        }
    }
    
    /**
     * ✅ الانجذاب للحافة الأقرب عند انتهاء السحب
     */
    private fun snapToEdge() {
        try {
            val params = currentParams ?: return
            val view = overlayView ?: return
            
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            
            // حساب المركز الحقيقي للفقاعة
            val bubbleCenterX = if (params.gravity and Gravity.END == Gravity.END) {
                screenWidth - params.x
            } else {
                params.x
            }
            
            // ✅ الانجذاب للحافة الأقرب
            if (bubbleCenterX < screenWidth / 2) {
                // أقرب للحافة اليسرى
                params.gravity = Gravity.TOP or Gravity.START
                params.x = 0
            } else {
                // أقرب للحافة اليمنى
                params.gravity = Gravity.TOP or Gravity.END
                params.x = 0
            }
            
            windowManager?.updateViewLayout(view, params)
            Log.d(TAG, "Snapped to ${if (params.gravity and Gravity.END == Gravity.END) "RIGHT" else "LEFT"} edge")
        } catch (e: Exception) {
            Log.e(TAG, "Error snapping to edge", e)
        }
    }
    
    private fun hideOverlay() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay view", e)
        }
        overlayView = null
        // Do NOT set isVisible = false here, as this is internal cleanup
        Log.d(TAG, "Overlay hidden (View removed)")
    }
    
    private fun toggleMute() {
        // ✅ نعتمد على نتيجة Service بدلاً من قلب القيمة محلياً لتجنب التفارق عند الفشل
        val newMuted = inCallServiceBridge.toggleMute()
        isMuted = newMuted
        Log.d(TAG, "Mute toggled to: $isMuted")
    }
    
    private fun toggleSpeaker() {
        // ✅ نعتمد على نتيجة Service بدلاً من قلب القيمة محلياً لتجنب التفارق عند الفشل
        val newSpeaker = inCallServiceBridge.toggleSpeaker()
        isSpeakerOn = newSpeaker
        currentAudioRoute = inCallServiceBridge.getCurrentAudioRoute()
        Log.d(TAG, "Speaker toggled to: $isSpeakerOn, route: $currentAudioRoute")
    }
    
    private fun answerCall() {
        inCallServiceBridge.getCurrentCall()?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        Log.d(TAG, "Call answered")
    }
    
    private fun rejectCall() {
        isPendingStop = true // ✅ Static flag: يمنع أي instance من إعادة إنشاء الـ overlay
        isVisible = false
        hideOverlay()
        val call = inCallServiceBridge.getCurrentCall()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            call?.reject(Call.REJECT_REASON_DECLINED)
        } else {
            @Suppress("DEPRECATION")
            call?.reject(false, null)
        }
        Log.d(TAG, "Call rejected - overlay hidden immediately")
    }
    
    @SuppressLint("MissingPermission")
    private fun silenceCall() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.silenceRinger()
            Log.d(TAG, "Call silenced")
        } catch (e: SecurityException) {
            // silenceRinger requires a privileged Telecom permission on some devices.
            Log.w(TAG, "Call silencing is unavailable on this device", e)
        }
    }
    
    private fun endCall() {
        isPendingStop = true // ✅ Static flag: يمنع أي instance من إعادة إنشاء الـ overlay
        isVisible = false
        hideOverlay()
        inCallServiceBridge.getCurrentCall()?.disconnect()
        Log.d(TAG, "Call disconnect requested - overlay hidden immediately")
    }
    
    // ✅ Multi-call actions
    private fun answerWaitingCall() {
        inCallServiceBridge.answerAndHold()
        Log.d(TAG, "Waiting call answered, current call held")
    }
    
    private fun rejectWaitingCall() {
        inCallServiceBridge.rejectWaitingCall()
        secondCallName = null
        secondCallNumber = null
        secondCallState = null
        Log.d(TAG, "Waiting call rejected")
    }
    
    private fun swapCalls() {
        inCallServiceBridge.swapCalls()
        Log.d(TAG, "Calls swapped")
    }
    
    private fun openCallScreen() {
        try {
            val intent = Intent(this, com.rasmi.purevon.presentation.screen.incall.InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening call screen", e)
        }
    }
    
    /**
     * ✅ Uses InCallActivity.isInForeground flag directly - reliable on all Android versions.
     * getRunningTasks() was deprecated in API 21 and returns wrong results on many OEMs.
     */
    private fun isActivityInForeground(): Boolean {
        return com.rasmi.purevon.presentation.screen.incall.InCallActivity.isInForeground
    }
}
