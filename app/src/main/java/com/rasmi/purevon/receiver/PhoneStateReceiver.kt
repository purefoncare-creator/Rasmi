package com.rasmi.purevon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import android.util.Log
import com.rasmi.purevon.domain.call.InCallServiceBridge
import com.rasmi.purevon.domain.usecase.spam.CheckIfSpamUseCase
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.util.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Receiver for monitoring phone call states
 * Handles incoming calls, spam detection, and call blocking
 */
@AndroidEntryPoint
class PhoneStateReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var checkIfSpamUseCase: CheckIfSpamUseCase
    
    @Inject
    lateinit var notificationHelper: NotificationHelper
    
    @Inject
    lateinit var inCallServiceBridge: InCallServiceBridge
    
    companion object {
        private const val TAG = "PhoneStateReceiver"
        private const val PREFS_NAME = "purevon_phone_state"
        private const val KEY_LAST_STATE = "last_state"
        private const val KEY_IS_INCOMING = "is_incoming"
        private const val KEY_SAVED_NUMBER = "saved_number"
        private val stateLock = Any()
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isIncoming = false
        private var savedNumber: String? = null

        internal fun loadPersistedState(context: Context) {
            synchronized(stateLock) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                lastState = prefs.getInt(KEY_LAST_STATE, TelephonyManager.CALL_STATE_IDLE)
                isIncoming = prefs.getBoolean(KEY_IS_INCOMING, false)
                savedNumber = prefs.getString(KEY_SAVED_NUMBER, null)
            }
        }

        internal fun persistState(context: Context) {
            synchronized(stateLock) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_LAST_STATE, lastState)
                    .putBoolean(KEY_IS_INCOMING, isIncoming)
                    .putString(KEY_SAVED_NUMBER, savedNumber)
                    .apply()
            }
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                loadPersistedState(context)

                if (!::checkIfSpamUseCase.isInitialized ||
                    !::notificationHelper.isInitialized) {
                    Log.e(TAG, "Dependencies not initialized yet")
                    return@launch
                }

                val action = intent.action
                Log.d(TAG, "PhoneStateReceiver received action: $action")

                when (action) {
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                        val incomingNumber = getIncomingNumber(context, intent)

                        Log.d(TAG, "Phone state: $state, number: ${DebugLogger.maskPhoneNumber(incomingNumber ?: "")}")

                        val (incomingToHandle, missedToHandle, answeredSnapshot) = synchronized(stateLock) {
                            var incoming: String? = null
                            var missed: String? = null
                            var answeredNum: String? = null

                            when (state) {
                                TelephonyManager.EXTRA_STATE_RINGING -> {
                                    isIncoming = true
                                    savedNumber = incomingNumber
                                    incoming = incomingNumber
                                }

                                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                                        answeredNum = savedNumber
                                    }
                                }

                                TelephonyManager.EXTRA_STATE_IDLE -> {
                                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                                        missed = savedNumber
                                    }
                                    isIncoming = false
                                    savedNumber = null
                                }
                            }

                            lastState = when (state) {
                                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                                else -> TelephonyManager.CALL_STATE_IDLE
                            }

                            Triple(incoming, missed, answeredNum)
                        }

                        persistState(context)

                        withContext(Dispatchers.Main) {
                            answeredSnapshot?.let {
                                Log.d(TAG, "Incoming call answered: ${DebugLogger.maskPhoneNumber(it)}")
                            }
                            incomingToHandle?.let { number ->
                                Log.d(TAG, "Handling incoming call: ${DebugLogger.maskPhoneNumber(number)}")
                                handleIncomingCall(number)
                            } ?: run {
                                if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                                    Log.w(TAG, "Incoming call but no number detected")
                                }
                            }
                            missedToHandle?.let { number ->
                                Log.d(TAG, "Handling missed call: ${DebugLogger.maskPhoneNumber(number)}")
                                handleMissedCall(number)
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in PhoneStateReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    /**
     * Get incoming number using multiple methods for better compatibility
     * This is needed because EXTRA_INCOMING_NUMBER is deprecated and unreliable
     */
    private fun getIncomingNumber(context: Context, intent: Intent): String? {
        // Method 1: Try the deprecated but sometimes still working EXTRA_INCOMING_NUMBER
        @Suppress("DEPRECATION")
        var number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        
        if (!number.isNullOrBlank()) {
            return number
        }
        
        // The InCallService is the primary source of truth for call information
        Log.d(TAG, "Could not get number from intent, will rely on InCallService")
        
        return number
    }
    
    private fun handleIncomingCall(phoneNumber: String) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(25_000L) {
                    val spamResult = checkIfSpamUseCase(phoneNumber)
                    if (spamResult.isSpam) {
                        Log.d(TAG, "Spam call detected: ${DebugLogger.maskPhoneNumber(phoneNumber)} (score: ${spamResult.spamScore})")
                        notificationHelper.showSpamCallNotification(
                            phoneNumber = phoneNumber,
                            spamScore = spamResult.spamScore
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming call", e)
            } finally {
                scope.cancel()
            }
        }
    }

    private fun handleMissedCall(phoneNumber: String) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(25_000L) {
                    if (inCallServiceBridge.wasCallRejectedByUser) {
                        Log.d(TAG, "[MISSED] Call was rejected by user - skipping missed call notification")
                        inCallServiceBridge.setCallRejectedByUser(false)
                        return@withTimeout
                    }
                    notificationHelper.showMissedCallNotification(phoneNumber)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling missed call", e)
            } finally {
                scope.cancel()
            }
        }
    }
}
