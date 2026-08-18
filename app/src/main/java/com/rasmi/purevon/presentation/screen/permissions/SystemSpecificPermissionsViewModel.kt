package com.rasmi.purevon.presentation.screen.permissions

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.util.DeviceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SystemSpecificPermissionsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak")
    private val context: Context = application.applicationContext

    private val _uiState = MutableStateFlow(SystemSpecificPermissionsUiState())
    val uiState: StateFlow<SystemSpecificPermissionsUiState> = _uiState.asStateFlow()

    init {
        loadDeviceInfo()
    }

    private fun loadDeviceInfo() {
        viewModelScope.launch {
            val manufacturer = DeviceUtils.getManufacturer()
            val requiredPermissions = DeviceUtils.getRequiredPermissions()

            _uiState.value = _uiState.value.copy(
                manufacturer = manufacturer,
                manufacturerNameResId = DeviceUtils.getManufacturerNameResId(),
                requiredPermissions = requiredPermissions,
                needsSpecialPermissions = DeviceUtils.needsSpecialPermissions()
            )
        }
    }

    fun onEvent(event: SystemSpecificPermissionsEvent) {
        when (event) {
            is SystemSpecificPermissionsEvent.OpenPermissionSettings -> {
                DeviceUtils.openSpecialPermissionSettings(context, event.permission)
            }
            SystemSpecificPermissionsEvent.SkipForNow -> {
                // User chose to skip - save preference
                _uiState.value = _uiState.value.copy(skipped = true)
            }
            SystemSpecificPermissionsEvent.Continue -> {
                _uiState.value = _uiState.value.copy(completed = true)
            }
        }
    }

    fun refresh() {
        loadDeviceInfo()
    }
}

data class SystemSpecificPermissionsUiState(
    val manufacturer: DeviceUtils.DeviceManufacturer = DeviceUtils.DeviceManufacturer.STOCK,
    val manufacturerNameResId: Int = 0,
    val requiredPermissions: List<DeviceUtils.SpecialPermission> = emptyList(),
    val needsSpecialPermissions: Boolean = false,
    val skipped: Boolean = false,
    val completed: Boolean = false
)

sealed class SystemSpecificPermissionsEvent {
    data class OpenPermissionSettings(val permission: DeviceUtils.SpecialPermission) : SystemSpecificPermissionsEvent()
    data object SkipForNow : SystemSpecificPermissionsEvent()
    data object Continue : SystemSpecificPermissionsEvent()
}
