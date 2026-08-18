package com.rasmi.purevon.util

import android.content.Context
import java.io.File

/**
 * Directory for temp files that must be exposed to other apps/processes via
 * FileProvider (camera captures, MMS send/receive temp files, shared vCards).
 * Kept isolated from the rest of the cache dir so provider_paths.xml can grant
 * access to only this subdirectory instead of the entire app cache.
 */
fun Context.sharedCacheDir(): File =
    File(cacheDir, "shared").apply { mkdirs() }

/** A file inside [sharedCacheDir] ready to be handed to [androidx.core.content.FileProvider]. */
fun Context.newSharedCacheFile(fileName: String): File =
    File(sharedCacheDir(), fileName)
