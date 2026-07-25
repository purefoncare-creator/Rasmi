package com.rasmi.purevon.util.image

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for Coil image loading with optimized caching
 */
@Singleton
class ImageLoaderProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Provides configured ImageLoader with memory and disk caching
     */
    fun provideImageLoader(): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(context)
                    // Set max size to 25% of app memory
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    // Set max size to 250MB
                    .maxSizeBytes(250 * 1024 * 1024)
                    .build()
            }
            // Enable network caching
            .okHttpClient {
                OkHttpClient.Builder()
                    .cache(
                        okhttp3.Cache(
                            directory = context.cacheDir.resolve("http_cache"),
                            maxSize = 50 * 1024 * 1024 // 50MB
                        )
                    )
                    .build()
            }
            // Enable crossfade animation
            .crossfade(true)
            // Enable placeholder and error handling
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            // Add debug logging (remove in production)
            .logger(DebugLogger())
            .build()
    }
    
    companion object {
        /**
         * Clear all image caches
         */
        @coil.annotation.ExperimentalCoilApi
        fun clearCache(imageLoader: ImageLoader) {
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        }
        
        /**
         * Clear memory cache only
         */
        fun clearMemoryCache(imageLoader: ImageLoader) {
            imageLoader.memoryCache?.clear()
        }
    }
}
