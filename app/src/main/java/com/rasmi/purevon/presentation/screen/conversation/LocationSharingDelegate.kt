package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Handles location fetching and formatting for location sharing.
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

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun handleShareLocation(hasPermission: Boolean) {
        if (!hasPermission) {
            _uiState.update { it.copy(error = context.getString(com.rasmi.purevon.R.string.msg_location_permission_required)) }
            return
        }

        // Prevent duplicate requests
        if (_uiState.value.isFetchingLocation) return

        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingLocation = true, error = null) }

            try {
                // 1. Check if GPS/Location Services are enabled
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                if (!isGpsEnabled && !isNetworkEnabled) {
                    _uiState.update { it.copy(
                        isFetchingLocation = false,
                        error = context.getString(com.rasmi.purevon.R.string.msg_location_service_disabled)
                    ) }
                    return@launch
                }

                // 2. Get current location with 15s timeout
                val cancellationTokenSource = CancellationTokenSource()

                // Permission is guaranteed by the hasPermission check at the top of handleShareLocation()
                @Suppress("MissingPermission")
                val location = withTimeoutOrNull(15_000L) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).await()
                }

                if (location == null) {
                    cancellationTokenSource.cancel()
                    _uiState.update { it.copy(
                        isFetchingLocation = false,
                        error = context.getString(com.rasmi.purevon.R.string.msg_location_failed)
                    ) }
                    return@launch
                }

                val latitude = location.latitude
                val longitude = location.longitude
                val accuracy = if (location.hasAccuracy()) " (±${location.accuracy.toInt()}m)" else ""

                // 3. Reverse geocode to get address
                val address = withContext(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            // Use callback API on Android 13+
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
                                    addr.adminArea?.let { if (isNotEmpty()) append("، "); append(it) }
                                }.takeIf { it.isNotBlank() }
                            } else null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Geocoding failed", e)
                        null
                    }
                }

                // 4. Format location message with geo: URI for mini-map detection
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

                // 5. Insert into message text
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
}
