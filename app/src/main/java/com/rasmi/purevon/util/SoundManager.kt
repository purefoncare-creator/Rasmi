package com.rasmi.purevon.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.rasmi.purevon.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for playing message sounds (like iMessage)
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SoundManager"
    }
    
    private val lock = Any()
    private var sendPlayer: MediaPlayer? = null
    private var receivePlayer: MediaPlayer? = null
    private var otpPlayer: MediaPlayer? = null
    private var reminderPlayer: MediaPlayer? = null
    private var copyPlayer: MediaPlayer? = null
    @Volatile private var isSoundEnabled = true
    @Volatile private var isVibrationEnabled = true
    
    /**
     * Check if the device ringer mode allows playing notification sounds.
     * Returns false when the phone is in silent or vibrate mode.
     */
    private fun isRingerNormal(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }
    
    /**
     * Play message sent sound
     */
    fun playMessageSentSound() {
        if (!isSoundEnabled || !isRingerNormal()) return
        
        try {
            synchronized(lock) {
                // Release previous player if exists
                sendPlayer?.release()

                sendPlayer = MediaPlayer.create(context, R.raw.message_sent)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    setOnCompletionListener { mp ->
                        synchronized(lock) {
                            mp.release()
                            if (sendPlayer === mp) sendPlayer = null
                        }
                    }
                    start()
                }
            }
            
            Log.d(TAG, "Message sent sound played")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sent sound", e)
        }
    }
    
    /**
     * Play OTP notification sound
     */
    fun playOtpSound() {
        if (!isSoundEnabled || !isRingerNormal()) return
        try {
            synchronized(lock) {
                otpPlayer?.release()
                otpPlayer = MediaPlayer.create(context, R.raw.otp)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    setOnCompletionListener { mp ->
                        synchronized(lock) {
                            mp.release()
                            if (otpPlayer === mp) otpPlayer = null
                        }
                    }
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing OTP sound", e)
        }
    }
    
    /**
     * Play callback reminder sound
     */
    fun playReminderSound() {
        if (!isSoundEnabled) return
        try {
            synchronized(lock) {
                reminderPlayer?.release()
                reminderPlayer = MediaPlayer.create(context, R.raw.remembercall)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    setOnCompletionListener { mp ->
                        synchronized(lock) {
                            mp.release()
                            if (reminderPlayer === mp) reminderPlayer = null
                        }
                    }
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing reminder sound", e)
        }
    }
    
    /**
     * Play copy-to-clipboard sound
     */
    fun playCopySound() {
        if (!isSoundEnabled || !isRingerNormal()) return
        try {
            synchronized(lock) {
                copyPlayer?.release()
                copyPlayer = MediaPlayer.create(context, R.raw.copy)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    setOnCompletionListener { mp ->
                        synchronized(lock) {
                            mp.release()
                            if (copyPlayer === mp) copyPlayer = null
                        }
                    }
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing copy sound", e)
        }
    }

    /**
     * Play blocked call notification sound
     */
    fun playBlockedCallSound() {
        if (!isSoundEnabled || !isRingerNormal()) return
        try {
            synchronized(lock) {
                // For blocked call sound, we can use a system notification sound or a custom one
                // Using a simple beep for now - you can replace R.raw.blocked_call with your sound
                val soundRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Try to use a notification sound on newer Android versions
                    R.raw.notification
                } else {
                    // Fallback to a system sound or silent if needed
                    R.raw.notification
                }

                MediaPlayer.create(context, soundRes)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    setOnCompletionListener { mp ->
                        synchronized(lock) {
                            mp.release()
                        }
                    }
                    start()
                }

            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing blocked call sound", e)
        }
    }
    
    /**
     * Short vibration for message received
     */
    private fun vibrateShort() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            vibrator.vibrate(
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating", e)
        }
    }
    
    /**
     * Enable/disable sounds
     */
    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
    }
    
    /**
     * Enable/disable vibration
     */
    fun setVibrationEnabled(enabled: Boolean) {
        isVibrationEnabled = enabled
    }
    
    /**
     * Release resources
     */
    fun release() {
        synchronized(lock) {
            sendPlayer?.release()
            sendPlayer = null
            receivePlayer?.release()
            receivePlayer = null
            otpPlayer?.release()
            otpPlayer = null
            reminderPlayer?.release()
            reminderPlayer = null
            copyPlayer?.release()
            copyPlayer = null
        }
    }
}
