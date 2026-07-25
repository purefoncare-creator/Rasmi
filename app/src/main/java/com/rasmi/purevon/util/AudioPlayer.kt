package com.rasmi.purevon.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Audio Player utility for playing voice messages
 * Supports both local files and content URIs
 */
class AudioPlayer(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioPlayer"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentUri: String? = null
    
    // ✅ FIX #33: Audio Focus management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    // ✅ FIX #33: Pause on headphone disconnect
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }
    private var noisyReceiverRegistered = false
    
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss — stop
                stop()
                hasAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss (e.g. phone call) — pause
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Could duck, but for voice messages just pause
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus — resume if was playing
                resume()
            }
        }
    }
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()
    
    private val _currentPlayingUri = MutableStateFlow<String?>(null)
    val currentPlayingUri: StateFlow<String?> = _currentPlayingUri.asStateFlow()
    
    /**
     * Play audio from URI (file:// or content://)
     */
    fun play(uri: String) {
        try {
            // If already playing this URI, just toggle pause/resume
            if (currentUri == uri && mediaPlayer != null) {
                if (_isPlaying.value) {
                    pause()
                } else {
                    resume()
                }
                return
            }
            
            // ✅ FIX #33: Request audio focus before playing
            if (!requestAudioFocus()) {
                Log.w(TAG, "Could not gain audio focus, playing anyway")
            }
            
            // Stop any existing playback
            stop()
            
            currentUri = uri
            _currentPlayingUri.value = uri
            
            mediaPlayer = MediaPlayer().apply {
                // ✅ FIX #33: Set audio attributes
                setAudioAttributes(audioAttributes)
                setDataSource(context, Uri.parse(uri))
                setOnPreparedListener { mp ->
                    _duration.value = mp.duration
                    mp.start()
                    _isPlaying.value = true
                    registerNoisyReceiver()
                    Log.d(TAG, "Started playing: $uri, duration: ${mp.duration}ms")
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = 0
                    abandonAudioFocus()
                    unregisterNoisyReceiver()
                    Log.d(TAG, "Playback completed: $uri")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _isPlaying.value = false
                    abandonAudioFocus()
                    unregisterNoisyReceiver()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio: $uri", e)
            _isPlaying.value = false
            abandonAudioFocus()
        }
    }
    
    /**
     * Pause playback
     */
    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _isPlaying.value = false
                    _currentPosition.value = it.currentPosition
                    Log.d(TAG, "Paused at position: ${it.currentPosition}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing", e)
        }
    }
    
    /**
     * Resume playback
     */
    fun resume() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    _isPlaying.value = true
                    Log.d(TAG, "Resumed playback")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming", e)
        }
    }
    
    /**
     * Seek to position
     */
    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _currentPosition.value = positionMs
            Log.d(TAG, "Seeked to: $positionMs")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }
    
    /**
     * Update current position
     */
    fun updatePosition() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    _currentPosition.value = it.currentPosition
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Stop playback
     */
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
            mediaPlayer = null
            currentUri = null
            _isPlaying.value = false
            _currentPosition.value = 0
            _currentPlayingUri.value = null
            abandonAudioFocus()
            unregisterNoisyReceiver()
            Log.d(TAG, "Stopped playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        stop()
    }
    
    // ✅ FIX #33: Audio focus management helpers
    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        audioFocusRequest = request
        val result = audioManager.requestAudioFocus(request)
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }
    
    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
        hasAudioFocus = false
    }
    
    private fun registerNoisyReceiver() {
        if (!noisyReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(noisyReceiver, filter)
            }
            noisyReceiverRegistered = true
        }
    }
    
    private fun unregisterNoisyReceiver() {
        if (noisyReceiverRegistered) {
            try { context.unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
            noisyReceiverRegistered = false
        }
    }
    
    /**
     * Get audio duration from URI
     */
    fun getDuration(uri: String): Long {
        return try {
            val mp = MediaPlayer().apply {
                setDataSource(context, Uri.parse(uri))
                prepare()
            }
            val duration = mp.duration.toLong()
            mp.release()
            duration
        } catch (e: Exception) {
            Log.e(TAG, "Error getting duration", e)
            0L
        }
    }
    
    /**
     * Check if currently playing a specific URI
     */
    fun isPlayingUri(uri: String): Boolean {
        return _isPlaying.value && currentUri == uri
    }
}

