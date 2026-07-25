package com.rasmi.purevon.domain.call

import android.telecom.Call
import kotlinx.coroutines.flow.StateFlow

/**
 * ✅ Interface to decouple ViewModels/components from PurevonInCallService.Companion
 * Provides testable, injectable access to InCall state instead of static singleton coupling.
 *
 * Issue #14: استخدم interface + DI بدل static PurevonInCallService.Companion
 */
interface InCallServiceBridge {
    
    // ── Call State ──
    fun getCurrentCall(): Call?
    fun getHeldCall(): Call?
    fun getWaitingCall(): Call?
    fun getActiveCalls(): List<Call>
    fun hasMultipleCalls(): Boolean
    fun hasWaitingCall(): Boolean
    fun findCallById(callId: String): Call?
    
    // ── Conference ──
    fun isConferenceCall(): Boolean
    fun getConferenceParticipants(): List<String>
    
    // ── Call Info ──
    fun getCallStartTime(): Long
    val currentPhoneNumber: String?
    val currentContactName: String?
    val wasCallRejectedByUser: Boolean
    val wasCallActive: Boolean
    
    fun setCallRejectedByUser(rejected: Boolean)
    fun setCallActiveState(active: Boolean)
    
    // ── State Flows ──
    val muteState: StateFlow<Boolean>
    val speakerState: StateFlow<Boolean>
    val callState: StateFlow<Int?>
    val audioRouteState: StateFlow<Int>
    
    // ── Call Control ──
    fun setStateChangeListener(listener: ((Call) -> Unit)?)
    fun setInCallActivityVisible(visible: Boolean)
    fun playDtmfTone(digit: Char)
    fun stopDtmfTone()
    
    // ── Instance operations (delegate to service, return safe defaults if unavailable) ──
    fun toggleMute(): Boolean
    fun toggleSpeaker(): Boolean
    fun isMuted(): Boolean
    fun isSpeakerOn(): Boolean
    fun setAudioRoute(route: Int)
    fun getAvailableAudioRoutes(): Int
    fun getCurrentAudioRoute(): Int
    fun answerAndHold(): Boolean
    fun rejectWaitingCall(): Boolean
    fun swapCalls(): Boolean
    fun mergeCalls(): Boolean
    fun holdCurrentCall(): Boolean
    fun unholdCall(): Boolean
    
    // ── Utility ──
    fun getStateName(state: Int): String
}
