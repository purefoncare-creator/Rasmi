package com.rasmi.purevon.presentation.screen.incall

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.ConfigurationCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.presentation.theme.PurevonTheme
import com.rasmi.purevon.receiver.CallActionReceiver
import com.rasmi.purevon.domain.call.InCallServiceBridge
import com.rasmi.purevon.R
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
    private var callStateJob: Job? = null
    
    // Job لمراقبة حالة كتم الصوت (لتحديث أزرار PiP)
    private var muteStateJob: Job? = null
    
    // ✅ حالة Picture-in-Picture — عند التفعيل نعرض محتوى مبسّط (اسم + تايمر) بدل شاشة المكالمة الكاملة
    private var pipMode by mutableStateOf(false)

    // ✅ يبقى true بعد دخول PiP — نستخدمه في onDestroy لنتأكد أن الإغلاق جاء من
    // إغلاق نافذة PiP (وليس إغلاقاً عادياً) فنعيد فتح شاشة المكالمة الواردة.
    private var enteredPipMode = false
    
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
                // ✅ أدخل وضع PiP إذا كانت المكالمة نشطة، وإلا تحرك بالخلفية
                if (!enterPipIfPossible()) {
                    moveTaskToBack(true)
                }
            }
        })
        
        enableEdgeToEdge()

        setContent {
            // ✅ FIX M40: الهوية موحدة داكنة — لا جمع لتفضيلات ثيم ميتة
            val appLanguage by settingsDataStore.appLanguage.collectAsStateWithLifecycle(initialValue = "system")
            
            // RTL layout direction is determined dynamically by the active language preference or system default
            val systemLocale = androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current).get(0)
            val activeLanguage = if (appLanguage == "system") (systemLocale?.language ?: "en") else appLanguage
            val isRtl = activeLanguage == "ar" || activeLanguage == "fa" || activeLanguage == "ur" || activeLanguage == "he"
            
            // Determine dark theme based on settings
            val useDarkTheme = false
            
            PurevonTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl
                        else androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (pipMode) {
                            // ✅ محتوى مبسّط لنافذة Picture-in-Picture (اسم + تايمر)
                            PictureInPictureCallContent()
                        } else {
                            InCallScreen()
                        }
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
                    if (isInPictureInPictureMode) {
                        updatePipActions()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in speaker state monitoring, disabling proximity sensor for safety", e)
                disableProximitySensor()
            }
        }
        
        // مراقبة تغييرات حالة كتم الصوت لتحديث أزرار PiP
        muteStateJob?.cancel()
        muteStateJob = lifecycleScope.launch {
            try {
                inCallServiceBridge.muteState.collectLatest { isMuted ->
                    if (isInPictureInPictureMode) {
                        updatePipActions()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in mute state monitoring", e)
            }
        }

        // ✅ مراقبة تغييرات حالة المكالمة لتحديث أزرار PiP
        callStateJob?.cancel()
        callStateJob = lifecycleScope.launch {
            try {
                inCallServiceBridge.callState.collectLatest {
                    if (isInPictureInPictureMode) {
                        updatePipActions()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in call state monitoring for PiP", e)
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

        // ✅ إيقاف مراقبة حالة كتم الصوت
        muteStateJob?.cancel()
        muteStateJob = null

        // ✅ إيقاف مراقبة حالة المكالمة
        callStateJob?.cancel()
        callStateJob = null

        // ✅ تعطيل حساس القرب عند الخروج من الشاشة
        disableProximitySensor()
    }
    
    /**
     * Called when user intentionally leaves the activity (e.g., pressing Home button)
     * Note: May not be called reliably with gesture navigation
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        android.util.Log.d(TAG, "onUserLeaveHint called - user is leaving intentionally")
        // ✅ تحويل شاشة المكالمة إلى وضع Picture-in-Picture تلقائياً عند الخروج
        enterPipIfPossible()
    }

    /**
     * ✅ يدخل وضع Picture-in-Picture (PiP) إذا كانت المكالمة نشطة وكان الجهاز يدعم PiP.
     * بديل نظيف وأكثر استقراراً عن النافذة العائمة (SYSTEM_ALERT_WINDOW).
     * @return true إذا تم الدخول إلى PiP بنجاح (أو كان النشاط في PiP بالفعل)
     */
    private fun enterPipIfPossible(): Boolean {
        // لا ندخل PiP إذا كنا في وضع PiP بالفعل أو إذا كانت الشاشة في الخلفية بالكامل
        if (isInPictureInPictureMode) return true

        val deviceSupportsPip = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (!deviceSupportsPip) {
            android.util.Log.w(TAG, "Device does not support Picture-in-Picture")
            return false
        }

        val call = inCallServiceBridge.getCurrentCall()
        // ✅ Fix: اسمح بدخول PiP أثناء RINGING أيضاً
        val pipEligible = call?.let {
            val s = it.state
            s == android.telecom.Call.STATE_RINGING ||
                s == android.telecom.Call.STATE_DIALING ||
                s == android.telecom.Call.STATE_CONNECTING ||
                s == android.telecom.Call.STATE_ACTIVE ||
                s == android.telecom.Call.STATE_HOLDING
        } ?: false

        if (!pipEligible) {
            android.util.Log.d(TAG, "Not entering PiP - no active call")
            return false
        }

        return try {
            val params = buildPipParams()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(params)
                android.util.Log.d(TAG, "✅ Entered Picture-in-Picture mode")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error entering PiP", e)
            false
        }
    }

    /**
     * ✅ بناء PictureInPictureParams مع أزرار: كتم الصوت، مكبر الصوت، إنهاء.
     */
    private fun buildPipParams(): PictureInPictureParams {
        val paramsBuilder = PictureInPictureParams.Builder()
        paramsBuilder.setAspectRatio(Rational(9, 16))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val call = inCallServiceBridge.getCurrentCall()
            val isRinging = call?.state == android.telecom.Call.STATE_RINGING

            val actions = if (isRinging) {
                // ✅ أثناء الرنين: أزرار الرد / الرفض
                val answerAction = actionRecipient(
                    CallActionReceiver.ACTION_ANSWER,
                    R.drawable.ic_call_answer,
                    getString(R.string.action_accept),
                    2004
                )
                val declineAction = actionRecipient(
                    CallActionReceiver.ACTION_DECLINE,
                    R.drawable.ic_call_decline,
                    getString(R.string.action_decline),
                    2005
                )
                listOfNotNull(answerAction, declineAction)
            } else {
                // ✅ أثناء المكالمة النشطة/مؤقتة: كتم / سماعة / إنهاء
                val muteAction = actionRecipient(
                    CallActionReceiver.ACTION_TOGGLE_MUTE,
                    R.drawable.ic_mute,
                    getString(R.string.incall_mute),
                    2001
                )
                val speakerAction = actionRecipient(
                    CallActionReceiver.ACTION_TOGGLE_SPEAKER,
                    R.drawable.ic_speaker,
                    getString(R.string.incall_speaker),
                    2002
                )
                val endAction = actionRecipient(
                    CallActionReceiver.ACTION_END_CALL,
                    R.drawable.ic_call_end,
                    getString(R.string.incall_end_call),
                    2003
                )
                listOfNotNull(muteAction, speakerAction, endAction)
            }

            if (actions.isNotEmpty()) {
                paramsBuilder.setActions(actions)
            }
        }
        return paramsBuilder.build()
    }

    /**
     * ✅ إنشاء RemoteAction يوجه إجراءً إلى CallActionReceiver.
     */
    private fun actionRecipient(action: String, iconRes: Int, title: String, requestCode: Int): RemoteAction? {
        return try {
            val intent = Intent(this, com.rasmi.purevon.receiver.CallActionReceiver::class.java).apply {
                this.action = action
                setPackage(packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val icon = Icon.createWithResource(this, iconRes)
            RemoteAction(icon, title, title, pendingIntent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error creating PiP action $action", e)
            null
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        android.util.Log.d(TAG, "PiP mode changed to: $isInPictureInPictureMode")

        // ✅ تحديث المحتوى المعروض: في PiP نعرض الاسم + التايمر فقط
        pipMode = isInPictureInPictureMode

        if (isInPictureInPictureMode) {
            enteredPipMode = true
            // ✅ في وضع PiP نقوم بتحديث الأزرار حسب حالة كتم الصوت/السماعة الحالية
            updatePipActions()
            // إيقاف حساس القرب في وضع PiP (لا معنى له في النافذة الصغيرة)
            disableProximitySensor()
        }
    }

    /**
     * ✅ تحديث أزرار PiP بعد تغيّر حالة كتم الصوت أو السماعة (يعكس الحالة الفعلية للأيقونات).
     */
    private fun updatePipActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            runCatching {
                setPictureInPictureParams(buildPipParams())
            }.onFailure { e ->
                android.util.Log.e(TAG, "Error updating PiP actions", e)
            }
        }
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
        
        // ✅ إلغاء تسجيل BroadcastReceiver
        try {
            unregisterReceiver(closeReceiver)
            android.util.Log.d(TAG, "Close receiver unregistered")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error unregistering receiver", e)
        }
        
        // ✅ التأكد من تعطيل حساس القرب
        disableProximitySensor()

        // ✅ Fix: عند إغلاق المستخدم نافذة PiP بينما المكالمة ما زالت واردة
        // (رنين / اتصال / جاهزة للرد)، لا يوجد إشعار احتياطي للعودة إليها،
        // لذلك نعيد فتح شاشة المكالمة ليتسنى الرد أو الرفض بأزرار واضحة.
        if (enteredPipMode && shouldReturnToCallScreen()) {
            android.util.Log.d(TAG, "PiP closed while call still ringing - reopening incoming call screen")
            relaunchCallScreen()
        }
    }

    /**
     * ✅ هل المكالمة ما زالت بحاجة إلى شاشة (لم تُرد أو تُرفض بعد)؟
     */
    private fun shouldReturnToCallScreen(): Boolean {
        return try {
            val call = inCallServiceBridge.getCurrentCall() ?: return false
            val s = call.state
            s == android.telecom.Call.STATE_RINGING ||
                s == android.telecom.Call.STATE_DIALING ||
                s == android.telecom.Call.STATE_CONNECTING
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking call state for relaunch", e)
            false
        }
    }

    /**
     * ✅ إعادة فتح شاشة المكالمة الواردة بأزرار الرد/الرفض.
     */
    private fun relaunchCallScreen() {
        android.os.Handler(mainLooper).postDelayed({
            try {
                val intent = Intent(applicationContext, InCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                applicationContext.startActivity(intent)
                android.util.Log.d(TAG, "Incoming call screen reopened after PiP dismissal")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error reopening incoming call screen", e)
            }
        }, 300)
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
