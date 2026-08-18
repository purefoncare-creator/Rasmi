package com.rasmi.purevon.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.iOSBlue
import com.rasmi.purevon.util.AudioPlayer
import kotlinx.coroutines.delay

/**
 * Audio Player UI Component for voice messages
 * Used in both message input preview and message bubbles
 */
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
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(100),
        label = "progress"
    )
    
    // Update progress while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            audioPlayer.updatePosition()
            val position = audioPlayer.currentPosition.value
            currentPositionMs = position.toLong()
            progress = if (durationMs > 0) {
                position.toFloat() / durationMs.toFloat()
            } else {
                0f
            }
            delay(100)
        }
    }
    
    // Observe player state
    LaunchedEffect(audioUri) {
        audioPlayer.isPlaying.collect { playing ->
            isPlaying = playing
            // ✅ FIX #45: Only reset on completion, not on pause
            if (!playing && durationMs > 0 && audioPlayer.currentPosition.value >= durationMs.toInt() - 100) {
                progress = 0f
                currentPositionMs = 0
            }
        }
    }
    
    // Cleanup — re-runs when audioUri changes so old player is released
    DisposableEffect(audioUri) {
        onDispose {
            audioPlayer.release()
        }
    }
    
    val backgroundColor = if (isOutgoing) {
        Color.White.copy(alpha = 0.2f)
    } else {
        iOSBlue.copy(alpha = 0.1f)
    }
    
    val contentColor = if (isOutgoing) Color.White else iOSBlue
    val secondaryColor = if (isOutgoing) Color.White.copy(alpha = 0.7f) else iOSBlue.copy(alpha = 0.7f)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause Button
            IconButton(
                onClick = {
                    if (isPlaying) {
                        audioPlayer.pause()
                    } else {
                        audioPlayer.play(audioUri)
                    }
                },
                modifier = Modifier
                    .size(if (compact) 32.dp else 40.dp)
                    .background(contentColor, CircleShape),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = contentColor
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.msg_cd_pause) else stringResource(R.string.msg_cd_play),
                    tint = if (isOutgoing) iOSBlue else Color.White,
                    modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                )
            }
            
            // Waveform / Progress
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Progress bar (simulating waveform)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 20.dp else 28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(secondaryColor.copy(alpha = 0.2f))
                ) {
                    // Waveform visualization (simplified as bars)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compact) 20.dp else 28.dp)
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val barCount = if (compact) 20 else 30
                        repeat(barCount) { index ->
                            val barProgress = index.toFloat() / barCount.toFloat()
                            val barHeight = (8 + (index % 5) * 3).dp
                            val isActive = barProgress <= animatedProgress
                            
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(
                                        if (isActive) contentColor
                                        else secondaryColor.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }
                
                // Duration text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor,
                        fontSize = if (compact) 9.sp else 10.sp
                    )
                    Text(
                        text = formatDuration(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor,
                        fontSize = if (compact) 9.sp else 10.sp
                    )
                }
            }
            
            if (!compact) {
                // Mic icon indicator
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = secondaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Simplified audio player for message input preview
 */
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
    
    // Observe player state
    LaunchedEffect(audioUri) {
        audioPlayer.isPlaying.collect { playing ->
            isPlaying = playing
            // ✅ FIX #45: Only reset on completion
            if (!playing && audioPlayer.currentPosition.value <= 0) {
                progress = 0f
            }
        }
    }
    
    // Update progress
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            audioPlayer.updatePosition()
            val position = audioPlayer.currentPosition.value
            progress = if (durationMs > 0) {
                position.toFloat() / durationMs.toFloat()
            } else {
                0f
            }
            delay(100)
        }
    }
    
    // Cleanup — re-runs when audioUri changes so old player is released
    DisposableEffect(audioUri) {
        onDispose {
            audioPlayer.release()
        }
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = iOSBlue.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause Button
            IconButton(
                onClick = {
                    if (isPlaying) {
                        audioPlayer.pause()
                    } else {
                        audioPlayer.play(audioUri)
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(iOSBlue, CircleShape),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = iOSBlue
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.msg_cd_pause) else stringResource(R.string.msg_cd_play),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Progress and info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "رسالة صوتية",
                    style = MaterialTheme.typography.bodyMedium,
                    color = iOSBlue,
                    fontWeight = FontWeight.Bold
                )
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = iOSBlue,
                    trackColor = iOSBlue.copy(alpha = 0.2f),
                )
                
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSBlue.copy(alpha = 0.7f)
                )
            }
            
            // Cancel button
            IconButton(
                onClick = {
                    audioPlayer.stop()
                    onCancel()
                },
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.Red.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = Color.Red,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // Send button
            IconButton(
                onClick = {
                    audioPlayer.stop()
                    onSend()
                },
                modifier = Modifier
                    .size(32.dp)
                    .background(iOSBlue, CircleShape)
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

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
