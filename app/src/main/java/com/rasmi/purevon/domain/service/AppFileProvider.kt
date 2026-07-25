package com.rasmi.purevon.domain.service

import java.io.File

/**
 * Abstraction for file system operations that require Android Context.
 * Implementations live in the data layer (e.g., AndroidAppFileProvider).
 */
interface AppFileProvider {
    
    /**
     * Get the external files directory for the app (maps to Context.getExternalFilesDir).
     */
    fun getExternalFilesDir(): File?
    
    /**
     * Resolve a File to a content:// URI string via FileProvider.
     * @return URI string suitable for IPC / Intent extras
     */
    fun getContentUri(file: File): String
}
