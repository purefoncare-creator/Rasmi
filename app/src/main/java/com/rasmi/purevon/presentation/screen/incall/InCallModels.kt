package com.rasmi.purevon.presentation.screen.incall

import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.util.sim.SimInfo

/**
 * UI State for InCall Screen
 */
data class InCallUiState(
    val phoneNumber: String = "",
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val callState: String = "",
    val isRinging: Boolean = false,
    val callDuration: Long = 0,
    val callStartTime: Long = 0L,
    val isActive: Boolean = false,
    val isOutgoing: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val currentAudioRoute: Int = android.telecom.CallAudioState.ROUTE_EARPIECE,
    val availableAudioRoutes: Int = android.telecom.CallAudioState.ROUTE_EARPIECE,
    val showAudioRoutePicker: Boolean = false,
    val isOnHold: Boolean = false,
    val heldCall: String? = null,
    val heldCallName: String? = null,
    val hasMultipleCalls: Boolean = false,
    val waitingCall: String? = null,
    val waitingCallName: String? = null,
    val hasWaitingCall: Boolean = false,
    val isConference: Boolean = false,
    val conferenceParticipants: List<String> = emptyList(),
    val conferenceParticipantNames: List<String?> = emptyList(),
    val showKeypad: Boolean = false,
    val showAddCallDialog: Boolean = false,
    val showContactsDialog: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    val callNotes: String = "",
    val existingNotes: List<com.rasmi.purevon.data.local.entity.ContactNoteEntity> = emptyList(),
    val showNewNoteInput: Boolean = false,
    val lastCallStatus: String? = null,
    val lastCallType: Int? = null,
    val lastCallTime: Long? = null,
    val showCallbackReminder: Boolean = false,
    val middleCardTab: Int = 0,
    val callStatistics: CallStatistics? = null,
    val isBlocked: Boolean = false,
    val isFavorite: Boolean = false,
    val isFavoriteLoading: Boolean = false,
    val isBlockLoading: Boolean = false,
    val isEndingCall: Boolean = false,
    val isSilenced: Boolean = false,
    val showSimPickerForAddCall: Boolean = false,
    val pendingAddCallNumber: String = "",
    val availableSimsForAddCall: List<SimInfo> = emptyList(),
)

/**
 * Call statistics for a contact
 */
data class CallStatistics(
    val incomingCalls: Int = 0,
    val outgoingCalls: Int = 0,
    val missedCalls: Int = 0,
    val totalCalls: Int = 0,
    val totalDurationSeconds: Long = 0,
    val lastCallTimestamp: Long? = null
) {
    fun getFormattedDuration(): String {
        val hours = totalDurationSeconds / 3600
        val minutes = (totalDurationSeconds % 3600) / 60
        val seconds = totalDurationSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}

/**
 * UI Events for InCall Screen
 */
sealed class InCallUiEvent {
    data object AnswerCall : InCallUiEvent()
    data object ToggleMute : InCallUiEvent()
    data object ToggleSpeaker : InCallUiEvent()
    data object ShowAudioRoutePicker : InCallUiEvent()
    data object HideAudioRoutePicker : InCallUiEvent()
    data class SelectAudioRoute(val route: Int) : InCallUiEvent()
    data object ToggleHold : InCallUiEvent()
    data object SwapCalls : InCallUiEvent()
    data object AnswerAndHold : InCallUiEvent()
    data object RejectWaitingCall : InCallUiEvent()
    data object MergeCalls : InCallUiEvent()
    data object EndCall : InCallUiEvent()
    data object EndHeldCall : InCallUiEvent()
    data object ToggleKeypad : InCallUiEvent()
    data class SendDtmfTone(val digit: Char) : InCallUiEvent()
    data object ShowAddCall : InCallUiEvent()
    data object ShowContacts : InCallUiEvent()
    data class AddCallToNumber(val phoneNumber: String) : InCallUiEvent()
    data class AddCallToContact(val contact: Contact) : InCallUiEvent()
    data object SilenceCall : InCallUiEvent()
    data class SendQuickMessage(val message: String) : InCallUiEvent()
    data class UpdateCallNotes(val notes: String) : InCallUiEvent()
    data object SaveCallNote : InCallUiEvent()
    // ✅ FIX M34: حذف ملاحظة من شاشة المكالمة
    data class DeleteCallNote(val noteId: Long) : InCallUiEvent()
    data object ShowNewNoteInput : InCallUiEvent()
    data object HideNewNoteInput : InCallUiEvent()
    data class SetCallbackReminder(val minutes: Int) : InCallUiEvent()
    data object ShowCallbackReminderDialog : InCallUiEvent()
    data object HideCallbackReminderDialog : InCallUiEvent()
    data class ChangeMiddleCardTab(val tab: Int) : InCallUiEvent()
    data object SendLocation : InCallUiEvent()
    data object ShareImage : InCallUiEvent()
    data object SendQuickSms : InCallUiEvent()
    data object ToggleFavorite : InCallUiEvent()
    data object ToggleBlock : InCallUiEvent()
    data class SimSelectedForAddCall(val phoneNumber: String, val subscriptionId: Int?) : InCallUiEvent()
    data object DismissSimPickerForAddCall : InCallUiEvent()
    data object DismissAddCallDialog : InCallUiEvent()
}

/**
 * UI Events for user feedback
 */
sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
    data class ShowError(val message: String) : UiEvent()
    data class ShowToast(val message: String) : UiEvent()
    data object FinishActivity : UiEvent()
    data object MoveToBackground : UiEvent()
}
