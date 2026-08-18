package com.rasmi.purevon.presentation.screen.incall

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.ConfigurationCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.presentation.theme.PurevonTheme
import com.rasmi.purevon.service.FloatingCallService
import com.rasmi.purevon.domain.call.InCallServiceBridge
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Separate Activity for In-Call UI
 * This ensures the call screen is always shown properly over lock screen
 */
@AndroidEntryPoint
class InCallActivity : AppCompatActivity() {
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    @Inject
    lateinit var inCallServiceBridge: InCallServiceBridge
    
    companion object {
        private const val TAG = "InCallActivity"

        // Track if InCallActivity is in foreground
        var isInForeground = false
            private set
    }
    

    
    // ✅ BroadcastReceiver لإغلاق النشاط عندما تنتهي مكالمة واردة لم يتم الرد عليها
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.rasmi.purevon.CLOSE_INCALL_ACTIVITY") {
                android.util.Log.d(TAG, "Received close broadcast - finishing activity")
                finish()
            }
        }
    }
    
    // ✅ Proximity Sensor WakeLock - لإيقاف الشاشة عند تقريب الهاتف للوجه
    private var proximityWakeLock: PowerManager.WakeLock? = null
    
    // Job لمراقبة حالة السماعة الخارجية
    private var speakerStateJob: Job? = null
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Configure window BEFORE super.onCreate() and setContent()
        // This is critical for proper lock screen behavior
        configureWindowForCall()
        
        super.onCreate(savedInstanceState)
        android.util.Log.d(TAG, "onCreate called")
        isInForeground = true // Set immediately on create
        
        // ✅ تسجيل BroadcastReceiver لإغلاق النشاط
        val filter = IntentFilter("com.rasmi.purevon.CLOSE_INCALL_ACTIVITY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(
                closeReceiver,
                filter,
                "com.rasmi.purevon.permission.INTERNAL_BROADCAST",
                null
            )
        }
        android.util.Log.d(TAG, "Close receiver registered")
        
        // Handle back press - don't allow exit during call
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        
        enableEdgeToEdge()

        setContent {
            // Read theme settings from DataStore
            val isDarkMode by settingsDataStore.isDarkMode.collectAsState(initial = false)
            val autoTheme by settingsDataStore.autoTheme.collectAsState(initial = true)
            val appLanguage by settingsDataStore.appLanguage.collectAsState(initial = "system")
            
            // RTL layout direction is determined dynamically by the active language preference or system default
            val systemLocale = androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current).get(0)
            val activeLanguage = if (appLanguage == "system") (systemLocale?.language ?: "en") else appLanguage
            val isRtl = activeLanguage == "ar" || activeLanguage == "fa" || activeLanguage == "ur" || activeLanguage == "he"
            
            // Determine dark theme based on settings
            val useDarkTheme = if (autoTheme) {
                isSystemInDarkTheme()
            } else {
                isDarkMode
            }
            
            PurevonTheme(darkTheme = useDarkTheme) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl
                        else androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        InCallScreen()
                    }
                }
            }
        }
        
        // Request keyguard dismissal after UI is set up
        requestKeyguardDismissal()
    }
    
    private fun configureWindowForCall() {
        android.util.Log.d(TAG, "Configuring window for call")
        
        // ✅ ALWAYS configure for lock screen display - system will handle when to show
        // These settings MUST be set unconditionally for lock screen to work properly
        
        // For API 27+ use modern methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            android.util.Log.d(TAG, "Set showWhenLocked and turnScreenOn (API 27+)")
        }
        
        // Window flags for lock screen compatibility (all Android versions)
        // These flags tell the system this Activity should appear over lock screen
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        
        // Check if device is locked and wake up screen if needed
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked
        
        android.util.Log.d(TAG, "Device locked: $isLocked - Window configured for lock screen display")
        
        // Wake up the screen if device is locked
        if (isLocked) {
            wakeUpScreen()
        }
    }
    
    /**
     * Request keyguard dismissal - call after onCreate
     */
    private fun requestKeyguardDismissal() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                android.util.Log.d(TAG, "Keyguard dismissed successfully")
            }
            
            override fun onDismissError() {
                android.util.Log.e(TAG, "Keyguard dismiss error")
            }
            
            override fun onDismissCancelled() {
                android.util.Log.d(TAG, "Keyguard dismiss cancelled")
            }
        })
    }
    
    /**
     * Wake up the screen if it's off
     */
    private fun wakeUpScreen() {
        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            
            if (!powerManager.isInteractive) {
                android.util.Log.d(TAG, "Screen is off - waking up")
                
                // The activity's turnScreenOn/showWhenLocked window flags wake the display.
                // Keep only a short partial lock so the launch is not interrupted.
                val wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "Purevon:InCallWakeLock"
                )
                wakeLock.acquire(10000L) // 10 seconds max
                
                // Release after a short delay
                android.os.Handler(mainLooper).postDelayed({
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                        android.util.Log.d(TAG, "WakeLock released")
                    }
                }, 5000L)
            } else {
                android.util.Log.d(TAG, "Screen is already on")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error waking up screen", e)
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        android.util.Log.d(TAG, "onNewIntent called")
        // Re-configure window in case we're coming back
        configureWindowForCall()
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d(TAG, "onResume called")
        isInForeground = true
        
        // ✅ إخفاء الشريط العائم دائماً عند عودة الشاشة (hide بدلاً من stop لتجنب إعادة إنشاء الخدمة)
        FloatingCallService.hide(this)
        android.util.Log.d(TAG, "🔴 Floating overlay hidden")
        
        // ✅ إخطار الخدمة بأن شاشة المكالمة مفتوحة
        inCallServiceBridge.setInCallActivityVisible(true)
        
        // ✅ تفعيل حساس القرب — مع مراعاة حالة السماعة الخارجية:
        // إذا كانت السماعة مفعلة → الهاتف بعيد عن الأذن → لا داعي للمستشعر
        val speakerOn = inCallServiceBridge.isSpeakerOn()
        if (!speakerOn) {
            enableProximitySensor()
        } else {
            android.util.Log.d(TAG, "Speaker is ON — proximity sensor disabled")
        }
        
        // مراقبة تغييرات حالة السماعة طوال مدة المكالمة
        speakerStateJob?.cancel()
        speakerStateJob = lifecycleScope.launch {
            try {
                inCallServiceBridge.speakerState.collectLatest { isSpeaker ->
                    if (isSpeaker) {
                        // السماعة مفعّلة: أوقف المستشعر لتبقى الشاشة مضاءة
                        disableProximitySensor()
                        android.util.Log.d(TAG, "Speaker ON → proximity sensor disabled")
                    } else {
                        // السماعة مُغلقة: أعد تفعيل المستشعر
                        enableProximitySensor()
                        android.util.Log.d(TAG, "Speaker OFF → proximity sensor enabled")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in speaker state monitoring, disabling proximity sensor for safety", e)
                disableProximitySensor()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        android.util.Log.d(TAG, "onPause called")
        isInForeground = false
        
        // ✅ إخطار الخدمة بأن شاشة المكالمة لم تعد مفتوحة
        inCallServiceBridge.setInCallActivityVisible(false)
        
        // ✅ إيقاف مراقبة حالة السماعة
        speakerStateJob?.cancel()
        speakerStateJob = null
        
        // ✅ تعطيل حساس القرب عند الخروج من الشاشة
        disableProximitySensor()
        
        // ✅ إظهار الشريط العائم عند إخفاء النشاط — دائماً، بما في ذلك حالة isFinishing.
        // السبب: عندما يضغط المستخدم "رجوع" أثناء مكالمة نشطة، تصبح isFinishing=true
        // لكن المكالمة لا تزال تعمل، لذا يجب إظهار الشريط.
        // فحص callState الأدناه يكفي لمنع الشريط عند انتهاء المكالمة.
        val currentCall = inCallServiceBridge.getCurrentCall()
        val phoneNumber = inCallServiceBridge.currentPhoneNumber
        val contactName = inCallServiceBridge.currentContactName
        
        if (currentCall != null && phoneNumber != null) {
            val callState = currentCall.state
            val isDialingState = callState == android.telecom.Call.STATE_DIALING || 
                callState == android.telecom.Call.STATE_CONNECTING ||
                callState == android.telecom.Call.STATE_NEW
            if (callState != android.telecom.Call.STATE_RINGING && 
                callState != android.telecom.Call.STATE_DISCONNECTED &&
                callState != android.telecom.Call.STATE_DISCONNECTING) {
                // ✅ استخدام update() بدلاً من start() لسببين:
                // 1) يُرسل callStartTime فيعمل العداد بشكل صحيح
                // 2) يُظهر الشريط فوراً (بدون تأخير 500ms) مما يمنع احتمالية
                //    أن يقتل onDestroy الخدمة قبل ظهور الشريط.
                FloatingCallService.update(
                    context = this,
                    contactName = contactName,
                    phoneNumber = phoneNumber,
                    callStartTime = inCallServiceBridge.getCallStartTime(),
                    isMuted = inCallServiceBridge.isMuted(),
                    isSpeakerOn = inCallServiceBridge.isSpeakerOn(),
                    isRinging = false,
                    isDialing = isDialingState,
                    currentAudioRoute = inCallServiceBridge.getCurrentAudioRoute()
                )
                android.util.Log.d(TAG, "🟢 Floating overlay updated for: ${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(phoneNumber)} (state: $callState, finishing: $isFinishing)")
            } else {
                android.util.Log.d(TAG, "⚠️ Call state is $callState - not showing overlay")
            }
        }
    }
    
    /**
     * Called when user intentionally leaves the activity (e.g., pressing Home button)
     * Note: May not be called reliably with gesture navigation
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        android.util.Log.d(TAG, "onUserLeaveHint called - user is leaving intentionally")
    }
    
    override fun onStop() {
        super.onStop()
        android.util.Log.d(TAG, "onStop called, isFinishing: $isFinishing")
        isInForeground = false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "onDestroy called")
        isInForeground = false
        
        // ✅ إيقاف الشريط العائم فقط إذا انتهت المكالمة فعلاً.
            // إذا كانت المكالمة لا تزال نشطة (مثلاً: المستخدم ضغط رجوع),
            // نُبقي الشريط العائم ظاهراً حتى يتمكن المستخدم من العودة للمكالمة.
            val callIsActive = inCallServiceBridge.getCurrentCall()?.let { call ->
                call.state != android.telecom.Call.STATE_DISCONNECTED &&
                call.state != android.telecom.Call.STATE_DISCONNECTING
            } ?: false

            if (!callIsActive) {
                FloatingCallService.stop(this)
                android.util.Log.d(TAG, "🔴 Floating overlay stopped on destroy (call ended)")
            } else {
                android.util.Log.d(TAG, "ℹ️ onDestroy: call still active — keeping floating overlay visible")
            }
        
        // ✅ إلغاء تسجيل BroadcastReceiver
        try {
            unregisterReceiver(closeReceiver)
            android.util.Log.d(TAG, "Close receiver unregistered")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error unregistering receiver", e)
        }
        
        // ✅ التأكد من تعطيل حساس القرب
        disableProximitySensor()
    }
    
    /**
     * ✅ تفعيل حساس القرب (Proximity Sensor)
     * يُطفئ الشاشة عند تقريب الهاتف للوجه أثناء المكالمة
     * لمنع اللمسات العشوائية
     */
    @Suppress("DEPRECATION")
    private fun enableProximitySensor() {
        try {
            // التحقق من أن حساس القرب غير مفعل مسبقاً
            if (proximityWakeLock == null || proximityWakeLock?.isHeld == false) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                
                // التحقق من دعم الجهاز لحساس القرب
                if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    proximityWakeLock = powerManager.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        "Purevon:ProximityWakeLock"
                    )
                    // احتفاظ لمدة 4 ساعات كحد أقصى (يكفي لأطول المكالمات)
                    proximityWakeLock?.acquire(4 * 60 * 60 * 1000L)
                    android.util.Log.d(TAG, "Proximity sensor enabled")
                } else {
                    android.util.Log.w(TAG, "Device does not support PROXIMITY_SCREEN_OFF_WAKE_LOCK")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error enabling proximity sensor", e)
        }
    }
    
    /**
     * ✅ تعطيل حساس القرب
     */
    private fun disableProximitySensor() {
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
                android.util.Log.d(TAG, "Proximity sensor disabled")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error disabling proximity sensor", e)
        }
    }
    
}
