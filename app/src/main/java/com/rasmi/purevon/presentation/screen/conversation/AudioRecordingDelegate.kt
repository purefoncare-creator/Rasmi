package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import com.rasmi.purevon.util.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Handles audio recording lifecycle for voice messages.
 * Extracted from ConversationViewModel to reduce class size.
 */
internal class AudioRecordingDelegate(
    private val context: Context,
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope,
    private val onSendMessage: () -> Unit
) {
    companion object {
        private const val TAG = "ConversationViewModel"
        /** Maximum voice recording duration in ms (5 minutes — MMS size limit) */
        const val MAX_RECORDING_DURATION_MS = 5 * 60 * 1000L
    }

    private val audioRecorder = AudioRecorder(context)
    private var recordingJob: Job? = null
    private var recordingStartTime = 0L

    fun startAudioRecording() {
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

            // Timer: update duration + poll amplitude every 80ms
            recordingJob = viewModelScope.launch {
                try {
                    while (_uiState.value.isRecording) {
                        delay(80)
                        val duration = System.currentTimeMillis() - recordingStartTime
                        val amp = audioRecorder.pollAmplitude()
                        _uiState.update { state ->
                            val amps = (state.recordingAmplitudes + amp).takeLast(50)
                            state.copy(recordingDuration = duration, recordingAmplitudes = amps)
                        }

                        // Auto-stop at max duration to avoid huge MMS files
                        if (duration >= MAX_RECORDING_DURATION_MS) {
                            Log.d(TAG, "Max recording duration reached (${MAX_RECORDING_DURATION_MS / 1000}s), auto-stopping to preview")
                            stopAudioRecording()
                            break
                        }
                    }
                } catch (e: Exception) {
                    // ✅ FIX #53: Don't freeze the UI if pollAmplitude throws
                    Log.e(TAG, "Recording amplitude poll error, cancelling", e)
                    cancelAudioRecording()
                }
            }
        }
    }

    fun stopAudioRecording() {
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
    }

    fun cancelAudioRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try { audioRecorder.cancelRecording() } catch (e: Exception) {
            android.util.Log.w("AudioRecordingDelegate", "cancelRecording failed", e)
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
    }

    /**
     * Stop recording and send immediately (hold-to-record release behavior).
     * Minimum 500ms to avoid accidental taps.
     */
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
    }

    fun onCleared() {
        recordingJob?.cancel()
        audioRecorder.cancelRecording()
    }
}
