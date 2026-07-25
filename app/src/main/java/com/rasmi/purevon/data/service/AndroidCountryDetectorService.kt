package com.rasmi.purevon.data.service

import android.content.Context
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.model.CountryPhoneRule
import com.rasmi.purevon.domain.service.CountryDetectorService
import com.rasmi.purevon.util.CountryDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCountryDetectorService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) : CountryDetectorService {
    
    // Cache the country code to avoid runBlocking on every call
    @Volatile
    private var cachedCountryCode: String? = null
    
    init {
        // Eagerly load the cached value (runs once on Singleton creation)
        try {
            cachedCountryCode = runBlocking { settingsDataStore.defaultCountryCode.first() }
        } catch (_: Exception) { }
    }
    
    override fun detectCountry(): CountryPhoneRule {
        return CountryDetector.detectCountry(context, cachedCountryCode)
    }
    
    /** Call this when the user changes their country setting */
    suspend fun refreshCache() {
        cachedCountryCode = try {
            settingsDataStore.defaultCountryCode.first()
        } catch (_: Exception) { null }
    }
}
