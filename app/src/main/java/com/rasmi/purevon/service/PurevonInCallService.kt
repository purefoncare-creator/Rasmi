package com.rasmi.purevon.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.rasmi.purevon.util.AppStateHelper
import com.rasmi.purevon.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * InCall Service for handling active calls.
 * Required for default dialer functionality.
 *
 * All mutable call state lives in [InCallServiceBridgeImpl].
 * Constants and call actions are extracted to [InCallServiceConstants],
 * [InCallNotificationManager], [InCallCallActions], and [InCallServiceHelpers].
 */
@AndroidEntryPoint
class PurevonInCallService : InCallService() {

    private companion object {
        private const val TAG = "PurevonInCallService"
    }

    @Inject internal lateinit var bridge: InCallServiceBridgeImpl
    @Inject lateinit var settingsDataStore: com.rasmi.purevon.data.preferences.SettingsDataStore

    internal lateinit var notifManager: InCallNotificationManager
        private set

    internal val serviceScope = CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    @Volatile
    private var ongoingNotifPosted: Boolean = false

    /**
     * ✅ FIX M30: calls whose framework callback is currently registered. Telecom can
     * destroy this service while Call objects are still alive (rebind cycles); without
     * cleanup those Calls would retain [callback] → this service after onDestroy.
     */
    private val trackedCallbackCalls = java.util.concurrent.CopyOnWriteArrayList<Call>()

    private val callback = object : Call.Callback() {
        @SuppressLint("MissingPermission")
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
            val currentCallState = getCallStateCompat(call)

            bridge.setCallState(state)

            Log.d(TAG, "========================================")
            Log.d(TAG, "[CALL] STATE CHANGED")
            Log.d(TAG, "Number: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
            Log.d(TAG, "Old State -> New State: ${InCallServiceConstants.getStateName(currentCallState)} -> ${InCallServiceConstants.getStateName(state)}")
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
                        val appInForeground = AppStateHelper.isAppInForeground()
                        val canShowOverlay = IncomingCallOverlayService.canShow(this@PurevonInCallService)
                        Log.d(TAG, "[RINGING] Device UNLOCKED - appInForeground=$appInForeground canShowOverlay=$canShowOverlay")

                        if (appInForeground && canShowOverlay) {
                            postLock.add {
                                serviceScope.launch {
                                    val (name, photoUri) = withContext(Dispatchers.IO) {
                                        val n = bridge.currentContactName ?: lookupContactName(phoneNumber)
                                        val p = lookupContactPhotoUri(phoneNumber)
                                        n to p
                                    }
                                    IncomingCallOverlayService.show(
                                        context = this@PurevonInCallService,
                                        phoneNumber = phoneNumber,
                                        contactName = name,
                                        contactPhotoUri = photoUri
                                    )
                                }
                            }
                            Log.d(TAG, "[RINGING] App in foreground - showing top overlay card instead of full-screen")
                        } else {
                            Log.d(TAG, "[RINGING] Device is UNLOCKED - launching InCallActivity directly")
                            postLock.add { launchInCallActivity() }
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
                    postLock.add { launchInCallActivity() }
                }
                Call.STATE_SELECT_PHONE_ACCOUNT -> {
                    Log.d(TAG, "[SELECT_PHONE_ACCOUNT] STATE_SELECT_PHONE_ACCOUNT - Need to select SIM")
                    val extras = call.details?.intentExtras
                    val suggestedAccounts = extras?.getParcelableArrayList<android.telecom.PhoneAccountHandle>(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS)

                    if (!suggestedAccounts.isNullOrEmpty()) {
                        Log.d(TAG, "Automatically selecting suggested phone account: ${suggestedAccounts[0]}")
                        call.phoneAccountSelected(suggestedAccounts[0], false)
                    } else {
                        val telecomManager = getSystemService(android.content.Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                        val accounts = telecomManager.callCapablePhoneAccounts
                        if (accounts.isNotEmpty()) {
                            Log.d(TAG, "Automatically selecting first phone account: ${accounts[0]}")
                            call.phoneAccountSelected(accounts[0], false)
                        }
                    }
                    postLock.add { launchInCallActivity() }
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
                    postLock.add { updateCurrentNotificationVisibility() }
                    postLock.add { bridge.stateChangeListener?.invoke(call) }
                    postLock.add { IncomingCallOverlayService.hide(this@PurevonInCallService) }
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
                    postLock.add { updateCurrentNotificationVisibility() }
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
                    }

                    IncomingCallOverlayService.hide(this@PurevonInCallService)
                    Log.d(TAG, "[DISCONNECTED] Incoming overlay hidden")

                    callToUnhold?.unhold()

                    if (shouldSwitchToWaiting) {
                        bridge.currentCall?.let { updateNotification(it) }
                        launchInCallActivity()
                    }

                    if (shouldStopForeground) {
                        Log.d(TAG, "[STOP] No more active calls - stopping foreground service")
                        ongoingNotifPosted = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                }
                else -> {
                    Log.d(TAG, "[UNKNOWN] Unknown state: ${InCallServiceConstants.getStateName(state)}")
                    postLock.add { updateNotification(call) }
                }
            }
            postLock.forEach { it() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifManager = InCallNotificationManager(this, bridge)
        bridge.registerService(this)
        notifManager.createNotificationChannels()

        Log.d(TAG, "InCallService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        // ✅ FIX M30: defensively detach from any framework Call objects that are
        // still holding our callback — Telecom may destroy the service mid-call.
        trackedCallbackCalls.forEach { call ->
            try {
                call.unregisterCallback(callback)
            } catch (_: Exception) {
                // Call already released by Telecom — nothing to do
            }
        }
        trackedCallbackCalls.clear()
        bridge.resetAllState()
        bridge.unregisterService()
        Log.d(TAG, "InCallService destroyed - all state cleaned via bridge")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        @Suppress("DEPRECATION")
        callAudioState?.let { state ->
            bridge.setAudioRouteState(state.route)
            bridge.setSpeakerState(state.route == android.telecom.CallAudioState.ROUTE_SPEAKER)
            bridge.setMuteState(state.isMuted)
        }

        android.util.Log.d(TAG, "========================================")
        android.util.Log.d(TAG, "[CALL] CALL ADDED - onCallAdded() INVOKED")
        android.util.Log.d(TAG, "========================================")

        val phoneNumber = call.details?.handle?.schemeSpecificPart
        val callState = getCallStateCompat(call)

        val isLikelyIncoming = callState == Call.STATE_RINGING ||
            (callState == Call.STATE_NEW && !isOutgoingCallCompat(call))

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
                 (callState == Call.STATE_NEW && isOutgoingCallCompat(call)))) {
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
        android.util.Log.d(TAG, "State: ${InCallServiceConstants.getStateName(callState)}")
        android.util.Log.d(TAG, "Is Incoming: ${callState == Call.STATE_RINGING}")
        android.util.Log.d(TAG, "Current active calls: $activeCallsCount")
        android.util.Log.d(TAG, "========================================")

        if (triggerHaptic) {
            triggerHapticFeedback()
        }
        notifyCall?.let { bridge.stateChangeListener?.invoke(it) }

        if (trackedCallbackCalls.addIfAbsent(call)) {
            call.registerCallback(callback)
        }

        val isIncoming = callState == Call.STATE_RINGING || isLikelyIncoming

        if (isIncoming) {
            Log.d(TAG, "[INCOMING] INCOMING CALL DETECTED (state=${InCallServiceConstants.getStateName(callState)}) - posting fullScreenIntent notification")
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

            val deviceLocked = isDeviceLocked()
            Log.d(TAG, "[INCOMING] Notification posted, UI handled (locked=$deviceLocked)")

            if (!deviceLocked && AppStateHelper.isAppInForeground() && IncomingCallOverlayService.canShow(this)) {
                val resolvedPhone = phoneNumber
                val resolvedName = bridge.currentContactName ?: lookupContactName(phoneNumber)
                val photoUri = lookupContactPhotoUri(phoneNumber)
                IncomingCallOverlayService.show(
                    context = this,
                    phoneNumber = resolvedPhone,
                    contactName = resolvedName,
                    contactPhotoUri = photoUri
                )
                Log.d(TAG, "[INCOMING] App in foreground - showing top overlay card")
            } else {
                launchInCallActivity()
            }
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
            launchInCallActivity()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")

        if (trackedCallbackCalls.remove(call)) {
            call.unregisterCallback(callback)
        }

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
            ongoingNotifPosted = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "No active calls - foreground stopped")
            IncomingCallOverlayService.hide(this)
        } else {
            bridge.stateChangeListener?.invoke(call)
        }
    }

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

    private fun getCallStateCompat(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details?.state ?: Call.STATE_NEW
        } else {
            @Suppress("DEPRECATION")
            call.state
        }
    }

    private fun isOutgoingCallCompat(call: Call): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return call.details?.callDirection == Call.Details.DIRECTION_OUTGOING
        }
        return when (call.state) {
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT -> true
            else -> false
        }
    }

    private fun updateNotification(call: Call, useHighPriority: Boolean = false) {
        notifManager.buildAndPostNotification(
            call = call,
            useHighPriority = useHighPriority,
            startForeground = { id, notification ->
                @Suppress("DEPRECATION", "ObsoleteSdkInt")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                } else {
                    startForeground(id, notification)
                }
            },
            stopForeground = { stopForeground(Service.STOP_FOREGROUND_REMOVE) }
        )
    }

    internal fun updateCurrentNotificationVisibility() {
        val call = bridge.currentCall
        if (call == null) {
            Log.w(TAG, "[ONGOING-DEBUG] updateCurrentNotificationVisibility: currentCall is NULL")
            return
        }
        // نُبقي إشعار المكالمة الجارية (مع زر إنهاء المكالمة) ظاهراً طوال المكالمة النشطة
        // لضمان إمكانية العودة للمكالمة من لوحة الإشعارات حتى لو أُغلقت نافذة PiP.
        val state = getCallStateCompat(call)
        val isOngoing = state == Call.STATE_ACTIVE ||
            state == Call.STATE_DIALING ||
            state == Call.STATE_CONNECTING ||
            state == Call.STATE_HOLDING
        Log.w(TAG, "[ONGOING-DEBUG] state=$state isOngoing=$isOngoing ongoingNotifPosted=$ongoingNotifPosted")
        if (isOngoing) {
            postOngoingCallNotification()
        } else {
            cancelOngoingCallNotification()
        }
    }

    private fun postOngoingCallNotification() {
        // تم إخفاء إشعار المكالمة الجارية تماماً حسب طلب المستخدم.
        // العودة للمكالمة تتم عبر بطاقة المكالمة النشطة داخل التطبيق.
        Log.d(TAG, "[ONGOING-DEBUG] postOngoingCallNotification suppressed (all call notifications hidden)")
    }

    private fun cancelOngoingCallNotification() {
        Log.w(TAG, "[ONGOING-DEBUG] cancelOngoingCallNotification: ongoingNotifPosted=$ongoingNotifPosted")
        if (!ongoingNotifPosted) return
        notifManager.cancelOngoingNotification(
            cancelForeground = { stopForeground(it) }
        )
        ongoingNotifPosted = false
        Log.w(TAG, "[ONGOING-DEBUG] Persistent ongoing call notification CANCELLED")
    }
}
