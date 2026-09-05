package com.rasmi.purevon.presentation.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.presentation.theme.*

@Composable
fun AudioRecordingBar(
    isRecording: Boolean,
    recordingDuration: Long,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        color = PurevonSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(44.dp)
                    .background(PurevonError.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel recording",
                    tint = PurevonError,
                    modifier = Modifier.size(24.dp)
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(if (isRecording) scale else 1f)
                        .clip(CircleShape)
                        .background(PurevonError.copy(alpha = if (isRecording) alpha else 1f))
                )

                Text(
                    text = formatRecordingTime(recordingDuration),
                    style = MessagingTypography.body01,
                    color = PurevonTextPrimary
                )

                AnimatedWaveformBars(
                    isRecording = isRecording,
                    modifier = Modifier.weight(1f)
                )
            }

            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(44.dp)
                    .background(PurevonPrimary, CircleShape),
                enabled = recordingDuration > 1000
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send voice message",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

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
        repeat(10) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "bar_$index")
            val barHeight by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400 + index * 50, easing = EaseInOut),
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
                        .background(PurevonPrimary.copy(alpha = 0.6f))
                )
            }
        }
    }
}

private fun formatRecordingTime(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, secs)
}
