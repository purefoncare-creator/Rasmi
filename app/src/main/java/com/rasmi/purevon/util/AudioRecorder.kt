package com.rasmi.purevon.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Professional Audio Recorder with real-time amplitude reporting.
 * 
 * Features:
 * - Real-time amplitude via StateFlow (for live waveform)
 * - Proper lifecycle management
 * - Cleanup of temp files on cancel
 * - AAC encoding at 128kbps/44.1kHz
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val MAX_AMPLITUDE = 32767f
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** Normalized amplitude (0.0 – 1.0) updated every ~100ms by the timer in ViewModel */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /** Whether recording is actively in progress */
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun startRecording(): File? {
        return try {
            // Clean up any previous session
            release()

            val audioDir = File(context.cacheDir, "audio_messages")
            if (!audioDir.exists()) audioDir.mkdirs()

            // Purge old temp files (> 1 hour)
            cleanupOldFiles(audioDir)

            outputFile = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }

            _isRecording.value = true
            _amplitude.value = 0f
            Log.d(TAG, "Recording started → ${outputFile?.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            release()
            null
        }
    }

    /**
     * Poll the current max amplitude from MediaRecorder.
     * Returns a normalized value 0.0 – 1.0.
     * Call this every ~80-100ms from the ViewModel timer.
     */
    fun pollAmplitude(): Float {
        return try {
            val raw = mediaRecorder?.maxAmplitude ?: 0
            val normalized = (raw / MAX_AMPLITUDE).coerceIn(0f, 1f)
            _amplitude.value = normalized
            normalized
        } catch (e: Exception) {
            0f
        }
    }

    fun stopRecording(): File? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _isRecording.value = false
            _amplitude.value = 0f
            Log.d(TAG, "Recording stopped → ${outputFile?.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            release()
            null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) { /* ignore – may not have started */ }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
        _isRecording.value = false
        _amplitude.value = 0f
        Log.d(TAG, "Recording cancelled")
    }

    private fun release() {
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        _isRecording.value = false
        _amplitude.value = 0f
    }

    /** Remove temp voice files older than 1 hour */
    private fun cleanupOldFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - 3600_000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach {
            it.delete()
        }
    }
}
