package com.rasmi.purevon.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.util.AudioPlayer
import com.rasmi.purevon.util.ActiveAudioManager
import com.rasmi.purevon.util.audio.AudioWaveformGenerator
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioMessageBubble(
    audioUri: String,
    durationMs: Long,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    waveformSamples: List<Float>? = null
) {
    val context = LocalContext.current
    val audioPlayer = remember(audioUri) { AudioPlayer(context) }
    val waveformGenerator = remember { AudioWaveformGenerator(context) }

    val playerDuration by audioPlayer.duration.collectAsStateWithLifecycle()
    val effectiveDuration = if (durationMs > 0) durationMs else playerDuration.toLong()

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var waveform by remember { mutableStateOf(waveformSamples) }
    val speed by audioPlayer.playbackSpeed.collectAsStateWithLifecycle()
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(audioUri) {
        if (waveform == null) {
            waveformGenerator.generateWaveform(audioUri).fold(
                onSuccess = { samples -> waveform = samples },
                onFailure = { waveform = waveformGenerator.generateMockWaveform() }
            )
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            audioPlayer.updatePosition()
            val position = audioPlayer.currentPosition.value
            currentPositionMs = position.toLong()
            if (!isSeeking) {
                progress = if (effectiveDuration > 0) {
                    (position.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
                } else 0f
            }
            delay(100)
        }
    }

    LaunchedEffect(audioUri) {
        audioPlayer.isPlaying.collect { playing ->
            isPlaying = playing
            if (!playing && effectiveDuration > 0 && audioPlayer.currentPosition.value >= effectiveDuration.toInt() - 100) {
                progress = 0f
                currentPositionMs = 0
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ActiveAudioManager.clearIfActive(audioPlayer)
            audioPlayer.release()
        }
    }

    val backgroundColor = if (isOutgoing) PurevonBubbleSent else PurevonBubbleReceived
    val contentColor = if (isOutgoing) PurevonBubbleSentText else PurevonBubbleReceivedText
    val secondaryColor = if (isOutgoing) PurevonBubbleSentText.copy(alpha = 0.7f) else PurevonTextSecondary

    Surface(
        modifier = modifier.widthIn(min = 220.dp, max = 310.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(MessagingDimensions.corner10x)
    ) {
        Row(
            modifier = Modifier
                .height(68.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        audioPlayer.pause()
                    } else {
                        ActiveAudioManager.setActive(audioPlayer)
                        audioPlayer.play(audioUri)
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(contentColor, CircleShape),
                colors = IconButtonDefaults.iconButtonColors(containerColor = contentColor)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = backgroundColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Slider(
                    value = if (isSeeking) seekPosition else progress,
                    onValueChange = { value ->
                        isSeeking = true
                        seekPosition = value
                        progress = value
                        currentPositionMs = (value * effectiveDuration).toLong()
                    },
                    onValueChangeFinished = {
                        val targetMs = (seekPosition * effectiveDuration).toInt()
                        audioPlayer.seekTo(targetMs)
                        currentPositionMs = targetMs.toLong()
                        isSeeking = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = contentColor,
                        activeTrackColor = contentColor,
                        inactiveTrackColor = secondaryColor.copy(alpha = 0.3f)
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(contentColor, CircleShape)
                        )
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPositionMs),
                        style = MessagingTypography.label01,
                        color = secondaryColor,
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatDuration(effectiveDuration),
                        style = MessagingTypography.label01,
                        color = secondaryColor,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                onClick = {
                    if (isPlaying) {
                        audioPlayer.cycleSpeed()
                    }
                },
                shape = RoundedCornerShape(MessagingDimensions.corner4x),
                color = if (speed != 1f) contentColor.copy(alpha = 0.2f) else Color.Transparent
            ) {
                Text(
                    text = "${if (speed == speed.toLong().toFloat()) speed.toLong() else speed}x",
                    style = MessagingTypography.badge01,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, secs)
}
