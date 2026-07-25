package com.rasmi.purevon.util.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for in-call audio routing and controls
 */
@Suppress("DEPRECATION")
@Singleton
class CallAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallAudioManager"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private val _audioState = MutableStateFlow(CallAudioState())
    val audioState: StateFlow<CallAudioState> = _audioState.asStateFlow()
    
    /**
     * Toggle microphone mute
     */
    fun toggleMute(): Boolean {
        return try {
            val currentMuteState = audioManager.isMicrophoneMute
            val newMuteState = !currentMuteState
            
            audioManager.isMicrophoneMute = newMuteState
            
            // Update state immediately
            _audioState.value = _audioState.value.copy(isMuted = newMuteState)
            
            Log.d(TAG, "Microphone mute changed from $currentMuteState to $newMuteState")
            return newMuteState
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute", e)
            val currentState = audioManager.isMicrophoneMute
            _audioState.value = _audioState.value.copy(isMuted = currentState)
            return currentState
        }
    }
    
    /**
     * Set mute state
     */
    fun setMute(mute: Boolean) {
        try {
            audioManager.isMicrophoneMute = mute
            _audioState.value = _audioState.value.copy(isMuted = mute)
            Log.d(TAG, "Microphone mute set to: $mute")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting mute", e)
        }
    }
    
    /**
     * ✅ Synchronize audio state with InCallService
     * This ensures single source of truth for audio state
     */
    fun syncWithInCallService(isMuted: Boolean, isSpeakerOn: Boolean) {
        _audioState.value = _audioState.value.copy(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            currentRoute = if (isSpeakerOn) AudioRoute.SPEAKER else detectCurrentRoute()
        )
        Log.d(TAG, "Audio state synced - Muted: $isMuted, Speaker: $isSpeakerOn, Route: ${_audioState.value.currentRoute}")
    }
    
    /**
     * Toggle speaker phone
     * Note: This should be called from a coroutine scope
     */
    @Suppress("DEPRECATION")
    suspend fun toggleSpeaker(): Boolean {
        return try {
            val currentSpeakerState = audioManager.isSpeakerphoneOn
            val newSpeakerState = !currentSpeakerState
            
            Log.d(TAG, "Toggling speaker - Current: $currentSpeakerState, Target: $newSpeakerState")
            
            // Set audio mode first
            audioManager.mode = AudioManager.MODE_IN_CALL
            delay(50) // ✅ Non-blocking delay
            
            // Then toggle speaker
            audioManager.isSpeakerphoneOn = newSpeakerState
            delay(50) // ✅ Non-blocking delay
            
            // Verify the change
            val actualState = audioManager.isSpeakerphoneOn
            Log.d(TAG, "Speaker state after toggle: $actualState")
            
            // Update state immediately
            val route = if (actualState) AudioRoute.SPEAKER else detectCurrentRoute()
            _audioState.value = _audioState.value.copy(
                isSpeakerOn = actualState,
                currentRoute = route
            )
            
            Log.d(TAG, "Speaker phone changed from $currentSpeakerState to $actualState, route: $route")
            Log.d(TAG, "Audio mode: ${audioManager.mode}, Speaker: ${audioManager.isSpeakerphoneOn}")
            
            actualState
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speaker", e)
            val currentState = audioManager.isSpeakerphoneOn
            _audioState.value = _audioState.value.copy(isSpeakerOn = currentState)
            currentState
        }
    }
    
    /**
     * Set speaker state
     * Note: This should be called from a coroutine scope
     */
    suspend fun setSpeaker(enabled: Boolean): Boolean {
        return try {
            Log.d(TAG, "Setting speaker to: $enabled")
            
            // Always set mode first
            audioManager.mode = AudioManager.MODE_IN_CALL
            delay(50) // ✅ Non-blocking delay
            
            // Then set speaker
            audioManager.isSpeakerphoneOn = enabled
            delay(100) // ✅ Non-blocking delay
            
            // Verify the actual state
            val actualState = audioManager.isSpeakerphoneOn
            Log.d(TAG, "Speaker actual state: $actualState")
            
            val route = if (actualState) AudioRoute.SPEAKER else detectCurrentRoute()
            _audioState.value = _audioState.value.copy(
                isSpeakerOn = actualState,
                currentRoute = route
            )
            
            Log.d(TAG, "Speaker set to: $actualState (requested: $enabled), route: $route")
            actualState
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speaker", e)
            val currentState = audioManager.isSpeakerphoneOn
            _audioState.value = _audioState.value.copy(isSpeakerOn = currentState)
            currentState
        }
    }
    
    /**
     * Route audio to Bluetooth
     */
    fun routeToBluetooth(): Boolean {
        return try {
            if (hasBluetoothDevice()) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                
                _audioState.value = _audioState.value.copy(
                    currentRoute = AudioRoute.BLUETOOTH,
                    isBluetoothAvailable = true
                )
                
                Log.d(TAG, "Audio routed to Bluetooth")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error routing to Bluetooth", e)
            false
        }
    }
    
    /**
     * Stop Bluetooth audio
     */
    fun stopBluetooth() {
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            
            val route = if (hasWiredHeadset()) AudioRoute.WIRED_HEADSET else AudioRoute.EARPIECE
            _audioState.value = _audioState.value.copy(
                currentRoute = route
            )
            
            Log.d(TAG, "Bluetooth audio stopped, route: $route")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Bluetooth", e)
        }
    }
    
    /**
     * Check if Bluetooth device is available
     */
    fun hasBluetoothDevice(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                             it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth", e)
            false
        }
    }
    
    /**
     * Check if a wired headset (3.5mm or USB-C) is connected
     */
    fun hasWiredHeadset(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isWiredHeadsetOn
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking wired headset", e)
            false
        }
    }

    /**
     * Detect the best current non-speaker route based on connected devices
     */
    private fun detectCurrentRoute(): AudioRoute {
        return when {
            audioManager.isBluetoothScoOn -> AudioRoute.BLUETOOTH
            hasBluetoothDevice() -> AudioRoute.BLUETOOTH
            hasWiredHeadset() -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.EARPIECE
        }
    }

    /**
     * Get current audio route
     */
    fun getCurrentRoute(): AudioRoute {
        return when {
            audioManager.isSpeakerphoneOn -> AudioRoute.SPEAKER
            audioManager.isBluetoothScoOn -> AudioRoute.BLUETOOTH
            hasBluetoothDevice() -> AudioRoute.BLUETOOTH
            hasWiredHeadset() -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.EARPIECE
        }
    }
    
    /**
     * Set audio mode for call
     */
    fun setCallMode() {
        try {
            audioManager.mode = AudioManager.MODE_IN_CALL
            updateAudioState()
            Log.d(TAG, "Audio mode set to IN_CALL")
            Log.d(TAG, "Initial state - Mute: ${audioManager.isMicrophoneMute}, Speaker: ${audioManager.isSpeakerphoneOn}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting call mode", e)
        }
    }
    
    /**
     * Reset audio mode to normal
     */
    fun resetAudioMode() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isMicrophoneMute = false
            
            if (audioManager.isBluetoothScoOn) {
                stopBluetooth()
            }
            
            _audioState.value = CallAudioState()
            
            Log.d(TAG, "Audio mode reset to NORMAL")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audio mode", e)
        }
    }
    
    /**
     * Update audio state
     */
    fun updateAudioState() {
        _audioState.value = CallAudioState(
            isMuted = audioManager.isMicrophoneMute,
            isSpeakerOn = audioManager.isSpeakerphoneOn,
            isBluetoothAvailable = hasBluetoothDevice(),
            currentRoute = getCurrentRoute()
        )
    }
    
    /**
     * Get volume level (0-100)
     */
    fun getVolumeLevel(): Int {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        return ((currentVolume.toFloat() / maxVolume) * 100).toInt()
    }
    
    /**
     * Set volume level (0-100)
     */
    fun setVolumeLevel(level: Int) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val volume = ((level.toFloat() / 100) * maxVolume).toInt()
        audioManager.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            volume,
            0
        )
    }
}

/**
 * Audio route options
 */
enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET
}

/**
 * State of call audio
 */
data class CallAudioState(
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isBluetoothAvailable: Boolean = false,
    val currentRoute: AudioRoute = AudioRoute.EARPIECE,
    val volumeLevel: Int = 50
)
