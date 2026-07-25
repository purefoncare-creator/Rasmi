package com.rasmi.purevon.presentation.screen.incall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.repository.ContactRepository
import com.rasmi.purevon.domain.repository.CallLogRepository
import com.rasmi.purevon.domain.usecase.block.BlockNumberUseCase
import com.rasmi.purevon.domain.usecase.block.UnblockNumberUseCase
import com.rasmi.purevon.domain.usecase.contact.ToggleFavoriteUseCase
import com.rasmi.purevon.R
import com.rasmi.purevon.receiver.CallbackReminderReceiver
import com.rasmi.purevon.domain.call.InCallServiceBridge
import com.rasmi.purevon.util.CallbackReminderScheduleManager
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.util.PhoneUtil
import com.rasmi.purevon.util.audio.CallAudioManager
import com.rasmi.purevon.util.sim.SimCallRouter
import com.rasmi.purevon.util.sim.SimInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * ViewModel for InCall Screen
 */
@RequiresApi(Build.VERSION_CODES.M)
@HiltViewModel
class InCallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callAudioManager: CallAudioManager,
    private val contactRepository: ContactRepository,
    private val contactNoteDao: com.rasmi.purevon.data.local.dao.ContactNoteDao,
    private val callLogRepository: CallLogRepository,
    private val blockNumberUseCase: BlockNumberUseCase,
    private val unblockNumberUseCase: UnblockNumberUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val inCallBridge: InCallServiceBridge,
    private val simCallRouter: SimCallRouter
) : ViewModel() {
    
    // ✅ ContactResolver for system contact lookups (moved from direct ContentResolver)
    private val contactResolver = com.rasmi.purevon.data.repository.ContactResolver(context)
    
    companion object {
        private const val TAG = "InCallViewModel"
    }
    
    private val _uiState = MutableStateFlow(InCallUiState())
    val uiState: StateFlow<InCallUiState> = _uiState.asStateFlow()
    
    // ✅ UI Events for feedback and error handling
    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow: SharedFlow<UiEvent> = _uiEventFlow.asSharedFlow()
    
    private val callStartTime = AtomicLong(0L)
    private var timerJob: Job? = null // ✅ Track timer job for proper cancellation
    
    // ✅ Fix #1 & #5: حماية ضد النقر المتكرر على الأزرار الحساسة
    // ✅ FIX #37: Use AtomicLong for thread-safe access
    private val lastCriticalActionTime = AtomicLong(0L)
    private val criticalActionDebounceMs = 500L // نصف ثانية بين كل عملية حساسة
    
    // حماية ضد طلبات Add Call المتعددة المتزامنة
    private val isPlacingAddCall = AtomicBoolean(false)
    
    /**
     * ✅ يمنع تنفيذ العمليات الحساسة (EndCall, Answer, Swap, Merge) بشكل متكرر وسريع
     * يُرجع true إذا كان يجب تنفيذ العملية، false إذا كانت مكررة
     */
    private fun shouldExecuteCriticalAction(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastCriticalActionTime.get()
        if (now - last < criticalActionDebounceMs) {
            Log.d(TAG, "Critical action debounced (${now - last}ms since last)")
            return false
        }
        return lastCriticalActionTime.compareAndSet(last, now)
    }
    

    init {
        observeCallUpdates()
        startCallDurationTimer()
        // Observe audio state with debounce to prevent conflicts
        observeAudioStateWithDebounce()
        observeContacts()
        callAudioManager.setCallMode()
        // ✅ loadLastCallStatus يُستدعى من updateCallState() بعد تحديد رقم الهاتف
    }
    
    private fun observeCallUpdates() {
        viewModelScope.launch {
            // ✅ استخدام مستمع واحد فقط للحفاظ على الموارد
            inCallBridge.setStateChangeListener { call ->
                Log.d(TAG, "[EVENT] Call state changed: ${call.state}")
                updateCallState(call)
            }
            
            // ✅ تحديث مبدئي للحالة عند بدء التشغيل
            inCallBridge.getCurrentCall()?.let { updateCallState(it) }
        }
    }

    /**
     * تحديث حالة الواجهة بناءً على كائن المكالمة
     */
    private fun updateCallState(call: Call) {
        val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val state = call.details?.state ?: Call.STATE_NEW
        
        // Handle Disconnection
        if (state == Call.STATE_DISCONNECTED) {
            // ✅ التحقق: هل هناك مكالمات أخرى لا تزال نشطة؟
            val remainingCalls = inCallBridge.getActiveCalls()
                .filter { it != call && it.state != Call.STATE_DISCONNECTED }
            val serviceHeldCall = inCallBridge.getHeldCall()
            val serviceWaitingCall = inCallBridge.getWaitingCall()
            
            if (remainingCalls.isNotEmpty() || serviceHeldCall != null || serviceWaitingCall != null) {
                // ✅ هناك مكالمة أخرى - لا تغلق النشاط
                Log.d(TAG, "Call disconnected but other calls exist (${remainingCalls.size}) - staying open")
                
                // انتظر قليلاً ثم أعد قراءة الحالة من الخدمة
                viewModelScope.launch {
                    delay(300)
                    inCallBridge.getCurrentCall()?.let { currentCall ->
                        updateCallState(currentCall)
                    }
                }
                return
            }
            
            Log.d(TAG, "Call disconnected - no remaining calls, closing activity")
            com.rasmi.purevon.service.FloatingCallService.stop(context)
            stopCallDurationTimer()
            
             _uiState.update {
                it.copy(
                    callState = context.getString(R.string.call_state_ended),
                    isActive = false,
                    isRinging = false,
                    heldCall = null,
                    heldCallName = null,
                    waitingCall = null,
                    waitingCallName = null,
                    hasWaitingCall = false,
                    hasMultipleCalls = false,
                    isConference = false,
                    conferenceParticipants = emptyList(),
                    conferenceParticipantNames = emptyList()
                )
            }
            
            viewModelScope.launch {
                delay(1500)
                _uiEventFlow.emit(UiEvent.FinishActivity)
            }
            return
        }

        // Check if outgoing (Call.Details.DIRECTION_OUTGOING = 1)
        val isOutgoing = call.details?.callDirection == Call.Details.DIRECTION_OUTGOING
        
        // معلومات المكالمات الأخرى (انتظار / احتجاز)
        val heldCall = inCallBridge.getHeldCall()
        val waitingCall = inCallBridge.getWaitingCall()
        val hasMultipleCalls = inCallBridge.hasMultipleCalls()
        
        // ✅ معلومات المؤتمر
        val isConference = inCallBridge.isConferenceCall()
        val conferenceParticipants = inCallBridge.getConferenceParticipants()
        
        val heldNumber = heldCall?.details?.handle?.schemeSpecificPart
        
        val waitingNumber = waitingCall?.details?.handle?.schemeSpecificPart
        
        // Sync Audio State
        val service = inCallBridge
        val isMuted = service.isMuted()
        val isSpeakerOn = service.isSpeakerOn()
        val currentAudioRoute = service.getCurrentAudioRoute()
        val availableAudioRoutes = service.getAvailableAudioRoutes()
        callAudioManager.syncWithInCallService(isMuted, isSpeakerOn)
        
        // Get Start Time
        var startTime = _uiState.value.callStartTime
        if (state == Call.STATE_ACTIVE) {
            if (startTime == 0L) {
                startTime = android.os.SystemClock.elapsedRealtime()
                callStartTime.set(startTime)
            }
        }
        
        // Contact Name — use cached contacts only (synchronous, safe on Main thread)
        // queryContactNameFromSystem is launched asynchronously below to avoid Main-thread I/O
        val cachedContactName = findContactNameFromCache(phoneNumber)
        val contactName = cachedContactName

        // Load additional details if number changed or not loaded
        if (phoneNumber != "Unknown" && (_uiState.value.phoneNumber != phoneNumber || _uiState.value.callStatistics == null)) {
            loadContactDetails(phoneNumber)
            loadLastCallStatus(phoneNumber)
            loadExistingNotes(phoneNumber)
        }

        // ✅ If not in cache, look up from system on IO thread (avoids Main-thread ContentResolver query)
        if (cachedContactName == null && phoneNumber != "Unknown") {
            viewModelScope.launch {
                val systemName = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    queryContactNameFromSystem(phoneNumber)
                }
                if (systemName != null) {
                    _uiState.update { it.copy(contactName = systemName) }
                }
            }
        }

        val isOnHold = state == Call.STATE_HOLDING // ✅ مشتق من حالة النظام الفعلية

        _uiState.update {
            it.copy(
                phoneNumber = phoneNumber,
                contactName = contactName,
                callState = getCallStateName(state),
                isActive = state == Call.STATE_ACTIVE,
                isRinging = state == Call.STATE_RINGING,
                isOnHold = isOnHold,
                isOutgoing = isOutgoing,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                currentAudioRoute = currentAudioRoute,
                availableAudioRoutes = availableAudioRoutes,
                heldCall = heldNumber,
                // الحفاظ على الاسم إذا لم يتغير الرقم — يمنع الوميض
                heldCallName = if (heldNumber == it.heldCall) it.heldCallName else null,
                hasMultipleCalls = hasMultipleCalls,
                waitingCall = waitingNumber,
                waitingCallName = if (waitingNumber == it.waitingCall) it.waitingCallName else null,
                hasWaitingCall = waitingCall != null,
                isConference = isConference,
                conferenceParticipants = conferenceParticipants,
                callStartTime = startTime
            )
        }

        // Fetch names asynchronously
        // ✅ FIXED: Use Dispatchers.IO to avoid main-thread ContentResolver queries
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val heldName = heldNumber?.let { findContactName(it) }
            val waitingName = waitingNumber?.let { findContactName(it) }
            
            // ✅ جلب أسماء المشاركين في المؤتمر
            val participantNames = if (isConference && conferenceParticipants.isNotEmpty()) {
                conferenceParticipants.map { number -> findContactName(number) }
            } else {
                emptyList()
            }
            
            if (heldName != null || waitingName != null || participantNames.isNotEmpty()) {
                _uiState.update {
                   it.copy(
                       heldCallName = heldName ?: it.heldCallName,
                       waitingCallName = waitingName ?: it.waitingCallName,
                       conferenceParticipantNames = if (participantNames.isNotEmpty()) participantNames else it.conferenceParticipantNames
                   )
                }
            }
        }
        
        // Timer Logic
        if (state == Call.STATE_ACTIVE) {
             if (timerJob?.isActive != true) {
                startCallDurationTimer()
            }
        }
    }
    
    /**
     * Find contact name from cached contacts only (safe for Main thread).
     * Returns null if not in cache — caller must launch async IO to query system.
     */
    private fun findContactNameFromCache(phoneNumber: String): String? {
        val contacts = _uiState.value.contacts

        // Try exact match first
        val exactMatch = contacts.find { it.phoneNumber == phoneNumber }
        if (exactMatch != null) return exactMatch.displayName

        // Try matching last 10 digits (handles country codes)
        val normalizedNumber = phoneNumber.filter { it.isDigit() }.takeLast(10)
        if (normalizedNumber.length >= 7) {
            val partialMatch = contacts.find { contact ->
                contact.phoneNumber.filter { it.isDigit() }.takeLast(10) == normalizedNumber
            }
            if (partialMatch != null) return partialMatch.displayName
        }
        return null
    }

    /**
     * Find contact name: checks cache first, then queries system synchronously.
     * Only safe to call from a background (IO) coroutine.
     */
    private fun findContactName(phoneNumber: String): String? {
        return findContactNameFromCache(phoneNumber) ?: queryContactNameFromSystem(phoneNumber)
    }
    
    /**
     * Query contact name directly from system ContactsContract via ContactResolver
     */
    private fun queryContactNameFromSystem(phoneNumber: String): String? {
        return try {
            contactResolver.resolveContactName(phoneNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contact name", e)
            null
        }
    }
    
    private fun startCallDurationTimer() {
        // ✅ Cancel any existing timer first
        timerJob?.cancel()
        
        timerJob = viewModelScope.launch {
            while (callStartTime.get() > 0 && _uiState.value.isActive) {
                delay(1000)
                val duration = (android.os.SystemClock.elapsedRealtime() - callStartTime.get()) / 1000
                _uiState.update { it.copy(callDuration = duration) }
            }
            Log.d(TAG, "Call duration timer stopped")
        }
    }
    
    /**
     * ✅ Stop the call duration timer
     */
    private fun stopCallDurationTimer() {
        timerJob?.cancel()
        timerJob = null
        Log.d(TAG, "Call duration timer cancelled")
    }
    
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeAudioStateWithDebounce() {
        viewModelScope.launch {
            inCallBridge.muteState
                .collect { muted ->
                    _uiState.update { it.copy(isMuted = muted) }
                }
        }
        viewModelScope.launch {
            inCallBridge.speakerState
                .collect { speaker ->
                    _uiState.update { it.copy(isSpeakerOn = speaker) }
                }
        }
        viewModelScope.launch {
            inCallBridge.audioRouteState
                .collect { confirmedRoute ->
                    _uiState.update {
                        it.copy(currentAudioRoute = confirmedRoute)
                    }
                }
        }
    }
    
    private fun observeContacts() {
        viewModelScope.launch {
            _uiState
                .map { it.showAddCallDialog || it.showContactsDialog }
                .distinctUntilChanged()
                .collect { needsContacts ->
                    if (needsContacts && _uiState.value.contacts.isEmpty()) {
                        try {
                            val contacts = contactRepository.getAllContacts().first()
                            _uiState.update { it.copy(contacts = contacts) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading contacts", e)
                        }
                    } else if (!needsContacts && _uiState.value.contacts.isNotEmpty()) {
                        _uiState.update { it.copy(contacts = emptyList()) }
                    }
                }
        }
    }
    
    fun onEvent(event: InCallUiEvent) {
        when (event) {
            // ✅ Fix #1 & #5: العمليات الحساسة محمية بـ debounce
            InCallUiEvent.AnswerCall -> if (shouldExecuteCriticalAction()) answerCall()
            InCallUiEvent.ToggleMute -> toggleMute()
            InCallUiEvent.ToggleSpeaker -> toggleSpeaker()
            InCallUiEvent.ShowAudioRoutePicker -> showAudioRoutePicker()
            InCallUiEvent.HideAudioRoutePicker -> _uiState.update { it.copy(showAudioRoutePicker = false) }
            is InCallUiEvent.SelectAudioRoute -> selectAudioRoute(event.route)
            InCallUiEvent.ToggleHold -> toggleHold()
            InCallUiEvent.SwapCalls -> if (shouldExecuteCriticalAction()) swapCalls()
            InCallUiEvent.AnswerAndHold -> if (shouldExecuteCriticalAction()) answerAndHold()
            InCallUiEvent.RejectWaitingCall -> if (shouldExecuteCriticalAction()) rejectWaitingCall()
            InCallUiEvent.MergeCalls -> if (shouldExecuteCriticalAction()) mergeCalls()
            InCallUiEvent.EndCall -> if (shouldExecuteCriticalAction()) endCall()
            InCallUiEvent.EndHeldCall -> if (shouldExecuteCriticalAction()) endHeldCall()
            // ✅ Re-enabled Keypad Toggle
            InCallUiEvent.ToggleKeypad -> _uiState.update { it.copy(showKeypad = !it.showKeypad) }
            is InCallUiEvent.SendDtmfTone -> sendDtmfTone(event.digit)
            InCallUiEvent.ShowAddCall -> _uiState.update { it.copy(showAddCallDialog = true) }
            InCallUiEvent.DismissAddCallDialog -> _uiState.update { it.copy(showAddCallDialog = false) }
            InCallUiEvent.ShowContacts -> _uiState.update { it.copy(showContactsDialog = !it.showContactsDialog) }
            is InCallUiEvent.AddCallToNumber -> addCallToNumber(event.phoneNumber)
            is InCallUiEvent.AddCallToContact -> addCallToContact(event.contact)
            is InCallUiEvent.SimSelectedForAddCall -> {
                _uiState.update { it.copy(showSimPickerForAddCall = false, availableSimsForAddCall = emptyList()) }
                placeAddCall(event.phoneNumber, event.subscriptionId)
            }
            InCallUiEvent.DismissSimPickerForAddCall ->
                _uiState.update { it.copy(showSimPickerForAddCall = false, pendingAddCallNumber = "", availableSimsForAddCall = emptyList()) }
            InCallUiEvent.SilenceCall -> silenceCall()
            is InCallUiEvent.SendQuickMessage -> sendQuickMessage(event.message)
            // ✅ أحداث الميزات الجديدة
            is InCallUiEvent.UpdateCallNotes -> updateCallNotes(event.notes)
            InCallUiEvent.SaveCallNote -> saveCallNote()
            InCallUiEvent.ShowNewNoteInput -> _uiState.update { it.copy(showNewNoteInput = true, callNotes = "") }
            InCallUiEvent.HideNewNoteInput -> _uiState.update { it.copy(showNewNoteInput = false, callNotes = "") }
            is InCallUiEvent.SetCallbackReminder -> setCallbackReminder(event.minutes)
            InCallUiEvent.ShowCallbackReminderDialog -> showCallbackReminderDialog()
            InCallUiEvent.HideCallbackReminderDialog -> _uiState.update { it.copy(showCallbackReminder = false) }
            is InCallUiEvent.ChangeMiddleCardTab -> changeMiddleCardTab(event.tab)
            InCallUiEvent.SendLocation -> sendLocation()
            InCallUiEvent.ShareImage -> shareImage()
            InCallUiEvent.SendQuickSms -> showQuickSmsDialog()
            
            // ✅ New Actions Handlers
            InCallUiEvent.ToggleFavorite -> toggleFavorite()
            InCallUiEvent.ToggleBlock -> toggleBlock()
        }
    }

    private fun toggleFavorite() {
        val phoneNumber = _uiState.value.phoneNumber
        if (phoneNumber.isEmpty() || phoneNumber == "Unknown") return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isFavoriteLoading = true) }
            try {
                // Find contact ID first (needed for use case)
                val contact = contactRepository.getContactByNumber(phoneNumber)
                if (contact != null) {
                    val newStatus = !_uiState.value.isFavorite
                    toggleFavoriteUseCase(contact.id, newStatus)
                    _uiState.update { 
                        it.copy(
                            isFavorite = newStatus,
                            isFavoriteLoading = false
                        ) 
                    }
                    _uiEventFlow.emit(UiEvent.ShowSnackbar(if (newStatus) context.getString(R.string.success_added_to_favorites) else context.getString(R.string.success_removed_from_favorites)))
                } else {
                    _uiState.update { it.copy(isFavoriteLoading = false) }
                    _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_contact_not_found)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling favorite", e)
                _uiState.update { it.copy(isFavoriteLoading = false) }
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_favorite_update_failed)))
            }
        }
    }
    
    private fun toggleBlock() {
        val phoneNumber = _uiState.value.phoneNumber
        if (phoneNumber.isEmpty() || phoneNumber == "Unknown") return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isBlockLoading = true) }
            try {
                val isBlocked = _uiState.value.isBlocked
                if (isBlocked) {
                    unblockNumberUseCase(phoneNumber)
                    _uiState.update { it.copy(isBlocked = false, isBlockLoading = false) }
                    _uiEventFlow.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_msg_number_unblocked)))
                } else {
                    blockNumberUseCase(phoneNumber, context.getString(R.string.incall_block_reason_incall))
                    _uiState.update { it.copy(isBlocked = true, isBlockLoading = false) }
                    _uiEventFlow.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_msg_number_blocked)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling block", e)
                _uiState.update { it.copy(isBlockLoading = false) }
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_block_update_failed)))
            }
        }
    }

    /**
     * Load call statistics, favorite status, and block status
     */
    private fun loadContactDetails(phoneNumber: String) {
        if (phoneNumber.isEmpty() || phoneNumber == "Unknown") return
        
        viewModelScope.launch {
            // Load call stats via repository
            val stats = getCallStatisticsFromSystem(phoneNumber)
            
            // Load status
            var isFav = false
            var isBlk = false
            var photoUri: String? = null
            
            try {
                 // Check favorite using repository
                 val contact = contactRepository.getContactByNumber(phoneNumber)
                 isFav = contact?.isFavorite == true
                 isBlk = contact?.isBlocked == true
                 photoUri = contact?.photoUri
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contact status", e)
            }

            // Fallback: look up photo via PhoneLookup if not found via contact
            if (photoUri == null) {
                photoUri = withContext(Dispatchers.IO) {
                    contactResolver.resolveContactPhotoUri(phoneNumber)
                }
            }
            
            _uiState.update { 
                it.copy(
                    callStatistics = stats,
                    isFavorite = isFav,
                    isBlocked = isBlk,
                    contactPhotoUri = photoUri
                ) 
            }
        }
    }

    /**
     * Get call statistics from system call log via repository
     */
    private suspend fun getCallStatisticsFromSystem(phoneNumber: String): CallStatistics {
        val stats = callLogRepository.getSystemCallStatisticsForNumber(phoneNumber)
        return CallStatistics(
            incomingCalls = stats.incomingCalls,
            outgoingCalls = stats.outgoingCalls,
            missedCalls = stats.missedCalls,
            totalCalls = stats.totalCalls,
            totalDurationSeconds = stats.totalDurationSeconds,
            lastCallTimestamp = stats.lastCallTimestamp
        )
    }
    
    private fun answerCall() {
        Log.d(TAG, "Answering call")
        try {
            // Get current call from InCallService - NOT local variable
            val call = inCallBridge.getCurrentCall()
            if (call != null) {
                call.answer(0) // 0 = video state AUDIO_ONLY
                Log.d(TAG, "Call answered successfully")
                
                // Update UI immediately to show call is being answered
                _uiState.update { 
                    it.copy(
                        callState = context.getString(R.string.incall_status_answering),
                        isActive = false // Will be set to true when STATE_ACTIVE is reached
                    )
                }
                
                // Force immediate state check after a short delay
                viewModelScope.launch {
                    delay(200) // Small delay to allow state transition
                    inCallBridge.getCurrentCall()?.let { currentCall ->
                        updateCallState(currentCall)
                    }
                }
            } else {
                Log.w(TAG, "No current call to answer")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call", e)
        }
    }
    
    private fun toggleMute() {
        Log.d(TAG, "Toggling mute - Current state: ${_uiState.value.isMuted}")
        
        viewModelScope.launch {
            try {
                // ✅ Use InCallService as primary source of truth
                val service = inCallBridge
                val newMuteState = service.toggleMute()
                
                // Sync with AudioManager
                callAudioManager.setMute(newMuteState)
                
                _uiState.update { it.copy(isMuted = newMuteState) }
                
                Log.d(TAG, "Mute state updated via InCallService: $newMuteState")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling mute", e)
                // ✅ Show error to user
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_mute_toggle_failed)))
            }
        }
    }
    
    private fun toggleSpeaker() {
        Log.d(TAG, "Toggling speaker - Current state: ${_uiState.value.isSpeakerOn}")
        
        viewModelScope.launch {
            try {
                val service = inCallBridge
                val newSpeakerState = service.toggleSpeaker()
                callAudioManager.syncWithInCallService(
                    isMuted = service.isMuted(),
                    isSpeakerOn = newSpeakerState
                )
                // ✅ FIX: Set currentAudioRoute optimistically.
                // service.getCurrentAudioRoute() is stale because setAudioRoute() is async.
                val optimisticRoute = if (newSpeakerState) {
                    android.telecom.CallAudioState.ROUTE_SPEAKER
                } else {
                    // When turning off speaker, the service picks BT > Headset > Earpiece.
                    // Query from service — if still SPEAKER (stale), fall back to EARPIECE.
                    val queried = service.getCurrentAudioRoute()
                    if (queried == android.telecom.CallAudioState.ROUTE_SPEAKER) {
                        android.telecom.CallAudioState.ROUTE_EARPIECE
                    } else {
                        queried
                    }
                }
                _uiState.update { it.copy(
                    isSpeakerOn = newSpeakerState,
                    currentAudioRoute = optimisticRoute,
                    availableAudioRoutes = service.getAvailableAudioRoutes()
                ) }
                Log.d(TAG, "Speaker state updated via InCallService: $newSpeakerState, route: $optimisticRoute")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling speaker", e)
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_speaker_toggle_failed)))
            }
        }
    }
    
    private fun showAudioRoutePicker() {
        val service = inCallBridge
        _uiState.update { it.copy(
            showAudioRoutePicker = true,
            currentAudioRoute = service.getCurrentAudioRoute(),
            availableAudioRoutes = service.getAvailableAudioRoutes()
        ) }
    }
    
    @Suppress("DEPRECATION")
    private fun selectAudioRoute(route: Int) {
        viewModelScope.launch {
            try {
                val service = inCallBridge
                service.setAudioRoute(route)
                val isSpeaker = route == android.telecom.CallAudioState.ROUTE_SPEAKER
                callAudioManager.syncWithInCallService(
                    isMuted = service.isMuted(),
                    isSpeakerOn = isSpeaker
                )
                // ✅ FIX: Set route optimistically (don't query stale callAudioState)
                _uiState.update { it.copy(
                    isSpeakerOn = isSpeaker,
                    currentAudioRoute = route,
                    showAudioRoutePicker = false
                ) }
                Log.d(TAG, "Audio route changed to: $route")
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting audio route", e)
            }
        }
    }
    
    private fun toggleHold() {
        Log.d(TAG, "Toggling hold")
        val call = inCallBridge.getCurrentCall()
        if (call != null) {
            try {
                val callState = call.state
                
                // ✅ التحقق: Hold فقط إذا كانت المكالمة نشطة أو محتجزة
                if (callState != Call.STATE_ACTIVE && callState != Call.STATE_HOLDING) {
                    Log.w(TAG, "Cannot hold call in state: $callState")
                    return
                }
                
                // Atomic read+update to prevent TOCTOU race
                val previousIsOnHold = _uiState.value.isOnHold
                _uiState.update { it.copy(isOnHold = !previousIsOnHold) }
                val wasOnHold = previousIsOnHold
                
                if (wasOnHold) {
                    Log.d(TAG, "Unholding call")
                    call.unhold()
                } else {
                    Log.d(TAG, "Holding call")
                    call.hold()
                }
                Log.d(TAG, "Hold state: $wasOnHold")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling hold", e)
            }
        } else {
            Log.w(TAG, "No current call to hold/unhold")
        }
    }
    
    // ✅ التبديل بين المكالمات
    private fun swapCalls() {
        Log.d(TAG, "Swapping calls")
        viewModelScope.launch {
            try {
                inCallBridge.swapCalls()
            } catch (e: Exception) {
                Log.e(TAG, "Error swapping calls", e)
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_swap_failed)))
            }
        }
    }
    
    // ✅ الرد على المكالمة المنتظرة مع احتجاز الحالية
    private fun answerAndHold() {
        Log.d(TAG, "Answering waiting call and holding current")
        viewModelScope.launch {
            try {
                val success = inCallBridge.answerAndHold() ?: false
                if (success) {
                    _uiState.update { 
                        it.copy(
                            hasWaitingCall = false,
                            waitingCall = null,
                            waitingCallName = null
                        )
                    }
                } else {
                    _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_no_waiting_call)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error answering and holding", e)
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_answer_waiting_failed)))
            }
        }
    }
    
    // ✅ رفض المكالمة المنتظرة
    private fun rejectWaitingCall() {
        Log.d(TAG, "Rejecting waiting call")
        viewModelScope.launch {
            try {
                val success = inCallBridge.rejectWaitingCall() ?: false
                if (success) {
                    _uiState.update { 
                        it.copy(
                            hasWaitingCall = false,
                            waitingCall = null,
                            waitingCallName = null
                        )
                    }
                } else {
                    _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_no_waiting_call)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting waiting call", e)
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_reject_failed)))
            }
        }
    }
    
    // ✅ دمج المكالمات في مؤتمر
    private fun mergeCalls() {
        Log.d(TAG, "Merging calls")
        viewModelScope.launch {
            try {
                val success = inCallBridge.mergeCalls() ?: false
                if (!success) {
                    _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_merge_not_supported)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error merging calls", e)
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_merge_failed)))
            }
        }
    }
    
    private fun endCall() {
        Log.d(TAG, "Ending call")
        
        // ✅ Fix #1: تعطيل الأزرار فوراً لمنع النقر المتكرر
        _uiState.update { it.copy(isEndingCall = true) }
        
        try {
            val call = inCallBridge.getCurrentCall()
            // ✅ إذا كانت المكالمة لم تُجب بعد (لا تزال ترن) → سجّل الرفض فوراً
            // لأن call.disconnect() قد يُنتج DisconnectCause.LOCAL وليس REJECTED
            if (!inCallBridge.wasCallActive) {
                inCallBridge.setCallRejectedByUser(true)
                Log.d(TAG, "endCall: call was ringing → marking as rejected by user BEFORE disconnect")
            }
            call?.disconnect()
            callStartTime.set(0)
            stopCallDurationTimer() // ✅ Stop timer when call ends
            callAudioManager.resetAudioMode()
            
            // ✅ لا نستدعي finish() هنا مباشرة
            // updateCallState() سيتعامل مع إغلاق النشاط عند حالة DISCONNECTED
            // هذا يمنع ظهور الشريط العائم للحظة بسبب سباق بين onPause و disconnect
            Log.d(TAG, "Call disconnect requested - activity will close on DISCONNECTED state")
            
            // 🛡️ معالج أمان احتياطي: إذا لم تغلق الشاشة خلال 1.2 ثانية، أغلقها تلقائياً
            viewModelScope.launch {
                delay(1200)
                _uiEventFlow.emit(UiEvent.FinishActivity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
            // ✅ إعادة تفعيل الأزرار في حالة الخطأ
            _uiState.update { it.copy(isEndingCall = false) }
        }
    }

    /** ✅ Disconnect the held (second) call without touching the active call. */
    private fun endHeldCall() {
        Log.d(TAG, "Ending held call")
        try {
            val held = inCallBridge.getHeldCall()
            if (held == null) {
                Log.w(TAG, "endHeldCall: no held call to disconnect")
                return
            }
            held.disconnect()
            Log.d(TAG, "Held call disconnect requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error ending held call", e)
        }
    }
    
    private fun sendDtmfTone(digit: Char) {
        Log.d(TAG, "Sending DTMF tone: $digit")
        val call = inCallBridge.getCurrentCall()
        if (call != null) {
            try {
                call.playDtmfTone(digit)
                // Stop the tone after 200ms
                viewModelScope.launch {
                    delay(200)
                    call.stopDtmfTone()
                }
                Log.d(TAG, "DTMF tone sent: $digit")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending DTMF tone", e)
            }
        } else {
            Log.w(TAG, "No current call to send DTMF tone")
        }
    }
    
    private fun addCallToNumber(phoneNumber: String) {
        Log.d(TAG, "Adding call to number: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
        _uiState.update { it.copy(showAddCallDialog = false) }
        viewModelScope.launch {
            try {
                when (val route = simCallRouter.resolveRoute()) {
                    is SimCallRouter.CallRoute.AskSim -> {
                        _uiState.update {
                            it.copy(
                                pendingAddCallNumber = phoneNumber,
                                availableSimsForAddCall = route.availableSims,
                                showSimPickerForAddCall = true
                            )
                        }
                    }
                    is SimCallRouter.CallRoute.Direct -> {
                        placeAddCall(phoneNumber, route.subscriptionId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[ADD_CALL] Error resolving SIM route", e)
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_add_call_failed, e.message ?: "")))
            }
        }
    }

    private fun placeAddCall(phoneNumber: String, subscriptionId: Int?) {
        if (!isPlacingAddCall.compareAndSet(false, true)) {
            Log.w(TAG, "[ADD_CALL] Already placing a call — ignoring duplicate request")
            return
        }
        Log.d(TAG, "[ADD_CALL] Placing call to ${DebugLogger.maskPhoneNumber(phoneNumber)}, subscriptionId=$subscriptionId")
        viewModelScope.launch {
            try {
                val held = inCallBridge.holdCurrentCall()
                if (!held) {
                    Log.e(TAG, "[ADD_CALL] holdCurrentCall() failed — aborting")
                    _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_add_call_failed, "")))
                    return@launch
                }
                _uiState.update { it.copy(isOnHold = true, pendingAddCallNumber = "") }
                delay(300)

                PhoneUtil.makeCall(context, phoneNumber, subscriptionId)
                Log.d(TAG, "[ADD_CALL] Call placed successfully")
            } catch (e: SecurityException) {
                Log.e(TAG, "[ADD_CALL] SecurityException - missing CALL_PHONE permission", e)
                inCallBridge.unholdCall()
                _uiState.update { it.copy(isOnHold = false) }
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_no_call_permission)))
            } catch (e: Exception) {
                Log.e(TAG, "[ADD_CALL] Error placing call", e)
                inCallBridge.unholdCall()
                _uiState.update { it.copy(isOnHold = false) }
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_error_add_call_failed, e.message ?: "")))
            } finally {
                isPlacingAddCall.set(false)
            }
        }
    }
    
    private fun addCallToContact(contact: Contact) {
        Log.d(TAG, "Adding call to contact: ${contact.name}")
        addCallToNumber(contact.phoneNumber)
    }
    
    /**
     * Silence the incoming call:
     * 1. Mute the ringtone for THIS CALL ONLY (not the whole phone)
     * 2. Close the InCall screen (go to background)
     * 3. Call continues ringing silently - caller keeps waiting
     */
    private fun silenceCall() {
        Log.d(TAG, "Silencing call - muting THIS call only and going to background")
        try {
            // 1. Silence THIS call only using TelecomManager API
            // This does NOT change the phone's ringer mode - only silences current call
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.silenceRinger()
            Log.d(TAG, "Call silenced using TelecomManager.silenceRinger() - phone ringer mode unchanged")
            
            // ✅ Fix #11: تتبع حالة الإسكات لتعطيل زر الإسكات عند العودة
            _uiState.update { it.copy(isSilenced = true) }
            
            // 2. Move activity to background (don't finish it - call keeps ringing silently)
            viewModelScope.launch { _uiEventFlow.emit(UiEvent.MoveToBackground) }
            Log.d(TAG, "Activity moved to background - caller still waiting")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error silencing call", e)
            // Fallback: try to move to background anyway
            viewModelScope.launch { _uiEventFlow.emit(UiEvent.MoveToBackground) }
        }
    }
    
    private fun sendQuickMessage(message: String) {
        Log.d(TAG, "Quick message sent: $message")
        viewModelScope.launch {
            delay(500)
            endCall()
        }
    }
    
    // ✅ دوال الميزات الجديدة
    private fun updateCallNotes(notes: String) {
        Log.d(TAG, "Updating call notes: $notes")
        _uiState.update { it.copy(callNotes = notes) }
    }
    
    private fun saveCallNote() {
        val notes = _uiState.value.callNotes.trim()
        if (notes.isEmpty()) {
            Log.d(TAG, "Note is empty, not saving")
            return
        }
        
        val phoneNumber = _uiState.value.phoneNumber
        if (phoneNumber.isEmpty() || phoneNumber == "Unknown") {
            Log.w(TAG, "Invalid phone number, cannot save note")
            viewModelScope.launch {
                _uiEventFlow.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_error_note_save_invalid_number)))
            }
            return
        }
        
        viewModelScope.launch {
            try {
                // ✅ FIX: Normalize phone number to digits-only (last 10) for consistent matching
                val normalizedPhone = phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
                val note = com.rasmi.purevon.data.local.entity.ContactNoteEntity(
                    phoneNumber = normalizedPhone,
                    note = notes,
                    callDuration = _uiState.value.callDuration,
                    isIncoming = !_uiState.value.isOutgoing,
                    createdAt = System.currentTimeMillis()
                )
                
                contactNoteDao.insertNote(note)
                Log.d(TAG, "Note saved successfully for ${DebugLogger.maskPhoneNumber(phoneNumber)}")

                // إعادة تحميل الملاحظات وإخفاء حقل الإدخال
                val updatedNotes = contactNoteDao.getNotesByPhoneNumberSync(normalizedPhone)
                _uiState.update {
                    it.copy(
                        callNotes = "",
                        existingNotes = updatedNotes,
                        showNewNoteInput = false
                    )
                }
                _uiEventFlow.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_msg_note_saved)))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving note", e)
                _uiEventFlow.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_error_note_save_failed)))
            }
        }
    }
    
    private fun setCallbackReminder(minutes: Int) {
        Log.d(TAG, "Setting callback reminder for $minutes minutes")
        
        // ✅ إغلاق الحوار فوراً بشكل مباشر (false ثابتة ليس toggle)
        _uiState.update { it.copy(showCallbackReminder = false) }
        
        val phoneNumber = _uiState.value.phoneNumber
        val contactName = _uiState.value.contactName
        
        if (phoneNumber.isEmpty() || phoneNumber == "Unknown") {
            viewModelScope.launch {
                _uiEventFlow.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_error_reminder_invalid_number)))
            }
            return
        }
        
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // ✅ requestCode فريد بناءً على الوقت لدعم تذكيرات متعددة لنفس الرقم
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
            
            // ✅ حفظ في سجل الجدولة
            CallbackReminderScheduleManager.add(
                context, requestCode, phoneNumber,
                contactName ?: "Unknown", triggerTime
            )
            
            Log.d(TAG, "Callback reminder scheduled: code=$requestCode, exact=$isExact, trigger=$triggerTime")
            
            viewModelScope.launch {
                delay(200)
                val label = if (minutes >= 60) context.getString(R.string.incall_msg_reminder_set_hours, minutes / 60)
                             else context.getString(R.string.incall_msg_reminder_set_minutes, minutes)
                val warning = if (!isExact) context.getString(R.string.incall_msg_reminder_approximate_warning) else ""
                _uiEventFlow.emit(UiEvent.ShowSnackbar(label + warning))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling callback reminder", e)
            viewModelScope.launch {
                _uiEventFlow.emit(UiEvent.ShowSnackbar(context.getString(R.string.incall_error_reminder_failed)))
            }
        }
    }
    
    private fun showCallbackReminderDialog() {
        _uiState.update { it.copy(showCallbackReminder = true) }
    }
    
    private fun changeMiddleCardTab(tab: Int) {
        _uiState.update { it.copy(middleCardTab = tab) }
    }
    
    private fun sendLocation() {
        Log.d(TAG, "Sending location")
        viewModelScope.launch {
            try {
                // ✅ هنا يمكنك إضافة منطق مشاركة الموقع
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_msg_location_coming_soon)))
            } catch (e: Exception) {
                Log.e(TAG, "Error sending location", e)
            }
        }
    }
    
    private fun shareImage() {
        Log.d(TAG, "Sharing image")
        viewModelScope.launch {
            try {
                // ✅ هنا يمكنك إضافة منطق مشاركة الصور
                _uiEventFlow.emit(UiEvent.ShowError(context.getString(R.string.incall_msg_image_sharing_coming_soon)))
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing image", e)
            }
        }
    }
    
    private fun showQuickSmsDialog() {
        // ✅ هنا يمكنك فتح حوار رسالة سريعة
        Log.d(TAG, "Opening quick SMS dialog")
    }
    
    private fun loadExistingNotes(phoneNumber: String) {
        if (phoneNumber.isEmpty() || phoneNumber == "Unknown") return
        viewModelScope.launch {
            try {
                val normalized = phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
                val notes = contactNoteDao.getNotesByPhoneNumberSync(normalized)
                _uiState.update {
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

    // ✅ تحميل آخر مكالمة من هذا الرقم - يُستدعى من updateCallState عند اكتشاف رقم جديد
    private fun loadLastCallStatus(phoneNumber: String) {
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
                    _uiState.update {
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
    
    override fun onCleared() {
        super.onCleared()
        inCallBridge.setStateChangeListener(null)
        stopCallDurationTimer() // Ensure timer is stopped
        callAudioManager.resetAudioMode()
        Log.d(TAG, "ViewModel cleared - resources cleaned up")
    }
    
    private fun getCallStateName(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> context.getString(R.string.call_state_calling)
            Call.STATE_RINGING -> context.getString(R.string.call_state_ringing)
            Call.STATE_DIALING -> context.getString(R.string.call_state_calling)
            Call.STATE_SELECT_PHONE_ACCOUNT -> context.getString(R.string.call_state_selecting_sim)
            Call.STATE_ACTIVE -> context.getString(R.string.call_state_connected)
            Call.STATE_HOLDING -> context.getString(R.string.call_state_on_hold)
            Call.STATE_DISCONNECTED -> context.getString(R.string.call_state_ended)
            Call.STATE_CONNECTING -> context.getString(R.string.call_state_calling)
            Call.STATE_DISCONNECTING -> context.getString(R.string.call_state_ending)
            else -> context.getString(R.string.call_state_unknown)
        }
    }
}

/**
 * UI State for InCall Screen
 */
data class InCallUiState(
    val phoneNumber: String = "",
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val callState: String = "",
    val isRinging: Boolean = false,
    val callDuration: Long = 0, // مدة المكالمة بالثواني
    val callStartTime: Long = 0L, // ✅ وقت بدء المكالمة
    val isActive: Boolean = false,
    val isOutgoing: Boolean = false, // هل المكالمة صادرة
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    // ✅ Audio route picker state
    val currentAudioRoute: Int = android.telecom.CallAudioState.ROUTE_EARPIECE,
    val availableAudioRoutes: Int = android.telecom.CallAudioState.ROUTE_EARPIECE,
    val showAudioRoutePicker: Boolean = false,
    val isOnHold: Boolean = false,
    // ✅ دعم المكالمات المتعددة
    val heldCall: String? = null, // رقم المكالمة المحتجزة
    val heldCallName: String? = null, // اسم المكالمة المحتجزة
    val hasMultipleCalls: Boolean = false, // هل هناك أكثر من مكالمة
    // ✅ دعم المكالمة المنتظرة (الواردة أثناء مكالمة)
    val waitingCall: String? = null, // رقم المكالمة المنتظرة
    val waitingCallName: String? = null, // اسم المكالمة المنتظرة
    val hasWaitingCall: Boolean = false, // هل هناك مكالمة منتظرة
    // ✅ دعم المؤتمر (Conference Call)
    val isConference: Boolean = false, // هل المكالمة مؤتمر
    val conferenceParticipants: List<String> = emptyList(), // أرقام المشاركين
    val conferenceParticipantNames: List<String?> = emptyList(), // أسماء المشاركين
    // ✅ Re-enabled for In-Call Dialpad
    val showKeypad: Boolean = false,
    val showAddCallDialog: Boolean = false,
    val showContactsDialog: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    // ✅ ميزات البطاقة الوسطى
    val callNotes: String = "", // الملاحظات السريعة
    val existingNotes: List<com.rasmi.purevon.data.local.entity.ContactNoteEntity> = emptyList(),
    val showNewNoteInput: Boolean = false, // عرض حقل إضافة ملاحظة جديدة
    val lastCallStatus: String? = null,
    val lastCallType: Int? = null,
    val lastCallTime: Long? = null,
    val showCallbackReminder: Boolean = false, // إظهار حوار التذكير
    val middleCardTab: Int = 0, // 0=Notes, 1=LastCall, 2=Actions
    
    // ✅ Call Statistics & Contact Status
    val callStatistics: CallStatistics? = null,
    val isBlocked: Boolean = false,
    val isFavorite: Boolean = false,
    val isFavoriteLoading: Boolean = false,
    val isBlockLoading: Boolean = false,
    // ✅ Fix #1: حالة إنهاء المكالمة - لمنع النقر المتكرر على الأزرار
    val isEndingCall: Boolean = false,
    // ✅ Fix #11: تتبع حالة الإسكات
    val isSilenced: Boolean = false,
    // SIM picker for Add Call
    val showSimPickerForAddCall: Boolean = false,
    val pendingAddCallNumber: String = "",
    val availableSimsForAddCall: List<SimInfo> = emptyList(),
)

/**
 * Call statistics for a contact (duplicated from ContactDetail for independence)
 */
data class CallStatistics(
    val incomingCalls: Int = 0,
    val outgoingCalls: Int = 0,
    val missedCalls: Int = 0,
    val totalCalls: Int = 0,
    val totalDurationSeconds: Long = 0,
    val lastCallTimestamp: Long? = null
) {
    /**
     * Format total duration as readable string
     */
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
    data object SwapCalls : InCallUiEvent() // ✅ التبديل بين المكالمات
    data object AnswerAndHold : InCallUiEvent() // ✅ الرد على المكالمة المنتظرة واحتجاز الحالية
    data object RejectWaitingCall : InCallUiEvent() // ✅ رفض المكالمة المنتظرة
    data object MergeCalls : InCallUiEvent() // ✅ دمج المكالمات في مؤتمر
    data object EndCall : InCallUiEvent()
    data object EndHeldCall : InCallUiEvent() // ✅ إنهاء المكالمة المحتجزة مباشرةً
    // ✅ Re-enabled Keypad Toggle
    data object ToggleKeypad : InCallUiEvent()
    data class SendDtmfTone(val digit: Char) : InCallUiEvent()
    data object ShowAddCall : InCallUiEvent()
    data object ShowContacts : InCallUiEvent()
    data class AddCallToNumber(val phoneNumber: String) : InCallUiEvent()
    data class AddCallToContact(val contact: Contact) : InCallUiEvent()
    data object SilenceCall : InCallUiEvent()
    data class SendQuickMessage(val message: String) : InCallUiEvent()
    // ✅ أحداث الميزات الجديدة
    data class UpdateCallNotes(val notes: String) : InCallUiEvent()
    data object SaveCallNote : InCallUiEvent()
    data object ShowNewNoteInput : InCallUiEvent()
    data object HideNewNoteInput : InCallUiEvent()
    data class SetCallbackReminder(val minutes: Int) : InCallUiEvent()
    data object ShowCallbackReminderDialog : InCallUiEvent()
    data object HideCallbackReminderDialog : InCallUiEvent()
    data class ChangeMiddleCardTab(val tab: Int) : InCallUiEvent()
    data object SendLocation : InCallUiEvent()
    data object ShareImage : InCallUiEvent()
    data object SendQuickSms : InCallUiEvent()
    
    // ✅ New Actions
    data object ToggleFavorite : InCallUiEvent()
    data object ToggleBlock : InCallUiEvent()
    // SIM selection for Add Call
    data class SimSelectedForAddCall(val phoneNumber: String, val subscriptionId: Int?) : InCallUiEvent()
    data object DismissSimPickerForAddCall : InCallUiEvent()
    data object DismissAddCallDialog : InCallUiEvent()
}

/**
 * ✅ UI Events for user feedback
 */
sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
    data class ShowError(val message: String) : UiEvent()
    data class ShowToast(val message: String) : UiEvent()
    data object FinishActivity : UiEvent()
    data object MoveToBackground : UiEvent()
}
