package com.rasmi.purevon.util.mms

import android.content.Context
import android.os.Build
import android.telephony.CarrierConfigManager
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * MMS Configuration Helper
 * 
 * Handles MMS configuration and settings
 * Based on quik-master MmsConfig implementation
 * - APN configuration
 * - Carrier settings
 * - MMS parameters
 */
object MmsConfiguration {
    
    private const val TAG = "MmsConfiguration"
    
    data class MmsConfig(
        val maxMessageSize: Int,
        val maxImageWidth: Int,
        val maxImageHeight: Int,
        val userAgent: String,
        val uaProfUrl: String
    )
    
    /**
     * Get MMS configuration for current carrier
     * Uses CarrierConfigManager for accurate carrier-specific settings
     */
    fun getMmsConfig(context: Context, subscriptionId: Int = SmsManager.getDefaultSmsSubscriptionId()): MmsConfig {
        return try {
            // ✅ Use CarrierConfigManager to get accurate carrier settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getCarrierConfig(context, subscriptionId)
            } else {
                // Fallback for older Android versions
                getDefaultMmsConfig()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS config", e)
            getDefaultMmsConfig()
        }
    }
    
    /**
     * ✅ NEW: Get MMS config from CarrierConfigManager
     * This provides accurate carrier-specific settings like quik-master
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getCarrierConfig(context: Context, subscriptionId: Int): MmsConfig {
        return try {
            val carrierConfigManager = context.getSystemService(Context.CARRIER_CONFIG_SERVICE) as? CarrierConfigManager
            
            val configBundle = if (subscriptionId >= 0) {
                carrierConfigManager?.getConfigForSubId(subscriptionId)
            } else {
                carrierConfigManager?.config
            }
            
            if (configBundle != null) {
                // Respect the carrier-reported limit.
                // We enforce a floor of 100 KB to avoid nonsensical values, and a ceiling
                // of 2 MB to prevent runaway large messages. Most modern carriers allow
                // 300 KB – 1.2 MB; some strict US legacy carriers cap at 300 KB.
                val rawMaxSize = configBundle.getInt(
                    CarrierConfigManager.KEY_MMS_MAX_MESSAGE_SIZE_INT,
                    1024 * 1024 // Default 1 MB when carrier config absent
                )
                val maxMessageSize = rawMaxSize.coerceIn(100 * 1024, 2 * 1024 * 1024)
                
                val maxImageWidth = maxOf(configBundle.getInt(
                    CarrierConfigManager.KEY_MMS_MAX_IMAGE_WIDTH_INT,
                    1280
                ), 1024)
                
                val maxImageHeight = maxOf(configBundle.getInt(
                    CarrierConfigManager.KEY_MMS_MAX_IMAGE_HEIGHT_INT,
                    1280
                ), 1024)
                
                val userAgent = configBundle.getString(
                    CarrierConfigManager.KEY_MMS_USER_AGENT_STRING,
                    "Android-Mms/2.0"
                ) ?: "Android-Mms/2.0"
                
                val uaProfUrl = configBundle.getString(
                    CarrierConfigManager.KEY_MMS_UA_PROF_URL_STRING,
                    ""
                ) ?: ""
                
                Log.d(TAG, "✅ Carrier config loaded:")
                Log.d(TAG, "   Max size: $maxMessageSize bytes")
                Log.d(TAG, "   Max image: ${maxImageWidth}x$maxImageHeight")
                Log.d(TAG, "   User agent: $userAgent")
                
                MmsConfig(
                    maxMessageSize = maxMessageSize,
                    maxImageWidth = maxImageWidth,
                    maxImageHeight = maxImageHeight,
                    userAgent = userAgent,
                    uaProfUrl = uaProfUrl
                )
            } else {
                Log.w(TAG, "CarrierConfigManager returned null bundle, using defaults")
                getDefaultMmsConfig()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting carrier config", e)
            getDefaultMmsConfig()
        }
    }
    
    /**
     * Get default MMS configuration
     * Used as fallback when carrier config is unavailable
     */
    private fun getDefaultMmsConfig(): MmsConfig {
        return MmsConfig(
            maxMessageSize = 1024 * 1024, // ✅ FIX: 1MB — modern carriers support this (was 300KB)
            maxImageWidth = 1280,         // ✅ FIX: Higher resolution (was 1024)
            maxImageHeight = 1280,        // ✅ FIX: Higher resolution (was 1024)
            userAgent = "Android-Mms/2.0",
            uaProfUrl = ""
        )
    }
}
