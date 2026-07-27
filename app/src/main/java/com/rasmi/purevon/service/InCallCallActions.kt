package com.rasmi.purevon.service

import android.telecom.Call
import android.util.Log
import com.rasmi.purevon.service.InCallServiceConstants.TAG

@Suppress("DEPRECATION")
internal fun PurevonInCallService.toggleMute(): Boolean {
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

@Suppress("DEPRECATION")
internal fun PurevonInCallService.toggleSpeaker(): Boolean {
    return try {
        val currentRoute = callAudioState?.route ?: android.telecom.CallAudioState.ROUTE_EARPIECE
        val supported = callAudioState?.supportedRouteMask ?: android.telecom.CallAudioState.ROUTE_EARPIECE

        val newRoute = if (currentRoute == android.telecom.CallAudioState.ROUTE_SPEAKER) {
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

@Suppress("DEPRECATION")
internal fun PurevonInCallService.isMuted(): Boolean {
    return callAudioState?.isMuted ?: false
}

@Suppress("DEPRECATION")
internal fun PurevonInCallService.isSpeakerOn(): Boolean {
    return callAudioState?.route == android.telecom.CallAudioState.ROUTE_SPEAKER
}

@Suppress("DEPRECATION")
internal fun PurevonInCallService.swapCalls() {
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
    }
}

@Suppress("DEPRECATION")
internal fun PurevonInCallService.holdCurrentCall(): Boolean {
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
    }
}

@Suppress("DEPRECATION")
internal fun PurevonInCallService.unholdCall(): Boolean {
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
    }
}

@Suppress("DEPRECATION")
internal fun PurevonInCallService.answerAndHold(): Boolean {
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
    }
}

@Suppress("DEPRECATION")
internal fun PurevonInCallService.rejectWaitingCall(): Boolean {
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

@Suppress("DEPRECATION")
internal fun PurevonInCallService.mergeCalls(): Boolean {
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
    }
}
