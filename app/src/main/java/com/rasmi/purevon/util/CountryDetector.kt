package com.rasmi.purevon.util

import android.content.Context
import android.telephony.TelephonyManager
import com.rasmi.purevon.data.model.CountryPhoneRules
import com.rasmi.purevon.domain.model.CountryPhoneRule
import java.util.Locale

/**
 * Detects the user's country for phone number normalization
 * Uses multiple sources in order of priority:
 * 1. SIM card country
 * 2. Network provider country  
 * 3. Device locale
 * 4. User preference (saved in settings)
 * 5. Default fallback (Saudi Arabia)
 */
object CountryDetector {
    
    private const val PREF_KEY_DEFAULT_COUNTRY = "default_country_code"
    
    /**
     * Detect the most appropriate country for this device
     * 
     * @param context Application context
     * @param savedCountryCode Optional country code from DataStore (avoids SharedPreferences read)
     * @return CountryPhoneRule for the detected country
     */
    fun detectCountry(context: Context, savedCountryCode: String? = null): CountryPhoneRule {
        // 1. Check user preference first
        val saved = getSavedCountryCode(context, savedCountryCode)
        if (saved != null) {
            CountryPhoneRules.getByCountryCode(saved)?.let {
                return it
            }
        }
        
        // 2. Try SIM card country
        getSimCountryCode(context)?.let { simCountry ->
            CountryPhoneRules.getByCountryCode(simCountry)?.let {
                return it
            }
        }
        
        // 3. Try network provider country
        getNetworkCountryCode(context)?.let { networkCountry ->
            CountryPhoneRules.getByCountryCode(networkCountry)?.let {
                return it
            }
        }
        
        // 4. Try device locale
        getLocaleCountryCode()?.let { localeCountry ->
            CountryPhoneRules.getByCountryCode(localeCountry)?.let {
                return it
            }
        }
        
        // 5. Fallback to Saudi Arabia
        return CountryPhoneRules.getDefault()
    }
    
    /**
     * Get country code from SIM card
     */
    private fun getSimCountryCode(context: Context): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.simCountryIso?.uppercase()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get country code from network provider
     */
    private fun getNetworkCountryCode(context: Context): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.networkCountryIso?.uppercase()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get country code from device locale
     */
    private fun getLocaleCountryCode(): String? {
        return try {
            Locale.getDefault().country.uppercase().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get saved country code from preferences
     * @deprecated Use SettingsDataStore.defaultCountryCode instead.
     * Kept as migration-only fallback.
     */
    internal fun getSavedCountryCodeFromLegacyPrefs(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences("phone_settings", Context.MODE_PRIVATE)
            prefs.getString(PREF_KEY_DEFAULT_COUNTRY, null)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get saved country code.
     * Reads from savedCountryCode parameter (from DataStore) if provided,
     * otherwise falls back to legacy SharedPreferences.
     */
    private fun getSavedCountryCode(context: Context, savedCountryCode: String? = null): String? {
        // Prefer DataStore value if provided
        if (savedCountryCode != null) return savedCountryCode
        // Legacy fallback
        return getSavedCountryCodeFromLegacyPrefs(context)
    }
    
    /**
     * Save user's preferred country code
     */
    fun saveCountryCode(context: Context, countryCode: String) {
        try {
            val prefs = context.getSharedPreferences("phone_settings", Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_KEY_DEFAULT_COUNTRY, countryCode.uppercase()).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Clear saved country preference (will auto-detect again)
     */
    fun clearSavedCountry(context: Context) {
        try {
            val prefs = context.getSharedPreferences("phone_settings", Context.MODE_PRIVATE)
            prefs.edit().remove(PREF_KEY_DEFAULT_COUNTRY).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Get detailed information about country detection sources
     * Useful for debugging and showing to user
     */
    fun getDetectionInfo(context: Context): CountryDetectionInfo {
        return CountryDetectionInfo(
            simCountry = getSimCountryCode(context),
            networkCountry = getNetworkCountryCode(context),
            localeCountry = getLocaleCountryCode(),
            savedCountry = getSavedCountryCode(context),
            detectedCountry = detectCountry(context).countryCode
        )
    }
}

/**
 * Information about how the country was detected
 */
data class CountryDetectionInfo(
    val simCountry: String?,
    val networkCountry: String?,
    val localeCountry: String?,
    val savedCountry: String?,
    val detectedCountry: String
)
