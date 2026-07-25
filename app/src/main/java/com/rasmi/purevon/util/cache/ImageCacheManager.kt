package com.rasmi.purevon.util.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced Image Cache Manager
 * Inspired by QKSMS + Coil caching strategy
 * 
 * Two-tier caching:
 * 1. Memory Cache (LRU) - Fast access, limited size
 * 2. Disk Cache - Slower but persistent, larger size
 * 
 * Features:
 * - Automatic cache eviction
 * - Size-based limits
 * - Cache invalidation
 * - Background prefetching
 */
@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "ImageCache"
        
        // Memory cache: 1/8 of available memory
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val MEMORY_CACHE_SIZE = maxMemory / 8 // KB
        
        // Disk cache: 100 MB
        private const val DISK_CACHE_SIZE = 100 * 1024 * 1024L // 100 MB
        
        private const val DISK_CACHE_DIR = "image_cache"
    }
    
    // Memory cache (LRU)
    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            // Size in KB
            return bitmap.byteCount / 1024
        }
        
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted) {
                Log.d(TAG, "Memory cache evicted: $key")
            }
        }
    }
    
    // Disk cache directory
    private val diskCacheDir: File by lazy {
        File(context.cacheDir, DISK_CACHE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Get image from cache (memory first, then disk)
     */
    suspend fun getImage(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val key = generateKey(uri)
        
        // Try memory cache first
        memoryCache.get(key)?.let {
            Log.d(TAG, "✅ Memory cache HIT: $key")
            return@withContext it
        }
        
        // Try disk cache
        getDiskCachedImage(key)?.let { bitmap ->
            Log.d(TAG, "✅ Disk cache HIT: $key")
            // Put in memory cache for faster access next time
            memoryCache.put(key, bitmap)
            return@withContext bitmap
        }
        
        Log.d(TAG, "❌ Cache MISS: $key")
        return@withContext null
    }
    
    /**
     * Put image in cache (memory + disk)
     */
    suspend fun putImage(uri: Uri, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val key = generateKey(uri)
        
        // Save to memory cache
        memoryCache.put(key, bitmap)
        Log.d(TAG, "📝 Saved to memory cache: $key")
        
        // Save to disk cache
        saveToDiskCache(key, bitmap)
    }
    
    /**
     * Load and cache image from URI
     */
    suspend fun loadAndCacheImage(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        // Check cache first
        getImage(uri)?.let { return@withContext it }
        
        // Load from URI
        return@withContext try {
            val bitmap = loadBitmapFromUri(uri)
            bitmap?.let {
                putImage(uri, it)
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image from $uri", e)
            null
        }
    }
    
    /**
     * Prefetch images in background
     */
    suspend fun prefetchImages(uris: List<Uri>) = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 Prefetching ${uris.size} images...")
        
        uris.forEach { uri ->
            loadAndCacheImage(uri)
        }
        
        Log.d(TAG, "✅ Prefetch completed")
    }
    
    /**
     * Clear memory cache
     */
    fun clearMemoryCache() {
        memoryCache.evictAll()
        Log.d(TAG, "🗑️ Memory cache cleared")
    }
    
    /**
     * Clear disk cache
     */
    suspend fun clearDiskCache() = withContext(Dispatchers.IO) {
        diskCacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "🗑️ Disk cache cleared")
    }
    
    /**
     * Clear all caches
     */
    suspend fun clearAllCaches() {
        clearMemoryCache()
        clearDiskCache()
        Log.d(TAG, "🗑️ All caches cleared")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val memorySize = memoryCache.size()
        val memoryMaxSize = memoryCache.maxSize()
        val diskSize = calculateDiskCacheSize()
        
        return CacheStats(
            memoryCacheSize = memorySize,
            memoryCacheMaxSize = memoryMaxSize,
            memoryCacheHitRate = memorySize.toFloat() / memoryMaxSize.coerceAtLeast(1),
            diskCacheSize = diskSize,
            diskCacheMaxSize = DISK_CACHE_SIZE,
            diskCacheFileCount = diskCacheDir.listFiles()?.size ?: 0
        )
    }
    
    /**
     * Trim caches to free up space
     */
    suspend fun trimCaches(level: Int) = withContext(Dispatchers.IO) {
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // Trim 50% of memory cache
                memoryCache.trimToSize(memoryCache.maxSize() / 2)
                Log.d(TAG, "⚠️ Trimmed memory cache (RUNNING_LOW)")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Clear all memory cache
                clearMemoryCache()
                Log.d(TAG, "⚠️ Cleared memory cache (RUNNING_CRITICAL)")
            }
        }
    }
    
    /**
     * Generate cache key from URI
     */
    private fun generateKey(uri: Uri): String {
        val uriString = uri.toString()
        return try {
            MessageDigest.getInstance("MD5")
                .digest(uriString.toByteArray())
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            uriString.hashCode().toString()
        }
    }
    
    /**
     * Get image from disk cache
     */
    private fun getDiskCachedImage(key: String): Bitmap? {
        val file = File(diskCacheDir, key)
        return if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading disk cache", e)
                null
            }
        } else null
    }
    
    // ✅ FIX #45: Only trim cache every 10 saves instead of every save
    private var diskCacheSaveCount = 0
    private val TRIM_INTERVAL = 10

    /**
     * Save bitmap to disk cache
     */
    private fun saveToDiskCache(key: String, bitmap: Bitmap) {
        val file = File(diskCacheDir, key)
        
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Log.d(TAG, "💾 Saved to disk cache: $key")
            
            // ✅ FIX #45: Only check disk cache size periodically
            if (++diskCacheSaveCount % TRIM_INTERVAL == 0) {
                trimDiskCacheIfNeeded()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to disk cache", e)
        }
    }
    
    /**
     * Load bitmap from URI
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            // ✅ FIX #44: Use inSampleSize to prevent OOM on large images
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val maxDim = 1024
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap", e)
            null
        }
    }
    
    /**
     * Calculate disk cache size
     */
    private fun calculateDiskCacheSize(): Long {
        return diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    /**
     * Trim disk cache if size exceeds limit
     */
    private fun trimDiskCacheIfNeeded() {
        val currentSize = calculateDiskCacheSize()
        
        if (currentSize > DISK_CACHE_SIZE) {
            Log.d(TAG, "⚠️ Disk cache size exceeded: ${currentSize / 1024 / 1024} MB")
            
            // Delete oldest files until size is under limit
            val files = diskCacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
            
            var deletedSize = 0L
            for (file in files) {
                if (currentSize - deletedSize <= DISK_CACHE_SIZE) break
                
                deletedSize += file.length()
                file.delete()
            }
            
            Log.d(TAG, "🗑️ Trimmed ${deletedSize / 1024 / 1024} MB from disk cache")
        }
    }
    
    data class CacheStats(
        val memoryCacheSize: Int,
        val memoryCacheMaxSize: Int,
        val memoryCacheHitRate: Float,
        val diskCacheSize: Long,
        val diskCacheMaxSize: Long,
        val diskCacheFileCount: Int
    ) {
        val memoryCacheUsagePercent: Int get() = (memoryCacheSize * 100 / memoryCacheMaxSize.coerceAtLeast(1))
        val diskCacheUsagePercent: Int get() = ((diskCacheSize * 100) / diskCacheMaxSize.coerceAtLeast(1)).toInt()
        
        override fun toString(): String {
            return """
                Memory Cache: ${memoryCacheSize / 1024} MB / ${memoryCacheMaxSize / 1024} MB ($memoryCacheUsagePercent%)
                Disk Cache: ${diskCacheSize / 1024 / 1024} MB / ${diskCacheMaxSize / 1024 / 1024} MB ($diskCacheUsagePercent%)
                Disk Files: $diskCacheFileCount
                Hit Rate: ${(memoryCacheHitRate * 100).toInt()}%
            """.trimIndent()
        }
    }
}
