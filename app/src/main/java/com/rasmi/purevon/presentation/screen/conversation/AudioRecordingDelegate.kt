package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.core.content.FileProvider
import com.rasmi.purevon.util.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AudioRecordingDelegate(
    private val context: Context,
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope,
    private val onSendMessage: () -> Unit
) {
    companion object {
        private const val TAG = "AudioRecordingDelegate"
        const val MAX_RECORDING_DURATION_MS = 5 * 60 * 1000L
    }

    private val audioRecorder = AudioRecorder(context)
    private var recordingJob: Job? = null
    private var recordingStartTime = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private var isStopping = false

    fun startAudioRecording() {
        acquireWakeLock()
        val audioFile = audioRecorder.startRecording()
        if (audioFile != null) {
            recordingStartTime = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isRecording = true,
                    recordingDuration = 0,
                    audioRecordingFile = audioFile.absolutePath,
                    recordingAmplitudes = emptyList(),
                    isRecordingLocked = false
                )
            }

            recordingJob = viewModelScope.launch {
                try {
                    while (isActive && _uiState.value.isRecording) {
                        delay(80)
                        val duration = System.currentTimeMillis() - recordingStartTime
                        val amp = audioRecorder.pollAmplitude()
                        _uiState.update { state ->
                            val amps = (state.recordingAmplitudes + amp).takeLast(50)
                            state.copy(recordingDuration = duration, recordingAmplitudes = amps)
                        }

                        if (duration >= MAX_RECORDING_DURATION_MS) {
                            Log.d(TAG, "Max recording duration reached, auto-stopping")
                            withContext(Dispatchers.Main) {
                                stopAudioRecording()
                            }
                            break
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (!isStopping) {
                        Log.d(TAG, "Recording coroutine cancelled (normal stop)")
                    }
                } catch (e: Exception) {
                    if (!isStopping) {
                        Log.e(TAG, "Recording amplitude poll error, cancelling", e)
                        cancelAudioRecording()
                    }
                }
            }
        } else {
            releaseWakeLock()
        }
    }

    fun stopAudioRecording() {
        isStopping = true
        recordingJob?.cancel()
        recordingJob = null
        val file = audioRecorder.stopRecording()
        _uiState.update {
            it.copy(
                isRecording = false,
                isRecordingLocked = false,
                audioRecordingFile = file?.absolutePath ?: it.audioRecordingFile
            )
        }
        isStopping = false
        releaseWakeLock()
    }

    fun cancelAudioRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecorder.cancelRecording()
        } catch (e: Exception) {
            Log.w(TAG, "cancelRecording failed", e)
        }
        _uiState.update {
            it.copy(
                isRecording = false,
                recordingDuration = 0,
                audioRecordingFile = null,
                recordingAmplitudes = emptyList(),
                isRecordingLocked = false
            )
        }
        releaseWakeLock()
    }

    fun onAppBackgrounded() {
        if (_uiState.value.isRecording) {
            Log.d(TAG, "App backgrounded during recording, auto-stopping")
            stopAudioRecording()
        }
    }

    fun stopAndSendAudioRecording() {
        if (!_uiState.value.isRecording && _uiState.value.audioRecordingFile == null) {
            return
        }
        val duration = _uiState.value.recordingDuration
        if (duration < 500) {
            cancelAudioRecording()
            return
        }
        recordingJob?.cancel()
        val file = audioRecorder.stopRecording()
        if (file != null && file.exists() && file.length() > 0) {
            val audioUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                ).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating FileProvider URI for audio file", e)
                null
            }

            if (audioUri != null) {
                val attachment = AttachmentData(
                    uri = audioUri,
                    fileName = "voice_recording.m4a",
                    mimeType = "audio/mp4",
                    isImage = false
                )
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        attachments = it.attachments + attachment,
                        audioRecordingFile = null,
                        recordingDuration = 0,
                        recordingAmplitudes = emptyList(),
                        isRecordingLocked = false
                    )
                }
                onSendMessage()
            } else {
                cancelAudioRecording()
            }
        } else {
            cancelAudioRecording()
        }
        releaseWakeLock()
    }

    fun sendAudioMessage() {
        recordingJob?.cancel()
        val recorderFile = if (_uiState.value.isRecording) audioRecorder.stopRecording() else null

        val audioFile = recorderFile?.absolutePath ?: _uiState.value.audioRecordingFile
        if (audioFile != null) {
            val file = java.io.File(audioFile)
            if (file.exists() && file.length() > 0) {
                val audioUri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    ).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating FileProvider URI for audio file", e)
                    cancelAudioRecording()
                    return
                }

                val attachment = AttachmentData(
                    uri = audioUri,
                    fileName = "voice_recording.m4a",
                    mimeType = "audio/mp4",
                    isImage = false
                )

                _uiState.update {
                    it.copy(
                        attachments = it.attachments + attachment,
                        audioRecordingFile = null,
                        recordingDuration = 0,
                        isRecording = false,
                        isRecordingLocked = false,
                        recordingAmplitudes = emptyList()
                    )
                }

                onSendMessage()
            } else {
                cancelAudioRecording()
            }
        }
        releaseWakeLock()
    }

    fun onCleared() {
        recordingJob?.cancel()
        audioRecorder.cancelRecording()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "rasmi:audio_recording_wakelock"
            ).apply {
                acquire(MAX_RECORDING_DURATION_MS + 10_000L)
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
            Log.d(TAG, "WakeLock released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }
}
