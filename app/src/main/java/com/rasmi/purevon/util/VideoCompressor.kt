package com.rasmi.purevon.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaCodec
import android.net.Uri
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Video Compressor - يضغط الفيديو لتناسب حدود MMS
 * يستخدم MediaCodec لإعادة ترميز الفيديو بحجم أصغر
 */
@Singleton
@SuppressLint("WrongConstant")
class VideoCompressor @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "VideoCompressor"
        
        // ✅ MMS video size limit — modern carriers support 1MB+
        // Reserve room for text, SMIL, PDU headers
        const val MAX_MMS_VIDEO_SIZE = 900 * 1024L // 900KB
        
        // Max duration for MMS video (30 seconds)
        private const val MAX_DURATION_MS = 30_000L
        
        // ✅ Target encoding: H.264/AVC at good quality within MMS limits
        private const val TARGET_WIDTH = 480
        private const val TARGET_HEIGHT = 360
        private const val TARGET_BITRATE = 400_000 // 400kbps — good quality at small size
        private const val TARGET_FRAME_RATE = 24
        private const val KEY_FRAME_INTERVAL = 2 // seconds between keyframes
        
        // Timeout for MediaCodec operations
        private const val CODEC_TIMEOUT_US = 10_000L // 10ms
    }
    
    data class VideoInfo(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val fileSize: Long,
        val mimeType: String?
    )
    
    /**
     * الحصول على معلومات الفيديو
     */
    fun getVideoInfo(videoUri: Uri): VideoInfo? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            
            // ✅ FIX: Use openAssetFileDescriptor for accurate file size
            val fileSize = try {
                context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { afd ->
                    afd.length.takeIf { it >= 0 } ?: 0L
                } ?: 0L
            } catch (e2: Exception) {
                context.contentResolver.openInputStream(videoUri)?.use {
                    it.available().toLong()
                } ?: 0L
            }
            
            retriever.release()
            
            VideoInfo(duration, width, height, fileSize, mimeType)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video info", e)
            null
        }
    }
    
    /**
     * ضغط الفيديو ليناسب MMS
     * 
     * Strategy:
     * 1. If video is small enough → send as-is
     * 2. If trimming duration is enough → trim only (fast, lossless)
     * 3. Re-encode with H.264 at lower resolution/bitrate (slower but effective)
     * 4. If still too large → try again with even lower quality
     * 
     * @param maxSize Maximum video size in bytes
     * @return URI of compressed video, or null if all attempts fail
     */
    suspend fun compressVideoForMms(
        videoUri: Uri, 
        maxSize: Long = MAX_MMS_VIDEO_SIZE
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val videoInfo = getVideoInfo(videoUri)
            if (videoInfo == null) {
                Log.e(TAG, "Cannot get video info")
                return@withContext null
            }
            
            Log.w(TAG, "📹 Video info: ${videoInfo.width}x${videoInfo.height}, " +
                "${videoInfo.durationMs}ms, ${videoInfo.fileSize / 1024}KB, mime=${videoInfo.mimeType}")
            
            // If video is already small enough, no compression needed
            if (videoInfo.fileSize <= maxSize) {
                Log.w(TAG, "✅ Video already within limit (${videoInfo.fileSize / 1024}KB ≤ ${maxSize / 1024}KB)")
                return@withContext videoUri
            }
            
            // Cap duration to MAX_DURATION_MS
            val targetDuration = minOf(videoInfo.durationMs, MAX_DURATION_MS)
            
            // Step 1: Try trim-only (mux without re-encoding) if duration cut is enough
            if (videoInfo.durationMs > targetDuration) {
                val estimatedTrimmedSize = (videoInfo.fileSize.toDouble() * targetDuration / videoInfo.durationMs).toLong()
                if (estimatedTrimmedSize <= maxSize) {
                    Log.w(TAG, "📹 Trying trim-only (estimated: ${estimatedTrimmedSize / 1024}KB)")
                    val trimmedFile = File(context.cacheDir, "trimmed_video_${System.currentTimeMillis()}.mp4")
                    if (trimVideo(videoUri, trimmedFile, targetDuration) && trimmedFile.length() in 1..maxSize) {
                        Log.w(TAG, "✅ Trim successful: ${trimmedFile.length() / 1024}KB")
                        return@withContext Uri.fromFile(trimmedFile)
                    }
                    trimmedFile.delete()
                }
            }
            
            // Step 2: Re-encode with H.264 at target quality
            val effectiveDuration = minOf(targetDuration, videoInfo.durationMs)
            val targetBitrate = calculateTargetBitrate(effectiveDuration, maxSize)
            Log.w(TAG, "📹 Re-encoding: ${TARGET_WIDTH}x${TARGET_HEIGHT} @ ${targetBitrate / 1000}kbps, dur=${effectiveDuration}ms")
            
            val outputFile = File(context.cacheDir, "compressed_video_${System.currentTimeMillis()}.mp4")
            val success = transcodeVideo(videoUri, outputFile, effectiveDuration, TARGET_WIDTH, TARGET_HEIGHT, targetBitrate)
            
            if (success && outputFile.exists() && outputFile.length() in 1..maxSize) {
                Log.w(TAG, "✅ Video re-encoded: ${videoInfo.fileSize / 1024}KB → ${outputFile.length() / 1024}KB")
                return@withContext Uri.fromFile(outputFile)
            }
            
            // Step 3: Try lower quality if still too large
            if (success && outputFile.exists() && outputFile.length() > maxSize) {
                Log.w(TAG, "⚠️ Still ${outputFile.length() / 1024}KB > ${maxSize / 1024}KB, trying lower quality")
                outputFile.delete()
                val lqBitrate = (targetBitrate * 0.5).toInt().coerceAtLeast(100_000)
                val lqFile = File(context.cacheDir, "compressed_video_lq_${System.currentTimeMillis()}.mp4")
                val lqSuccess = transcodeVideo(videoUri, lqFile, effectiveDuration, 320, 240, lqBitrate)
                if (lqSuccess && lqFile.length() in 1..maxSize) {
                    Log.w(TAG, "✅ Low-quality encode: ${lqFile.length() / 1024}KB")
                    return@withContext Uri.fromFile(lqFile)
                }
                lqFile.delete()
            } else {
                outputFile.delete()
            }
            
            Log.w(TAG, "❌ All compression attempts failed for video")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing video", e)
            return@withContext null
        }
    }
    
    /**
     * Calculate target video bitrate to fit within size limit
     */
    private fun calculateTargetBitrate(durationMs: Long, maxSizeBytes: Long): Int {
        // Reserve 15% for audio + container overhead
        val videoBudget = (maxSizeBytes * 0.85).toLong()
        val durationSec = durationMs / 1000.0
        if (durationSec <= 0) return TARGET_BITRATE
        val bitrate = ((videoBudget * 8) / durationSec).toInt()
        return bitrate.coerceIn(100_000, TARGET_BITRATE) // 100kbps min, TARGET max
    }
    
    /**
     * Trim video to max duration without re-encoding (fast, lossless)
     * ✅ FIX #30: Now includes audio track
     */
    private fun trimVideo(inputUri: Uri, outputFile: File, maxDurationMs: Long): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var fd: android.os.ParcelFileDescriptor? = null
        
        try {
            extractor = MediaExtractor()
            fd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return false
            extractor.setDataSource(fd.fileDescriptor)
            
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }
            if (videoTrackIndex < 0 || videoFormat == null) return false
            
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = if (audioTrackIndex >= 0 && audioFormat != null) {
                muxer.addTrack(audioFormat)
            } else -1
            muxer.start()
            
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            val maxDurationUs = maxDurationMs * 1000
            var totalBytes = 0L
            
            // Mux video track
            extractor.selectTrack(videoTrackIndex)
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0 || extractor.sampleTime > maxDurationUs) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                totalBytes += sampleSize
                extractor.advance()
            }
            
            // ✅ FIX #30: Mux audio track too
            if (audioTrackIndex >= 0 && muxerAudioTrack >= 0) {
                extractor.unselectTrack(videoTrackIndex)
                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0 || extractor.sampleTime > maxDurationUs) break
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                    totalBytes += sampleSize
                    extractor.advance()
                }
            }
            
            muxer.stop(); muxer.release(); muxer = null
            extractor.release(); extractor = null
            fd.close(); fd = null
            Log.w(TAG, "📹 Trimmed: $totalBytes bytes (audio=${audioTrackIndex >= 0})")
            return totalBytes > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming video", e)
            return false
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            try { fd?.close() } catch (_: Exception) {}
        }
    }
    
    /**
     * Re-encode video using Surface-based decode→encode pipeline (H.264/AVC)
     * Decoder renders to Surface → Encoder reads from Surface → Muxer writes output
     * ✅ FIX #30: Audio track is now passed through (copy without re-encoding)
     */
    private fun transcodeVideo(
        inputUri: Uri,
        outputFile: File,
        maxDurationMs: Long,
        targetWidth: Int,
        targetHeight: Int,
        targetBitrate: Int
    ): Boolean {
        var extractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var encoderInputSurface: Surface? = null
        var fd: android.os.ParcelFileDescriptor? = null
        var audioFd: android.os.ParcelFileDescriptor? = null
        
        try {
            extractor = MediaExtractor()
            fd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return false
            extractor.setDataSource(fd.fileDescriptor)
            
            // Find video track
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    inputFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }
            if (videoTrackIndex < 0 || inputFormat == null) {
                Log.e(TAG, "No video track found")
                return false
            }
            extractor.selectTrack(videoTrackIndex)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            Log.w(TAG, "📹 Input: $inputMime, ${inputFormat.getInteger(MediaFormat.KEY_WIDTH)}x${inputFormat.getInteger(MediaFormat.KEY_HEIGHT)}, hasAudio=${audioTrackIndex >= 0}")
            
            // Configure H.264/AVC encoder
            val encoderMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val encoderFormat = MediaFormat.createVideoFormat(encoderMime, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_INTERVAL)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
            
            encoder = try {
                MediaCodec.createEncoderByType(encoderMime)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot create H.264 encoder", e)
                return false
            }
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderInputSurface = encoder.createInputSurface()
            encoder.start()
            
            // Configure decoder → output to encoder's input Surface
            decoder = try {
                MediaCodec.createDecoderByType(inputMime)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot create decoder for $inputMime", e)
                return false
            }
            decoder.configure(inputFormat, encoderInputSurface, null, 0)
            decoder.start()
            
            // Create output muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            val maxDurationUs = maxDurationMs * 1000
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            var muxerVideoTrackIndex = -1
            var muxerAudioTrackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()
            var framesEncoded = 0
            
            Log.w(TAG, "📹 Starting transcode loop...")
            
            while (!encoderDone) {
                // 1. Feed compressed data to decoder
                if (!inputDone) {
                    val inputBufIdx = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufIdx >= 0) {
                        val inputBuf = decoder.getInputBuffer(inputBufIdx)!!
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0 || extractor.sampleTime > maxDurationUs) {
                            decoder.queueInputBuffer(inputBufIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                            Log.w(TAG, "📹 Input EOS")
                        } else {
                            decoder.queueInputBuffer(inputBufIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                
                // 2. Drain decoder → renders to encoder's input Surface
                if (!decoderDone) {
                    val decoderBufIdx = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                    if (decoderBufIdx >= 0) {
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        // render=true pushes frame to Surface (skip EOS frame)
                        decoder.releaseOutputBuffer(decoderBufIdx, !eos)
                        if (eos) {
                            encoder.signalEndOfInputStream()
                            decoderDone = true
                            Log.w(TAG, "📹 Decoder EOS → signaling encoder")
                        }
                    }
                }
                
                // 3. Drain encoder output → muxer
                val encoderBufIdx = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    encoderBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            val newFormat = encoder.outputFormat
                            Log.w(TAG, "📹 Encoder format: $newFormat")
                            muxerVideoTrackIndex = muxer.addTrack(newFormat)
                            // ✅ FIX #30: Add audio track to muxer before starting
                            if (audioTrackIndex >= 0 && audioFormat != null) {
                                muxerAudioTrackIndex = muxer.addTrack(audioFormat)
                            }
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    encoderBufIdx >= 0 -> {
                        val encoderBuf = encoder.getOutputBuffer(encoderBufIdx)!!
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (muxerStarted && bufferInfo.size > 0 && !isConfig) {
                            muxer.writeSampleData(muxerVideoTrackIndex, encoderBuf, bufferInfo)
                            framesEncoded++
                        }
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(encoderBufIdx, false)
                        if (eos) {
                            encoderDone = true
                        }
                    }
                }
            }
            
            Log.w(TAG, "📹 Transcode complete: $framesEncoded frames")
            
            // ✅ FIX #30: Copy audio track (passthrough — no re-encoding)
            if (audioTrackIndex >= 0 && muxerAudioTrackIndex >= 0 && muxerStarted) {
                audioExtractor = MediaExtractor()
                audioFd = context.contentResolver.openFileDescriptor(inputUri, "r")
                if (audioFd != null) {
                    audioExtractor!!.setDataSource(audioFd!!.fileDescriptor)
                    audioExtractor!!.selectTrack(audioTrackIndex)
                    audioExtractor!!.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    
                    val audioBuf = java.nio.ByteBuffer.allocate(256 * 1024)
                    val audioInfo = MediaCodec.BufferInfo()
                    var audioSamples = 0
                    
                    while (true) {
                        val sampleSize = audioExtractor!!.readSampleData(audioBuf, 0)
                        if (sampleSize < 0 || audioExtractor!!.sampleTime > maxDurationUs) break
                        audioInfo.offset = 0
                        audioInfo.size = sampleSize
                        audioInfo.presentationTimeUs = audioExtractor!!.sampleTime
                        audioInfo.flags = audioExtractor!!.sampleFlags
                        muxer.writeSampleData(muxerAudioTrackIndex, audioBuf, audioInfo)
                        audioSamples++
                        audioExtractor!!.advance()
                    }
                    Log.w(TAG, "📹 Audio passthrough: $audioSamples samples")
                    audioExtractor!!.release(); audioExtractor = null
                    audioFd!!.close(); audioFd = null
                }
            }
            
            // Clean up in order
            decoder.stop(); decoder.release(); decoder = null
            encoder.stop(); encoder.release(); encoder = null
            encoderInputSurface.release(); encoderInputSurface = null
            muxer.stop(); muxer.release(); muxer = null
            extractor.release(); extractor = null
            fd.close(); fd = null
            
            return framesEncoded > 0
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in video transcoding", e)
            return false
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { encoderInputSurface?.release() } catch (_: Exception) {}
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { fd?.close() } catch (_: Exception) {}
            try { audioFd?.close() } catch (_: Exception) {}
        }
    }
    
    /**
     * Find a supported encoder for the given MIME type
     */
    private fun findEncoder(mimeType: String): String? {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    return info.name
                }
            }
        }
        return null
    }
    
    /**
     * Check if video needs compression for MMS
     */
    fun needsCompression(videoUri: Uri, maxSize: Long = MAX_MMS_VIDEO_SIZE): Boolean {
        return try {
            val fileSize = context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { afd ->
                afd.length.takeIf { it >= 0 } ?: 0L
            } ?: 0L
            fileSize > maxSize
        } catch (e: Exception) {
            true
        }
    }
}
