package com.rasmi.purevon.presentation.screen.incall

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.R
import com.rasmi.purevon.receiver.CallbackReminderReceiver
import com.rasmi.purevon.util.CallbackReminderScheduleManager
import com.rasmi.purevon.util.DebugLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "InCallViewModel"

// ─── Notes ───────────────────────────────────────────────

internal fun InCallViewModel.updateCallNotesImpl(notes: String) {
    Log.d(TAG, "Updating call notes: $notes")
    uiStateUpdater.update { it.copy(callNotes = notes) }
}

internal fun InCallViewModel.saveCallNoteImpl() {
    val notes = uiStateUpdater.value.callNotes.trim()
    if (notes.isEmpty()) {
        Log.d(TAG, "Note is empty, not saving")
        return
    }

    val phoneNumber = uiStateUpdater.value.phoneNumber
    if (phoneNumber.isEmpty() || phoneNumber == "Unknown") {
        Log.w(TAG, "Invalid phone number, cannot save note")
        viewModelScope.launch {
            uiEventEmitter.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_error_note_save_invalid_number)))
        }
        return
    }

    viewModelScope.launch {
        try {
            val normalizedPhone = phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
            val note = com.rasmi.purevon.data.local.entity.ContactNoteEntity(
                phoneNumber = normalizedPhone,
                note = notes,
                callDuration = uiStateUpdater.value.callDuration,
                isIncoming = !uiStateUpdater.value.isOutgoing,
                createdAt = System.currentTimeMillis()
            )

            contactNoteDao.insertNote(note)
            Log.d(TAG, "Note saved successfully for ${DebugLogger.maskPhoneNumber(phoneNumber)}")

            val updatedNotes = contactNoteDao.getNotesByPhoneNumberSync(normalizedPhone)
            uiStateUpdater.update {
                it.copy(
                    callNotes = "",
                    existingNotes = updatedNotes,
                    showNewNoteInput = false
                )
            }
            uiEventEmitter.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_msg_note_saved)))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving note", e)
            uiEventEmitter.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_error_note_save_failed)))
        }
    }
}

internal fun InCallViewModel.loadExistingNotesImpl(phoneNumber: String) {
    if (phoneNumber.isEmpty() || phoneNumber == "Unknown") return
    viewModelScope.launch {
        try {
            val normalized = phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
            val notes = contactNoteDao.getNotesByPhoneNumberSync(normalized)
            uiStateUpdater.update {
                it.copy(
                    existingNotes = notes,
                    showNewNoteInput = notes.isEmpty()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading existing notes", e)
        }
    }
}

// ─── Callback Reminder ───────────────────────────────────

@SuppressLint("MissingPermission")
internal fun InCallViewModel.setCallbackReminderImpl(minutes: Int) {
    Log.d(TAG, "Setting callback reminder for $minutes minutes")

    uiStateUpdater.update { it.copy(showCallbackReminder = false) }

    val phoneNumber = uiStateUpdater.value.phoneNumber
    val contactName = uiStateUpdater.value.contactName

    if (phoneNumber.isEmpty() || phoneNumber == "Unknown") {
        viewModelScope.launch {
            uiEventEmitter.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_error_reminder_invalid_number)))
        }
        return
    }

    try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000L)

        val intent = Intent(context, CallbackReminderReceiver::class.java).apply {
            putExtra(CallbackReminderReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(CallbackReminderReceiver.EXTRA_CONTACT_NAME, contactName ?: "Unknown")
            putExtra(CallbackReminderReceiver.EXTRA_REQUEST_CODE, requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var isExact = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                isExact = false
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        CallbackReminderScheduleManager.add(
            context, requestCode, phoneNumber,
            contactName ?: "Unknown", triggerTime
        )

        Log.d(TAG, "Callback reminder scheduled: code=$requestCode, exact=$isExact, trigger=$triggerTime")

        viewModelScope.launch {
            delay(200)
            val label = if (minutes >= 60) context.resources.getQuantityString(R.plurals.incall_msg_reminder_set_hours, minutes / 60, minutes / 60)
                         else context.resources.getQuantityString(R.plurals.incall_msg_reminder_set_minutes, minutes, minutes)
            val warning = if (!isExact) context.getString(R.string.incall_msg_reminder_approximate_warning) else ""
            uiEventEmitter.emit(UiEvent.ShowSnackbar(label + warning))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error scheduling callback reminder", e)
        viewModelScope.launch {
            uiEventEmitter.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_error_reminder_failed)))
        }
    }
}

internal fun InCallViewModel.showCallbackReminderDialogImpl() {
    uiStateUpdater.update { it.copy(showCallbackReminder = true) }
}

internal fun InCallViewModel.changeMiddleCardTabImpl(tab: Int) {
    uiStateUpdater.update { it.copy(middleCardTab = tab) }
}

// ─── Silence Call ─────────────────────────────────────────

@SuppressLint("MissingPermission")
internal fun InCallViewModel.silenceCallImpl() {
    Log.d(TAG, "Silencing call - muting THIS call only and going to background")
    try {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecomManager.silenceRinger()
        Log.d(TAG, "Call silenced using TelecomManager.silenceRinger() - phone ringer mode unchanged")

        uiStateUpdater.update { it.copy(isSilenced = true) }

        viewModelScope.launch { uiEventEmitter.emit(UiEvent.MoveToBackground) }
        Log.d(TAG, "Activity moved to background - caller still waiting")

    } catch (e: Exception) {
        Log.e(TAG, "Error silencing call", e)
        viewModelScope.launch { uiEventEmitter.emit(UiEvent.MoveToBackground) }
    }
}

// ─── Quick Message ────────────────────────────────────────

internal fun InCallViewModel.sendQuickMessageImpl(message: String) {
    Log.d(TAG, "Quick message sent: $message")
    viewModelScope.launch {
        delay(500)
        endCall()
    }
}

// ─── Location & Image (stubs) ────────────────────────────

internal fun InCallViewModel.sendLocationImpl() {
    Log.d(TAG, "Sending location")
    viewModelScope.launch {
        try {
            uiEventEmitter.emit(UiEvent.ShowError(context.getString(R.string.incall_msg_location_coming_soon)))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending location", e)
        }
    }
}

internal fun InCallViewModel.shareImageImpl() {
    Log.d(TAG, "Sharing image")
    viewModelScope.launch {
        try {
            uiEventEmitter.emit(UiEvent.ShowError(context.getString(R.string.incall_msg_image_sharing_coming_soon)))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing image", e)
        }
    }
}

internal fun InCallViewModel.showQuickSmsDialogImpl() {
    Log.d(TAG, "Opening quick SMS dialog")
}

// ─── Load Last Call Status ────────────────────────────────

internal fun InCallViewModel.loadLastCallStatusImpl(phoneNumber: String) {
    if (phoneNumber.isEmpty() || phoneNumber == "Unknown") return
    viewModelScope.launch {
        try {
            val lastCall = callLogRepository.getLastSystemCallForNumber(phoneNumber)
            if (lastCall != null) {
                val statusText = when (lastCall.type) {
                    android.provider.CallLog.Calls.INCOMING_TYPE -> context.getString(R.string.call_state_incoming)
                    android.provider.CallLog.Calls.OUTGOING_TYPE -> context.getString(R.string.call_state_outgoing)
                    android.provider.CallLog.Calls.MISSED_TYPE -> context.getString(R.string.call_state_missed)
                    android.provider.CallLog.Calls.REJECTED_TYPE -> context.getString(R.string.call_state_rejected)
                    else -> context.getString(R.string.call_state_calling)
                }
                uiStateUpdater.update {
                    it.copy(
                        lastCallStatus = statusText,
                        lastCallType = lastCall.type,
                        lastCallTime = lastCall.timestamp
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading last call status", e)
        }
    }
}
