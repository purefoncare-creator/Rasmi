package com.rasmi.purevon.presentation.screen.conversation

import com.rasmi.purevon.domain.model.Message

/**
 * Recipient suggestion for the "To:" autocomplete field
 */
data class RecipientSuggestion(
    val contactName: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val existingThreadId: Long? = null,
    val isFavorite: Boolean = false
)

/**
 * Isolated input bar state — changes on every keystroke.
 * Observed separately to prevent message list recomposition.
 */
data class InputBarState(
    val messageText: String = "",
    val attachments: List<AttachmentData> = emptyList(),
    val attachmentsTotalSize: Long = 0L,
    val replyToMessage: Message? = null,
    val isSending: Boolean = false,
    val isCompressingAttachments: Boolean = false,
    val isFetchingLocation: Boolean = false
)

/**
 * Isolated audio recording state — updates ~10x/sec during recording.
 * Must be observed separately to avoid destroying UI performance.
 */
data class AudioRecordingState(
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0,
    val audioRecordingFile: String? = null,
    val recordingAmplitudes: List<Float> = emptyList(),
    val isRecordingLocked: Boolean = false
)

/**
 * Dialog/overlay visibility flags — usually one active at a time.
 */
data class DialogState(
    val showScheduleDialog: Boolean = false,
    val editingScheduledMessage: EditScheduledMessageData? = null,
    val showTemplateDialog: Boolean = false,
    val showCreateTemplateDialog: Boolean = false,
    val showReactionPicker: Boolean = false,
    val selectedMessageForReaction: Message? = null,
    val showImageViewer: Boolean = false,
    val imageViewerUrls: List<String> = emptyList(),
    val imageViewerInitialIndex: Int = 0,
    val showMessageActions: Boolean = false,
    val selectedMessageForActions: Message? = null,
    val showForwardDialog: Boolean = false,
    val messageToForward: Message? = null,
    val showContactPickerDialog: Boolean = false,
    val pendingContactPreview: com.rasmi.purevon.domain.model.Contact? = null,
    val showRecipientPickerDialog: Boolean = false,
    val showSmsSimPickerForSend: Boolean = false
)

/**
 * Extended UI State for Conversation Screen
 * Includes new features: reactions, reply, typing indicator
 */
data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val threadId: Long? = null,
    val phoneNumber: String? = null,
    val contactName: String? = null,
    val isNewConversation: Boolean = false, // ✅ جديد: محادثة جديدة أم موجودة
    val messageText: String = "",
    val attachments: List<AttachmentData> = emptyList(),
    val attachmentsTotalSize: Long = 0L, // ✅ Cached total size to avoid O(n) I/O on each add
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0,
    val audioRecordingFile: String? = null,
    val recordingAmplitudes: List<Float> = emptyList(), // live waveform samples
    val isRecordingLocked: Boolean = false, // locked mode (hands-free)
    val showScheduleDialog: Boolean = false,
    val editingScheduledMessage: EditScheduledMessageData? = null, // ✅ Fix #14: Edit scheduled message
    val showTemplateDialog: Boolean = false,
    val isSending: Boolean = false,
    val isCompressingAttachments: Boolean = false, // ✅ جاري ضغط المرفقات
    val isFetchingLocation: Boolean = false, // ✅ جاري تحديد الموقع
    val error: String? = null,
    val scheduledSuccess: String? = null, // ✅ Success feedback for scheduled messages
    
    // New features
    val replyToMessage: Message? = null,
    val showReactionPicker: Boolean = false,
    val selectedMessageForReaction: Message? = null,
    val showImageViewer: Boolean = false,
    val imageViewerUrls: List<String> = emptyList(),
    val imageViewerInitialIndex: Int = 0,
    val showMessageActions: Boolean = false,
    val selectedMessageForActions: Message? = null,
    
    // ✅ P2: Forward UI
    val showForwardDialog: Boolean = false,
    val messageToForward: Message? = null,
    
    // ✅ Contact picker dialog
    val showContactPickerDialog: Boolean = false,
    
    // ✅ Contact preview before sharing
    val pendingContactPreview: com.rasmi.purevon.domain.model.Contact? = null,

    // ✅ Recipient picker (for new conversation "To:" field)
    val showRecipientPickerDialog: Boolean = false,

    // ✅ Create new template dialog
    val showCreateTemplateDialog: Boolean = false,

    // SIM selection for outgoing SMS
    val smsSimLabel: String = "SIM 1",
    val availableSims: List<com.rasmi.purevon.util.sim.SimInfo> = emptyList(),
    val isSmsAskMode: Boolean = false,          // ✅ وضع ASK: يسأل عن الشريحة قبل كل رسالة
    val showSmsSimPickerForSend: Boolean = false, // ✅ إظهار منتقي الشريحة عند الإرسال

    // ✅ Recipient autocomplete suggestions
    val recipientSuggestions: List<RecipientSuggestion> = emptyList()
)

/**
 * Extended UI Events for Conversation Screen
 */
sealed class ConversationUiEvent {
    data class LoadConversation(val threadId: Long) : ConversationUiEvent()
    data class SetPhoneNumber(val phoneNumber: String) : ConversationUiEvent() // ✅ جديد: للمحادثات الجديدة
    data class MessageTextChanged(val text: String) : ConversationUiEvent()
    data object SendMessage : ConversationUiEvent()
    data object CancelSend : ConversationUiEvent() // ✅ Cancel ongoing MMS send
    
    // Attachments
    data class AttachFile(val attachment: AttachmentData) : ConversationUiEvent()
    data class RemoveAttachment(val uri: String) : ConversationUiEvent()
    data object ClearAllAttachments : ConversationUiEvent()
    data object ShareContact : ConversationUiEvent()
    data class ShareLocation(val hasPermission: Boolean) : ConversationUiEvent()
    
    // Contact picker
    data object ShowContactPicker : ConversationUiEvent()
    data object HideContactPicker : ConversationUiEvent()
    data class ContactSelected(val contact: com.rasmi.purevon.domain.model.Contact) : ConversationUiEvent()
    data object ConfirmContactShare : ConversationUiEvent()
    data object DismissContactPreview : ConversationUiEvent()

    // Recipient picker (for new conversation "To:" field)
    data object ShowRecipientPicker : ConversationUiEvent()
    data object HideRecipientPicker : ConversationUiEvent()
    data class RecipientContactSelected(val contact: com.rasmi.purevon.domain.model.Contact) : ConversationUiEvent()

    // ✅ Autocomplete: user tapped a suggestion from the inline dropdown
    data class SelectRecipientSuggestion(val suggestion: RecipientSuggestion) : ConversationUiEvent()
    
    // Scheduling
    data object ShowScheduleDialog : ConversationUiEvent()
    data object HideScheduleDialog : ConversationUiEvent()
    data class ScheduleMessage(
        val scheduledTimeMillis: Long,
        val repeatInterval: com.rasmi.purevon.domain.model.RepeatInterval = com.rasmi.purevon.domain.model.RepeatInterval.NONE
    ) : ConversationUiEvent()
    data class CancelScheduledMessage(val scheduleId: Long) : ConversationUiEvent() // ✅ Fix #10: Cancel from bubble
    data class EditScheduledMessage(val message: Message) : ConversationUiEvent() // ✅ Fix #14: Edit scheduled message
    data class ConfirmEditScheduledMessage(
        val scheduleId: Long,
        val newBody: String,
        val newScheduledTime: Long,
        val newRepeatInterval: com.rasmi.purevon.domain.model.RepeatInterval
    ) : ConversationUiEvent()
    data object DismissEditScheduledMessage : ConversationUiEvent()
    data object ClearScheduledSuccess : ConversationUiEvent() // ✅ Fix #6: Clear success snackbar
    
    // Templates
    data object ShowTemplateDialog : ConversationUiEvent()
    data object HideTemplateDialog : ConversationUiEvent()
    data class SelectTemplate(val template: com.rasmi.purevon.domain.model.MessageTemplate) : ConversationUiEvent()
    data class DeleteTemplate(val template: com.rasmi.purevon.domain.model.MessageTemplate) : ConversationUiEvent()
    data object ShowCreateTemplateDialog : ConversationUiEvent()
    data object HideCreateTemplateDialog : ConversationUiEvent()
    data class CreateTemplate(val title: String, val content: String, val emoji: String? = null) : ConversationUiEvent()
    
    // Audio Recording
    // SIM switching
    data object SwitchSmsSim : ConversationUiEvent()
    data class SimSelectedForMessage(val subscriptionId: Int?) : ConversationUiEvent() // ✅ المستخدم اختار الشريحة
    data object DismissSmsSimPicker : ConversationUiEvent()                            // ✅ إغلاق دon اختيار

    data object StartRecording : ConversationUiEvent()
    data object StopRecording : ConversationUiEvent()
    data object CancelRecording : ConversationUiEvent()
    data object SendAudioMessage : ConversationUiEvent()
    data object LockRecording : ConversationUiEvent() // slide-up lock
    data object StopAndSendRecording : ConversationUiEvent() // release = send
    
    // Conversation Actions
    data class DeleteConversation(val threadId: Long) : ConversationUiEvent()
    
    // Message Actions
    data class ToggleMessageFavorite(val message: Message) : ConversationUiEvent()
    data class DeleteMessage(val message: Message) : ConversationUiEvent()
    data class CopyMessage(val message: Message) : ConversationUiEvent()
    
    // ✅ NEW: Retry Failed Message
    data class RetryMessage(val messageId: Long) : ConversationUiEvent()
    
    // NEW: Reply to Message
    data class ReplyToMessage(val message: Message) : ConversationUiEvent()
    data object CancelReply : ConversationUiEvent()
    
    // NEW: Reactions
    data class ShowReactionPicker(val message: Message) : ConversationUiEvent()
    data object HideReactionPicker : ConversationUiEvent()
    data class AddReaction(val message: Message, val emoji: String) : ConversationUiEvent()
    data class RemoveReaction(val message: Message, val emoji: String) : ConversationUiEvent()
    
    // NEW: Message Actions Sheet
    data class ShowMessageActions(val message: Message) : ConversationUiEvent()
    data object HideMessageActions : ConversationUiEvent()
    
    // NEW: Image Viewer
    data class ShowImageViewer(val urls: List<String>, val initialIndex: Int) : ConversationUiEvent()
    data object HideImageViewer : ConversationUiEvent()
    
    // NEW: Forward Message
    data class ShowForwardDialog(val message: Message) : ConversationUiEvent()
    data object HideForwardDialog : ConversationUiEvent()
    data class ConfirmForward(val contacts: List<com.rasmi.purevon.domain.model.Contact>) : ConversationUiEvent()
    
    // Clear error state
    data object ClearError : ConversationUiEvent()
}

/**
 * Attachment data class
 */
data class AttachmentData(
    val uri: String,
    val fileName: String,
    val mimeType: String?,
    val isImage: Boolean,
    val fileSize: Long? = null
)

/**
 * Data for editing a scheduled message
 */
data class EditScheduledMessageData(
    val scheduleId: Long,
    val body: String,
    val scheduledTime: Long,
    val repeatInterval: com.rasmi.purevon.domain.model.RepeatInterval
)
