package com.rasmi.purevon.util.telecom

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.rasmi.purevon.R
import com.rasmi.purevon.service.PurevonConnectionService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for registering and managing PhoneAccount with TelecomManager
 * Required for handling outgoing and incoming calls
 */
@RequiresApi(Build.VERSION_CODES.M)
@Singleton
class PhoneAccountManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PhoneAccountManager"
        private const val PHONE_ACCOUNT_ID = "PurevonAccount"
        private const val PHONE_ACCOUNT_LABEL = "Purevon"
    }

    private val telecomManager: TelecomManager? by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }

    /**
     * Get the phone account handle for this app
     */
    fun getPhoneAccountHandle(): PhoneAccountHandle {
        val componentName = ComponentName(
            context,
            PurevonConnectionService::class.java
        )
        return PhoneAccountHandle(componentName, PHONE_ACCOUNT_ID)
    }

    /**
     * Register phone account with TelecomManager
     * This must be called when app becomes default dialer
     */
    fun registerPhoneAccount(): Boolean {
        return try {
            val phoneAccountHandle = getPhoneAccountHandle()

            // Check if already registered
            val existingAccount = telecomManager?.getPhoneAccount(phoneAccountHandle)
            if (existingAccount != null) {
                Log.d(TAG, "PhoneAccount already registered")
                return true
            }

            // Build phone account
            val phoneAccountBuilder = PhoneAccount.builder(
                phoneAccountHandle,
                PHONE_ACCOUNT_LABEL
            )
                .setCapabilities(
                    PhoneAccount.CAPABILITY_CALL_PROVIDER
                )
                .setShortDescription(PHONE_ACCOUNT_LABEL)
                .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))

            // Set icon if available
            try {
                val icon = Icon.createWithResource(context, com.rasmi.purevon.R.mipmap.ic_launcher)
                phoneAccountBuilder.setIcon(icon)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set icon", e)
            }

            phoneAccountBuilder.setAddress(
                android.net.Uri.fromParts(PhoneAccount.SCHEME_TEL, "", null)
            )

            val phoneAccount = phoneAccountBuilder.build()

            // Register with TelecomManager
            telecomManager?.registerPhoneAccount(phoneAccount)

            Log.d(TAG, "PhoneAccount registered successfully: $PHONE_ACCOUNT_ID")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: App must be default dialer to register PhoneAccount", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register PhoneAccount", e)
            false
        }
    }

    /**
     * Unregister phone account
     */
    fun unregisterPhoneAccount() {
        try {
            val phoneAccountHandle = getPhoneAccountHandle()
            telecomManager?.unregisterPhoneAccount(phoneAccountHandle)
            Log.d(TAG, "PhoneAccount unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister PhoneAccount", e)
        }
    }

    /**
     * Check if phone account is registered
     */
    fun isPhoneAccountRegistered(): Boolean {
        return try {
            val phoneAccountHandle = getPhoneAccountHandle()
            val account = telecomManager?.getPhoneAccount(phoneAccountHandle)
            account != null && account.isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking PhoneAccount registration", e)
            false
        }
    }

    /**
     * Enable phone account
     */
    fun enablePhoneAccount(enabled: Boolean) {
        try {
            val phoneAccountHandle = getPhoneAccountHandle()
            val phoneAccount = telecomManager?.getPhoneAccount(phoneAccountHandle)
            
            if (phoneAccount != null) {
                if (!phoneAccount.isEnabled && enabled) {
                    registerPhoneAccount()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable PhoneAccount", e)
        }
    }

    /**
     * Get all call capable phone accounts
     */
    fun getCallCapablePhoneAccounts(): List<PhoneAccountHandle> {
        return try {
            telecomManager?.callCapablePhoneAccounts ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get call capable phone accounts", e)
            emptyList()
        }
    }

    /**
     * Check if app has calling accounts
     */
    fun hasCallingAccount(): Boolean {
        return try {
            val handle = getPhoneAccountHandle()
            getCallCapablePhoneAccounts().contains(handle)
        } catch (e: Exception) {
            false
        }
    }
}
