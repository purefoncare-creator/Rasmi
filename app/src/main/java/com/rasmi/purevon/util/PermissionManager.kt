package com.rasmi.purevon.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Centralized Permission Manager for the app
 */
object PermissionManager {
    
    // Permission groups
    object PermissionGroups {
        val PHONE_PERMISSIONS = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_NUMBERS
        )
        
        val CONTACTS_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        
        val SMS_PERMISSIONS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS
        )
        
        val NOTIFICATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
        
        val ALL_PERMISSIONS = PHONE_PERMISSIONS + CONTACTS_PERMISSIONS + 
                              SMS_PERMISSIONS + NOTIFICATION_PERMISSIONS
    }
    
    /**
     * Check if system alert window permission is granted
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Check if battery optimizations are ignored
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // ✅ FIX #27: Use safe cast to avoid ClassCastException
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }
    
    /**
     * Check if a specific permission is granted
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if all permissions in an array are granted
     */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { hasPermission(context, it) }
    }
    
    /**
     * Get list of denied permissions from an array
     */
    fun getDeniedPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter { !hasPermission(context, it) }
    }
    
    /**
     * Request multiple permissions
     */
    fun requestPermissions(
        activity: Activity,
        permissions: Array<String>,
        requestCode: Int
    ) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }
    
    /**
     * Check if should show rationale for permission
     */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    
    /**
     * Check phone permissions status
     */
    fun hasPhonePermissions(context: Context): Boolean {
        return hasPermissions(context, PermissionGroups.PHONE_PERMISSIONS)
    }
    
    /**
     * Check contacts permissions status
     */
    fun hasContactsPermissions(context: Context): Boolean {
        return hasPermissions(context, PermissionGroups.CONTACTS_PERMISSIONS)
    }
    
    /**
     * Check SMS permissions status
     */
    fun hasSmsPermissions(context: Context): Boolean {
        return hasPermissions(context, PermissionGroups.SMS_PERMISSIONS)
    }
    
    /**
     * Check all critical permissions
     */
    fun hasAllCriticalPermissions(context: Context): Boolean {
        return hasPhonePermissions(context) && 
               hasContactsPermissions(context) && 
               hasSmsPermissions(context)
    }
    
    /**
     * Get permission description for user
     */
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_PHONE_STATE -> "Access phone state to detect calls"
            Manifest.permission.READ_CALL_LOG -> "Read call history"
            Manifest.permission.WRITE_CALL_LOG -> "Save call logs"
            Manifest.permission.CALL_PHONE -> "Make phone calls"
            Manifest.permission.ANSWER_PHONE_CALLS -> "Answer incoming calls"
            Manifest.permission.READ_PHONE_NUMBERS -> "Read your phone number"
            Manifest.permission.READ_CONTACTS -> "Read your contacts"
            Manifest.permission.WRITE_CONTACTS -> "Save contacts"
            Manifest.permission.READ_SMS -> "Read your messages"
            Manifest.permission.SEND_SMS -> "Send messages"
            Manifest.permission.RECEIVE_SMS -> "Receive messages"
            Manifest.permission.RECEIVE_MMS -> "Receive MMS"
            Manifest.permission.POST_NOTIFICATIONS -> "Show notifications"
            else -> "Unknown permission"
        }
    }
    
    // Request codes for permission requests
    object RequestCodes {
        const val PHONE_PERMISSIONS = 100
        const val CONTACTS_PERMISSIONS = 101
        const val SMS_PERMISSIONS = 102
        const val NOTIFICATION_PERMISSIONS = 103
        const val ALL_PERMISSIONS = 104
    }
}
