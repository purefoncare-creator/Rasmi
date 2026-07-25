package com.rasmi.purevon.presentation.screen.permissions

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.util.DefaultAppManager
import com.rasmi.purevon.util.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionRequestViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val defaultAppManager: DefaultAppManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PermissionRequestUiState())
    val uiState: StateFlow<PermissionRequestUiState> = _uiState.asStateFlow()
    
    init {
        checkPermissions()
    }
    
    fun onEvent(event: PermissionRequestUiEvent) {
        when (event) {
            is PermissionRequestUiEvent.RequestDefaultApps -> {
                // This will be handled by the screen using DefaultAppManager
                checkPermissions()
            }
            is PermissionRequestUiEvent.RefreshStatus -> {
                checkPermissions()
            }
        }
    }
    
    private fun checkPermissions() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDefaultDialer = defaultAppManager.isDefaultDialer(),
                    isDefaultSms = defaultAppManager.isDefaultSmsApp(),
                    isDefaultAppsSet = defaultAppManager.areBothDefaultAppsSet(),
                    isOverlayGranted = PermissionManager.hasOverlayPermission(context),
                    isBatteryOptimizationIgnored = PermissionManager.isIgnoringBatteryOptimizations(context),
                    isFullScreenIntentGranted = checkFullScreenIntentPermission()
                )
            }
        }
    }
    
    /**
     * Check if full screen intent permission is granted
     * Required on Android 14+ for showing calls on lock screen
     */
    private fun checkFullScreenIntentPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.canUseFullScreenIntent() ?: true
        } else {
            // Permission not required for older versions
            true
        }
    }
}

data class PermissionRequestUiState(
    val isDefaultDialer: Boolean = false,
    val isDefaultSms: Boolean = false,
    val isDefaultAppsSet: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isFullScreenIntentGranted: Boolean = true
)

sealed class PermissionRequestUiEvent {
    data object RequestDefaultApps : PermissionRequestUiEvent()
    data object RefreshStatus : PermissionRequestUiEvent()
}
