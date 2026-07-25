package com.rasmi.purevon

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.StrictMode
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.util.DisclosurePreferences
import com.rasmi.purevon.util.SystemDataObserver
import com.rasmi.purevon.worker.SyncWorkManager
import dagger.hilt.android.HiltAndroidApp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.rasmi.purevon.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Application class with Hilt Dependency Injection and WorkManager
 * 
 * Features:
 * - Hilt dependency injection
 * - WorkManager configuration
 * - LeakCanary memory leak detection (debug builds only)
 * - StrictMode for development builds
 * - Activity lifecycle tracking
 */
@HiltAndroidApp
class PurevonApp : Application(), Configuration.Provider {
    
    companion object {
        private const val TAG = "PurevonApp"
    }
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var syncWorkManager: SyncWorkManager
    
    @Inject
    lateinit var systemDataObserver: SystemDataObserver
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    @Inject
    lateinit var messageRepository: MessageRepository
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()
    
    override fun onCreate() {
        super.onCreate()
        
        if (BuildConfig.DEBUG) {
            setupDebugTools()
        }
        
        // ✅ Fix NN: Migrate country code from legacy SharedPreferences to DataStore
        migrateCountryPreference()
        
        // Initialize app components safely (observer + sync workers when permitted)
        startBackgroundServices()
        
        // ✅ تمت إزالة Eager Loading - المزامنة ستحدث في MessagesViewModel فقط
    }
    
    /**
     * Setup debug tools (LeakCanary, StrictMode, Activity tracking)
     */
    private fun setupDebugTools() {
        // Configure LeakCanary (only available in debug builds)
        // Using reflection since LeakCanary is only in debugImplementation
        try {
            val leakCanary = Class.forName("leakcanary.LeakCanary")
            // ✅ FIX #62: Simply show the launcher icon without trying to call copy()
            // The copy() method signature varies across LeakCanary versions
            leakCanary.getMethod("showLeakDisplayActivityLauncherIcon", Boolean::class.java)
                .invoke(null, true)
            
            Log.d(TAG, "LeakCanary configured successfully")
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "LeakCanary not available (expected in release builds)")
        } catch (e: Exception) {
            Log.d(TAG, "LeakCanary configuration failed: ${e.message}")
        }
        
        // Setup Activity lifecycle tracking
        com.rasmi.purevon.util.ActivityLifecycleTracker.register(this)
        
        // Enable StrictMode
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        
        Log.d(TAG, "Debug tools initialized: LeakCanary, StrictMode, Activity tracking")
    }
    
    /**
     * ✅ Fix NN: Migrate country code from legacy SharedPreferences to DataStore.
     * Runs once — after migration, the old SharedPreferences key is removed.
     */
    private fun migrateCountryPreference() {
        applicationScope.launch {
            try {
                val legacyCode = com.rasmi.purevon.util.CountryDetector.getSavedCountryCodeFromLegacyPrefs(this@PurevonApp)
                if (legacyCode != null) {
                    settingsDataStore.setDefaultCountryCode(legacyCode)
                    // Remove from legacy SharedPreferences
                    com.rasmi.purevon.util.CountryDetector.clearSavedCountry(this@PurevonApp)
                    Log.d(TAG, "Country code migrated to DataStore: $legacyCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error migrating country preference", e)
            }
        }
    }
    
    /**
     * Initialize application components with proper error handling
     */
    private fun startBackgroundServices() {
        try {
            if (!DisclosurePreferences.isAccepted(this)) {
                Log.d(TAG, "Data disclosure not accepted; background data access is disabled")
                return
            }

            // Only start observing and syncing if we have required permissions
            if (hasRequiredPermissions()) {
                Log.d(TAG, "Required permissions granted, starting services")
                
                // Start observing system data changes
                try {
                    systemDataObserver.startObserving()
                    Log.d(TAG, "System data observer started successfully")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception when starting system data observer", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting system data observer", e)
                }
                
                // Schedule background sync workers
                try {
                    syncWorkManager.scheduleAllSyncWorkers()
                    Log.d(TAG, "Background sync workers scheduled successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error scheduling sync workers", e)
                }
            } else {
                Log.d(TAG, "Required permissions not granted, services will start when permissions are granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during app initialization", e)
            // Don't crash the app, continue with degraded functionality
        }
    }
    
    /**
     * Check if required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_SMS
        )
        
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Called when permissions are granted to start services
     */
    fun onPermissionsGranted() {
        Log.d(TAG, "Permissions granted, starting services")
        
        if (DisclosurePreferences.isAccepted(this) && hasRequiredPermissions()) {
            try {
                systemDataObserver.startObserving()
                syncWorkManager.scheduleAllSyncWorkers()
                Log.d(TAG, "Services started successfully after permission grant")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting services after permission grant", e)
            }
        }
    }
    
    /**
     * Called when permissions are revoked to stop services
     */
    fun onPermissionsRevoked() {
        Log.d(TAG, "Permissions revoked, stopping services")
        
        try {
            systemDataObserver.stopObserving()
            Log.d(TAG, "Services stopped successfully after permission revocation")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping services after permission revocation", e)
        }
    }
}
