package com.rasmi.purevon.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.content.pm.ServiceInfo
import android.os.VibrationEffect
import android.os.Vibrator
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.screen.incall.InCallActivity
import com.rasmi.purevon.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * InCall Service for handling active calls.
 * Required for default dialer functionality.
 *
 * All mutable call state lives in [InCallServiceBridgeImpl] (Finding C).
 * This class only keeps constants and delegates state reads/writes to [bridge].
 */
@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.M)
class PurevonInCallService : InCallService() {

    companion object {
        private const val TAG = "PurevonInCallService"
        private const val CHANNEL_ID_INCOMING = "purevon_incoming_calls_v2"
        private const val CHANNEL_ID_ONGOING = "purevon_ongoing_calls_v4"
        private const val CHANNEL_ID_INCOMING_SILENT = "purevon_incoming_silent_v1"
        private const val NOTIFICATION_ID = 1

        fun getStateName(state: Int): String {
            return when (state) {
                Call.STATE_NEW -> "NEW"
                Call.STATE_RINGING -> "RINGING"
                Call.STATE_DIALING -> "DIALING"
                Call.STATE_ACTIVE -> "ACTIVE"
                Call.STATE_HOLDING -> "HOLDING"
                Call.STATE_DISCONNECTED -> "DISCONNECTED"
                Call.STATE_CONNECTING -> "CONNECTING"
                Call.STATE_DISCONNECTING -> "DISCONNECTING"
                Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
                else -> "UNKNOWN"
            }
        }
    }

    @Inject lateinit var bridge: InCallServiceBridgeImpl
    @Inject lateinit var settingsDataStore: com.rasmi.purevon.data.preferences.SettingsDataStore
    
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // ✅ CoroutineScope لتشغيل عمليات I/O (البحث عن جهة الاتصال) خارج الـ Main Thread
    private val serviceScope = CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    /**
     * ✅ Check if device is locked
     * Used to determine notification style (Full Screen vs Heads-Up)
     */
    private fun isDeviceLocked(): Boolean {
        return try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            val isScreenOn = powerManager.isInteractive
            val isLocked = keyguardManager.isKeyguardLocked
            
            Log.d(TAG, "====== DEVICE STATE CHECK ======")
            Log.d(TAG, "Screen ON: $isScreenOn")
            Log.d(TAG, "Keyguard LOCKED: $isLocked")
            Log.d(TAG, "Final Result: ${if (isLocked) "LOCKED" else "UNLOCKED"}")
            Log.d(TAG, "================================")
            
            // Device is locked if keyguard is showing, regardless of screen state
            isLocked
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device lock state", e)
            true // Default to locked for safety (will show full screen intent)
        }
    }
    
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
            
            // ✅ نشر حالة المكالمة عبر StateFlow
            bridge.setCallState(state)
            
            Log.d(TAG, "========================================")
            Log.d(TAG, "[CALL] STATE CHANGED")
            Log.d(TAG, "Number: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
            Log.d(TAG, "Old State -> New State: ${Companion.getStateName(call.details?.state ?: Call.STATE_NEW)} -> ${Companion.getStateName(state)}")
            Log.d(TAG, "========================================")
            
            val postLock = mutableListOf<() -> Unit>()
            when (state) {
                Call.STATE_RINGING -> {
                    Log.d(TAG, "[RINGING] STATE_RINGING - Incoming call detected")
                    synchronized(bridge.callStateLock) {
                        bridge.isIncomingCall = true
                        bridge.wasMissedCall = true
                        bridge.currentPhoneNumber = phoneNumber
                    }
                    if (bridge.currentContactName == null) {
                        postLock.add {
                            serviceScope.launch {
                                bridge.currentContactName = withContext(Dispatchers.IO) { lookupContactName(phoneNumber) }
                            }
                        }
                    }

                    val deviceLocked = isDeviceLocked()
                    Log.d(TAG, "[RINGING] Device locked: $deviceLocked")

                    postLock.add { updateNotification(call, useHighPriority = true) }
                    Log.d(TAG, "[RINGING] Notification with fullScreenIntent posted")

                    if (!deviceLocked) {
                        if (incomingCallBannerOnly) {
                            Log.d(TAG, "[RINGING] Device UNLOCKED + Banner mode ON - showing floating overlay")
                            postLock.add {
                                serviceScope.launch {
                                    if (bridge.currentContactName == null) {
                                        bridge.currentContactName = withContext(Dispatchers.IO) { lookupContactName(phoneNumber) }
                                    }
                                    FloatingCallService.start(this@PurevonInCallService, bridge.currentContactName, phoneNumber)
                                    FloatingCallService.update(
                                        context = this@PurevonInCallService,
                                        contactName = bridge.currentContactName,
                                        phoneNumber = phoneNumber,
                                        callStartTime = 0L,
                                        isMuted = false,
                                        isSpeakerOn = false,
                                        isRinging = true,
                                        isDialing = false
                                    )
                                }
                            }
                        } else {
                            Log.d(TAG, "[RINGING] Device is UNLOCKED - launching InCallActivity directly")
                            postLock.add { launchActivity() }
                        }
                    } else {
                        Log.d(TAG, "[RINGING] Device is LOCKED - relying on fullScreenIntent")
                    }
                }
                Call.STATE_DIALING -> {
                    Log.d(TAG, "[DIALING] STATE_DIALING - Outgoing call detected")
                    synchronized(bridge.callStateLock) {
                        bridge.isIncomingCall = false
                        bridge.wasMissedCall = false
                        bridge.currentPhoneNumber = phoneNumber
                    }
                    if (bridge.currentContactName == null) {
                        postLock.add {
                            serviceScope.launch {
                                bridge.currentContactName = withContext(Dispatchers.IO) { lookupContactName(phoneNumber) }
                            }
                        }
                    }
                    postLock.add { updateNotification(call) }
                    Log.d(TAG, "[DIALING] Launching full screen activity")
                    postLock.add { launchActivity() }
                }
                Call.STATE_SELECT_PHONE_ACCOUNT -> {
                    Log.d(TAG, "[SELECT_PHONE_ACCOUNT] STATE_SELECT_PHONE_ACCOUNT - Need to select SIM")
                    val extras = call.details?.intentExtras
                    val suggestedAccounts = extras?.getParcelableArrayList<android.telecom.PhoneAccountHandle>(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS)
                    
                    if (!suggestedAccounts.isNullOrEmpty()) {
                        Log.d(TAG, "Automatically selecting suggested phone account: ${suggestedAccounts[0]}")
                        call.phoneAccountSelected(suggestedAccounts[0], false)
                    } else {
                        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                        val accounts = telecomManager.callCapablePhoneAccounts
                        if (accounts.isNotEmpty()) {
                            Log.d(TAG, "Automatically selecting first phone account: ${accounts[0]}")
                            call.phoneAccountSelected(accounts[0], false)
                        }
                    }
                    postLock.add { launchActivity() }
                }
                Call.STATE_CONNECTING -> {
                    Log.d(TAG, "[CONNECTING] STATE_CONNECTING - Call connecting")
                    postLock.add { updateNotification(call) }
                    postLock.add { bridge.stateChangeListener?.invoke(call) }
                }
                Call.STATE_ACTIVE -> {
                    Log.d(TAG, "[ACTIVE] STATE_ACTIVE - Call is now active (isManagingCalls=${bridge.isManagingCalls})")
                    
                    var callToHold: Call? = null
                    
                    synchronized(bridge.callStateLock) {
                        bridge.wasCallActive = true
                        
                        if (!bridge.isManagingCalls) {
                            if (call == bridge.waitingCall) {
                                Log.d(TAG, "[ACTIVE] Waiting call became active - setting as current")
                                if (bridge.currentCall != null) {
                                    callToHold = bridge.currentCall
                                    bridge.heldCall = bridge.currentCall
                                    Log.d(TAG, "[HOLD] Previous current call moved to held")
                                }
                                bridge.currentCall = call
                                bridge.waitingCall = null
                            } else if (bridge.currentCall == null) {
                                bridge.currentCall = call
                                Log.d(TAG, "[ACTIVE] Set as current call (was null)")
                            } else if (call == bridge.currentCall) {
                                Log.d(TAG, "[ACTIVE] Outgoing added-call is now active - already set as currentCall")
                            } else if (bridge.activeCalls.contains(call) && call != bridge.currentCall) {
                                Log.d(TAG, "[ACTIVE] Untracked call became active - setting as current, moving old to held")
                                if (bridge.currentCall != null) bridge.heldCall = bridge.currentCall
                                bridge.currentCall = call
                            }
                        } else {
                            Log.d(TAG, "[ACTIVE] isManagingCalls=true - skipping reference management")
                        }
                        
                        if (bridge.callStartTime == 0L) {
                            bridge.callStartTime = android.os.SystemClock.elapsedRealtime()
                            Log.d(TAG, "[TIMER] Call start time recorded: ${bridge.callStartTime}")
                        }
                        bridge.wasMissedCall = false
                    }
                    
                    callToHold?.hold()
                    
                    postLock.add { updateNotification(call) }
                    Log.d(TAG, "[ACTIVE] Call answered - Ready for user interaction")
                    postLock.add { bridge.stateChangeListener?.invoke(call) }
                    
                    if (!bridge.isInCallActivityVisible) {
                        postLock.add {
                            FloatingCallService.update(
                                context = this@PurevonInCallService,
                                contactName = bridge.currentContactName,
                                phoneNumber = phoneNumber,
                                callStartTime = bridge.callStartTime,
                                isMuted = bridge.isMuted(),
                                isSpeakerOn = bridge.isSpeakerOn(),
                                isRinging = false,
                                isDialing = false,
                                currentAudioRoute = bridge.getCurrentAudioRoute()
                            )
                        }
                    }
                }
                Call.STATE_HOLDING -> {
                    Log.d(TAG, "[HOLDING] STATE_HOLDING - Call put on hold (isManagingCalls=${bridge.isManagingCalls})")
                    
                    synchronized(bridge.callStateLock) {
                        if (!bridge.isManagingCalls) {
                            if (call == bridge.currentCall) {
                                bridge.heldCall = bridge.currentCall
                                bridge.currentCall = null
                                Log.d(TAG, "[HOLD] Current call moved to held")
                            }
                        } else {
                            Log.d(TAG, "[HOLDING] isManagingCalls=true - skipping reference management")
                        }
                    }
                    
                    postLock.add { updateNotification(call) }
                    postLock.add { bridge.stateChangeListener?.invoke(bridge.currentCall ?: call) }
                }
                Call.STATE_DISCONNECTING -> {
                    Log.d(TAG, "[DISCONNECTING] STATE_DISCONNECTING - Call ending...")
                }
                Call.STATE_DISCONNECTED -> {
                    val disconnectCause = call.details?.disconnectCause
                    Log.d(TAG, "[DISCONNECTED] STATE_DISCONNECTED - Call ended")
                    Log.d(TAG, "Disconnect cause: ${disconnectCause?.label ?: "Unknown"}")
                    Log.d(TAG, "[DISCONNECTED] wasCallActive: ${bridge.wasCallActive}")
                    
                    var shouldCloseUi = false
                    var callToUnhold: Call? = null
                    var shouldSwitchToWaiting = false
                    var shouldSwitchToHeld = false
                    var shouldStopForeground = false
                    
                    synchronized(bridge.callStateLock) {
                        if (!bridge.wasCallActive && call == bridge.currentCall) {
                            shouldCloseUi = true
                            val causeCode = disconnectCause?.code ?: android.telecom.DisconnectCause.UNKNOWN
                            val isRejectedByCause = causeCode == android.telecom.DisconnectCause.REJECTED
                                    || causeCode == android.telecom.DisconnectCause.LOCAL
                            if (!bridge.wasCallRejectedByUser) {
                                bridge.wasCallRejectedByUser = isRejectedByCause
                            }
                            Log.d(TAG, "[DISCONNECTED] disconnectCause.code=$causeCode, isRejectedByCause=$isRejectedByCause, wasCallRejectedByUser=${bridge.wasCallRejectedByUser}")
                        }
                        
                        bridge.activeCalls.remove(call)
                        Log.d(TAG, "[REMOVE] Removed disconnected call from activeCalls, remaining: ${bridge.activeCalls.size}")
                        
                        if (call == bridge.currentCall) {
                            bridge.currentCall = null
                            bridge.callStartTime = 0L
                            Log.d(TAG, "[RESET] Current call cleared")
                            
                            if (bridge.waitingCall != null && bridge.wasCallActive) {
                                bridge.currentCall = bridge.waitingCall
                                bridge.waitingCall = null
                                shouldSwitchToWaiting = true
                                Log.d(TAG, "[SWITCH] Switched to waiting call as current call")
                            } else if (bridge.heldCall != null && bridge.wasCallActive) {
                                bridge.currentCall = bridge.heldCall
                                bridge.heldCall = null
                                shouldSwitchToHeld = true
                                callToUnhold = bridge.currentCall
                                Log.d(TAG, "[SWITCH] Switched to held call as current call")
                            } else if (bridge.activeCalls.isNotEmpty() && bridge.wasCallActive) {
                                bridge.currentCall = bridge.activeCalls.first()
                                Log.d(TAG, "[SWITCH] Switched to first active call")
                            }
                        }
                        
                        if (call == bridge.waitingCall) {
                            bridge.waitingCall = null
                            Log.d(TAG, "[CLEAR] Waiting call cleared")
                        }
                        
                        if (call == bridge.heldCall) {
                            bridge.heldCall = null
                            Log.d(TAG, "[CLEAR] Held call cleared")
                        }
                        
                        if (bridge.activeCalls.isEmpty()) {
                            shouldStopForeground = true
                            bridge.wasCallActive = false
                            bridge.isConference = false
                            bridge.conferenceParticipants = emptyList()
                        } else {
                            Log.d(TAG, "[ACTIVE] Still have ${bridge.activeCalls.size} active call(s)")
                        }
                    }
                    
                    val shouldCloseActivity = shouldCloseUi || bridge.activeCalls.isEmpty()
                    if (shouldCloseActivity) {
                        Log.d(TAG, "[DISCONNECTED] No active calls remaining - closing UI")
                        val closeIntent = Intent("com.rasmi.purevon.CLOSE_INCALL_ACTIVITY")
                        closeIntent.setPackage(packageName)
                        sendBroadcast(closeIntent)
                        FloatingCallService.stop(this@PurevonInCallService)
                    }
                    
                    callToUnhold?.unhold()
                    
                    if (shouldSwitchToWaiting) {
                        bridge.currentCall?.let { updateNotification(it) }
                        launchActivity()
                    }
                    
                    if (shouldStopForeground) {
                        Log.d(TAG, "[STOP] No more active calls - stopping foreground service")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                }
                else -> {
                    Log.d(TAG, "[UNKNOWN] Unknown state: ${Companion.getStateName(state)}")
                    postLock.add { updateNotification(call) }
                }
            }
            postLock.forEach { it() }
        }
    }
    
    // ✅ Cached preference: show banner only for incoming calls
    @Volatile
    private var incomingCallBannerOnly: Boolean = false
    
    override fun onCreate() {
        super.onCreate()
        bridge.registerService(this)
        createNotificationChannel()
        
        // ✅ Observe banner preference asynchronously without blocking Main thread
        serviceScope.launch {
            settingsDataStore.incomingCallBannerOnly.collect { enabled ->
                incomingCallBannerOnly = enabled
                Log.d(TAG, "Updated incomingCallBannerOnly=$incomingCallBannerOnly")
            }
        }
        Log.d(TAG, "InCallService created - observing incomingCallBannerOnly")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        bridge.resetAllState()
        bridge.unregisterService()
        Log.d(TAG, "InCallService destroyed - all state cleaned via bridge")
    }
    
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        // ✅ مكالمة جديدة: إعادة تفعيل الشريط العائم (إزالة حظر isPendingStop من المكالمة السابقة)
        FloatingCallService.isPendingStop = false

        // ✅ Sync audio route on call start
        @Suppress("DEPRECATION")
        callAudioState?.let { state ->
            bridge.setAudioRouteState(state.route)
            bridge.setSpeakerState(state.route == android.telecom.CallAudioState.ROUTE_SPEAKER)
            bridge.setMuteState(state.isMuted)
        }
        
        android.util.Log.d("PurevonInCallService", "========================================")
        android.util.Log.d("PurevonInCallService", "[CALL] CALL ADDED - onCallAdded() INVOKED")
        android.util.Log.d("PurevonInCallService", "========================================")
        
        val phoneNumber = call.details?.handle?.schemeSpecificPart
        val callState = call.details?.state ?: Call.STATE_NEW
        
        val isLikelyIncoming = callState == Call.STATE_RINGING ||
            (callState == Call.STATE_NEW && call.details?.callDirection != android.telecom.Call.Details.DIRECTION_OUTGOING)

        var triggerHaptic = false
        var notifyCall: Call? = null
        var activeCallsCount = 0

        synchronized(bridge.callStateLock) {
            bridge.activeCalls.add(call)
            activeCallsCount = bridge.activeCalls.size
            bridge.wasCallRejectedByUser = false
            if (activeCallsCount == 1) {
                bridge.wasCallActive = false
            }

            if (bridge.currentCall != null && (callState == Call.STATE_RINGING || isLikelyIncoming)) {
                Log.d(TAG, "[WAITING] Call Waiting: New incoming call while another call is active")
                bridge.waitingCall = call
                triggerHaptic = true
                notifyCall = bridge.currentCall
                Log.d(TAG, "[WAITING] Waiting call set: ${DebugLogger.maskPhoneNumber(phoneNumber ?: "")} - ViewModel notified")
            } else if (bridge.currentCall != null &&
                (callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING || callState == Call.STATE_SELECT_PHONE_ACCOUNT ||
                 (callState == Call.STATE_NEW && call.details?.callDirection == android.telecom.Call.Details.DIRECTION_OUTGOING))) {
                Log.d(TAG, "[ADD_CALL] New outgoing call added while another is active - tracking as new current")
                bridge.heldCall = bridge.heldCall ?: bridge.currentCall
                bridge.currentCall = call
                Log.d(TAG, "[ADD_CALL] heldCall=${DebugLogger.maskPhoneNumber(bridge.heldCall?.details?.handle?.schemeSpecificPart ?: "")}, currentCall=${DebugLogger.maskPhoneNumber(phoneNumber ?: "")}")
                notifyCall = call
            } else if (bridge.currentCall == null) {
                bridge.currentCall = call
                Log.d(TAG, "[SET] Set as current call")
            }
        }

        android.util.Log.d(TAG, "========================================")
        android.util.Log.d(TAG, "[CALL] CALL ADDED")
        android.util.Log.d(TAG, "Number: ${DebugLogger.maskPhoneNumber(phoneNumber ?: "")}")
        android.util.Log.d(TAG, "State: ${getStateName(callState)}")
        android.util.Log.d(TAG, "Is Incoming: ${callState == Call.STATE_RINGING}")
        android.util.Log.d(TAG, "Current active calls: $activeCallsCount")
        android.util.Log.d(TAG, "========================================")
        
        if (triggerHaptic) {
            triggerHapticFeedback()
        }
        notifyCall?.let { bridge.stateChangeListener?.invoke(it) }

        call.registerCallback(callback)
        
        val isIncoming = callState == Call.STATE_RINGING || isLikelyIncoming

        if (isIncoming) {
            Log.d(TAG, "[INCOMING] INCOMING CALL DETECTED (state=${getStateName(callState)}) - posting fullScreenIntent notification")
            synchronized(bridge.callStateLock) {
                bridge.wasMissedCall = true
                bridge.callStartTime = 0L
                bridge.currentPhoneNumber = phoneNumber
                bridge.isIncomingCall = true
            }
            serviceScope.launch {
                val resolvedName = withContext(Dispatchers.IO) { lookupContactName(phoneNumber) }
                synchronized(bridge.callStateLock) {
                    bridge.currentContactName = resolvedName
                }
            }

            updateNotification(call, useHighPriority = true)
            
            // ✅ FIX: Respect incomingCallBannerOnly preference
            val deviceLocked = isDeviceLocked()
            if (!deviceLocked && incomingCallBannerOnly) {
                Log.d(TAG, "[INCOMING] Banner mode ON + UNLOCKED - showing floating overlay instead of full screen")
                serviceScope.launch {
                    val resolvedName = withContext(Dispatchers.IO) { lookupContactName(phoneNumber) }
                    synchronized(bridge.callStateLock) {
                        bridge.currentContactName = resolvedName
                    }
                    FloatingCallService.start(this@PurevonInCallService, resolvedName, phoneNumber ?: "")
                    FloatingCallService.update(
                        context = this@PurevonInCallService,
                        contactName = resolvedName,
                        phoneNumber = phoneNumber ?: "",
                        callStartTime = 0L,
                        isMuted = false,
                        isSpeakerOn = false,
                        isRinging = true,
                        isDialing = false
                    )
                }
            } else {
                launchActivity()
            }
            Log.d(TAG, "[INCOMING] Notification posted, UI handled (bannerOnly=$incomingCallBannerOnly, locked=$deviceLocked)")
        } else if (callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING || callState == Call.STATE_SELECT_PHONE_ACCOUNT) {
            Log.d(TAG, "[OUTGOING] OUTGOING CALL DETECTED")
            synchronized(bridge.callStateLock) {
                bridge.isIncomingCall = false
                bridge.wasMissedCall = false
                bridge.currentPhoneNumber = phoneNumber
            }
            serviceScope.launch {
                val resolvedName = withContext(Dispatchers.IO) { lookupContactName(phoneNumber) }
                synchronized(bridge.callStateLock) {
                    bridge.currentContactName = resolvedName
                }
            }

            updateNotification(call)
            Log.d(TAG, "[LAUNCH] Outgoing call - Launching full screen activity")
            launchActivity()
        }
    }
    
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
        
        call.unregisterCallback(callback)
        
        var activeCallsIsEmpty = false
        synchronized(bridge.callStateLock) {
            bridge.activeCalls.remove(call)
            
            if (call == bridge.waitingCall) {
                bridge.waitingCall = null
                Log.d(TAG, "Waiting call removed")
            }
            
            if (call == bridge.currentCall) {
                bridge.currentCall = null
                Log.d(TAG, "[REMOVE] Current call cleared (switch handled in onStateChanged DISCONNECTED)")
            }
            
            if (call == bridge.heldCall) {
                bridge.heldCall = null
                Log.d(TAG, "[REMOVE] Held call cleared")
            }
            activeCallsIsEmpty = bridge.activeCalls.isEmpty()
            if (activeCallsIsEmpty) {
                bridge.wasCallActive = false
            }
        }
        
        if (activeCallsIsEmpty) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            FloatingCallService.stop(this)
            Log.d(TAG, "Floating overlay stopped - no active calls")
        } else {
            bridge.stateChangeListener?.invoke(call)
        }
    }
    
    /**
     * ✅ System callback when audio route actually changes (async confirmation).
     * Keeps UI in sync with the real hardware state.
     */
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState == null) return
        
        val route = audioState.route
        val isMuted = audioState.isMuted
        val isSpeaker = route == android.telecom.CallAudioState.ROUTE_SPEAKER
        
        bridge.setAudioRouteState(route)
        bridge.setSpeakerState(isSpeaker)
        bridge.setMuteState(isMuted)
        
        Log.d(TAG, "onCallAudioStateChanged: route=$route, muted=$isMuted, speaker=$isSpeaker")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for INCOMING calls - HIGH importance for lock screen full-screen intent
            // IMPORTANCE_HIGH is REQUIRED for fullScreenIntent to work on lock screen!
            val incomingChannel = NotificationChannel(
                CHANNEL_ID_INCOMING,
                getString(R.string.notification_channel_incoming_calls),
                NotificationManager.IMPORTANCE_HIGH // Required for lock screen display and fullScreenIntent
            ).apply {
                description = getString(R.string.notification_channel_incoming_calls_desc)
                setSound(null, null) // Let Telecom handle the ringtone
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true) // Bypass Do Not Disturb for calls
                enableVibration(false) // Telecom handles vibration
                enableLights(true) // Enable notification light
            }
            notificationManager.createNotificationChannel(incomingChannel)
            Log.d(TAG, "Created incoming calls notification channel with IMPORTANCE_HIGH")
            
            // Delete the old ongoing calls channel so its cached settings (such as HIGH importance) are cleared
            runCatching {
                notificationManager.deleteNotificationChannel("purevon_ongoing_calls_v3")
            }

            // Channel for ONGOING/OUTGOING calls - LOW priority to prevent intrusive heads-up popups
            val ongoingChannel = NotificationChannel(
                CHANNEL_ID_ONGOING,
                getString(R.string.service_ongoing_calls_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_status_bar_chip_desc)
                setSound(null, null)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(ongoingChannel)
            Log.d(TAG, "Created ongoing calls notification channel with IMPORTANCE_LOW to prevent heads-up banners")

            // Channel for incoming calls when device is UNLOCKED.
            // IMPORTANCE_MIN = no heads-up banner, no sound — service stays alive silently.
            val incomingSilentChannel = NotificationChannel(
                CHANNEL_ID_INCOMING_SILENT,
                getString(R.string.notification_channel_incoming_call_unlocked),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.notification_channel_incoming_call_unlocked_desc)
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
    private fun updateNotification(call: Call, useHighPriority: Boolean = false) {
        val callState = call.details?.state ?: Call.STATE_NEW
        val phoneNumber = call.details?.handle?.schemeSpecificPart ?: bridge.currentPhoneNumber ?: ""
        val displayName = bridge.currentContactName?.takeIf { it.isNotBlank() } ?: phoneNumber

        // PendingIntent that opens InCallActivity (used for tap and for fullScreenIntent)
        val activityIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isIncoming = callState == Call.STATE_RINGING || useHighPriority

        // ✅ KEY FIX: On UNLOCKED devices the activity is launched directly via launchActivity().
        // Posting a HIGH-importance / PRIORITY_MAX foreground notification causes Android to
        // show an additional heads-up notification on top of the already-visible InCallActivity.
        // Solution: when the device is unlocked, use the silent ONGOING channel so the
        // foreground service stays alive but NO heads-up banner appears.
        // When the device is LOCKED, keep HIGH importance + fullScreenIntent so the system
        // shows the call screen on the lock screen.
        val deviceLockedForIncoming = if (isIncoming) isDeviceLocked() else false

        if (!isIncoming) {
            // The in-call UI is already visible for outgoing/active calls. Keeping an
            // ongoing foreground notification here creates a redundant banner above it.
            runCatching {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.cancel(NOTIFICATION_ID)
            }.onFailure { e ->
                android.util.Log.e(TAG, "Failed to remove non-incoming call notification", e)
            }
            android.util.Log.d(TAG, "[NOTIF] Removed notification for non-incoming call state=${Companion.getStateName(callState)}")
            return
        }

        val channelId = when {
            deviceLockedForIncoming -> CHANNEL_ID_INCOMING         // Lock screen: need full-screen intent
            else -> CHANNEL_ID_INCOMING_SILENT                    // Unlocked: IMPORTANCE_MIN, no heads-up
        }

        val title = when (callState) {
            Call.STATE_RINGING    -> getString(R.string.service_incoming_call)
            Call.STATE_DIALING,
            Call.STATE_CONNECTING,
            Call.STATE_SELECT_PHONE_ACCOUNT -> getString(R.string.service_calling)
            Call.STATE_HOLDING    -> getString(R.string.incall_on_hold)
            else                  -> getString(R.string.service_ongoing_call)
        }

        val priority = if (isIncoming && deviceLockedForIncoming) NotificationCompat.PRIORITY_MAX
                       else NotificationCompat.PRIORITY_MIN

        val builder = NotificationCompat.Builder(this, channelId)
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

        // fullScreenIntent — only needed when device is LOCKED.
        // On unlocked devices the activity is already launched directly, so fullScreenIntent
        // would just produce a redundant heads-up notification banner.
        if (isIncoming && deviceLockedForIncoming) {
            val canUse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationManager.canUseFullScreenIntent().also { granted ->
                    android.util.Log.d(TAG, "canUseFullScreenIntent = $granted (API ${Build.VERSION.SDK_INT})")
                }
            } else {
                true
            }
            if (canUse) {
                builder.setFullScreenIntent(contentIntent, true)
                android.util.Log.d(TAG, "[NOTIF] fullScreenIntent attached (device is LOCKED)")
            } else {
                android.util.Log.w(TAG, "⚠️ fullScreenIntent not available — USE_FULL_SCREEN_INTENT not granted. Falling back to direct launch.")
            }
        } else if (isIncoming) {
            android.util.Log.d(TAG, "[NOTIF] Skipping fullScreenIntent — device is UNLOCKED, activity launched directly")
        }

        val notification = builder.build()

        // startForeground is kept only for incoming calls. It is needed for lock-screen
        // presentation, while outgoing/active calls intentionally remain notification-free.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            android.util.Log.d(TAG, "[NOTIF] startForeground posted on channel=$channelId (locked=$deviceLockedForIncoming)")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ startForeground failed: ${e.message}", e)
        }
    }

    
    /**
     * ✅ البحث عن اسم جهة الاتصال من رقم الهاتف
     * يستخدم ContactsContract.PhoneLookup للبحث السريع
     */
    private fun lookupContactName(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name for ${DebugLogger.maskPhoneNumber(phoneNumber)}", e)
            null
        }
    }

    private fun launchActivity() {
        try {
            android.util.Log.e(TAG, "🚀 Launching InCall Activity")
            
            // ✅ First: Wake up screen
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isInteractive) {
                android.util.Log.e(TAG, "Screen is OFF - Waking up screen first")
                val wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Purevon:InCallWakeLock"
                )
                wakeLock.acquire(5000L) // Auto-released after 5 seconds
            }
            
            val intent = Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                // Don't exclude from recents so user can return to it
            }
            
            startActivity(intent)
            android.util.Log.e(TAG, "✅ InCall Activity launched successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error launching InCall Activity: ${e.message}", e)
        }
    }
    
    /**
     * Toggle microphone mute using InCallService API
     * ✅ ينشر الحالة تلقائياً عبر StateFlow
     */
    @Suppress("DEPRECATION")
    fun toggleMute(): Boolean {
        return try {
            val currentMuted = callAudioState?.isMuted ?: false
            val newMuted = !currentMuted
            setMuted(newMuted)
            bridge.setMuteState(newMuted)
            Log.d(TAG, "Mute toggled: $currentMuted -> $newMuted")
            newMuted
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute", e)
            false
        }
    }
    
    /**
     * Toggle speaker using InCallService API
     * ✅ ينشر الحالة تلقائياً عبر StateFlow
     * ✅ FIX: عند إيقاف مكبر الصوت، يرجع للسماعة المتصلة (بلوتوث/سلكية) بدلاً من السماعة الأذنية دائماً
     */
    @Suppress("DEPRECATION")
    fun toggleSpeaker(): Boolean {
        return try {
            val currentRoute = callAudioState?.route ?: android.telecom.CallAudioState.ROUTE_EARPIECE
            val supported = callAudioState?.supportedRouteMask ?: android.telecom.CallAudioState.ROUTE_EARPIECE
            
            val newRoute = if (currentRoute == android.telecom.CallAudioState.ROUTE_SPEAKER) {
                // ✅ FIX: عند إيقاف المكبر، اختر أفضل مسار متاح بالترتيب:
                // 1. بلوتوث (إذا متصل)
                // 2. سماعة سلكية/USB (إذا متصلة)
                // 3. سماعة الأذن (الافتراضي)
                when {
                    supported and android.telecom.CallAudioState.ROUTE_BLUETOOTH != 0 ->
                        android.telecom.CallAudioState.ROUTE_BLUETOOTH
                    supported and android.telecom.CallAudioState.ROUTE_WIRED_HEADSET != 0 ->
                        android.telecom.CallAudioState.ROUTE_WIRED_HEADSET
                    else ->
                        android.telecom.CallAudioState.ROUTE_EARPIECE
                }
            } else {
                android.telecom.CallAudioState.ROUTE_SPEAKER
            }
            setAudioRoute(newRoute)
            val newSpeakerState = newRoute == android.telecom.CallAudioState.ROUTE_SPEAKER
            bridge.setSpeakerState(newSpeakerState)
            Log.d(TAG, "Audio route changed: $currentRoute -> $newRoute (supported: $supported)")
            newSpeakerState
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speaker", e)
            false
        }
    }
    
    /**
     * Get current mute state
     */
    @Suppress("DEPRECATION")
    fun isMuted(): Boolean {
        return callAudioState?.isMuted ?: false
    }
    
    /**
     * Get current speaker state
     */
    @Suppress("DEPRECATION")
    fun isSpeakerOn(): Boolean {
        return callAudioState?.route == android.telecom.CallAudioState.ROUTE_SPEAKER
    }
    
    /**
     * التبديل بين المكالمة النشطة والمحتجزة (Swap calls)
     */
    @Suppress("DEPRECATION")
    fun swapCalls() {
        synchronized(bridge.callStateLock) {
        try {
            val callToActivate = bridge.heldCall
            val callToHold = bridge.currentCall
            
            if (callToActivate != null && callToHold != null) {
                Log.d(TAG, "[SWAP] Starting swap: holding current, activating held")
                bridge.isManagingCalls = true
                callToHold.hold()
                callToActivate.unhold()
                bridge.currentCall = callToActivate
                bridge.heldCall = callToHold
                bridge.isManagingCalls = false
                Log.d(TAG, "[SWAP] Calls swapped successfully")
                bridge.currentCall?.let { bridge.stateChangeListener?.invoke(it) }
            } else {
                Log.w(TAG, "[SWAP] Cannot swap: currentCall=${callToHold != null}, heldCall=${callToActivate != null}")
            }
        } catch (e: Exception) {
            bridge.isManagingCalls = false
            Log.e(TAG, "Error swapping calls", e)
        }
        } // synchronized
    }
    
    /**
     * احتجاز المكالمة الحالية
     */
    @Suppress("DEPRECATION")
    fun holdCurrentCall(): Boolean {
        synchronized(bridge.callStateLock) {
        return try {
            val callToHold = bridge.currentCall ?: return false
            bridge.isManagingCalls = true
            callToHold.hold()
            bridge.heldCall = callToHold
            bridge.currentCall = null
            bridge.isManagingCalls = false
            Log.d(TAG, "[HOLD] Current call put on hold")
            bridge.stateChangeListener?.invoke(callToHold)
            true
        } catch (e: Exception) {
            bridge.isManagingCalls = false
            Log.e(TAG, "Error holding call", e)
            false
        }
        } // synchronized
    }
    
    /**
     * إلغاء احتجاز المكالمة المحتجزة
     */
    @Suppress("DEPRECATION")
    fun unholdCall(): Boolean {
        synchronized(bridge.callStateLock) {
        return try {
            val callToResume = bridge.heldCall ?: return false
            bridge.isManagingCalls = true
            callToResume.unhold()
            bridge.currentCall = callToResume
            bridge.heldCall = null
            bridge.isManagingCalls = false
            Log.d(TAG, "[UNHOLD] Held call resumed")
            bridge.currentCall?.let { bridge.stateChangeListener?.invoke(it) }
            true
        } catch (e: Exception) {
            bridge.isManagingCalls = false
            Log.e(TAG, "Error unholding call", e)
            false
        }
        } // synchronized
    }
    
    /**
     * الرد على المكالمة المنتظرة مع احتجاز المكالمة الحالية
     */
    @Suppress("DEPRECATION")
    fun answerAndHold(): Boolean {
        synchronized(bridge.callStateLock) {
        try {
            val callToAnswer = bridge.waitingCall
            val callToHold = bridge.currentCall
            
            if (callToAnswer != null && callToHold != null) {
                Log.d(TAG, "[ANSWER_HOLD] Answering waiting call and holding current")
                bridge.isManagingCalls = true
                callToHold.hold()
                callToAnswer.answer(0)
                bridge.heldCall = callToHold
                bridge.currentCall = callToAnswer
                bridge.waitingCall = null
                bridge.isManagingCalls = false
                Log.d(TAG, "[ANSWER_HOLD] Successfully answered and held")
                bridge.currentCall?.let { bridge.stateChangeListener?.invoke(it) }
                return true
            } else if (callToAnswer != null) {
                bridge.isManagingCalls = true
                callToAnswer.answer(0)
                bridge.currentCall = callToAnswer
                bridge.waitingCall = null
                bridge.isManagingCalls = false
                Log.d(TAG, "[ANSWER_HOLD] Answered waiting call (no current call)")
                bridge.currentCall?.let { bridge.stateChangeListener?.invoke(it) }
                return true
            }
            Log.w(TAG, "[ANSWER_HOLD] No waiting call to answer")
            return false
        } catch (e: Exception) {
            bridge.isManagingCalls = false
            Log.e(TAG, "Error answering and holding call", e)
            return false
        }
        } // synchronized
    }
    
    /**
     * رفض المكالمة المنتظرة
     */
    @Suppress("DEPRECATION")
    fun rejectWaitingCall(): Boolean {
        try {
            if (bridge.waitingCall != null) {
                Log.d(TAG, "Rejecting waiting call")
                bridge.waitingCall?.disconnect()
                bridge.waitingCall = null
                return true
            }
            Log.w(TAG, "No waiting call to reject")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting waiting call", e)
            return false
        }
    }
    
    /**
     * ✅ Haptic feedback عند وصول مكالمة منتظرة
     */
    private fun triggerHapticFeedback() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(300)
            }
            Log.d(TAG, "📳 Haptic feedback triggered for waiting call")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error triggering haptic feedback", e)
        }
    }
    
    
    @Suppress("DEPRECATION")
    fun mergeCalls(): Boolean {
        synchronized(bridge.callStateLock) {
        try {
            Log.d(TAG, "[MERGE] Attempting to merge calls")
            
            var merged = false
            
            val mergedHeldNumber = bridge.heldCall?.details?.handle?.schemeSpecificPart
            
            val conferenceableCalls = bridge.currentCall?.conferenceableCalls
            if (!conferenceableCalls.isNullOrEmpty()) {
                Log.d(TAG, "[MERGE] Using conferenceableCalls: ${conferenceableCalls.size} available")
                bridge.currentCall?.conference(conferenceableCalls.first())
                Log.d(TAG, "[MERGE] Calls merged successfully via conferenceableCalls")
                bridge.heldCall = null
                merged = true
            }
            
            if (!merged && bridge.currentCall?.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true) {
                Log.d(TAG, "[MERGE] Using mergeConference capability")
                bridge.currentCall?.mergeConference()
                Log.d(TAG, "[MERGE] Calls merged successfully via mergeConference")
                bridge.heldCall = null
                merged = true
            }
            
            if (!merged && bridge.currentCall != null && bridge.heldCall != null) {
                Log.d(TAG, "[MERGE] Using manual conference with heldCall")
                bridge.currentCall?.conference(bridge.heldCall)
                Log.d(TAG, "[MERGE] Calls merged successfully via manual conference")
                bridge.heldCall = null
                merged = true
            }
            
            if (merged) {
                bridge.isConference = true
                
                val participants = mutableSetOf<String>()
                bridge.currentPhoneNumber?.let { participants.add(it) }
                mergedHeldNumber?.let { participants.add(it) }
                
                try {
                    bridge.currentCall?.children?.forEach { child ->
                        child.details?.handle?.schemeSpecificPart?.let { participants.add(it) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[MERGE] Error reading conference children", e)
                }
                
                bridge.activeCalls.forEach { call ->
                    call.details?.handle?.schemeSpecificPart?.let { participants.add(it) }
                }
                
                bridge.conferenceParticipants = participants.toList()
                Log.d(TAG, "[MERGE] Conference established with ${bridge.conferenceParticipants.size} participants: ${bridge.conferenceParticipants}")
                bridge.currentCall?.let { bridge.stateChangeListener?.invoke(it) }
                return true
            }
            
            Log.w(TAG, "[MERGE] Cannot merge: no conferenceableCalls, no merge capability, and no held call")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error merging calls", e)
            return false
        }
        } // synchronized
    }
    
    // Floating Call Bar removed

    /**
     * ✅ تحديث رؤية الإشعار بناءً على حالة شاشة المكالمة
     * Called from InCallServiceBridgeImpl when visibility changes.
     */
    internal fun updateCurrentNotificationVisibility() {
        val call = bridge.currentCall ?: return
        updateNotification(call)
    }

}
