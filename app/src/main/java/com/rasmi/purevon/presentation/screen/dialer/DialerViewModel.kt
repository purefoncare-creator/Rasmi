package com.rasmi.purevon.presentation.screen.dialer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.usecase.contact.SearchContactsUseCase
import com.rasmi.purevon.util.T9SearchUtil
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.util.error.ErrorMapper
import com.rasmi.purevon.util.event.EventBus
import com.rasmi.purevon.util.event.AppEvent
import com.rasmi.purevon.util.sim.SimManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Dialer Screen
 */
@HiltViewModel
class DialerViewModel @Inject constructor(
    private val searchContactsUseCase: SearchContactsUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val simManager: SimManager,
    private val callLogRepository: com.rasmi.purevon.domain.repository.CallLogRepository, // ✅ إضافة repository
    private val blockRepository: com.rasmi.purevon.domain.repository.BlockRepository, // ✅ لفحص حالة الحجب
    private val inCallBridge: com.rasmi.purevon.domain.call.InCallServiceBridge // ✅ FIXED Issue #14: Injectable bridge
) : ViewModel() {
    
    companion object {
        private const val TAG = "DialerViewModel"
    }
    
    // Store default SIM subscription ID
    private var defaultSimSubscriptionId: Int = -1
    
    // Current selected SIM index (0 for SIM1, 1 for SIM2)
    private var currentSimIndex: Int = 0
    
    // ASK mode: عند تفعيله يسأل المستخدم عن الشريحة قبل كل اتصال
    private var isAskMode: Boolean = false
    
    // ✅ Single source of truth for available SIMs: always use _uiState.value.availableSims
    private inline val availableSims: List<com.rasmi.purevon.util.sim.SimInfo>
        get() = _uiState.value.availableSims
    
    private val _uiState = MutableStateFlow(DialerUiState())
    val uiState: StateFlow<DialerUiState> = _uiState.asStateFlow()

    private val _uiAction = kotlinx.coroutines.channels.Channel<DialerUiAction>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val uiAction = _uiAction
    
    init {
        observeNumberChanges()
        observeDefaultSim()
        loadAvailableSims()
        observeAskMode() // ✅ استرجاع وضع ASK من DataStore
        loadRecentUniqueContacts() // ✅ تحميل آخر 10 جهات اتصال مختلفة تم الاتصال بها
        observeCallEndedForSuggestions() // ✅ تحديث الاقتراحات عند انتهاء المكالمة
    }
    
    /**
     * ✅ تحديث الاقتراحات تلقائياً عند انتهاء أي مكالمة (IDLE)
     * يحل مشكلة عدم تحديث الاقتراحات إلا عند إعادة فتح التطبيق
     */
    private fun observeCallEndedForSuggestions() {
        viewModelScope.launch {
            EventBus.events.collect { event ->
                if (event is AppEvent.CallStateChanged && event.state == "IDLE") {
                    Log.d(TAG, "Call ended — refreshing recent contacts suggestions")
                    loadRecentUniqueContacts()
                }
            }
        }
    }
    
    /**
     * ✅ يُستدعى من الشاشة عند العودة إليها (onResume) لتحديث الاقتراحات
     */
    fun refreshSuggestions() {
        loadRecentUniqueContacts()
    }
    
    /**
     * Observe ASK mode from DataStore
     */
    private fun observeAskMode() {
        viewModelScope.launch {
            settingsDataStore.isSimAskMode.collect { isAsk ->
                isAskMode = isAsk
                updateSimLabel()
            }
        }
    }
    
    /**
     * ✅ تحميل آخر 10 جهات اتصال مختلفة تم الاتصال بها
     * يعرضها كاقتراحات افتراضية عندما يكون حقل الإدخال فارغاً
     */
    private fun loadRecentUniqueContacts() {
        viewModelScope.launch {
            try {
                // جلب آخر 40 مكالمة فريدة من النظام (كافية لإيجاد 10 مختلفة)
                val recentCalls = callLogRepository.getRecentUniqueSystemCalls(limit = 40)
                
                // اختيار آخر 10 أرقام مختلفة (بعد تطبيع الأرقام)
                val uniqueContacts = recentCalls
                    .distinctBy { it.phoneNumber.replace(Regex("[^0-9+]"), "") }
                    .take(10)
                    .map { call ->
                        // ✅ فحص حالة الحجب لكل رقم (عبر BlockRepository الذي يدعم E.164 + Wildcard)
                        val isBlocked = try {
                            blockRepository.shouldBlockCall(call.phoneNumber)
                        } catch (e: Exception) {
                            Log.w(TAG, "isBlocked check failed for ${DebugLogger.maskPhoneNumber(call.phoneNumber)}", e)
                            false
                        }
                        Contact(
                            id = -(call.phoneNumber.replace(Regex("[^0-9+]"), "").hashCode().toLong() and 0x7FFFFFFFL) - 1,
                            displayName = call.contactName ?: call.phoneNumber,
                            phoneNumber = call.phoneNumber,
                            phoneType = null,
                            photoUri = call.photoUri,
                            email = null,
                            company = null,
                            isFavorite = false,
                            isBlocked = isBlocked,
                            lastContactedTime = call.timestamp,
                            timesContacted = 0,
                            preferredSimSlot = null
                        )
                    }
                
                _uiState.update { it.copy(recentContacts = uniqueContacts) }
                Log.d(TAG, "✅ Loaded ${uniqueContacts.size} recent unique contacts for suggestions")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recent contacts", e)
            }
        }
    }
    
    /**
     * Observe default SIM subscription ID from settings
     */
    private fun observeDefaultSim() {
        viewModelScope.launch {
            try {
                settingsDataStore.defaultSimSubscriptionId.collect { subscriptionId ->
                    defaultSimSubscriptionId = subscriptionId
                    // ✅ تحديث currentSimIndex بناءً على الشريحة المحفوظة
                    if (subscriptionId > 0 && availableSims.isNotEmpty()) {
                        val simIndex = availableSims.indexOfFirst { it.subscriptionId == subscriptionId }
                        if (simIndex >= 0) {
                            currentSimIndex = simIndex
                        }
                    }
                    updateSimLabel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing default SIM setting", e)
            }
        }
    }
    
    /**
     * Load available SIMs
     */
    private fun loadAvailableSims() {
        viewModelScope.launch {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    val sims = simManager.getAvailableSims()
                    Log.d(TAG, "Available SIMs: ${sims.size}")
                    sims.forEachIndexed { index, sim ->
                        Log.d(TAG, "  SIM $index: ${sim.displayName} (ID: ${sim.subscriptionId})")
                    }
                    
                    // حفظ الشرائح في الـ State لاستخدامها في حوار ASK
                    _uiState.update { it.copy(availableSims = sims) }
                    
                    // ✅ قراءة القيمة المحفوظة مباشرة من DataStore لتجنب race condition
                    val savedSubId = settingsDataStore.defaultSimSubscriptionId.first()
                    val savedAskMode = settingsDataStore.isSimAskMode.first()
                    defaultSimSubscriptionId = savedSubId
                    isAskMode = savedAskMode
                    
                    // Set current SIM index based on default subscription ID
                    if (savedSubId > 0) {
                        val simIndex = sims.indexOfFirst { it.subscriptionId == savedSubId }
                        if (simIndex >= 0) {
                            currentSimIndex = simIndex
                        } else if (sims.isNotEmpty()) {
                            // الشريحة المحفوظة لم تعد موجودة — نعيد التعيين للشريحة الأولى المتاحة
                            currentSimIndex = 0
                            settingsDataStore.setDefaultSimSubscriptionId(sims[0].subscriptionId)
                            Log.w(TAG, "Saved SIM (id=$savedSubId) not found, reset to ${sims[0].displayName}")
                        }
                    }
                    
                    updateSimLabel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading SIMs", e)
            }
        }
    }
    
    /**
     * Update SIM label in UI
     */
    private fun updateSimLabel() {
        val label = when {
            isAskMode -> "ASK"
            availableSims.isNotEmpty() && currentSimIndex < availableSims.size -> {
                availableSims[currentSimIndex].displayName.let { name ->
                    when {
                        name.contains("SIM 1", ignoreCase = true) || name.contains("SIM1", ignoreCase = true) -> "SIM1"
                        name.contains("SIM 2", ignoreCase = true) || name.contains("SIM2", ignoreCase = true) -> "SIM2"
                        else -> "SIM${currentSimIndex + 1}"
                    }
                }
            }
            else -> "SIM1"
        }
        
        _uiState.update { it.copy(currentSimLabel = label) }
    }
    
    /**
     * Switch to next SIM
     */
    fun switchSim() {
        viewModelScope.launch {
            if (availableSims.isEmpty()) {
                Log.w(TAG, "No SIMs available to switch")
                return@launch
            }
            
            // الدورة: SIM1 → SIM2 → ... → ASK → SIM1
            when {
                isAskMode -> {
                    // من ASK ارجع إلى SIM1
                    isAskMode = false
                    settingsDataStore.setSimAskMode(false)
                    currentSimIndex = 0
                    val selectedSim = availableSims[currentSimIndex]
                    settingsDataStore.setDefaultSimSubscriptionId(selectedSim.subscriptionId)
                    Log.d(TAG, "ASK → SIM1: ${selectedSim.displayName}")
                }
                currentSimIndex + 1 >= availableSims.size -> {
                    // آخر شريحة → انتقل إلى وضع ASK
                    isAskMode = true
                    settingsDataStore.setSimAskMode(true)
                    Log.d(TAG, "SIM${currentSimIndex + 1} → ASK mode")
                }
                else -> {
                    // انتقل للشريحة التالية
                    currentSimIndex++
                    val selectedSim = availableSims[currentSimIndex]
                    settingsDataStore.setDefaultSimSubscriptionId(selectedSim.subscriptionId)
                    Log.d(TAG, "Switched to SIM ${currentSimIndex + 1}: ${selectedSim.displayName}")
                }
            }
            
            updateSimLabel()
        }
    }
    
    @OptIn(FlowPreview::class)
    private fun observeNumberChanges() {
        viewModelScope.launch {
            _uiState
                .map { it.dialedNumber }
                .distinctUntilChanged()
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.isNotEmpty()) {
                        kotlinx.coroutines.flow.flow { emit(searchContactsSuspend(query)) }
                    } else {
                        kotlinx.coroutines.flow.flow { emit(emptyList()) }
                    }
                }
                .collect { results ->
                    _uiState.update { it.copy(searchResults = results) }
                }
        }
    }

    private suspend fun searchContactsSuspend(query: String): List<com.rasmi.purevon.domain.model.Contact> {
        return try { searchContactsUseCase(query).first() } catch (_: Exception) { emptyList() }
    }
    
    fun onEvent(event: DialerUiEvent) {
        when (event) {
            is DialerUiEvent.NumberChanged -> {
                _uiState.update { 
                    it.copy(
                        dialedNumber = event.number,
                        showCallButton = event.number.isNotEmpty(),
                        isContactSelected = false, // ✅ عند التعديل يدوياً، إلغاء علامة الاختيار
                        selectedContactName = null // ✅ إلغاء اسم جهة الاتصال
                    ) 
                }
            }
            
            is DialerUiEvent.ClearInput -> {
                _uiState.update { 
                    it.copy(
                        dialedNumber = "",
                        showCallButton = false,
                        searchResults = emptyList(),
                        isContactSelected = false,
                        selectedContactName = null
                    ) 
                }
            }
            
            is DialerUiEvent.DigitPressed -> {
                // ✅ إذا كانت هناك مكالمة نشطة، أرسل DTMF tone
                val activeCall = inCallBridge.getCurrentCall()
                if (activeCall != null) {
                    // Send DTMF tone for active call
                    try {
                        val digit = event.digit.firstOrNull() ?: return
                        activeCall.playDtmfTone(digit)
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(200)
                            activeCall.stopDtmfTone()
                        }
                        Log.d(TAG, "DTMF tone sent during active call: $digit")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending DTMF tone", e)
                    }
                }
                
                // إضافة الرقم للـ input بشكل عادي
                val newNumber = _uiState.value.dialedNumber + event.digit
                _uiState.update { 
                    it.copy(
                        dialedNumber = newNumber,
                        showCallButton = newNumber.isNotEmpty(),
                        isContactSelected = false, // ✅ عند الإضافة يدوياً، إلغاء علامة الاختيار
                        selectedContactName = null // ✅ إلغاء اسم جهة الاتصال
                    ) 
                }
            }
            
            is DialerUiEvent.BackspacePressed -> {
                val current = _uiState.value.dialedNumber
                if (current.isNotEmpty()) {
                    _uiState.update { 
                        it.copy(
                            dialedNumber = current.dropLast(1),
                            showCallButton = current.length > 1,
                            isContactSelected = false, // ✅ عند الحذف، إلغاء علامة الاختيار
                            selectedContactName = null // ✅ إلغاء اسم جهة الاتصال
                        ) 
                    }
                }
            }
            
            is DialerUiEvent.BackspaceLongPressed -> {
                _uiState.update { 
                    it.copy(
                        dialedNumber = "",
                        showCallButton = false,
                        searchResults = emptyList(),
                        isContactSelected = false, // ✅ إلغاء علامة الاختيار
                        selectedContactName = null // ✅ إلغاء اسم جهة الاتصال
                    ) 
                }
            }
            
            is DialerUiEvent.ContactSelected -> {
                val phoneNumber = event.contact.phoneNumber
                // ✅ اعتبار جهة الاتصال "مختارة" فقط إذا كان لها اسم حقيقي (ليس مجرد الرقم)
                val hasRealName = event.contact.name != event.contact.phoneNumber &&
                    event.contact.name.isNotBlank() &&
                    !event.contact.name.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                _uiState.update { 
                    it.copy(
                        dialedNumber = phoneNumber,
                        showCallButton = phoneNumber.isNotEmpty(),
                        isContactSelected = hasRealName,
                        selectedContactName = if (hasRealName) event.contact.name else null,
                        searchResults = emptyList() // Clear search results after selection
                    ) 
                }
            }
            
            is DialerUiEvent.InitiateCall -> {
                val phoneNumber = _uiState.value.dialedNumber
                
                // ✅ إذا كان الحقل فارغاً، جلب آخر مكالمة صادرة
                if (phoneNumber.isEmpty()) {
                    viewModelScope.launch {
                        try {
                            val lastOutgoingCall = callLogRepository.getLastOutgoingCall()
                            if (lastOutgoingCall != null) {
                                // ✅ فقط إذا كان من جهات الاتصال المحفوظة (له اسم)
                                val hasContactName = !lastOutgoingCall.contactName.isNullOrBlank()
                                _uiState.update { 
                                    it.copy(
                                        dialedNumber = lastOutgoingCall.phoneNumber,
                                        showCallButton = true,
                                        isContactSelected = hasContactName,
                                        selectedContactName = if (hasContactName) lastOutgoingCall.contactName else null
                                    ) 
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting last outgoing call", e)
                        }
                    }
                    return
                }
                
                if (event.hasPermission) {
                    if (isAskMode) {
                        // وضع ASK: اعرض حوار اختيار الشريحة قبل الاتصال
                        Log.d(TAG, "ASK mode: showing SIM picker for ${DebugLogger.maskPhoneNumber(phoneNumber)}")
                        _uiAction.trySend(DialerUiAction.ShowSimPickerForCall(phoneNumber))
                    } else {
                        // شريحة محددة مسبقاً
                        val subscriptionId = if (availableSims.isNotEmpty() && currentSimIndex < availableSims.size) {
                            availableSims[currentSimIndex].subscriptionId
                        } else if (defaultSimSubscriptionId > 0) {
                            defaultSimSubscriptionId
                        } else {
                            null
                        }
                        
                        Log.d(TAG, "Making call with SIM subscription ID: $subscriptionId")
                        _uiAction.trySend(DialerUiAction.MakePhoneCall(phoneNumber, subscriptionId))
                    }
                } else {
                    _uiAction.trySend(DialerUiAction.RequestPermission)
                }
            }
            
            is DialerUiEvent.SimSwitchPressed -> {
                switchSim()
            }
            
            is DialerUiEvent.SimSelectedForCall -> {
                Log.d(TAG, "ASK: user selected subscriptionId=${event.subscriptionId} for ${DebugLogger.maskPhoneNumber(event.phoneNumber)}")
                _uiAction.trySend(DialerUiAction.MakePhoneCall(event.phoneNumber, event.subscriptionId))
            }
            
            is DialerUiEvent.RequestCallPermission -> {
                _uiAction.trySend(DialerUiAction.RequestPermission)
            }
            
            is DialerUiEvent.DismissError -> {
                _uiState.update { it.copy(error = null) }
            }
        }
    }
    
    /**
     * Clear UI action after it's been handled
     */
    private var searchJob: kotlinx.coroutines.Job? = null
    
    private fun searchContacts(query: String) {
        searchJob?.cancel() // ✅ Cancel previous search to prevent stale results overwriting newer ones
        searchJob = viewModelScope.launch {
            if (com.rasmi.purevon.BuildConfig.ENABLE_LOGGING) {
                Log.d(TAG, "🔍 searchContacts: query='$query'")
            }
            searchContactsUseCase(query)
                .map { contacts ->
                    if (com.rasmi.purevon.BuildConfig.ENABLE_LOGGING) {
                        Log.d(TAG, "📦 Received ${contacts.size} contacts from repository")
                    }
                    
                    // Smart international number matching
                    val filtered = contacts
                        .filter { contact ->
                            val normalizedPhone = contact.phoneNumber.replace(Regex("[^0-9]"), "")
                            val isNumericQuery = query.all { it.isDigit() }
                            
                            val matches = if (isNumericQuery) {
                                // Try direct match first
                                val matchesDirect = normalizedPhone.contains(query)
                                
                                // Try without leading zero (for international format like +966564... vs 0564...)
                                // When user types "05954", also search for "5954" to match "+966595458251"
                                val matchesWithoutZero = if (query.startsWith("0") && query.length >= 2) {
                                    val queryWithoutZero = query.substring(1) // Remove leading 0
                                    normalizedPhone.contains(queryWithoutZero)
                                } else {
                                    false
                                }
                                
                                matchesDirect || matchesWithoutZero
                            } else {
                                // For text queries, search in name
                                contact.displayName.contains(query, ignoreCase = true)
                            }
                            
                            if (matches) {
                                Log.d(TAG, "  ✅ MATCH: ${contact.displayName} - $normalizedPhone contains '$query'")
                            }
                            
                            matches
                        }
                    
                    Log.d(TAG, "🎯 Filtered to ${filtered.size} matching contacts")
                    
                    filtered
                        .sortedWith(
                            compareBy(
                                // Priority 1: Starts with query (highest relevance)
                                { contact ->
                                    val isNumericQuery = query.all { it.isDigit() }
                                    if (isNumericQuery) {
                                        val normalizedPhone = contact.phoneNumber.replace(Regex("[^0-9]"), "")
                                        !normalizedPhone.startsWith(query)
                                    } else {
                                        !contact.displayName.startsWith(query, ignoreCase = true)
                                    }
                                },
                                // Priority 2: Alphabetical order
                                { it.displayName }
                            )
                        )
                        .take(50)
                }
                .flowOn(Dispatchers.Default)
                .onStart {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }
                .catch { e ->
                    val errorMessage = ErrorMapper.mapToMessage(e as? Exception ?: Exception(e))
                    _uiState.update { 
                        it.copy(
                            error = errorMessage,
                            isLoading = false,
                            searchResults = emptyList()
                        ) 
                    }
                }
                .collect { data ->
                    _uiState.update { 
                        it.copy(
                            searchResults = data,
                            isLoading = false,
                            error = null
                        ) 
                    }
                }
        }
    }
}
