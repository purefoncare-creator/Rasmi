package com.rasmi.purevon.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.presentation.theme.iOSBlue
import com.rasmi.purevon.util.AudioPlayer
import com.rasmi.purevon.util.ActiveAudioManager
import com.rasmi.purevon.util.audio.AudioWaveformGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Audio Message Bubble Component
 * iOS-style voice message with waveform visualization
 */
@Composable
fun AudioMessageBubble(
    audioUri: String,
    durationMs: Long,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    waveformSamples: List<Float>? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioPlayer = remember(audioUri) { AudioPlayer(context) }
    val waveformGenerator = remember { AudioWaveformGenerator(context) }
    
    // ✅ FIX #31: Use player's actual duration as fallback when durationMs=0
    val playerDuration by audioPlayer.duration.collectAsState()
    val effectiveDuration = if (durationMs > 0) durationMs else playerDuration.toLong()
    
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var waveform by remember { mutableStateOf(waveformSamples) }
    
    // Generate waveform if not provided
    LaunchedEffect(audioUri) {
        if (waveform == null) {
            waveformGenerator.generateWaveform(audioUri).fold(
                onSuccess = { samples -> waveform = samples },
                onFailure = { waveform = waveformGenerator.generateMockWaveform() }
            )
        }
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(100),
        label = "audio_progress"
    )
    
    // Update progress while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            audioPlayer.updatePosition()
            val position = audioPlayer.currentPosition.value
            currentPositionMs = position.toLong()
            progress = if (effectiveDuration > 0) {
                (position.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
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
            if (!playing && effectiveDuration > 0 && audioPlayer.currentPosition.value >= effectiveDuration.toInt() - 100) {
                progress = 0f
                currentPositionMs = 0
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            ActiveAudioManager.clearIfActive(audioPlayer)
            audioPlayer.release()
        }
    }
    
    val backgroundColor = when {
        isOutgoing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val secondaryColor = if (isOutgoing) {
        Color.White.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    
    Surface(
        modifier = modifier
            .widthIn(min = 200.dp, max = 280.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause Button
            IconButton(
                onClick = {
                    scope.launch {
                        if (isPlaying) {
                            audioPlayer.pause()
                        } else {
                            // ✅ Stop any other playing audio first
                            ActiveAudioManager.setActive(audioPlayer)
                            audioPlayer.play(audioUri)
                        }
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isOutgoing) Color.White.copy(alpha = 0.25f)
                        else iOSBlue.copy(alpha = 0.15f),
                        CircleShape
                    ),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Waveform & Duration
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Waveform visualization
                if (waveform != null) {
                    WaveformVisualization(
                        waveform = waveform!!,
                        progress = animatedProgress,
                        activeColor = contentColor,
                        inactiveColor = secondaryColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                    )
                } else {
                    // Loading placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(secondaryColor.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = contentColor,
                            strokeWidth = 2.dp
                        )
                    }
                }
                
                // Time display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(if (isPlaying) currentPositionMs else 0),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = formatDuration(effectiveDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Waveform Visualization Component
 */
@Composable
private fun WaveformVisualization(
    waveform: List<Float>,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        waveform.forEachIndexed { index, amplitude ->
            val barProgress = index.toFloat() / waveform.size.toFloat()
            val isActive = barProgress <= progress
            
            // Calculate bar height based on amplitude (0.0 to 1.0)
            val minHeight = 4.dp
            val maxHeight = 28.dp
            val barHeight = minHeight + (maxHeight - minHeight) * amplitude
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(maxHeight)
                    .clip(RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isActive) activeColor
                            else inactiveColor.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

/**
 * Format duration in mm:ss format
 */
private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}
