package com.rasmi.purevon.data.service

import android.content.Context
import androidx.core.content.FileProvider
import com.rasmi.purevon.domain.service.AppFileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [AppFileProvider].
 * Provides file system access and FileProvider URI resolution.
 */
@Singleton
class AndroidAppFileProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : AppFileProvider {
    
    override fun getExternalFilesDir(): File? {
        return context.getExternalFilesDir(null)
    }
    
    override fun getContentUri(file: File): String {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        ).toString()
    }
}
