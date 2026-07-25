package com.rasmi.purevon.presentation.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.presentation.theme.iOSBlue

/**
 * Audio Recording UI Component
 * Shows recording state with waveform animation and timer
 */
@Composable
fun AudioRecordingBar(
    isRecording: Boolean,
    recordingDuration: Long,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing animation for recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel button
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        Color.Red.copy(alpha = 0.1f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel recording",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Recording info
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing red dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(if (isRecording) scale else 1f)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = if (isRecording) alpha else 1f))
                )
                
                // Timer
                Text(
                    text = formatRecordingTime(recordingDuration),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Animated waveform bars
                AnimatedWaveformBars(
                    isRecording = isRecording,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Send button
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(44.dp)
                    .background(iOSBlue, CircleShape),
                enabled = recordingDuration > 1000 // At least 1 second
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send voice message",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Animated waveform bars during recording
 */
@Composable
private fun AnimatedWaveformBars(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Create 10 bars with different animation delays
        repeat(10) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "bar_$index")
            
            val barHeight by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + index * 50,
                        easing = EaseInOut
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_height_$index"
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(if (isRecording) barHeight else 0.2f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(iOSBlue.copy(alpha = 0.6f))
                )
            }
        }
    }
}

/**
 * Format recording time as mm:ss
 */
private fun formatRecordingTime(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}

/**
 * Audio Preview Component (shows recorded audio before sending)
 */
@Composable
fun AudioPreview(
    audioFilePath: String,
    duration: Long,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mic icon
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = iOSBlue,
                modifier = Modifier
                    .size(40.dp)
                    .background(iOSBlue.copy(alpha = 0.1f), CircleShape)
                    .padding(8.dp)
            )
            
            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "تسجيل صوتي",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatRecordingTime(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
