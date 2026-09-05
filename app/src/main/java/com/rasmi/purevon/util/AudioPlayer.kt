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

class AudioPlayer(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentUri: String? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

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
                stop()
                hasAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pause()
            AudioManager.AUDIOFOCUS_GAIN -> resume()
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

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun play(uri: String, startAtPosition: Int = 0) {
        try {
            if (currentUri == uri && mediaPlayer != null) {
                if (_isPlaying.value) {
                    pause()
                } else {
                    resume()
                }
                return
            }

            if (!requestAudioFocus()) {
                Log.w(TAG, "Could not gain audio focus, playing anyway")
            }

            stop()

            currentUri = uri
            _currentPlayingUri.value = uri

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(context, Uri.parse(uri))
                setOnPreparedListener { mp ->
                    _duration.value = mp.duration
                    if (startAtPosition > 0) {
                        mp.seekTo(startAtPosition.toLong(), MediaPlayer.SEEK_CLOSEST_SYNC)
                    }
                    mp.start()
                    applySpeed()
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

    fun resume() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    applySpeed()
                    _isPlaying.value = true
                    Log.d(TAG, "Resumed playback")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming", e)
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs.toLong(), MediaPlayer.SEEK_CLOSEST_SYNC)
            _currentPosition.value = positionMs
            Log.d(TAG, "Seeked to: $positionMs")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }

    fun setSpeed(speed: Float) {
        val previous = _playbackSpeed.value
        _playbackSpeed.value = speed
        if (!applySpeed()) {
            _playbackSpeed.value = previous
        }
    }

    fun cycleSpeed(): Float {
        val next = when (_playbackSpeed.value) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        setSpeed(next)
        return _playbackSpeed.value
    }

    private fun applySpeed(): Boolean {
        return try {
            mediaPlayer?.let { mp ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val params = mp.playbackParams
                    mp.playbackParams = params.setSpeed(_playbackSpeed.value)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speed", e)
            false
        }
    }

    fun updatePosition() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    _currentPosition.value = it.currentPosition
                }
            }
        } catch (_: Exception) {
        }
    }

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
            _playbackSpeed.value = 1f
            abandonAudioFocus()
            unregisterNoisyReceiver()
            Log.d(TAG, "Stopped playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }
    }

    fun release() {
        stop()
    }

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
            try {
                context.unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {
            }
            noisyReceiverRegistered = false
        }
    }

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

    fun isPlayingUri(uri: String): Boolean {
        return _isPlaying.value && currentUri == uri
    }
}
