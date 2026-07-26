package com.rasmi.purevon.util.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manager for getting call location.
 * Uses Android's built-in LocationManager (no Google Play Services required).
 */
@Singleton
class CallLocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val geocoder = Geocoder(context, Locale.getDefault())

    /**
     * Get current location
     */
    suspend fun getCurrentLocation(): Result<CallLocation> = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermission()) {
                return@withContext Result.failure(SecurityException("Location permission not granted"))
            }

            val location = getCurrentLocationInternal()
                ?: return@withContext Result.failure(Exception("Unable to get location"))

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
     * Get current location using Android LocationManager.
     * Tries GPS first, then falls back to network provider.
     */
    private suspend fun getCurrentLocationInternal(): Location? =
        suspendCancellableCoroutine { continuation ->
            if (!hasLocationPermission()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
            }

            var resumed = false
            val callback = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!resumed) {
                        resumed = true
                        locationManager.removeUpdates(this)
                        continuation.resume(location)
                    }
                }
                @Deprecated("Deprecated in API")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    if (!resumed) {
                        resumed = true
                        continuation.resume(null)
                    }
                }
            }

            locationManager.requestLocationUpdates(
                provider,
                0L,
                0f,
                callback,
                Looper.getMainLooper()
            )

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(callback)
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
