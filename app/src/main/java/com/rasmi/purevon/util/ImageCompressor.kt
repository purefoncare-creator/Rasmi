package com.rasmi.purevon.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image Compressor - يضغط الصور الكبيرة قبل الإرسال
 * يحافظ على الجودة مع تقليل الحجم
 */
@Singleton
class ImageCompressor @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ImageCompressor"
        
        private const val MAX_WIDTH = 1920
        private const val MAX_HEIGHT = 1920
        
        private const val MAX_FILE_SIZE = 2 * 1024 * 1024
        
        private const val INITIAL_QUALITY = 90
        private const val MIN_QUALITY = 60
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE)) else -1L
            } ?: -1L
        } catch (_: Exception) { -1L }
    }
    
    /**
     * ✅ MMS-specific compression: compress to fit within carrier's MMS size limit
     * while preserving good image quality.
     *
     * Modern carriers (STC, Mobily, Zain, etc.) typically support 1MB+ MMS.
     * We target 600KB by default to leave room for PDU headers, SMIL, text parts.
     *
     * ✅ FIX #38: Preserves PNG transparency when the source is PNG/WebP-lossless.
     *
     * @param maxSizeBytes Maximum target size (default 600KB)
     * @param maxWidth Maximum pixel width for MMS (default 1280)
     * @param maxHeight Maximum pixel height for MMS (default 1280)
     */
    suspend fun compressImageForMms(
        imageUri: Uri,
        maxSizeBytes: Int = 600 * 1024, // 600KB — safe for modern carriers
        maxWidth: Int = 1280,
        maxHeight: Int = 1280
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for: $imageUri")
                return@withContext null
            }
            inputStream.close()
            val originalSize = getFileSizeFromUri(imageUri)
            
            // ✅ FIX #38: Detect source format to preserve PNG transparency
            val mimeType = context.contentResolver.getType(imageUri)
            val isPng = mimeType == "image/png" || mimeType == "image/webp"
            val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val fileExt = if (isPng) "png" else "jpg"
            
            Log.w(TAG, "📱 MMS image compression: original=${originalSize / 1024}KB, target=${maxSizeBytes / 1024}KB, format=$fileExt, maxDim=${maxWidth}x${maxHeight}")
            
            // Decode bitmap with MMS dimensions
            val bitmap = decodeBitmapForMms(imageUri, maxWidth, maxHeight) ?: run {
                Log.e(TAG, "Failed to decode bitmap for MMS")
                return@withContext null
            }
            
            // Rotate if needed
            val rotatedBitmap = rotateImageIfRequired(imageUri, bitmap)
            
            // Resize to MMS dimensions
            val resizedBitmap = resizeBitmapToFit(rotatedBitmap, maxWidth, maxHeight)
            
            Log.w(TAG, "📱 MMS bitmap: ${resizedBitmap.width}x${resizedBitmap.height}")
            
            // Compress iteratively until it fits — start high quality, reduce gradually
            val tempFile = File(context.cacheDir, "mms_img_${System.currentTimeMillis()}.$fileExt")
            var quality = 92 // ✅ FIX: Start with high quality (was 85)
            var fileSize: Long
            val minQuality = 55 // ✅ FIX: Don't go below 55 (was 30) — 30 causes blurry images
            
            do {
                FileOutputStream(tempFile).use { out ->
                    resizedBitmap.compress(compressFormat, quality, out)
                }
                fileSize = tempFile.length()
                
                if (fileSize > maxSizeBytes && quality > minQuality) {
                    quality -= 5 // ✅ FIX: Smaller steps (was 10) for finer quality control
                    Log.w(TAG, "📱 MMS still ${fileSize / 1024}KB > ${maxSizeBytes / 1024}KB, reducing quality to $quality")
                } else {
                    break
                }
            } while (quality >= minQuality)
            
            // ✅ FIX: Safe bitmap cleanup — track which are unique before recycling
            val bitmapsToRecycle = mutableSetOf<Bitmap>()
            if (bitmap !== resizedBitmap) bitmapsToRecycle.add(bitmap)
            if (rotatedBitmap !== bitmap && rotatedBitmap !== resizedBitmap) bitmapsToRecycle.add(rotatedBitmap)
            // Keep resizedBitmap alive for the log line below, then recycle
            val finalWidth = resizedBitmap.width
            val finalHeight = resizedBitmap.height
            bitmapsToRecycle.forEach { it.recycle() }
            resizedBitmap.recycle()
            
            Log.w(TAG, "✅ MMS image compressed: ${originalSize / 1024}KB → ${fileSize / 1024}KB (quality=$quality, ${finalWidth}x${finalHeight})")
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image for MMS", e)
            null
        }
    }
    
    /**
     * Decode bitmap sized for MMS dimensions
     */
    private fun decodeBitmapForMms(uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            val sampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            
            val actualInputStream = context.contentResolver.openInputStream(uri) ?: return null
            val actualOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeStream(actualInputStream, null, actualOptions)
            actualInputStream.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap for MMS", e)
            null
        }
    }
    
    /**
     * Resize bitmap to fit within max dimensions
     */
    private fun resizeBitmapToFit(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
        val ratio = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * ضغط صورة إذا كانت أكبر من الحد المسموح
     * @return URI للصورة المضغوطة أو null إذا فشل الضغط
     */
    suspend fun compressImageIfNeeded(imageUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for: $imageUri")
                return@withContext null
            }
            inputStream.close()
            
            val originalSize = getFileSizeFromUri(imageUri)
            
            Log.w(TAG, "🖼️ Original image size: ${originalSize / 1024} KB")
            
            // إذا كانت الصورة صغيرة بالفعل، لا حاجة للضغط
            if (originalSize <= MAX_FILE_SIZE) {
                Log.w(TAG, "🖼️ Image is already small enough (${originalSize / 1024} KB <= ${MAX_FILE_SIZE / 1024} KB), no compression needed")
                return@withContext imageUri
            }
            
            // قراءة الصورة
            val bitmap = decodeBitmap(imageUri) ?: run {
                Log.e(TAG, "Failed to decode bitmap")
                return@withContext null
            }
            
            // تدوير الصورة حسب EXIF
            val rotatedBitmap = rotateImageIfRequired(imageUri, bitmap)
            
            // تصغير الأبعاد إذا لزم الأمر
            val resizedBitmap = resizeBitmap(rotatedBitmap)
            
            // ✅ FIX #38: Detect source format to preserve PNG transparency
            val sourceMime = context.contentResolver.getType(imageUri)
            val format = if (sourceMime == "image/png" || sourceMime == "image/webp") {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            
            // ضغط الصورة وحفظها
            val compressedFile = compressAndSave(resizedBitmap, format)
            
            // تنظيف
            if (bitmap != rotatedBitmap) bitmap.recycle()
            if (resizedBitmap != rotatedBitmap) resizedBitmap.recycle()
            rotatedBitmap.recycle()
            
            if (compressedFile != null) {
                val compressedSize = compressedFile.length()
                Log.w(TAG, "✅ Compressed: ${originalSize / 1024} KB -> ${compressedSize / 1024} KB")
                Log.w(TAG, "   Saved: ${((originalSize - compressedSize) * 100 / originalSize)}%")
                Uri.fromFile(compressedFile)
            } else {
                Log.e(TAG, "Failed to compress image")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            null
        }
    }
    
    /**
     * فك تشفير Bitmap مع تصغير أولي للذاكرة
     */
    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            
            // قراءة أبعاد الصورة أولاً بدون تحميلها
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // حساب معامل التصغير
            val sampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT)
            
            // قراءة الصورة مع التصغير
            val actualInputStream = context.contentResolver.openInputStream(uri) ?: return null
            val actualOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeStream(actualInputStream, null, actualOptions)
            actualInputStream.close()
            
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap", e)
            null
        }
    }
    
    /**
     * حساب معامل التصغير
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
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
    
    /**
     * تدوير الصورة حسب EXIF data
     */
    private fun rotateImageIfRequired(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()
            
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating image", e)
            bitmap
        }
    }
    
    /**
     * تدوير Bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * تصغير أبعاد الصورة
     */
    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            return bitmap
        }
        
        val ratio = minOf(
            MAX_WIDTH.toFloat() / width,
            MAX_HEIGHT.toFloat() / height
        )
        
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        Log.d(TAG, "Resizing: ${width}x${height} -> ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * ضغط وحفظ الصورة
     * ✅ FIX #38: Detect source format — use PNG for transparent images
     */
    private fun compressAndSave(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): File? {
        return try {
            val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
            val tempFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.$ext")
            
            var quality = INITIAL_QUALITY
            var fileSize: Long
            
            // ضغط تدريجي حتى نصل للحجم المطلوب
            do {
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(format, quality, out)
                }
                fileSize = tempFile.length()
                
                if (fileSize > MAX_FILE_SIZE && quality > MIN_QUALITY) {
                    quality -= 10
                    Log.d(TAG, "File still large ($fileSize bytes), reducing quality to $quality")
                } else {
                    break
                }
            } while (quality >= MIN_QUALITY)
            
            tempFile
        } catch (e: IOException) {
            Log.e(TAG, "Error saving compressed image", e)
            null
        }
    }
    
    /**
     * ✅ FIX #44: Clean up old temp files from previous compression/send operations
     * Call this after successful message send.
     */
    fun cleanupTempFiles(maxAgeMs: Long = 30 * 60 * 1000L) {
        try {
            val now = System.currentTimeMillis()
            val cacheDir = context.cacheDir
            val prefixes = listOf("mms_img_", "compressed_", "camera_", "mms_video_")
            cacheDir.listFiles()?.forEach { file ->
                if (prefixes.any { prefix -> file.name.startsWith(prefix) } &&
                    now - file.lastModified() > maxAgeMs) {
                    if (file.delete()) {
                        Log.d(TAG, "🗑️ Cleaned up temp file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
        }
    }
}
