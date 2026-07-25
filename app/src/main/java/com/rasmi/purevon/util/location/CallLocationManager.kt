package com.rasmi.purevon.util.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manager for getting call location
 */
@Singleton
class CallLocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private val geocoder = Geocoder(context, Locale.getDefault())
    
    /**
     * Get current location
     */
    suspend fun getCurrentLocation(): Result<CallLocation> = withContext(Dispatchers.IO) {
        try {
            // Check permission
            if (!hasLocationPermission()) {
                return@withContext Result.failure(SecurityException("Location permission not granted"))
            }
            
            val location = getCurrentLocationInternal()
                ?: return@withContext Result.failure(Exception("Unable to get location"))
            
            // Get address
            val address = getAddressFromLocation(location)
            
            Result.success(
                CallLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = address,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get current location internal
     */
    private suspend fun getCurrentLocationInternal(): Location? =
        suspendCancellableCoroutine { continuation ->
            try {
                if (!hasLocationPermission()) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                
                val cancellationTokenSource = CancellationTokenSource()
                
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
                
                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }
            } catch (e: SecurityException) {
                continuation.resume(null)
            }
        }
    
    /**
     * Get address from location coordinates
     */
    private suspend fun getAddressFromLocation(location: Location): String? =
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    buildString {
                        address.thoroughfare?.let { append(it).append(", ") }
                        address.locality?.let { append(it).append(", ") }
                        address.adminArea?.let { append(it) }
                    }.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    
    /**
     * Check if location permission is granted
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Format location for display
     */
    fun formatLocation(callLocation: CallLocation): String {
        return callLocation.address ?: String.format(
            "%.6f, %.6f",
            callLocation.latitude,
            callLocation.longitude
        )
    }
}

/**
 * Call location data
 */
data class CallLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val timestamp: Long
)
