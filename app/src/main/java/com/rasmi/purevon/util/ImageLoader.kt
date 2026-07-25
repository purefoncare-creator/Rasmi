package com.rasmi.purevon.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Advanced Image Loader with memory and performance optimizations
 * يستخدم ImageCacheManager للتخزين المؤقت
 */
object ImageLoader {
    
    /**
     * تحميل صورة مع التخزين المؤقت
     */
    suspend fun loadImage(
        context: Context,
        uri: Uri,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Try cache first
            val cacheKey = uri.toString()
            val cachedBitmap = getCachedBitmap(cacheKey)
            if (cachedBitmap != null) return@withContext cachedBitmap
            
            // Load from URI with efficient sampling
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                
                // Calculate sample size
                options.inSampleSize = calculateSampleSize(
                    options.outWidth, options.outHeight, maxWidth, maxHeight
                )
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565 // Memory efficient
                
                // Reopen stream for actual decoding
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)?.also { bitmap ->
                        // Cache the bitmap
                        cacheBitmap(cacheKey, bitmap)
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.e("ImageLoader", "Failed to load image", e)
            null
        }
    }
    
    /**
     * تحميل صورة مصغرة صغيرة للمعاينة
     */
    suspend fun loadThumbnail(
        context: Context,
        uri: Uri,
        size: Int = 200
    ): Bitmap? = loadImage(context, uri, size, size)
    
    // Simple in-memory cache (thread-safe)
    private val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    
    private fun getCachedBitmap(key: String): Bitmap? = bitmapCache[key]
    
    private fun cacheBitmap(key: String, bitmap: Bitmap) {
        if (bitmapCache.size > 50) {
            val oldKey = bitmapCache.keys.firstOrNull()
            if (oldKey != null) {
                bitmapCache.remove(oldKey)?.recycle()
            }
        }
        bitmapCache[key] = bitmap
    }
    
    /**
     * Calculate efficient sample size for memory optimization
     */
    private fun calculateSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
}

/**
 * Composable لاستخدام ImageLoader بسهولة
 */
@Composable
fun rememberImageBitmap(uri: Uri?, maxWidth: Int = 1024, maxHeight: Int = 1024): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(uri) {
        uri?.let {
            bitmap = ImageLoader.loadImage(context, it, maxWidth, maxHeight)
        }
    }
    
    return bitmap
}

/**
 * Composable لتحميل صورة مصغرة
 */
@Composable
fun rememberThumbnail(uri: Uri?, size: Int = 200): Bitmap? {
    val context = LocalContext.current
    var thumbnail by remember(uri) { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(uri) {
        uri?.let {
            thumbnail = ImageLoader.loadThumbnail(context, it, size)
        }
    }
    
    return thumbnail
}
