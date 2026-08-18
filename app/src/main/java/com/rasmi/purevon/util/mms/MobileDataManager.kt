package com.rasmi.purevon.util.mms

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Manages mobile data state checks for MMS sending.
 */
object MobileDataManager {
    
    private const val TAG = "MobileDataManager"
    
    /**
     * Check if mobile data is enabled by inspecting the active network's capabilities.
     * Uses official NetworkCapabilities API (API 23+) — no reflection required.
     *
     * @return true if an active cellular connection exists, false otherwise, null if unknown
     */
    fun isMobileDataEnabled(context: Context): Boolean? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null

        return try {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking mobile data status", e)
            null
        }
    }
    
    /**
     * Check if device is in airplane mode
     */
    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking airplane mode", e)
            false
        }
    }
}
