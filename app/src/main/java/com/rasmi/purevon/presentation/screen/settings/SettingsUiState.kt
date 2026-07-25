package com.rasmi.purevon.presentation.screen.settings

import androidx.annotation.StringRes
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.util.CallbackReminderScheduleManager
import com.rasmi.purevon.util.FakeCallScheduleManager
import com.rasmi.purevon.util.sim.SimInfo

/**
 * UI State for Settings Screen
 */
data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val autoTheme: Boolean = true,
    val isRtlEnabled: Boolean = false,
    val appLanguage: String = "system",
    val showLanguageSelectorDialog: Boolean = false,
    val defaultSimSlot: Int = 0,
    val defaultSimSubscriptionId: Int = -1,
    val defaultSmsSimSubscriptionId: Int = -1,
    val availableSims: List<SimInfo> = emptyList(),
    val showSimSelectorDialog: Boolean = false,
    val showSmsSimSelectorDialog: Boolean = false,
    val hideSensitiveNotifications: Boolean = false,
    val version: String = "1.0.0",
    val successMessage: String? = null,
    val error: String? = null,
    // Call Blocking
    val callBlockingEnabled: Boolean = false,
    val callBlockingSimSubscriptionId: Int = -1, // -1 = All SIMs
    val showCallBlockingSimSelectorDialog: Boolean = false,
    // Incoming Call UI
    val incomingCallBannerOnly: Boolean = false,
    // OTP
    val otpEnabled: Boolean = true,
    val showBlockedListDialog: Boolean = false,
    val showWhitelistDialog: Boolean = false,
    val blockedNumbers: List<String> = emptyList(),
    val whitelistNumbers: List<WhitelistItem> = emptyList(),
    // ✅ قائمة جهات الاتصال وسجل المكالمات للاختيار منها
    val contactsForPicker: List<Contact> = emptyList(),
    val recentCallsForPicker: List<RecentCallItem> = emptyList(),
    val pickerSearchQuery: String = "",
    // ✅ الاتصالات الوهمية المجدولة
    val scheduledFakeCalls: List<FakeCallScheduleManager.ScheduledFakeCall> = emptyList(),
    val showFakeCallsDialog: Boolean = false,
    // ✅ التذكيرات بإعادة الاتصال المجدولة
    val scheduledCallbackReminders: List<CallbackReminderScheduleManager.ScheduledReminder> = emptyList(),
    val showCallbackRemindersDialog: Boolean = false,
) {
    /**
     * Get the display name for the currently selected Call SIM
     * Returns the SIM display name, or a @StringRes for "Ask every time" / "No SIM"
     */
    val selectedCallSimDisplayName: String
        get() = availableSims.find { it.subscriptionId == defaultSimSubscriptionId }?.displayName
            ?: "" // Composable layer should use selectedCallSimFallbackRes instead
    
    /** @StringRes fallback when no Call SIM is selected */
    @get:StringRes
    val selectedCallSimFallbackRes: Int
        get() = if (availableSims.isNotEmpty()) R.string.sim_ask_every_time else R.string.sim_no_sim
    
    /** True if the selected Call SIM display name is from a real SIM card */
    val hasSelectedCallSim: Boolean
        get() = availableSims.any { it.subscriptionId == defaultSimSubscriptionId }
    
    /**
     * Get the display name for the currently selected SMS SIM
     */
    val selectedSmsSimDisplayName: String
        get() = availableSims.find { it.subscriptionId == defaultSmsSimSubscriptionId }?.displayName
            ?: "" // Composable layer should use selectedSmsSimFallbackRes instead
    
    /** @StringRes fallback when no SMS SIM is selected */
    @get:StringRes
    val selectedSmsSimFallbackRes: Int
        get() = if (availableSims.isNotEmpty()) R.string.sim_ask_every_time else R.string.sim_no_sim
    
    /** True if the selected SMS SIM display name is from a real SIM card */
    val hasSelectedSmsSim: Boolean
        get() = availableSims.any { it.subscriptionId == defaultSmsSimSubscriptionId }
}

/**
 * Whitelist item model
 */
data class WhitelistItem(
    val phoneNumber: String,
    val contactName: String?
)

/**
 * ✅ Recent call item for picker
 */
data class RecentCallItem(
    val phoneNumber: String,
    val contactName: String?,
    val callType: Int, // 1=Incoming, 2=Outgoing, 3=Missed
    val timestamp: Long
)

/**
 * UI Events for Settings Screen
 */
sealed class SettingsUiEvent {
    data class ThemeChanged(val isDark: Boolean) : SettingsUiEvent()
    data class AutoThemeToggled(val enabled: Boolean) : SettingsUiEvent()
    // Layout Direction (RTL)
    data class RtlToggled(val enabled: Boolean) : SettingsUiEvent()
    // Call SIM Events
    data class DefaultSimChanged(val subscriptionId: Int) : SettingsUiEvent()
    data object ShowSimSelector : SettingsUiEvent()
    data object HideSimSelector : SettingsUiEvent()
    // SMS SIM Events
    data class DefaultSmsSimChanged(val subscriptionId: Int) : SettingsUiEvent()
    data object ShowSmsSimSelector : SettingsUiEvent()
    data object HideSmsSimSelector : SettingsUiEvent()
    // Privacy Events
    data class HideSensitiveNotificationsToggled(val enabled: Boolean) : SettingsUiEvent()

    // Cache & Clear Events
    data object ClearMessage : SettingsUiEvent()
    data object ClearError : SettingsUiEvent()
    // Floating Features Events
    // Call Blocking Events
    data class CallBlockingToggled(val enabled: Boolean) : SettingsUiEvent()
    data class CallBlockingSimChanged(val subscriptionId: Int) : SettingsUiEvent()
    data object ShowCallBlockingSimSelector : SettingsUiEvent()
    data object HideCallBlockingSimSelector : SettingsUiEvent()
    data class IncomingCallBannerOnlyToggled(val enabled: Boolean) : SettingsUiEvent()
    // OTP
    data class OtpToggled(val enabled: Boolean) : SettingsUiEvent()
    data object ShowBlockedList : SettingsUiEvent()
    data object HideBlockedList : SettingsUiEvent()
    data object ShowWhitelist : SettingsUiEvent()
    data object HideWhitelist : SettingsUiEvent()
    data class AddToBlockedList(val phoneNumber: String, val contactName: String? = null) : SettingsUiEvent()
    data class RemoveFromBlockedList(val phoneNumber: String) : SettingsUiEvent()
    data class AddToWhitelist(val phoneNumber: String, val contactName: String?) : SettingsUiEvent()
    data class RemoveFromWhitelist(val phoneNumber: String) : SettingsUiEvent()
    // ✅ Picker Search
    data class PickerSearchChanged(val query: String) : SettingsUiEvent()
    // ✅ Fake Call Management
    data object ShowFakeCalls : SettingsUiEvent()
    data object HideFakeCalls : SettingsUiEvent()
    data class CancelFakeCall(val requestCode: Int) : SettingsUiEvent()
    data object RefreshFakeCalls : SettingsUiEvent()
    // ✅ Callback Reminder Management
    data object ShowCallbackReminders : SettingsUiEvent()
    data object HideCallbackReminders : SettingsUiEvent()
    data class CancelCallbackReminder(val requestCode: Int) : SettingsUiEvent()
    data object RefreshCallbackReminders : SettingsUiEvent()
    // Language Events
    data object ShowLanguageSelector : SettingsUiEvent()
    data object HideLanguageSelector : SettingsUiEvent()
    data class LanguageChanged(val languageCode: String) : SettingsUiEvent()

    // AI Events
}
