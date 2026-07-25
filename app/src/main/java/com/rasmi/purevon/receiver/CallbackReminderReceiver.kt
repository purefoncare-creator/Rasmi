package com.rasmi.purevon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rasmi.purevon.presentation.screen.callbackreminder.CallbackReminderActivity
import com.rasmi.purevon.util.CallbackReminderScheduleManager
import com.rasmi.purevon.util.SoundManager
import dagger.hilt.android.EntryPointAccessors

class CallbackReminderReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CallbackReminderReceiver"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_REQUEST_CODE = "callback_request_code"
    }

    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    @dagger.hilt.EntryPoint
    interface SoundManagerEntryPoint {
        fun soundManager(): SoundManager
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Callback reminder triggered")
        
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: return
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)

        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        if (requestCode != -1) {
            CallbackReminderScheduleManager.remove(context, requestCode)
        }
        CallbackReminderScheduleManager.removeExpired(context)
        
        Log.d(TAG, "Opening callback reminder for: ${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(phoneNumber)} (Name: ${com.rasmi.purevon.util.DebugLogger.maskName(contactName)})")
        
        try {
            val entryPoint = EntryPointAccessors.fromApplication(context, SoundManagerEntryPoint::class.java)
            entryPoint.soundManager().playReminderSound()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing reminder sound", e)
        }
        
        val reminderIntent = CallbackReminderActivity.createIntent(
            context = context,
            phoneNumber = phoneNumber,
            contactName = contactName
        )
        context.startActivity(reminderIntent)
    }
}
