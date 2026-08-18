package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Waveform Visualization for Audio Messages
 * WhatsApp/Telegram style animated waveform
 */
@Composable
fun AudioWaveform(
    amplitudes: List<Float>,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
    playedColor: Color = MaterialTheme.colorScheme.primary,
    unplayedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    maxBarHeight: Float = 40f
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(maxBarHeight.dp)
    ) {
        val width = size.width
        val height = size.height
        val barCount = amplitudes.size.coerceAtLeast(1)
        val barWidth = (width / barCount) * 0.6f
        val spacing = (width / barCount) * 0.4f
        
        amplitudes.forEachIndexed { index, amplitude ->
            val barHeight = (amplitude * height).coerceIn(4f, height)
            val x = index * (barWidth + spacing) + spacing / 2
            val y = (height - barHeight) / 2
            
            // Determine color based on progress
            val playProgress = progress * barCount
            val color = if (index < playProgress) playedColor else unplayedColor
            
            // Draw rounded rectangle bar
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

/**
 * Generate sample waveform data from audio amplitude samples
 */
fun generateWaveformData(audioData: FloatArray, targetBars: Int = 40): List<Float> {
    if (audioData.isEmpty()) {
        // Return default waveform
        return List(targetBars) { 0.3f + (it % 3) * 0.2f }
    }
    
    val samplesPerBar = audioData.size / targetBars
    return List(targetBars) { barIndex ->
        val startIndex = barIndex * samplesPerBar
        val endIndex = (startIndex + samplesPerBar).coerceAtMost(audioData.size)
        
        // Calculate RMS (Root Mean Square) for this segment
        val segment = audioData.sliceArray(startIndex until endIndex)
        val rms = kotlin.math.sqrt(segment.map { it * it }.average()).toFloat()
        
        // Normalize to 0-1 range
        (rms * 2).coerceIn(0.1f, 1f)
    }
}

/**
 * Simple waveform for messages without analyzed data
 */
@Composable
fun SimpleAudioWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    isOutgoing: Boolean = false
) {
    // Generate simple random-ish waveform
    val amplitudes = remember {
        List(35) { index ->
            // Create a wave-like pattern
            0.3f + kotlin.math.sin(index * 0.3).toFloat() * 0.3f + (index % 3) * 0.15f
        }
    }
    
    AudioWaveform(
        amplitudes = amplitudes,
        progress = if (isPlaying) progress else 0f,
        modifier = modifier,
        playedColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary,
        unplayedColor = if (isOutgoing) 
            Color.White.copy(alpha = 0.4f) 
        else 
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    )
}
