package com.rasmi.purevon.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.rasmi.purevon.R

/**
 * Device-specific utilities for detecting manufacturer and ROM
 */
object DeviceUtils {
    private const val TAG = "DeviceUtils"

    enum class DeviceManufacturer {
        XIAOMI,      // MIUI
        SAMSUNG,     // One UI
        OPPO,        // ColorOS
        REALME,      // Realme UI
        HUAWEI,      // EMUI
        VIVO,        // Funtouch OS
        ONEPLUS,     // OxygenOS
        STOCK        // Stock Android
    }

    /**
     * Get current device manufacturer
     */
    fun getManufacturer(): DeviceManufacturer {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        return when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            manufacturer.contains("redmi") || brand.contains("redmi") ||
            manufacturer.contains("poco") || brand.contains("poco") -> DeviceManufacturer.XIAOMI
            
            manufacturer.contains("samsung") || brand.contains("samsung") -> DeviceManufacturer.SAMSUNG
            
            manufacturer.contains("oppo") || brand.contains("oppo") -> DeviceManufacturer.OPPO
            
            manufacturer.contains("realme") || brand.contains("realme") -> DeviceManufacturer.REALME
            
            manufacturer.contains("huawei") || brand.contains("huawei") ||
            manufacturer.contains("honor") || brand.contains("honor") -> DeviceManufacturer.HUAWEI
            
            manufacturer.contains("vivo") || brand.contains("vivo") -> DeviceManufacturer.VIVO
            
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> DeviceManufacturer.ONEPLUS
            
            else -> DeviceManufacturer.STOCK
        }
    }

    /**
     * Check if device needs special permissions for call screen on lock screen
     */
    fun needsSpecialPermissions(): Boolean {
        val manufacturer = getManufacturer()
        return when (manufacturer) {
            DeviceManufacturer.XIAOMI -> true   // MIUI always needs special permissions
            DeviceManufacturer.OPPO -> true     // ColorOS needs special permissions
            DeviceManufacturer.REALME -> true   // Realme UI needs special permissions
            DeviceManufacturer.HUAWEI -> true   // EMUI needs special permissions
            DeviceManufacturer.VIVO -> true     // Funtouch OS may need permissions
            DeviceManufacturer.SAMSUNG -> {
                // Samsung One UI 5+ usually works fine
                Build.VERSION.SDK_INT < 31
            }
            DeviceManufacturer.ONEPLUS -> {
                // Older OxygenOS may need permissions
                Build.VERSION.SDK_INT < 33
            }
            DeviceManufacturer.STOCK -> false   // Stock Android works fine
        }
    }

    /**
     * Get required permissions list for current device
     */
    fun getRequiredPermissions(): List<SpecialPermission> {
        val manufacturer = getManufacturer()
        val permissions = mutableListOf<SpecialPermission>()

        when (manufacturer) {
            DeviceManufacturer.XIAOMI -> {
                permissions.add(SpecialPermission.DISPLAY_OVER_OTHER_APPS)
                permissions.add(SpecialPermission.DISPLAY_ON_LOCK_SCREEN)
                permissions.add(SpecialPermission.AUTO_START)
            }
            DeviceManufacturer.OPPO, DeviceManufacturer.REALME -> {
                permissions.add(SpecialPermission.DISPLAY_OVER_OTHER_APPS)
                permissions.add(SpecialPermission.AUTO_START)
            }
            DeviceManufacturer.HUAWEI -> {
                permissions.add(SpecialPermission.DISPLAY_OVER_OTHER_APPS)
                permissions.add(SpecialPermission.PROTECTED_APPS)
            }
            DeviceManufacturer.VIVO -> {
                permissions.add(SpecialPermission.DISPLAY_OVER_OTHER_APPS)
                permissions.add(SpecialPermission.AUTO_START)
            }
            DeviceManufacturer.SAMSUNG -> {
                if (Build.VERSION.SDK_INT < 31) {
                    permissions.add(SpecialPermission.DISPLAY_OVER_OTHER_APPS)
                }
            }
            DeviceManufacturer.ONEPLUS -> {
                if (Build.VERSION.SDK_INT < 33) {
                    permissions.add(SpecialPermission.DISPLAY_OVER_OTHER_APPS)
                }
            }
            DeviceManufacturer.STOCK -> {
                // No special permissions needed
            }
        }

        return permissions
    }

    /**
     * Open system-specific settings for special permissions
     */
    fun openSpecialPermissionSettings(context: Context, permission: SpecialPermission): Boolean {
        return try {
            val intent = when (permission) {
                SpecialPermission.DISPLAY_OVER_OTHER_APPS -> {
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                }
                SpecialPermission.DISPLAY_ON_LOCK_SCREEN -> {
                    // MIUI specific - go to app details
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                }
                SpecialPermission.AUTO_START -> {
                    when (getManufacturer()) {
                        DeviceManufacturer.XIAOMI -> {
                            try {
                                Intent().apply {
                                    setClassName(
                                        "com.miui.securitycenter",
                                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                    )
                                }
                            } catch (e: Exception) {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            }
                        }
                        DeviceManufacturer.OPPO, DeviceManufacturer.REALME -> {
                            try {
                                Intent().apply {
                                    setClassName(
                                        "com.coloros.safecenter",
                                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                                    )
                                }
                            } catch (e: Exception) {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            }
                        }
                        else -> {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                    }
                }
                SpecialPermission.PROTECTED_APPS -> {
                    // Huawei specific
                    try {
                        Intent().apply {
                            setClassName(
                                "com.huawei.systemmanager",
                                "com.huawei.systemmanager.optimize.process.ProtectActivity"
                            )
                        }
                    } catch (e: Exception) {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    }
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Opened settings for: $permission")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings for: $permission", e)
            false
        }
    }

    /**
     * Get manufacturer name string resource ID
     */
    fun getManufacturerNameResId(): Int {
        return when (getManufacturer()) {
            DeviceManufacturer.XIAOMI -> R.string.manufacturer_xiaomi
            DeviceManufacturer.SAMSUNG -> R.string.manufacturer_samsung
            DeviceManufacturer.OPPO -> R.string.manufacturer_oppo
            DeviceManufacturer.REALME -> R.string.manufacturer_realme
            DeviceManufacturer.HUAWEI -> R.string.manufacturer_huawei
            DeviceManufacturer.VIVO -> R.string.manufacturer_vivo
            DeviceManufacturer.ONEPLUS -> R.string.manufacturer_oneplus
            DeviceManufacturer.STOCK -> R.string.manufacturer_stock
        }
    }

    /**
     * Get manufacturer name in current locale
     * @deprecated Use getManufacturerNameResId() and context.getString() instead
     */
    @Deprecated("Use getManufacturerNameResId() instead")
    fun getManufacturerNameArabic(): String {
        return when (getManufacturer()) {
            DeviceManufacturer.XIAOMI -> "شاومي (MIUI)"
            DeviceManufacturer.SAMSUNG -> "سامسونج (One UI)"
            DeviceManufacturer.OPPO -> "أوبو (ColorOS)"
            DeviceManufacturer.REALME -> "ريلمي (Realme UI)"
            DeviceManufacturer.HUAWEI -> "هواوي (EMUI)"
            DeviceManufacturer.VIVO -> "فيفو (Funtouch OS)"
            DeviceManufacturer.ONEPLUS -> "ون بلس (OxygenOS)"
            DeviceManufacturer.STOCK -> "أندرويد"
        }
    }

    enum class SpecialPermission {
        DISPLAY_OVER_OTHER_APPS,
        DISPLAY_ON_LOCK_SCREEN,
        AUTO_START,
        PROTECTED_APPS
    }
}
