package com.rasmi.purevon.presentation.screen.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rasmi.purevon.BuildConfig
import com.rasmi.purevon.domain.usecase.whitelist.AddToWhitelistUseCase
import com.rasmi.purevon.domain.usecase.whitelist.GetWhitelistNumbersUseCase
import com.rasmi.purevon.domain.usecase.whitelist.RemoveFromWhitelistUseCase
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.usecase.block.BlockNumberUseCase
import com.rasmi.purevon.domain.usecase.block.GetBlockedNumbersUseCase
import com.rasmi.purevon.domain.usecase.block.UnblockNumberUseCase
import com.rasmi.purevon.domain.usecase.call.ClearAllCallLogsUseCase
import com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
import com.rasmi.purevon.domain.repository.CallLogRepository
import com.rasmi.purevon.receiver.CallbackReminderReceiver
import com.rasmi.purevon.receiver.FakeCallReceiver
import com.rasmi.purevon.util.CallbackReminderScheduleManager
import com.rasmi.purevon.util.FakeCallScheduleManager
import com.rasmi.purevon.util.RtlPreferences
import com.rasmi.purevon.util.sim.SimManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Settings Screen
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val simManager: SimManager,
    private val getBlockedNumbersUseCase: GetBlockedNumbersUseCase,
    private val blockNumberUseCase: BlockNumberUseCase,
    private val unblockNumberUseCase: UnblockNumberUseCase,
    private val getWhitelistNumbersUseCase: GetWhitelistNumbersUseCase,
    private val addToWhitelistUseCase: AddToWhitelistUseCase,
    private val removeFromWhitelistUseCase: RemoveFromWhitelistUseCase,
    private val clearAllCallLogsUseCase: ClearAllCallLogsUseCase,
    private val getAllContactsUseCase: GetAllContactsUseCase,
    private val callLogRepository: CallLogRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        observeSettings()
        observeOtpEnabled()
        loadAvailableSims()
        observeBlockedAndWhitelist()
        // ✅ Fix DD: Contacts loaded lazily when blocked/whitelist dialog opens
        loadScheduledFakeCalls()     // ✅ تحميل الاتصالات الوهمية المجدولة
        loadScheduledCallbackReminders() // ✅ تحميل تذكيرات إعادة الاتصال المجدولة
    }
    
    /**
     * Load available SIM cards from system
     */
    private fun loadAvailableSims() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val sims = simManager.getAvailableSims()
            _uiState.update { it.copy(availableSims = sims) }
        }
    }
    
    /**
     * Helper data classes for collecting settings flows
     */
    private data class BasicSettings(
        val isDarkMode: Boolean,
        val autoTheme: Boolean,
        val defaultSimSlot: Int,
        val defaultSimSubscriptionId: Int,
        val defaultSmsSimSubscriptionId: Int,
        val isRtlEnabled: Boolean,
        val appLanguage: String
    )
    
    private data class NotificationSettings(
        val hideSensitiveNotifications: Boolean
    )
    
    private data class FeatureSettings(
        val callBlockingEnabled: Boolean,
        val callBlockingSimSubscriptionId: Int,
        val incomingCallBannerOnly: Boolean
    )
    
    private data class AllSettings(
        val basic: BasicSettings,
        val notification: NotificationSettings,
        val features: FeatureSettings
    )
    
    private fun observeSettings() {
        viewModelScope.launch {
            // Observe basic settings
            combine(
                settingsDataStore.isDarkMode,
                settingsDataStore.isAutoTheme,
                settingsDataStore.defaultSimSlot,
                settingsDataStore.defaultSimSubscriptionId,
                settingsDataStore.defaultSmsSimSubscriptionId,
                settingsDataStore.isRtlEnabled,
                settingsDataStore.appLanguage
            ) { values ->
                BasicSettings(
                    values[0] as Boolean,
                    values[1] as Boolean,
                    values[2] as Int,
                    values[3] as Int,
                    values[4] as Int,
                    values[5] as Boolean,
                    values[6] as String
                )
            }.combine(
                settingsDataStore.hideSensitiveNotifications.map { NotificationSettings(it) }
            ) { basic, notif -> Pair(basic, notif) }.combine(
                combine(
                    settingsDataStore.callBlockingEnabled,
                    settingsDataStore.callBlockingSimSubscriptionId,
                    settingsDataStore.incomingCallBannerOnly
                ) { blocking, scope, banner -> FeatureSettings(blocking, scope, banner) }
            ) { pair, features ->
                AllSettings(pair.first, pair.second, features)
            }.collect { allSettings ->
                val sims = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    simManager.getAvailableSims()
                } else {
                    emptyList()
                }
                
                _uiState.update { current ->
                    current.copy(
                        isDarkMode = allSettings.basic.isDarkMode,
                        autoTheme = allSettings.basic.autoTheme,
                        isRtlEnabled = allSettings.basic.isRtlEnabled,
                        appLanguage = allSettings.basic.appLanguage,
                        defaultSimSlot = allSettings.basic.defaultSimSlot,
                        defaultSimSubscriptionId = allSettings.basic.defaultSimSubscriptionId,
                        defaultSmsSimSubscriptionId = allSettings.basic.defaultSmsSimSubscriptionId,
                        availableSims = sims,
                        hideSensitiveNotifications = allSettings.notification.hideSensitiveNotifications,
                        version = BuildConfig.VERSION_NAME,
                        callBlockingEnabled = allSettings.features.callBlockingEnabled,
                        callBlockingSimSubscriptionId = allSettings.features.callBlockingSimSubscriptionId,
                        incomingCallBannerOnly = allSettings.features.incomingCallBannerOnly
                    )
                }
            }
        }
    }
    
    // Helper classes for type-safe combines

    private fun observeOtpEnabled() {
        viewModelScope.launch {
            settingsDataStore.otpAutoCopyEnabled.collect { enabled ->
                _uiState.update { it.copy(otpEnabled = enabled) }
            }
        }
    }

    private data class Tuple6<A, B, C, D, E, F>(
        val first: A, val second: B, val third: C,
        val fourth: D, val fifth: E, val sixth: F
    )
    
    private fun observeBlockedAndWhitelist() {
        // Observe blocked numbers
        viewModelScope.launch {
            getBlockedNumbersUseCase().collect { blocked ->
                _uiState.update { 
                    it.copy(blockedNumbers = blocked)
                }
            }
        }
        
        // Observe whitelist numbers
        viewModelScope.launch {
            getWhitelistNumbersUseCase().collect { whitelist ->
                _uiState.update { 
                    it.copy(whitelistNumbers = whitelist.map { item -> 
                        WhitelistItem(item.phoneNumber, item.contactName) 
                    })
                }
            }
        }
    }
    
    /**
     * ✅ تحميل جهات الاتصال وسجل المكالمات للاختيار منها
     * ✅ Fix DD: Loaded lazily only when blocked/whitelist dialog is opened
     */
    private var contactsLoaded = false
    private fun loadContactsAndRecentCalls() {
        if (contactsLoaded) return
        contactsLoaded = true
        // تحميل جهات الاتصال
        viewModelScope.launch {
            getAllContactsUseCase().collect { contacts ->
                _uiState.update { it.copy(contactsForPicker = contacts) }
            }
        }
        
        // تحميل سجل المكالمات الأخيرة via Repository
        viewModelScope.launch {
            try {
                val recentCalls = getRecentCallsFromSystem()
                _uiState.update { it.copy(recentCallsForPicker = recentCalls) }
            } catch (e: Exception) {
                // تجاهل الأخطاء - قد لا تتوفر الصلاحيات
            }
        }
    }
    
    /**
     * ✅ جلب سجل المكالمات من النظام عبر Repository
     */
    private suspend fun getRecentCallsFromSystem(): List<RecentCallItem> {
        return callLogRepository.getRecentUniqueSystemCalls(50).map {
            RecentCallItem(
                phoneNumber = it.phoneNumber,
                contactName = it.contactName,
                callType = it.callType,
                timestamp = it.timestamp
            )
        }
    }
    
    fun onEvent(event: SettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsUiEvent.ThemeChanged -> {
                    settingsDataStore.setDarkMode(event.isDark)
                }
                is SettingsUiEvent.AutoThemeToggled -> {
                    settingsDataStore.setAutoTheme(event.enabled)
                }
                // Layout Direction (RTL)
                is SettingsUiEvent.RtlToggled -> {
                    settingsDataStore.setRtlEnabled(event.enabled)
                    // Mirror to lightweight SharedPreferences for synchronous reads
                    // (e.g. FakeInCallActivity on first frame).
                    RtlPreferences.setRtlEnabled(context, event.enabled)
                }
                is SettingsUiEvent.DefaultSimChanged -> {
                    settingsDataStore.setDefaultSimSubscriptionId(event.subscriptionId)
                    // Find the slot for this subscription
                    val sim = _uiState.value.availableSims.find { it.subscriptionId == event.subscriptionId }
                    if (sim != null) {
                        settingsDataStore.setDefaultSimSlot(sim.slotIndex)
                    }
                    _uiState.update { it.copy(showSimSelectorDialog = false) }
                }
                SettingsUiEvent.ShowSimSelector -> {
                    _uiState.update { it.copy(showSimSelectorDialog = true) }
                }
                SettingsUiEvent.HideSimSelector -> {
                    _uiState.update { it.copy(showSimSelectorDialog = false) }
                }
                // SMS SIM Events
                is SettingsUiEvent.DefaultSmsSimChanged -> {
                    settingsDataStore.setDefaultSmsSimSubscriptionId(event.subscriptionId)
                    _uiState.update { it.copy(showSmsSimSelectorDialog = false) }
                }
                SettingsUiEvent.ShowSmsSimSelector -> {
                    _uiState.update { it.copy(showSmsSimSelectorDialog = true) }
                }
                SettingsUiEvent.HideSmsSimSelector -> {
                    _uiState.update { it.copy(showSmsSimSelectorDialog = false) }
                }
                is SettingsUiEvent.HideSensitiveNotificationsToggled -> {
                    settingsDataStore.setHideSensitiveNotifications(event.enabled)
                }
                SettingsUiEvent.ClearMessage -> {
                    _uiState.update { it.copy(successMessage = null) }
                }
                SettingsUiEvent.ClearError -> {
                    _uiState.update { it.copy(error = null) }
                }
                // Call Blocking Events
                is SettingsUiEvent.CallBlockingToggled -> {
                    settingsDataStore.setCallBlockingEnabled(event.enabled)
                    if (event.enabled && _uiState.value.availableSims.size > 1) {
                        _uiState.update { it.copy(showCallBlockingSimSelectorDialog = true) }
                    }
                }
                is SettingsUiEvent.CallBlockingSimChanged -> {
                    settingsDataStore.setCallBlockingSimSubscriptionId(event.subscriptionId)
                    _uiState.update { it.copy(showCallBlockingSimSelectorDialog = false) }
                }
                SettingsUiEvent.ShowCallBlockingSimSelector -> {
                    if (_uiState.value.availableSims.size > 1) {
                        _uiState.update { it.copy(showCallBlockingSimSelectorDialog = true) }
                    }
                }
                SettingsUiEvent.HideCallBlockingSimSelector -> {
                    _uiState.update { it.copy(showCallBlockingSimSelectorDialog = false) }
                }
                // Incoming Call Banner
                is SettingsUiEvent.IncomingCallBannerOnlyToggled -> {
                    settingsDataStore.setIncomingCallBannerOnly(event.enabled)
                }
                // OTP Events
                is SettingsUiEvent.OtpToggled -> {
                    settingsDataStore.setOtpAutoCopyEnabled(event.enabled)
                }
                SettingsUiEvent.ShowBlockedList -> {
                    loadContactsAndRecentCalls() // ✅ lazy load on first open
                    _uiState.update { it.copy(showBlockedListDialog = true) }
                }
                SettingsUiEvent.HideBlockedList -> {
                    _uiState.update { it.copy(showBlockedListDialog = false) }
                }
                SettingsUiEvent.ShowWhitelist -> {
                    loadContactsAndRecentCalls() // ✅ lazy load on first open
                    _uiState.update { it.copy(showWhitelistDialog = true) }
                }
                SettingsUiEvent.HideWhitelist -> {
                    _uiState.update { it.copy(showWhitelistDialog = false) }
                }
                is SettingsUiEvent.AddToBlockedList -> {
                    blockNumberUseCase(event.phoneNumber, event.contactName ?: "Added by user")
                }
                is SettingsUiEvent.RemoveFromBlockedList -> {
                    unblockNumberUseCase(event.phoneNumber)
                }
                is SettingsUiEvent.AddToWhitelist -> {
                    addToWhitelistUseCase(event.phoneNumber, event.contactName)
                }
                is SettingsUiEvent.RemoveFromWhitelist -> {
                    removeFromWhitelistUseCase(event.phoneNumber)
                }
                is SettingsUiEvent.PickerSearchChanged -> {
                    _uiState.update { it.copy(pickerSearchQuery = event.query) }
                }
                // ✅ Fake Call Management
                SettingsUiEvent.ShowFakeCalls -> {
                    loadScheduledFakeCalls()
                    _uiState.update { it.copy(showFakeCallsDialog = true) }
                }
                SettingsUiEvent.HideFakeCalls -> {
                    _uiState.update { it.copy(showFakeCallsDialog = false) }
                }
                SettingsUiEvent.RefreshFakeCalls -> {
                    loadScheduledFakeCalls()
                }
                is SettingsUiEvent.CancelFakeCall -> {
                    cancelScheduledFakeCall(event.requestCode)
                }
                // ✅ Callback Reminder Management
                SettingsUiEvent.ShowCallbackReminders -> {
                    loadScheduledCallbackReminders()
                    _uiState.update { it.copy(showCallbackRemindersDialog = true) }
                }
                SettingsUiEvent.HideCallbackReminders -> {
                    _uiState.update { it.copy(showCallbackRemindersDialog = false) }
                }
                SettingsUiEvent.RefreshCallbackReminders -> {
                    loadScheduledCallbackReminders()
                }
                is SettingsUiEvent.CancelCallbackReminder -> {
                    cancelScheduledCallbackReminder(event.requestCode)
                }
                SettingsUiEvent.ShowLanguageSelector -> {
                    _uiState.update { it.copy(showLanguageSelectorDialog = true) }
                }
                SettingsUiEvent.HideLanguageSelector -> {
                    _uiState.update { it.copy(showLanguageSelectorDialog = false) }
                }
                is SettingsUiEvent.LanguageChanged -> {
                    _uiState.update { it.copy(showLanguageSelectorDialog = false) }
                    settingsDataStore.setAppLanguage(event.languageCode)
                    val localeList = if (event.languageCode == "system") {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(event.languageCode)
                    }
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
            }
        }
    }

    /**
     * ✅ تحميل الاتصالات الوهمية المجدولة من SharedPreferences
     */
    private fun loadScheduledFakeCalls() {
        FakeCallScheduleManager.removeExpired(context)
        val calls = FakeCallScheduleManager.getAll(context)
        _uiState.update { it.copy(scheduledFakeCalls = calls) }
    }

    /**
     * ✅ إلغاء اتصال وهمي مجدول وحذفه من AlarmManager + السجل
     */
    private fun cancelScheduledFakeCall(requestCode: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FakeCallReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
            FakeCallScheduleManager.remove(context, requestCode)
            loadScheduledFakeCalls()
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Failed to cancel fake call: ${e.message}")
        }
    }
    
    /**
     * ✅ تحميل تذكيرات إعادة الاتصال المجدولة من SharedPreferences
     */
    private fun loadScheduledCallbackReminders() {
        CallbackReminderScheduleManager.removeExpired(context)
        val reminders = CallbackReminderScheduleManager.getAll(context)
        _uiState.update { it.copy(scheduledCallbackReminders = reminders) }
    }

    /**
     * ✅ إلغاء تذكير إعادة الاتصال وحذفه من AlarmManager + السجل
     */
    private fun cancelScheduledCallbackReminder(requestCode: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, CallbackReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
            CallbackReminderScheduleManager.remove(context, requestCode)
            loadScheduledCallbackReminders()
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Failed to cancel callback reminder: ${e.message}")
        }
    }

    private fun getCurrentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            return "system"
        }
        val locale = locales.get(0) ?: return "system"
        val language = locale.language ?: "system"
        val country = locale.country ?: ""
        val code = if (country.isNotEmpty()) "$language-$country" else language
        
        return if (code.startsWith("zh-", ignoreCase = true)) {
            if (code.contains("TW", ignoreCase = true) || code.contains("Hant", ignoreCase = true)) {
                "zh-TW"
            } else {
                "zh-CN"
            }
        } else {
            language
        }
    }
}
