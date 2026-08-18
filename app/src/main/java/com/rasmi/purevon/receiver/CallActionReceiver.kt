package com.rasmi.purevon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import com.rasmi.purevon.domain.call.InCallServiceBridge
import com.rasmi.purevon.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receiver for handling notification actions (Answer/Decline)
 */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var inCallServiceBridge: InCallServiceBridge
    
    companion object {
        const val ACTION_ANSWER = "com.rasmi.purevon.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.rasmi.purevon.ACTION_DECLINE"
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        private const val TAG = "CallActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")
        
        // ✅ محاولة الحصول على Call ID من Intent
        val callId = intent.getStringExtra(EXTRA_CALL_ID)
        Log.d(TAG, "Call ID from intent: $callId")
        
        // ✅ البحث عن المكالمة الصحيحة
        val call = if (!callId.isNullOrEmpty()) {
            // ابحث بواسطة Call ID (الطريقة الصحيحة)
            val foundCall = inCallServiceBridge.findCallById(callId)
            if (foundCall != null) {
                Log.d(TAG, "Found call by ID: $callId")
                foundCall
            } else {
                Log.w(TAG, "Call not found by ID: $callId, falling back to current call")
                inCallServiceBridge.getCurrentCall()
            }
        } else {
            // إذا لم يكن هناك Call ID، استخدم المكالمة الحالية (للتوافق القديم)
            Log.d(TAG, "No Call ID provided, using current call")
            inCallServiceBridge.getCurrentCall()
        }
        
        if (call == null) {
            Log.w(TAG, "No call to act on")
            return
        }
        
        val callState = call.state
        Log.d(TAG, "Call state: $callState")

        when (intent.action) {
            ACTION_ANSWER -> {
                Log.d(TAG, "Answering call: ${DebugLogger.maskPhoneNumber(call.details?.handle?.schemeSpecificPart ?: "")}")
                if (callState == Call.STATE_RINGING) {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    Log.d(TAG, "✅ Call answered")
                } else {
                    Log.w(TAG, "⚠️ Cannot answer call in state: $callState")
                }
            }
            ACTION_DECLINE -> {
                Log.d(TAG, "Declining call: ${DebugLogger.maskPhoneNumber(call.details?.handle?.schemeSpecificPart ?: "")}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    call.reject(Call.REJECT_REASON_DECLINED)
                } else {
                    call.reject(false, null)
                }
                Log.d(TAG, "✅ Call declined")
            }
        }
    }
}
