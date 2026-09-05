package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.util.AudioPlayer
import com.rasmi.purevon.util.ActiveAudioManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerComponent(
    audioUri: String,
    durationMs: Long,
    modifier: Modifier = Modifier,
    isOutgoing: Boolean = false,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val audioPlayer = remember(audioUri) { AudioPlayer(context) }

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    val speed by audioPlayer.playbackSpeed.collectAsStateWithLifecycle()
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            audioPlayer.updatePosition()
            val position = audioPlayer.currentPosition.value
            currentPositionMs = position.toLong()
            if (!isSeeking) {
                progress = if (durationMs > 0) {
                    position.toFloat() / durationMs.toFloat()
                } else 0f
            }
            delay(100)
        }
    }

    LaunchedEffect(audioUri) {
        audioPlayer.isPlaying.collect { playing ->
            isPlaying = playing
            if (!playing && durationMs > 0 && audioPlayer.currentPosition.value >= durationMs.toInt() - 100) {
                progress = 0f
                currentPositionMs = 0
            }
        }
    }

    DisposableEffect(audioUri) {
        onDispose { audioPlayer.release() }
    }

    val backgroundColor = if (isOutgoing) PurevonBubbleSent else PurevonBubbleReceived
    val contentColor = if (isOutgoing) PurevonBubbleSentText else PurevonBubbleReceivedText
    val secondaryColor = if (isOutgoing) PurevonBubbleSentText.copy(alpha = 0.7f) else PurevonTextSecondary

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = RoundedCornerShape(MessagingDimensions.corner10x)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
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
                        .size(if (compact) 32.dp else 36.dp)
                        .background(contentColor, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = contentColor)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.msg_cd_pause) else stringResource(R.string.msg_cd_play),
                        tint = backgroundColor,
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                    )
                }

                Slider(
                    value = if (isSeeking) seekPosition else progress,
                    onValueChange = { value ->
                        isSeeking = true
                        seekPosition = value
                        progress = value
                        currentPositionMs = (value * durationMs).toLong()
                    },
                    onValueChangeFinished = {
                        val targetMs = (seekPosition * durationMs).toInt()
                        audioPlayer.seekTo(targetMs)
                        currentPositionMs = targetMs.toLong()
                        isSeeking = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = contentColor,
                        activeTrackColor = contentColor,
                        inactiveTrackColor = secondaryColor.copy(alpha = 0.3f)
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(if (compact) 10.dp else 14.dp)
                                .background(contentColor, CircleShape)
                        )
                    }
                )

                Surface(
                    onClick = { if (isPlaying) audioPlayer.cycleSpeed() },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPositionMs),
                    style = MessagingTypography.label01,
                    color = secondaryColor,
                    fontSize = if (compact) 9.sp else 10.sp
                )
                Text(
                    text = formatDuration(durationMs),
                    style = MessagingTypography.label01,
                    color = secondaryColor,
                    fontSize = if (compact) 9.sp else 10.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPreviewPlayer(
    audioUri: String,
    durationMs: Long,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioPlayer = remember(audioUri) { AudioPlayer(context) }

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(audioUri) {
        audioPlayer.isPlaying.collect { playing ->
            isPlaying = playing
            if (!playing && audioPlayer.currentPosition.value <= 0) {
                progress = 0f
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            audioPlayer.updatePosition()
            val position = audioPlayer.currentPosition.value
            currentPositionMs = position.toLong()
            if (!isSeeking) {
                progress = if (durationMs > 0) {
                    position.toFloat() / durationMs.toFloat()
                } else 0f
            }
            delay(100)
        }
    }

    DisposableEffect(audioUri) {
        onDispose { audioPlayer.release() }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PurevonSurfaceMuted,
        shape = RoundedCornerShape(MessagingDimensions.corner12x)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) audioPlayer.pause() else audioPlayer.play(audioUri)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(PurevonPrimary, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = PurevonPrimary)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.msg_cd_pause) else stringResource(R.string.msg_cd_play),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Slider(
                    value = if (isSeeking) seekPosition else progress,
                    onValueChange = { value ->
                        isSeeking = true
                        seekPosition = value
                        progress = value
                        currentPositionMs = (value * durationMs).toLong()
                    },
                    onValueChangeFinished = {
                        val targetMs = (seekPosition * durationMs).toInt()
                        audioPlayer.seekTo(targetMs)
                        currentPositionMs = targetMs.toLong()
                        isSeeking = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = PurevonPrimary,
                        activeTrackColor = PurevonPrimary,
                        inactiveTrackColor = PurevonTextSecondary.copy(alpha = 0.3f)
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(PurevonPrimary, CircleShape)
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(currentPositionMs),
                    style = MessagingTypography.label01,
                    color = PurevonTextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = formatDuration(durationMs),
                    style = MessagingTypography.label01,
                    color = PurevonTextSecondary,
                    fontSize = 10.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        audioPlayer.stop()
                        onCancel()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(PurevonError.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = PurevonError,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        audioPlayer.stop()
                        onSend()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(PurevonPrimary, CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.msg_cd_send),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
