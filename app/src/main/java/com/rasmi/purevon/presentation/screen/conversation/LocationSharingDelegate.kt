package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Handles location fetching and formatting for location sharing.
 * Uses Android's built-in LocationManager (no Google Play Services required).
 * Extracted from ConversationViewModel to reduce class size.
 */
internal class LocationSharingDelegate(
    private val context: Context,
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ConversationViewModel"
    }

    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun handleShareLocation(hasPermission: Boolean) {
        if (!hasPermission) {
            _uiState.update { it.copy(error = context.getString(com.rasmi.purevon.R.string.msg_location_permission_required)) }
            return
        }

        if (_uiState.value.isFetchingLocation) return

        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingLocation = true, error = null) }

            try {
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                if (!isGpsEnabled && !isNetworkEnabled) {
                    _uiState.update { it.copy(
                        isFetchingLocation = false,
                        error = context.getString(com.rasmi.purevon.R.string.msg_location_service_disabled)
                    ) }
                    return@launch
                }

                @Suppress("MissingPermission")
                val location = withTimeoutOrNull(15_000L) {
                    getCurrentLocationFromManager()
                }

                if (location == null) {
                    _uiState.update { it.copy(
                        isFetchingLocation = false,
                        error = context.getString(com.rasmi.purevon.R.string.msg_location_failed)
                    ) }
                    return@launch
                }

                val latitude = location.latitude
                val longitude = location.longitude
                val accuracy = if (location.hasAccuracy()) " (±${location.accuracy.toInt()}m)" else ""

                val address = withContext(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                                    if (addresses.isNotEmpty()) {
                                        val addr = addresses[0]
                                        val result = buildString {
                                            addr.thoroughfare?.let { append(it) }
                                            addr.subLocality?.let { if (isNotEmpty()) append("، "); append(it) }
                                            addr.locality?.let { if (isNotEmpty()) append("، "); append(it) }
                                            addr.adminArea?.let { if (isNotEmpty()) append("، "); append(it) }
                                        }.takeIf { it.isNotBlank() }
                                        cont.resumeWith(Result.success(result))
                                    } else {
                                        cont.resumeWith(Result.success(null))
                                    }
                                }
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                buildString {
                                    addr.thoroughfare?.let { append(it) }
                                    addr.subLocality?.let { if (isNotEmpty()) append("، "); append(it) }
                                    addr.locality?.let { if (isNotEmpty()) append("، "); append(it) }
                                    addr.adminArea?.let { if (isNotEmpty()) append("؛ "); append(it) }
                                }.takeIf { it.isNotBlank() }
                            } else null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Geocoding failed", e)
                        null
                    }
                }

                val locationText = buildString {
                    if (address != null) {
                        append("📍 $address$accuracy\n")
                    } else {
                        val myLocationLabel = context.getString(com.rasmi.purevon.R.string.msg_my_location)
                        append("$myLocationLabel$accuracy\n")
                    }
                    append("geo:$latitude,$longitude\n")
                    append("https://maps.google.com/?q=$latitude,$longitude")
                }

                val currentText = _uiState.value.messageText
                val newText = if (currentText.isBlank()) locationText
                              else "$currentText\n\n$locationText"

                _uiState.update { it.copy(
                    messageText = newText,
                    isFetchingLocation = false
                ) }

                Log.d(TAG, "📍 Location shared successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error getting location", e)
                _uiState.update { it.copy(
                    isFetchingLocation = false,
                    error = context.getString(com.rasmi.purevon.R.string.msg_location_error)
                ) }
            }
        }
    }

    /**
     * Get current location using Android LocationManager (no Play Services).
     * Tries GPS first, falls back to network provider.
     */
    @Suppress("MissingPermission")
    private suspend fun getCurrentLocationFromManager(): Location? {
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return suspendCancellableCoroutine { cont ->
            var resumed = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!resumed) {
                        resumed = true
                        locationManager.removeUpdates(this)
                        cont.resume(location)
                    }
                }
                @Deprecated("Deprecated in API")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    if (!resumed) {
                        resumed = true
                        cont.resume(null)
                    }
                }
            }

            locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())

            cont.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        }
    }
}
