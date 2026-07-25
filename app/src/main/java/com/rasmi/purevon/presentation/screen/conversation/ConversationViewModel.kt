package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.os.Build
import android.util.Log
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rasmi.purevon.util.event.EventBus
import com.rasmi.purevon.util.event.AppEvent
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.data.mapper.toEntity
import com.rasmi.purevon.util.sim.SimManager
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.util.getLocalizedMessage
import com.rasmi.purevon.presentation.util.getLocalizedSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for Conversation Screen
 *
 * Note about Message Sending:
 * - Currently using SendMessageUseCase for basic message sending
 * - For delivery tracking, inject SendMessageWithTrackingUseCase instead
 * - Delivery tracking provides sent/delivered status callbacks
 *
 * Thread Safety: Repository layer uses Mutex to prevent concurrent message sending
 *
 * Finding D — Refactored: 31 constructor params → 8 via aggregate Ops classes.
 */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    val smsCharacterCounter: com.rasmi.purevon.util.message.SmsCharacterCounter,
    private val simManager: SimManager,
    private val messageOps: ConversationMessageOps,
    private val contactOps: ConversationContactOps,
    private val templateOps: ConversationTemplateOps,
    private val scheduleOps: ConversationScheduleOps,
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()
    
    /**
     * Derived sub-states for targeted recomposition.
     * The Screen observes these instead of the monolithic uiState where possible,
     * preventing cross-state recompositions (e.g., typing doesn't recompose message list).
     */
    val inputBarState: StateFlow<InputBarState> = _uiState
        .map { InputBarState(
            messageText = it.messageText,
            attachments = it.attachments,
            attachmentsTotalSize = it.attachmentsTotalSize,
            replyToMessage = it.replyToMessage,
            isSending = it.isSending,
            isCompressingAttachments = it.isCompressingAttachments,
            isFetchingLocation = it.isFetchingLocation
        ) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InputBarState())
    
    val audioState: StateFlow<AudioRecordingState> = _uiState
        .map { AudioRecordingState(
            isRecording = it.isRecording,
            recordingDuration = it.recordingDuration,
            audioRecordingFile = it.audioRecordingFile,
            recordingAmplitudes = it.recordingAmplitudes,
            isRecordingLocked = it.isRecordingLocked
        ) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioRecordingState())
    
    val dialogState: StateFlow<DialogState> = _uiState
        .map { DialogState(
            showScheduleDialog = it.showScheduleDialog,
            editingScheduledMessage = it.editingScheduledMessage,
            showTemplateDialog = it.showTemplateDialog,
            showCreateTemplateDialog = it.showCreateTemplateDialog,
            showReactionPicker = it.showReactionPicker,
            selectedMessageForReaction = it.selectedMessageForReaction,
            showImageViewer = it.showImageViewer,
            imageViewerUrls = it.imageViewerUrls,
            imageViewerInitialIndex = it.imageViewerInitialIndex,
            showMessageActions = it.showMessageActions,
            selectedMessageForActions = it.selectedMessageForActions,
            showForwardDialog = it.showForwardDialog,
            messageToForward = it.messageToForward,
            showContactPickerDialog = it.showContactPickerDialog,
            pendingContactPreview = it.pendingContactPreview,
            showRecipientPickerDialog = it.showRecipientPickerDialog,
            showSmsSimPickerForSend = it.showSmsSimPickerForSend
        ) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DialogState())
    
    // ✅ P2: Contacts for forward dialog
    private val _contacts = MutableStateFlow<List<com.rasmi.purevon.domain.model.Contact>>(emptyList())
    val contacts: StateFlow<List<com.rasmi.purevon.domain.model.Contact>> = _contacts.asStateFlow()
    
    // Templates flow - exposed for UI
    val templates: Flow<List<com.rasmi.purevon.domain.model.MessageTemplate>> = templateOps.getAllTemplates()

    // ✅ FIX #12: Track last sync time to prevent duplicate syncs from EventBus + ContentObserver
    private val lastEventBusSyncTime = java.util.concurrent.atomic.AtomicLong(0L)
    
    // ============================================
    // Delegate Helpers (extracted from God class)
    // ============================================
    private val vCardBuilder = VCardBuilder(context)
    private val draftManager = DraftManager(context)
    private val audioRecordingDelegate = AudioRecordingDelegate(
        context, _uiState, viewModelScope,
        onSendMessage = { onEvent(ConversationUiEvent.SendMessage) }
    )
    private val locationSharingDelegate = LocationSharingDelegate(context, _uiState, viewModelScope)
    private val recipientAutocompleteDelegate = RecipientAutocompleteDelegate(
        _uiState, viewModelScope,
        contactOps.getAllContacts, contactOps.getAllConversations, contactOps.getContactByNumber,
        loadConversation = { threadId -> conversationLoaderDelegate.loadConversation(threadId) }
    )
    private val messageSchedulingDelegate = MessageSchedulingDelegate(
        context, _uiState, viewModelScope, scheduleOps.saveScheduledMessage, settingsDataStore
    )
    private val templateSelectionDelegate = TemplateSelectionDelegate(
        context, _uiState, viewModelScope, templateOps.addTemplate, templateOps.updateTemplateUsage, templateOps.deleteTemplate
    )
    private val messageSendingDelegate = MessageSendingDelegate(
        context, _uiState, viewModelScope,
        messageOps.sendMessage, messageOps.sendMmsMessage, messageOps.retryFailedMessage,
        settingsDataStore, messageOps.messageRetryManager, messageOps.syncThreadMessages, messageOps.getOrCreateThreadId,
        isSmsAskMode = { isSmsAskMode },
        cachedResolvedPhone = { cachedResolvedPhone },
        isValidPhoneNumber = ::isValidPhoneNumber,
        clearDraft = ::clearDraft,
        loadConversation = { threadId -> conversationLoaderDelegate.loadConversation(threadId) }
    )
    private val conversationLoaderDelegate = ConversationLoaderDelegate(
        _uiState, viewModelScope,
        messageOps.getMessagesByThread, messageOps.getMessagesByThreadPaged,
        messageOps.getAddressFromThreadId, contactOps.getContactByNumber,
        messageOps.markThreadAsRead, scheduleOps.scheduledMessageDao,
        isValidPhoneNumber = ::isValidPhoneNumber,
        isUsableAddress = ::isUsableAddress,
        loadDraft = ::loadDraft,
        getCachedResolvedPhone = { cachedResolvedPhone },
        setCachedResolvedPhone = { cachedResolvedPhone = it }
    )
    private val attachmentDelegate = AttachmentDelegate(
        context, _uiState, viewModelScope, vCardBuilder,
        loadContactsIfNeeded = ::loadContactsIfNeeded
    )
    private val messageActionsDelegate = MessageActionsDelegate(
        context, _uiState, viewModelScope, _contacts,
        messageOps.deleteConversation, messageOps.toggleMessageStarred, messageOps.deleteMessage,
        contactOps.toggleReaction, contactOps.removeReaction,
        messageOps.sendMessage, messageOps.sendMmsMessage, contactOps.getAllContacts, contactOps.soundManager
    )

    // SMS SIM state
    @Volatile private var isSmsAskMode: Boolean = false      // ✅ وضع ASK للرسائل
    @Volatile private var currentSmsSimIndex: Int = 0         // ✅ فهرس الشريحة الحالية
    @Volatile private var preAskSmsSimIndex: Int = 0          // ✅ FIX S6: فهرس الشريحة قبل وضع ASK
    
    companion object {
        private const val TAG = "ConversationViewModel"
    }
    
    init {
        observeSmsSim()
        // Observe SMS events using EventBus
        viewModelScope.launch {
            EventBus.events.collect { event ->
                try {
                when (event) {
                    is AppEvent.SmsReceived -> {
                        Log.d(TAG, "SMS received event from: ${event.phoneNumber}, threadId: ${event.threadId}")
                        // ✅ FIX: Use threadId for comparison (more reliable than phone number string match)
                        val currentThreadId = _uiState.value.threadId
                        // Reload if thread IDs match OR if phone numbers match (fallback)
                        if (currentThreadId == event.threadId || (currentThreadId != null && _uiState.value.phoneNumber == event.phoneNumber)) {
                            // ✅ CRITICAL: Cancel and reload the conversation to ensure immediate sync
                            Log.d(TAG, "Message received for active thread - reloading conversation")
                            // ✅ FIX #12: Mark sync time so ContentObserver skips redundant sync
                            lastEventBusSyncTime.set(System.currentTimeMillis())
                            val targetThreadId = currentThreadId ?: event.threadId
                            if (targetThreadId > 0) {
                                conversationLoaderDelegate.loadConversation(targetThreadId)
                            }
                        }
                    }
                    is AppEvent.SmsSent -> {
                        Log.d(TAG, "SMS/MMS sent event: success=${event.success}, uri=${event.messageUri}")
                        // ✅ FIX: Trigger immediate sync so status updates instantly
                        // Don't wait for ContentObserver debounce chain (1500ms+)
                        val currentThreadId = _uiState.value.threadId
                        if (currentThreadId != null && currentThreadId > 0) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    messageOps.syncThreadMessages(currentThreadId)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error syncing thread after send", e)
                                }
                            }
                        }
                    }
                    is AppEvent.SmsDelivered -> {
                        Log.d(TAG, "SMS delivered event: success=${event.success}, uri=${event.messageUri}")
                        val currentThreadId = _uiState.value.threadId
                        if (currentThreadId != null && currentThreadId > 0) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    messageOps.syncThreadMessages(currentThreadId)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error syncing thread after delivery", e)
                                }
                            }
                        }
                    }
                    else -> { /* Ignore other events */ }
                }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing EventBus event", e)
                }
            }
        }
        
        // Listen to global message changes (MMS / system-external changes).
        // Debounce 300ms for quick status updates while avoiding excessive syncs.
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            messageOps.getMessageChangeEvents()
                .debounce(300)
                .collect {
                    _uiState.value.threadId?.let { threadId ->
                        // ✅ FIX #12: Skip if EventBus already synced within last 500ms
                        val timeSinceEventBus = System.currentTimeMillis() - lastEventBusSyncTime.get()
                        if (timeSinceEventBus < 500) {
                            Log.d(TAG, "Global message change skipped — EventBus synced ${timeSinceEventBus}ms ago")
                            return@collect
                        }
                        Log.d(TAG, "Global message change — running safety sync for thread $threadId")
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                messageOps.syncThreadMessages(threadId)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in safety sync for thread $threadId", e)
                            }
                        }
                    }
                }
        }
        
        // ✅ P2: Contacts will be loaded on-demand when forward dialog opens
        // For now, keep empty list - will be populated from repository when needed
        
        // ✅ محسّن: حفظ المسودات مع Debounce 500ms لتوازن الأداء
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            uiState
                .map { it.messageText }
                .distinctUntilChanged()
                .debounce(500) // ✅ Debounce 500ms - يقلل العمليات من 1000+ إلى ~20 فقط
                .collect { text ->
                    val threadId = _uiState.value.threadId
                    val phoneNumber = _uiState.value.phoneNumber
                    
                    val draftKey = threadId?.toString() ?: phoneNumber ?: return@collect
                    
                    if (text.isNotBlank()) {
                        saveDraft(draftKey, text)
                    } else {
                        clearDraft(draftKey)
                    }
                }
        }
        
        // ✅ تنظيف الرسائل المؤقتة القديمة — مرة واحدة بعد 5 دقائق (event-driven بدل polling)
        // الرسائل المؤقتة تُزال تلقائياً عند وصول الرسالة الحقيقية (سطر ~937)
        // هذا فقط safety net للرسائل اليتيمة
        viewModelScope.launch {
            kotlinx.coroutines.delay(5 * 60 * 1000L) // 5 دقائق
            cleanupOldTempMessages()
        }

        // ✅ تنظيف ملفات vCard المؤقتة القديمة (Issue #6)
        cleanupVcfTempFiles()
    }
    
    override fun onCleared() {
        super.onCleared()
        // ✅ FIX #13: audioRecordingDelegate is the only delegate with owned resources (MediaRecorder).
        // Other delegates use viewModelScope which is auto-cancelled by super.onCleared().
        audioRecordingDelegate.onCleared()
        
        // ✅ FIX M23: Save draft on a background thread to avoid ANR on main thread
        val currentState = _uiState.value
        val draftKey = currentState.threadId?.toString() ?: currentState.phoneNumber
        if (draftKey != null && currentState.messageText.isNotBlank()) {
            // Use a non-cancellable scope since viewModelScope is already cancelled
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                draftManager.saveDraftSync(draftKey, currentState.messageText)
            }
        }
    }
    
    /**
     * حفظ المسودة
     */
    /**
     * تنظيف الرسائل المؤقتة القديمة (أكثر من 5 دقائق)
     */
    private fun cleanupOldTempMessages() {
        _uiState.update { state ->
            val now = System.currentTimeMillis()
            val filteredMessages = state.messages.filter { msg ->
                // احتفظ بالرسائل الحقيقية (ID موجب) دائماً
                if (msg.id > 0) return@filter true
                
                // احتفظ بالرسائل المجدولة (timestamp في المستقبل)
                if (msg.timestamp > now) return@filter true
                
                // احذف الرسائل المؤقتة الأقدم من 5 دقائق
                val age = now - msg.timestamp
                age < 5 * 60 * 1000 // 5 دقائق
            }
            
            if (filteredMessages.size < state.messages.size) {
                Log.d(TAG, "🧹 Cleaned ${state.messages.size - filteredMessages.size} old temp messages")
            }
            
            state.copy(messages = filteredMessages)
        }
    }
    
    // ============================================
    // Draft — delegated to DraftManager
    // ============================================
    private fun saveDraft(key: String, text: String) = draftManager.saveDraft(key, text, viewModelScope)
    private fun clearDraft(key: String) = draftManager.clearDraft(key, viewModelScope)
    private fun loadDraft(key: String): String? = draftManager.loadDraft(key)

    /**
     * ✅ Fix #10: Cancel a scheduled message from the conversation bubble
     */
    private fun cancelScheduledMessage(scheduleId: Long) {
        viewModelScope.launch {
            try {
                scheduleOps.messageScheduler.cancelScheduledMessage(scheduleId)
                scheduleOps.scheduledMessageDao.updateStatus(scheduleId, com.rasmi.purevon.data.local.entity.ScheduleStatus.CANCELLED)
                // Remove the temp message from UI
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.filter { it.scheduleId != scheduleId },
                        scheduledSuccess = context.getString(R.string.msg_scheduled_cancelled)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling scheduled message", e)
                _uiState.update { it.copy(error = context.getString(R.string.error_failed_cancel_scheduled, e.message ?: "")) }
            }
        }
    }

    /**
     * Fix #14: Edit a scheduled message — update body, time, repeat, and reschedule WorkManager.
     */
    private fun editScheduledMessage(
        scheduleId: Long,
        newBody: String,
        newScheduledTime: Long,
        newRepeatInterval: com.rasmi.purevon.domain.model.RepeatInterval
    ) {
        viewModelScope.launch {
            try {
                val existing = scheduleOps.scheduledMessageDao.getMessageById(scheduleId) ?: return@launch
                
                // 1) Cancel old WorkManager work
                scheduleOps.messageScheduler.cancelScheduledMessage(scheduleId)
                
                // 2) Update DB entity — convert domain RepeatInterval to data layer
                val updated = existing.copy(
                    messageBody = newBody,
                    scheduledTime = newScheduledTime,
                    repeatInterval = newRepeatInterval.toEntity()
                )
                scheduleOps.scheduledMessageDao.update(updated)
                
                // 3) Reschedule with new time via MessageScheduler
                scheduleOps.messageScheduler.scheduleMessage(
                    messageId = scheduleId,
                    recipient = existing.recipient,
                    messageBody = newBody,
                    conversationId = 0L,
                    scheduledTime = newScheduledTime
                )
                
                // 4) Update UI — replace temp message and dismiss dialog
                _uiState.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.scheduleId == scheduleId) {
                            msg.copy(
                                body = newBody,
                                scheduledTime = newScheduledTime,
                                timestamp = newScheduledTime
                            )
                        } else msg
                    }
                    state.copy(
                        messages = updatedMessages,
                        editingScheduledMessage = null,
                        scheduledSuccess = context.getString(R.string.success_scheduled_updated)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error editing scheduled message", e)
                _uiState.update { it.copy(error = context.getString(R.string.error_failed_edit_scheduled, e.message ?: ""), editingScheduledMessage = null) }
            }
        }
    }

    fun onEvent(event: ConversationUiEvent) {
        when (event) {
            is ConversationUiEvent.LoadConversation -> {
                conversationLoaderDelegate.loadConversation(event.threadId)
            }
            
            is ConversationUiEvent.SwitchSmsSim -> {
                viewModelScope.launch {
                    val sims = _uiState.value.availableSims
                    if (sims.isEmpty()) return@launch
                    when {
                        isSmsAskMode -> {
                            // ✅ FIX S6: ASK → الرجوع إلى الشريحة التالية بعد آخر شريحة مستخدمة (بدلاً من SIM1 دائماً)
                            isSmsAskMode = false
                            settingsDataStore.setSmsSimAskMode(false)
                            currentSmsSimIndex = (preAskSmsSimIndex + 1) % sims.size
                            val selectedSim = sims[currentSmsSimIndex]
                            settingsDataStore.setDefaultSmsSimSubscriptionId(selectedSim.subscriptionId)
                            _uiState.update { it.copy(smsSimLabel = formatSmsSimLabel(selectedSim, currentSmsSimIndex), isSmsAskMode = false) }
                        }
                        currentSmsSimIndex + 1 >= sims.size -> {
                            // آخر شريحة → وضع ASK
                            preAskSmsSimIndex = currentSmsSimIndex
                            isSmsAskMode = true
                            settingsDataStore.setSmsSimAskMode(true)
                            _uiState.update { it.copy(smsSimLabel = context.getString(R.string.msg_sim_ask_me), isSmsAskMode = true) }
                        }
                        else -> {
                            // الشريحة التالية
                            currentSmsSimIndex++
                            val selectedSim = sims[currentSmsSimIndex]
                            settingsDataStore.setDefaultSmsSimSubscriptionId(selectedSim.subscriptionId)
                            _uiState.update { it.copy(smsSimLabel = formatSmsSimLabel(selectedSim, currentSmsSimIndex), isSmsAskMode = false) }
                        }
                    }
                }
            }

            is ConversationUiEvent.SimSelectedForMessage -> {
                // ✅ المستخدم اختار الشريحة من نافذة ASK → أرسل الرسالة بها
                _uiState.update { it.copy(showSmsSimPickerForSend = false) }
                messageSendingDelegate.sendMessage(simSlotOverride = event.subscriptionId)
            }

            is ConversationUiEvent.DismissSmsSimPicker -> {
                // ✅ المستخدم أغلق نافذة ASK دون اختيار → ألغِ الإرسال
                _uiState.update { it.copy(showSmsSimPickerForSend = false) }
            }
            
            is ConversationUiEvent.SetPhoneNumber -> {
                // ✅ جديد: تحديد رقم الهاتف للمحادثة الجديدة + فلترة الاقتراحات
                _uiState.update { 
                    it.copy(
                        phoneNumber = event.phoneNumber,
                        isNewConversation = true
                    )
                }
                recipientAutocompleteDelegate.filterRecipientSuggestions(event.phoneNumber)
            }
            
            is ConversationUiEvent.MessageTextChanged -> {
                _uiState.update { it.copy(messageText = event.text) }
            }
            
            is ConversationUiEvent.SendMessage -> {
                messageSendingDelegate.sendMessage()
            }
            
            is ConversationUiEvent.AttachFile -> {
                attachmentDelegate.attachFile(event.attachment)
            }
            
            is ConversationUiEvent.RemoveAttachment -> {
                attachmentDelegate.removeAttachment(event.uri)
            }
            
            ConversationUiEvent.ClearAllAttachments -> {
                attachmentDelegate.clearAllAttachments()
            }
            
            ConversationUiEvent.ShareContact -> {
                attachmentDelegate.showContactPicker()
            }
            
            ConversationUiEvent.ShowContactPicker -> {
                attachmentDelegate.showContactPicker()
            }
            
            ConversationUiEvent.HideContactPicker -> {
                attachmentDelegate.hideContactPicker()
            }

            ConversationUiEvent.ShowRecipientPicker -> {
                _uiState.update { it.copy(showRecipientPickerDialog = true) }
                loadContactsIfNeeded()
            }

            ConversationUiEvent.HideRecipientPicker -> {
                _uiState.update { it.copy(showRecipientPickerDialog = false) }
            }

            is ConversationUiEvent.RecipientContactSelected -> {
                val phone = event.contact.phoneNumber
                _uiState.update {
                    it.copy(
                        showRecipientPickerDialog = false,
                        phoneNumber = phone,
                        recipientSuggestions = emptyList()
                    )
                }
                // ✅ Check if existing conversation exists for this contact
                recipientAutocompleteDelegate.resolveExistingThread(phone, event.contact.displayName)
            }

            is ConversationUiEvent.SelectRecipientSuggestion -> {
                val suggestion = event.suggestion
                _uiState.update {
                    it.copy(
                        phoneNumber = suggestion.phoneNumber,
                        contactName = suggestion.contactName,
                        recipientSuggestions = emptyList()
                    )
                }
                if (suggestion.existingThreadId != null && suggestion.existingThreadId > 0) {
                    // ✅ Load existing conversation directly
                    conversationLoaderDelegate.loadConversation(suggestion.existingThreadId)
                } else {
                    // ✅ No existing thread — stay in new conversation mode
                    recipientAutocompleteDelegate.resolveExistingThread(suggestion.phoneNumber, suggestion.contactName)
                }
            }

            is ConversationUiEvent.ContactSelected -> {
                attachmentDelegate.onContactSelected(event.contact)
            }
            
            ConversationUiEvent.ConfirmContactShare -> {
                attachmentDelegate.confirmContactShare()
            }
            
            ConversationUiEvent.DismissContactPreview -> {
                attachmentDelegate.dismissContactPreview()
            }
            
            is ConversationUiEvent.ShareLocation -> {
                locationSharingDelegate.handleShareLocation(event.hasPermission)
            }
            
            ConversationUiEvent.ShowScheduleDialog -> {
                _uiState.update { it.copy(showScheduleDialog = true) }
            }
            
            ConversationUiEvent.HideScheduleDialog -> {
                _uiState.update { it.copy(showScheduleDialog = false) }
            }
            
            is ConversationUiEvent.ScheduleMessage -> {
                messageSchedulingDelegate.scheduleMessage(event.scheduledTimeMillis, event.repeatInterval)
            }

            is ConversationUiEvent.CancelScheduledMessage -> {
                cancelScheduledMessage(event.scheduleId)
            }

            is ConversationUiEvent.EditScheduledMessage -> {
                val msg = event.message
                _uiState.update {
                    it.copy(
                        editingScheduledMessage = EditScheduledMessageData(
                            scheduleId = msg.scheduleId ?: 0L,
                            body = msg.body ?: "",
                            scheduledTime = msg.scheduledTime ?: System.currentTimeMillis(),
                            repeatInterval = com.rasmi.purevon.domain.model.RepeatInterval.NONE
                        )
                    )
                }
            }

            is ConversationUiEvent.ConfirmEditScheduledMessage -> {
                editScheduledMessage(
                    event.scheduleId, event.newBody, event.newScheduledTime, event.newRepeatInterval
                )
            }

            ConversationUiEvent.DismissEditScheduledMessage -> {
                _uiState.update { it.copy(editingScheduledMessage = null) }
            }

            ConversationUiEvent.ClearScheduledSuccess -> {
                _uiState.update { it.copy(scheduledSuccess = null) }
            }
            
            ConversationUiEvent.ShowTemplateDialog -> {
                _uiState.update { it.copy(showTemplateDialog = true) }
            }
            
            ConversationUiEvent.HideTemplateDialog -> {
                _uiState.update { it.copy(showTemplateDialog = false) }
            }
            
            is ConversationUiEvent.SelectTemplate -> {
                templateSelectionDelegate.selectTemplate(event.template)
            }
            
            is ConversationUiEvent.DeleteTemplate -> {
                templateSelectionDelegate.deleteTemplate(event.template)
            }
            
            ConversationUiEvent.ShowCreateTemplateDialog -> {
                _uiState.update { it.copy(showTemplateDialog = false, showCreateTemplateDialog = true) }
            }
            
            ConversationUiEvent.HideCreateTemplateDialog -> {
                _uiState.update { it.copy(showCreateTemplateDialog = false) }
            }
            
            is ConversationUiEvent.CreateTemplate -> {
                templateSelectionDelegate.createTemplate(event.title, event.content, event.emoji)
            }
            
            ConversationUiEvent.StartRecording -> {
                audioRecordingDelegate.startAudioRecording()
            }
            
            ConversationUiEvent.StopRecording -> {
                audioRecordingDelegate.stopAudioRecording()
            }
            
            ConversationUiEvent.CancelRecording -> {
                audioRecordingDelegate.cancelAudioRecording()
            }
            
            ConversationUiEvent.SendAudioMessage -> {
                audioRecordingDelegate.sendAudioMessage()
            }

            ConversationUiEvent.LockRecording -> {
                _uiState.update { it.copy(isRecordingLocked = true) }
            }

            ConversationUiEvent.StopAndSendRecording -> {
                // Release = stop recording & send immediately (no preview)
                audioRecordingDelegate.stopAndSendAudioRecording()
            }
            
            is ConversationUiEvent.DeleteConversation -> {
                messageActionsDelegate.deleteConversation(event.threadId)
            }
            
            is ConversationUiEvent.ToggleMessageFavorite -> {
                messageActionsDelegate.toggleFavorite(event.message.id)
            }
            
            is ConversationUiEvent.DeleteMessage -> {
                messageActionsDelegate.deleteMessage(event.message.id)
            }
            
            // ✅ NEW: Retry Failed Message
            is ConversationUiEvent.RetryMessage -> {
                messageSendingDelegate.retryMessage(event.messageId)
            }
            
            is ConversationUiEvent.CopyMessage -> {
                messageActionsDelegate.copyMessage(event.message.body)
            }
            
            is ConversationUiEvent.ReplyToMessage -> {
                messageActionsDelegate.setReplyTo(event.message)
            }
            
            ConversationUiEvent.CancelReply -> {
                messageActionsDelegate.cancelReply()
            }
            
            is ConversationUiEvent.ShowReactionPicker -> {
                messageActionsDelegate.showReactionPicker(event.message)
            }
            
            ConversationUiEvent.HideReactionPicker -> {
                messageActionsDelegate.hideReactionPicker()
            }
            
            is ConversationUiEvent.AddReaction -> {
                messageActionsDelegate.addReaction(event.message.id, event.emoji)
            }
            
            is ConversationUiEvent.RemoveReaction -> {
                messageActionsDelegate.removeReaction(event.message.id)
            }
            
            is ConversationUiEvent.ShowMessageActions -> {
                messageActionsDelegate.showMessageActions(event.message)
            }
            
            ConversationUiEvent.HideMessageActions -> {
                messageActionsDelegate.hideMessageActions()
            }
            
            is ConversationUiEvent.ShowImageViewer -> {
                messageActionsDelegate.showImageViewer(event.urls, event.initialIndex)
            }
            
            ConversationUiEvent.HideImageViewer -> {
                messageActionsDelegate.hideImageViewer()
            }
            
            is ConversationUiEvent.ShowForwardDialog -> {
                messageActionsDelegate.forwardMessage(event.message)
            }
            
            ConversationUiEvent.HideForwardDialog -> {
                messageActionsDelegate.hideForwardDialog()
            }
            
            is ConversationUiEvent.ConfirmForward -> {
                messageActionsDelegate.confirmForward(event.contacts)
            }
            
            ConversationUiEvent.ClearError -> {
                _uiState.update { it.copy(error = null) }
            }

        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SIM Management
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun observeSmsSim() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
        val sims = simManager.getAvailableSims()
        if (sims.isEmpty()) return
        viewModelScope.launch {
            try {
            combine(
                settingsDataStore.defaultSmsSimSubscriptionId,
                settingsDataStore.isSmsSimAskMode
            ) { savedId, isAsk -> Pair(savedId, isAsk) }
            .collect { (savedId, isAsk) ->
                isSmsAskMode = isAsk
                if (isAsk) {
                    _uiState.update { it.copy(smsSimLabel = context.getString(R.string.msg_sim_ask_me), isSmsAskMode = true, availableSims = sims) }
                } else {
                    val activeSim = sims.firstOrNull { it.subscriptionId == savedId } ?: sims.first()
                    currentSmsSimIndex = sims.indexOf(activeSim).coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            smsSimLabel = formatSmsSimLabel(activeSim, currentSmsSimIndex),
                            isSmsAskMode = false,
                            availableSims = sims
                        )
                    }
                }
            }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing SMS SIM setting", e)
            }
        }
    }

    /** Format SIM display label (e.g. "SIM1", "SIM2") */
    private fun formatSmsSimLabel(sim: com.rasmi.purevon.util.sim.SimInfo, index: Int): String {
        return sim.displayName.let { name ->
            when {
                name.contains("SIM 1", ignoreCase = true) || name.contains("SIM1", ignoreCase = true) -> "SIM1"
                name.contains("SIM 2", ignoreCase = true) || name.contains("SIM2", ignoreCase = true) -> "SIM2"
                else -> "SIM${index + 1}"
            }
        }
    }

    /**
     * Check if a phoneNumber is usable for sending (contains at least one digit).
     */
    private fun isValidPhoneNumber(phone: String?): Boolean {
        return !phone.isNullOrBlank() && phone.any { it.isDigit() }
    }

    /**
     * Check if an address is a usable identifier (phone number OR alphanumeric sender ID).
     */
    private fun isUsableAddress(addr: String?): Boolean {
        return !addr.isNullOrBlank() && !addr.equals("Unknown", ignoreCase = true)
    }

    /** Cached resolved phone number for the current thread */
    @Volatile private var cachedResolvedPhone: String? = null

    // ============================================
    // Delegated: loadConversation, loadConversationPaged → ConversationLoaderDelegate
    // Delegated: sendMessage, retryMessage → MessageSendingDelegate
    // ============================================
    fun loadConversationPaged(threadId: Long): Flow<PagingData<Message>> =
        conversationLoaderDelegate.loadConversationPaged(threadId)
    
    // ============================================
    // vCard — delegated to VCardBuilder
    // ============================================
    private fun buildVCardString(contact: com.rasmi.purevon.domain.model.Contact): String =
        vCardBuilder.buildVCardString(contact)
    
    /**
     * ✅ Load contacts with caching (Issue #8)
     * Only reloads if list is empty.
     */
    private fun loadContactsIfNeeded() {
        if (_contacts.value.isEmpty()) {
            viewModelScope.launch {
                _contacts.value = contactOps.getAllContacts().first()
            }
        }
    }
    
    /**
     * ✅ Clean up old .vcf temp files from cache (Issue #6)
     */
    private fun cleanupVcfTempFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                val now = System.currentTimeMillis()
                cacheDir.listFiles()?.filter { 
                    it.extension == "vcf" && (now - it.lastModified()) > 60 * 60 * 1000 // older than 1 hour
                }?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning vcf temp files", e)
            }
        }
    }
}
