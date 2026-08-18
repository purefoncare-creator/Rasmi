package com.rasmi.purevon.service

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/**
 * ConnectionService for handling outgoing and incoming calls
 * This is required for the app to actually place and receive calls
 */
class PurevonConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "PurevonConnectionService"
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Log.d(TAG, "onCreateOutgoingConnection: ${request?.address}")

        return try {
            val connection = createConnection(request)
            connection.setInitialized()
            
            // Let the system handle the actual call via default telephony
            connection.setDialing()
            
            Log.d(TAG, "Outgoing connection created successfully")
            connection
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create outgoing connection", e)
            null
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Log.d(TAG, "onCreateIncomingConnection: ${request?.address}")

        return try {
            val connection = createConnection(request)
            connection.setRinging()
            
            Log.d(TAG, "Incoming connection created successfully")
            connection
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create incoming connection", e)
            null
        }
    }

    private fun createConnection(request: ConnectionRequest?): Connection {
        return object : Connection() {
            init {
                // Set call properties
                setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
                setCallerDisplayName(
                    request?.extras?.getString(TelecomManager.EXTRA_CALL_SUBJECT),
                    TelecomManager.PRESENTATION_ALLOWED
                )

                // Set connection capabilities
                connectionCapabilities = CAPABILITY_SUPPORT_HOLD or
                        CAPABILITY_HOLD or
                        CAPABILITY_MUTE

                // Set audio properties
                audioModeIsVoip = false
            }

            override fun onAnswer() {
                super.onAnswer()
                Log.d(TAG, "Call answered")
                setActive()
            }

            override fun onReject() {
                super.onReject()
                Log.d(TAG, "Call rejected")
                setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                super.onDisconnect()
                Log.d(TAG, "Call disconnected")
                setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                destroy()
            }

            override fun onAbort() {
                super.onAbort()
                Log.d(TAG, "Call aborted")
                setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.CANCELED))
                destroy()
            }

            override fun onHold() {
                super.onHold()
                Log.d(TAG, "Call held")
                setOnHold()
            }

            override fun onUnhold() {
                super.onUnhold()
                Log.d(TAG, "Call unheld")
                setActive()
            }

            override fun onPlayDtmfTone(c: Char) {
                super.onPlayDtmfTone(c)
                Log.d(TAG, "Play DTMF tone: $c")
            }

            override fun onStopDtmfTone() {
                super.onStopDtmfTone()
                Log.d(TAG, "Stop DTMF tone")
            }
        }
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e(TAG, "Failed to create outgoing connection")
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e(TAG, "Failed to create incoming connection")
    }
}
