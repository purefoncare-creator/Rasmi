package com.rasmi.purevon.service

import android.telecom.Call
import com.rasmi.purevon.domain.call.InCallServiceBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmName

/**
 * ✅ Owns all mutable in-call state (Finding C — migrated from PurevonInCallService.Companion).
 *
 * PurevonInCallService registers itself on creation and reads/writes state through
 * this singleton. External consumers (ViewModels, Activities) access state via the
 * [InCallServiceBridge] interface.
 */
@Singleton
class InCallServiceBridgeImpl @Inject constructor() : InCallServiceBridge {

    // ── Service Instance ──────────────────────────────────────────────────────
    @Volatile
    var serviceInstance: PurevonInCallService? = null
        private set

    fun registerService(service: PurevonInCallService) {
        serviceInstance = service
    }

    fun unregisterService() {
        serviceInstance = null
    }

    // ── Mutable Call State ────────────────────────────────────────────────────
    @set:JvmName("_setInCallActivityVisible")
    @Volatile
    var isInCallActivityVisible: Boolean = false

    val activeCalls = CopyOnWriteArrayList<Call>()

    /** Lock for compound read-modify-write operations on call fields. */
    val callStateLock = Any()

    @Volatile
    private var _currentCall: Call? = null
    @get:JvmName("currentCallDirect")
    var currentCall: Call?
        get() = _currentCall
        set(value) {
            synchronized(callStateLock) { _currentCall = value }
        }

    @Volatile
    private var _heldCall: Call? = null
    @get:JvmName("heldCallDirect")
    var heldCall: Call?
        get() = _heldCall
        set(value) {
            synchronized(callStateLock) { _heldCall = value }
        }

    @Volatile
    private var _waitingCall: Call? = null
    @get:JvmName("waitingCallDirect")
    var waitingCall: Call?
        get() = _waitingCall
        set(value) {
            synchronized(callStateLock) { _waitingCall = value }
        }

    @Volatile
    var isConference: Boolean = false

    @get:JvmName("conferenceParticipantsDirect")
    @Volatile
    var conferenceParticipants: List<String> = emptyList()

    /** When true, callbacks skip automatic reference management (swap/merge/answerAndHold). */
    @Volatile
    var isManagingCalls: Boolean = false

    @get:JvmName("callStartTimeDirect")
    @Volatile
    var callStartTime: Long = 0L

    @Volatile
    override var currentPhoneNumber: String? = null

    @Volatile
    override var currentContactName: String? = null

    @Volatile
    var isIncomingCall: Boolean = false

    @Volatile
    override var wasCallRejectedByUser: Boolean = false

    @Volatile
    var wasMissedCall: Boolean = false

    @Volatile
    override var wasCallActive: Boolean = false

    override fun setCallRejectedByUser(rejected: Boolean) {
        wasCallRejectedByUser = rejected
    }

    override fun setCallActiveState(active: Boolean) {
        wasCallActive = active
    }

    @set:JvmName("_setStateChangeListener")
    @Volatile
    var stateChangeListener: ((Call) -> Unit)? = null

    // ── StateFlows ────────────────────────────────────────────────────────────
    private val _muteState = MutableStateFlow(false)
    override val muteState: StateFlow<Boolean> = _muteState.asStateFlow()

    private val _speakerState = MutableStateFlow(false)
    override val speakerState: StateFlow<Boolean> = _speakerState.asStateFlow()

    private val _callState = MutableStateFlow<Int?>(null)
    override val callState: StateFlow<Int?> = _callState.asStateFlow()

    @Suppress("DEPRECATION")
    private val _audioRouteState = MutableStateFlow(android.telecom.CallAudioState.ROUTE_EARPIECE)
    override val audioRouteState: StateFlow<Int> = _audioRouteState.asStateFlow()

    /** Update mute StateFlow (called by the service after toggling hardware). */
    fun setMuteState(value: Boolean) { _muteState.value = value }
    /** Update speaker StateFlow (called by the service after toggling hardware). */
    fun setSpeakerState(value: Boolean) { _speakerState.value = value }
    /** Publish new call state to observers. */
    fun setCallState(value: Int?) { _callState.value = value }
    /** Publish confirmed audio route from system callback. */
    fun setAudioRouteState(route: Int) { _audioRouteState.value = route }

    // ── Interface: Call State ─────────────────────────────────────────────────
    override fun getCurrentCall(): Call? = currentCall
    override fun getHeldCall(): Call? = heldCall
    override fun getWaitingCall(): Call? = waitingCall
    override fun getActiveCalls(): List<Call> = activeCalls.toList()
    override fun hasMultipleCalls(): Boolean = activeCalls.size > 1
    override fun hasWaitingCall(): Boolean = waitingCall != null
    override fun findCallById(callId: String): Call? =
        activeCalls.find { call ->
            val handle = call.details?.handle?.schemeSpecificPart
            "${call.details?.creationTimeMillis ?: 0}_${handle ?: ""}" == callId
        }

    // ── Interface: Conference ─────────────────────────────────────────────────
    override fun isConferenceCall(): Boolean = isConference
    override fun getConferenceParticipants(): List<String> = conferenceParticipants

    // ── Interface: Call Info ──────────────────────────────────────────────────
    override fun getCallStartTime(): Long = callStartTime

    // ── Interface: Call Control ───────────────────────────────────────────────
    override fun setStateChangeListener(listener: ((Call) -> Unit)?) {
        stateChangeListener = listener
    }

    override fun setInCallActivityVisible(visible: Boolean) {
        isInCallActivityVisible = visible
        serviceInstance?.updateCurrentNotificationVisibility()
    }

    override fun playDtmfTone(digit: Char) { currentCall?.playDtmfTone(digit) }
    override fun stopDtmfTone() { currentCall?.stopDtmfTone() }

    // ── Interface: Instance Operations (safe defaults when service unavailable) ──
    override fun toggleMute(): Boolean = serviceInstance?.toggleMute() ?: false
    override fun toggleSpeaker(): Boolean = serviceInstance?.toggleSpeaker() ?: false
    override fun isMuted(): Boolean = serviceInstance?.isMuted() ?: false
    override fun isSpeakerOn(): Boolean = serviceInstance?.isSpeakerOn() ?: false
    override fun setAudioRoute(route: Int) { serviceInstance?.setAudioRoute(route) }
    @Suppress("DEPRECATION")
    override fun getAvailableAudioRoutes(): Int = serviceInstance?.callAudioState?.supportedRouteMask ?: android.telecom.CallAudioState.ROUTE_EARPIECE
    @Suppress("DEPRECATION")
    override fun getCurrentAudioRoute(): Int = serviceInstance?.callAudioState?.route ?: android.telecom.CallAudioState.ROUTE_EARPIECE
    override fun answerAndHold(): Boolean = serviceInstance?.answerAndHold() ?: false
    override fun rejectWaitingCall(): Boolean = serviceInstance?.rejectWaitingCall() ?: false
    override fun swapCalls(): Boolean {
        serviceInstance?.swapCalls()
        return serviceInstance != null
    }
    override fun mergeCalls(): Boolean = serviceInstance?.mergeCalls() ?: false
    override fun holdCurrentCall(): Boolean = serviceInstance?.holdCurrentCall() ?: false
    override fun unholdCall(): Boolean = serviceInstance?.unholdCall() ?: false

    // ── Interface: Utility ────────────────────────────────────────────────────
    override fun getStateName(state: Int): String = PurevonInCallService.getStateName(state)

    // ── Cleanup ───────────────────────────────────────────────────────────────
    /** Reset every mutable field — called from PurevonInCallService.onDestroy(). */
    fun resetAllState() {
        synchronized(callStateLock) {
            _currentCall = null
            _heldCall = null
            _waitingCall = null
        }
        callStartTime = 0L
        currentPhoneNumber = null
        currentContactName = null
        isIncomingCall = false
        wasMissedCall = false
        wasCallRejectedByUser = false
        wasCallActive = false
        isConference = false
        conferenceParticipants = emptyList()
        isManagingCalls = false
        isInCallActivityVisible = false
        stateChangeListener = null
        activeCalls.clear()
        _muteState.value = false
        _speakerState.value = false
        _callState.value = null
        @Suppress("DEPRECATION")
        _audioRouteState.value = android.telecom.CallAudioState.ROUTE_EARPIECE
    }
}
