package com.rasmi.purevon.util.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

/**
 * Generates audio waveform data from audio files
 * Used for visualizing voice messages
 */
class AudioWaveformGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioWaveformGenerator"
        private const val DEFAULT_SAMPLES = 50 // Number of bars to display
        private const val MAX_AMPLITUDE = 32767f // 16-bit audio max
    }
    
    /**
     * Generate waveform data from audio file
     * Returns list of normalized amplitudes (0.0 to 1.0)
     */
    suspend fun generateWaveform(
        audioUri: String,
        sampleCount: Int = DEFAULT_SAMPLES
    ): Result<List<Float>> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(audioUri)
            val extractor = MediaExtractor()
            
            // Set data source
            if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                }
            } else {
                extractor.setDataSource(audioUri)
            }
            
            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            
            if (audioTrackIndex < 0) {
                extractor.release()
                return@withContext Result.failure(Exception("No audio track found"))
            }
            
            extractor.selectTrack(audioTrackIndex)
            
            // Extract samples
            val samples = mutableListOf<Float>()
            val buffer = ByteBuffer.allocate(256 * 1024) // 256KB buffer
            var totalSamples = 0L
            
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                // Calculate RMS (Root Mean Square) for this chunk
                val rms = calculateRMS(buffer, sampleSize)
                samples.add(rms)
                totalSamples++
                
                extractor.advance()
                buffer.clear()
            }
            
            extractor.release()
            
            // Downsample to desired sample count
            val downsampled = if (samples.size > sampleCount) {
                downsample(samples, sampleCount)
            } else {
                // Upsample if we have fewer samples
                upsample(samples, sampleCount)
            }
            
            // Normalize to 0.0-1.0 range
            val normalized = normalize(downsampled)
            
            Log.d(TAG, "Generated waveform: ${normalized.size} samples from $totalSamples chunks")
            Result.success(normalized)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating waveform", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generate waveform from file path
     */
    suspend fun generateWaveformFromFile(
        file: File,
        sampleCount: Int = DEFAULT_SAMPLES
    ): Result<List<Float>> {
        return generateWaveform("file://${file.absolutePath}", sampleCount)
    }
    
    /**
     * Calculate RMS (Root Mean Square) amplitude from audio buffer
     */
    private fun calculateRMS(buffer: ByteBuffer, size: Int): Float {
        var sum = 0.0
        var count = 0
        
        // Assume 16-bit PCM audio
        for (i in 0 until size step 2) {
            if (i + 1 < size) {
                val sample = buffer.getShort(i).toInt()
                sum += sample * sample
                count++
            }
        }
        
        return if (count > 0) {
            kotlin.math.sqrt(sum / count).toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Downsample array to target size
     */
    private fun downsample(samples: List<Float>, targetSize: Int): List<Float> {
        if (samples.size <= targetSize) return samples
        
        val result = mutableListOf<Float>()
        val chunkSize = samples.size.toFloat() / targetSize
        
        for (i in 0 until targetSize) {
            val start = (i * chunkSize).toInt()
            val end = ((i + 1) * chunkSize).toInt().coerceAtMost(samples.size)
            
            // Take max amplitude in this chunk
            val maxInChunk = samples.subList(start, end).maxOrNull() ?: 0f
            result.add(maxInChunk)
        }
        
        return result
    }
    
    /**
     * Upsample array to target size
     */
    private fun upsample(samples: List<Float>, targetSize: Int): List<Float> {
        if (samples.isEmpty()) return List(targetSize) { 0.1f }
        if (samples.size >= targetSize) return samples
        
        val result = mutableListOf<Float>()
        val ratio = samples.size.toFloat() / targetSize
        
        for (i in 0 until targetSize) {
            val index = (i * ratio).toInt().coerceIn(0, samples.size - 1)
            result.add(samples[index])
        }
        
        return result
    }
    
    /**
     * Normalize samples to 0.0-1.0 range
     */
    private fun normalize(samples: List<Float>): List<Float> {
        if (samples.isEmpty()) return emptyList()
        
        val maxValue = samples.maxOrNull() ?: 1f
        if (maxValue == 0f) return List(samples.size) { 0.1f }
        
        return samples.map { sample ->
            val normalized = sample / maxValue
            // Ensure minimum visibility (at least 0.1)
            max(normalized, 0.1f).coerceIn(0f, 1f)
        }
    }
    
    /**
     * Generate mock waveform for preview (when actual generation fails)
     */
    fun generateMockWaveform(sampleCount: Int = DEFAULT_SAMPLES): List<Float> {
        return List(sampleCount) { index ->
            // Create varied heights for visual appeal
            val base = 0.3f + (index % 3) * 0.15f
            val variation = (index % 5) * 0.1f
            (base + variation).coerceIn(0.2f, 1f)
        }
    }
}
