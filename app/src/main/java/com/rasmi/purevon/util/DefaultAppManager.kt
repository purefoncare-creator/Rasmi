package com.rasmi.purevon.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.activity.result.ActivityResultLauncher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for handling default app requests (Dialer and SMS)
 */
@Singleton
class DefaultAppManager @Inject constructor(
    private val context: Context
) {
    
    /**
     * Check if app is default dialer
     */
    fun isDefaultDialer(): Boolean {
        // Use RoleManager for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            return roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
        }
        
        // Fallback for older versions
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            context.packageName == telecomManager.defaultDialerPackage
        } else {
            false
        }
    }
    
    /**
     * Check if app is default SMS app
     */
    fun isDefaultSmsApp(): Boolean {
        // Use RoleManager for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            return roleManager?.isRoleHeld(RoleManager.ROLE_SMS) ?: false
        }
        
        // Fallback for older versions
        return context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
    }
    
    /**
     * Request to become default dialer
     * Use with ActivityResultLauncher
     */
    fun requestDefaultDialer(launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (context.packageName != telecomManager.defaultDialerPackage) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
                launcher.launch(intent)
            }
        }
    }
    
    /**
     * Request to become default SMS app
     * Use with ActivityResultLauncher
     */
    fun requestDefaultSmsApp(launcher: ActivityResultLauncher<Intent>) {
        if (context.packageName != Telephony.Sms.getDefaultSmsPackage(context)) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
            launcher.launch(intent)
        }
    }
    
    /**
     * Request both default dialer and SMS (for Android 10+)
     * Uses RoleManager which is more modern
     */
    fun requestDefaultApps(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
            
            // Request default dialer role
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                activity.startActivityForResult(intent, requestCode)
            }
            
            // Request default SMS role
            if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                activity.startActivityForResult(intent, requestCode + 1)
            }
        } else {
            // For older Android versions, use the traditional methods
            requestDefaultDialerLegacy(activity, requestCode)
            requestDefaultSmsAppLegacy(activity, requestCode + 1)
        }
    }
    
    /**
     * Legacy method for requesting default dialer
     */
    private fun requestDefaultDialerLegacy(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = activity.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (activity.packageName != telecomManager.defaultDialerPackage) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
                }
                activity.startActivityForResult(intent, requestCode)
            }
        }
    }
    
    /**
     * Legacy method for requesting default SMS app
     */
    private fun requestDefaultSmsAppLegacy(activity: Activity, requestCode: Int) {
        if (activity.packageName != Telephony.Sms.getDefaultSmsPackage(activity)) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
            }
            activity.startActivityForResult(intent, requestCode)
        }
    }
    
    /**
     * Check if both default apps are set
     */
    fun areBothDefaultAppsSet(): Boolean {
        return isDefaultDialer() && isDefaultSmsApp()
    }
    
    /**
     * Get status message for UI
     */
    fun getStatusMessage(): String {
        return when {
            areBothDefaultAppsSet() -> "Purevon is your default Phone and SMS app"
            isDefaultDialer() && !isDefaultSmsApp() -> "Purevon is your default Phone app only"
            !isDefaultDialer() && isDefaultSmsApp() -> "Purevon is your default SMS app only"
            else -> "Purevon is not set as default"
        }
    }
}
