package com.rasmi.purevon.util.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Video utilities for thumbnail generation and metadata extraction
 */
class VideoUtils(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoUtils"
        private const val THUMBNAIL_WIDTH = 640
        private const val THUMBNAIL_HEIGHT = 480
    }
    
    /**
     * Generate thumbnail from video
     */
    suspend fun generateThumbnail(videoUri: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(videoUri)
            
            if (uri.scheme == "content") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(videoUri)
            }
            
            // Get frame at 1 second
            val bitmap = retriever.getFrameAtTime(
                1_000_000, // 1 second in microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            
            if (bitmap != null) {
                // Scale down if needed
                val scaledBitmap = if (bitmap.width > THUMBNAIL_WIDTH || bitmap.height > THUMBNAIL_HEIGHT) {
                    Bitmap.createScaledBitmap(bitmap, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true).also {
                        bitmap.recycle()
                    }
                } else {
                    bitmap
                }
                
                Log.d(TAG, "Generated thumbnail: ${scaledBitmap.width}x${scaledBitmap.height}")
                Result.success(scaledBitmap)
            } else {
                Result.failure(Exception("Failed to extract frame"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating thumbnail", e)
            Result.failure(e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing retriever", e)
            }
        }
    }
    
    /**
     * Save thumbnail to cache
     */
    suspend fun saveThumbnailToCache(bitmap: Bitmap, videoFileName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val thumbDir = File(context.cacheDir, "video_thumbnails")
            if (!thumbDir.exists()) {
                thumbDir.mkdirs()
            }
            
            val thumbFile = File(thumbDir, "thumb_$videoFileName.jpg")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            Log.d(TAG, "Saved thumbnail: ${thumbFile.absolutePath}")
            Result.success(thumbFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving thumbnail", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get video duration in milliseconds
     */
    suspend fun getVideoDuration(videoUri: String): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(videoUri)
            
            if (uri.scheme == "content") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(videoUri)
            }
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting duration", e)
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Get video dimensions
     */
    suspend fun getVideoDimensions(videoUri: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(videoUri)
            
            if (uri.scheme == "content") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(videoUri)
            }
            
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            
            if (width != null && height != null) {
                Pair(width, height)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dimensions", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Check if file is a video
     */
    fun isVideoFile(uri: String, mimeType: String?): Boolean {
        val lowerUri = uri.lowercase()
        return mimeType?.startsWith("video/") == true ||
                lowerUri.endsWith(".mp4") ||
                lowerUri.endsWith(".3gp") ||
                lowerUri.endsWith(".mkv") ||
                lowerUri.endsWith(".mov") ||
                lowerUri.endsWith(".avi") ||
                lowerUri.endsWith(".webm") ||
                mimeType?.contains("video") == true
    }
    
    /**
     * Format video duration
     */
    fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000).toInt()
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds % 60)
            else -> String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds % 60)
        }
    }
}
