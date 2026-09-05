package com.rasmi.purevon.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "purevon_settings")

/**
 * DataStore for managing app settings and preferences
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.dataStore
    
    companion object {
        // ✅ FIX M40: أزيل مفاتيح dark_mode/auto_theme/theme_color — الهوية موحدة داكنة
        
        // Layout Direction
        private val RTL_ENABLED = booleanPreferencesKey("rtl_enabled")
        
        // Call Settings
        private val DEFAULT_SIM_SLOT = intPreferencesKey("default_sim_slot")
        private val DEFAULT_SIM_SUBSCRIPTION_ID = intPreferencesKey("default_sim_subscription_id")
        private val DEFAULT_SMS_SIM_SUBSCRIPTION_ID = intPreferencesKey("default_sms_sim_subscription_id")
        private val SIM_ASK_MODE = booleanPreferencesKey("sim_ask_mode") // ✅ وضع ASK: اسأل عن الشريحة قبل كل اتصال
        private val SMS_SIM_ASK_MODE = booleanPreferencesKey("sms_sim_ask_mode") // ✅ وضع ASK للرسائل
        private val VIBRATE_ON_CALL = booleanPreferencesKey("vibrate_on_call")
        private val SHOW_CALL_NOTES = booleanPreferencesKey("show_call_notes")
        
        // Message Settings
        private val SMART_REPLY_ENABLED = booleanPreferencesKey("smart_reply_enabled")
        private val AUTO_DELETE_OTP = booleanPreferencesKey("auto_delete_otp")
        private val OTP_DELETE_DELAY = intPreferencesKey("otp_delete_delay")
        
        // OTP Settings
        private val OTP_AUTO_COPY_ENABLED = booleanPreferencesKey("otp_auto_copy_enabled")
        private val OTP_SHOW_NOTIFICATION = booleanPreferencesKey("otp_show_notification")
        // Note: auto-delete is managed by AUTO_DELETE_OTP in the Message Settings block above.
        private val OTP_MIN_LENGTH = intPreferencesKey("otp_min_length")
        private val OTP_ONLY_TRUSTED_SENDERS = booleanPreferencesKey("otp_only_trusted_senders")
        private val OTP_TRUSTED_SENDERS = stringPreferencesKey("otp_trusted_senders")
        
        // Spam Settings
        private val SPAM_FILTER_ENABLED = booleanPreferencesKey("spam_filter_enabled")
        private val AUTO_BLOCK_SPAM = booleanPreferencesKey("auto_block_spam")
        private val SPAM_THRESHOLD = intPreferencesKey("spam_threshold")
        
        // Call Blocking Settings
        private val CALL_BLOCKING_ENABLED = booleanPreferencesKey("call_blocking_enabled")
        private val WHITELIST_ONLY_MODE = booleanPreferencesKey("whitelist_only_mode")
        private val BLOCK_UNKNOWN_NUMBERS = booleanPreferencesKey("block_unknown_numbers")
        // ✅ نطاق تطبيق الحظر على الشرائح: -1 = كل الشرائح، أو subscriptionId محدد
        private val CALL_BLOCKING_SIM_SUBSCRIPTION_ID = intPreferencesKey("call_blocking_sim_subscription_id")
        
        // Privacy
        private val HIDE_SENSITIVE_NOTIFICATIONS = booleanPreferencesKey("hide_sensitive_notifications")
        
        // Notifications
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val NOTIFICATION_SOUND = stringPreferencesKey("notification_sound")
        
        // First Run
        private val FIRST_RUN = booleanPreferencesKey("first_run")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        
        // Country Detection
        private val DEFAULT_COUNTRY_CODE = stringPreferencesKey("default_country_code")

        // App Language
        private val APP_LANGUAGE = stringPreferencesKey("app_language")

        // ✅ VIRAL #4: بطاقتي
        private val MY_CARD_FIRST_NAME = stringPreferencesKey("my_card_first_name")
        private val MY_CARD_LAST_NAME = stringPreferencesKey("my_card_last_name")
        private val MY_CARD_PHONE = stringPreferencesKey("my_card_phone")
        private val MY_CARD_EMAIL = stringPreferencesKey("my_card_email")
        private val MY_CARD_COMPANY = stringPreferencesKey("my_card_company")
    }
    
    
    // App Language
    val appLanguage: Flow<String> = dataStore.data.map { it[APP_LANGUAGE] ?: "system" }

    suspend fun setAppLanguage(language: String) {
        dataStore.edit { it[APP_LANGUAGE] = language }
    }

    // Country Detection
    val defaultCountryCode: Flow<String?> = dataStore.data.map { it[DEFAULT_COUNTRY_CODE] }
    
    suspend fun setDefaultCountryCode(code: String?) {
        dataStore.edit {
            if (code != null) it[DEFAULT_COUNTRY_CODE] = code.uppercase()
            else it.remove(DEFAULT_COUNTRY_CODE)
        }
    }
    
    // Layout Direction Settings (RTL)
    val isRtlEnabled: Flow<Boolean> = dataStore.data.map { it[RTL_ENABLED] ?: false }

    suspend fun setRtlEnabled(enabled: Boolean) {
        dataStore.edit { it[RTL_ENABLED] = enabled }
    }
    
    // Call Settings
    val defaultSimSlot: Flow<Int> = dataStore.data.map { it[DEFAULT_SIM_SLOT] ?: 0 }
    val defaultSimSubscriptionId: Flow<Int> = dataStore.data.map { it[DEFAULT_SIM_SUBSCRIPTION_ID] ?: -1 }
    val defaultSmsSimSubscriptionId: Flow<Int> = dataStore.data.map { it[DEFAULT_SMS_SIM_SUBSCRIPTION_ID] ?: -1 }
    val isSimAskMode: Flow<Boolean> = dataStore.data.map { it[SIM_ASK_MODE] ?: false } // ✅ وضع ASK للاتصال
    val isSmsSimAskMode: Flow<Boolean> = dataStore.data.map { it[SMS_SIM_ASK_MODE] ?: false } // ✅ وضع ASK للرسائل
    val vibrateOnCall: Flow<Boolean> = dataStore.data.map { it[VIBRATE_ON_CALL] ?: true }
    val showCallNotes: Flow<Boolean> = dataStore.data.map { it[SHOW_CALL_NOTES] ?: true }
    
    suspend fun setDefaultSimSlot(slot: Int) {
        dataStore.edit { it[DEFAULT_SIM_SLOT] = slot }
    }
    
    suspend fun setDefaultSimSubscriptionId(subscriptionId: Int) {
        dataStore.edit { it[DEFAULT_SIM_SUBSCRIPTION_ID] = subscriptionId }
    }
    
    suspend fun setDefaultSmsSimSubscriptionId(subscriptionId: Int) {
        dataStore.edit { it[DEFAULT_SMS_SIM_SUBSCRIPTION_ID] = subscriptionId }
    }
    
    suspend fun setSimAskMode(enabled: Boolean) { // ✅ وضع ASK للاتصال
        dataStore.edit { it[SIM_ASK_MODE] = enabled }
    }

    suspend fun setSmsSimAskMode(enabled: Boolean) { // ✅ وضع ASK للرسائل
        dataStore.edit { it[SMS_SIM_ASK_MODE] = enabled }
    }
    
    suspend fun setVibrateOnCall(enabled: Boolean) {
        dataStore.edit { it[VIBRATE_ON_CALL] = enabled }
    }
    
    suspend fun setShowCallNotes(enabled: Boolean) {
        dataStore.edit { it[SHOW_CALL_NOTES] = enabled }
    }
    
    // Message Settings
    val smartReplyEnabled: Flow<Boolean> = dataStore.data.map { it[SMART_REPLY_ENABLED] ?: true }
    val autoDeleteOtp: Flow<Boolean> = dataStore.data.map { it[AUTO_DELETE_OTP] ?: false }
    val otpDeleteDelay: Flow<Int> = dataStore.data.map { it[OTP_DELETE_DELAY] ?: 5 }
    
    suspend fun setSmartReplyEnabled(enabled: Boolean) {
        dataStore.edit { it[SMART_REPLY_ENABLED] = enabled }
    }
    
    suspend fun setAutoDeleteOtp(enabled: Boolean) {
        dataStore.edit { it[AUTO_DELETE_OTP] = enabled }
    }
    
    suspend fun setOtpDeleteDelay(minutes: Int) {
        dataStore.edit { it[OTP_DELETE_DELAY] = minutes }
    }
    
    // OTP Settings
    val otpAutoCopyEnabled: Flow<Boolean> = dataStore.data.map { it[OTP_AUTO_COPY_ENABLED] ?: true }
    val otpShowNotification: Flow<Boolean> = dataStore.data.map { it[OTP_SHOW_NOTIFICATION] ?: true }
    val otpMinLength: Flow<Int> = dataStore.data.map { it[OTP_MIN_LENGTH] ?: 4 }
    val otpOnlyTrustedSenders: Flow<Boolean> = dataStore.data.map { it[OTP_ONLY_TRUSTED_SENDERS] ?: false }
    val otpTrustedSenders: Flow<List<String>> = dataStore.data.map { prefs ->
        // ✅ FIX #17: Support both "|" (new) and "," (legacy) delimiters
        val raw = prefs[OTP_TRUSTED_SENDERS] ?: return@map emptyList()
        raw.split("|", ",").filter { it.isNotBlank() }
    }
    
    suspend fun setOtpAutoCopyEnabled(enabled: Boolean) {
        dataStore.edit { it[OTP_AUTO_COPY_ENABLED] = enabled }
    }
    
    suspend fun setOtpShowNotification(enabled: Boolean) {
        dataStore.edit { it[OTP_SHOW_NOTIFICATION] = enabled }
    }

    suspend fun setOtpMinLength(length: Int) {
        dataStore.edit { it[OTP_MIN_LENGTH] = length }
    }
    
    suspend fun setOtpOnlyTrustedSenders(enabled: Boolean) {
        dataStore.edit { it[OTP_ONLY_TRUSTED_SENDERS] = enabled }
    }
    
    suspend fun setOtpTrustedSenders(senders: List<String>) {
        // ✅ FIX #17: Use pipe delimiter to avoid comma-in-name issues
        dataStore.edit { it[OTP_TRUSTED_SENDERS] = senders.joinToString("|") }
    }
    
    // Spam Settings
    val spamFilterEnabled: Flow<Boolean> = dataStore.data.map { it[SPAM_FILTER_ENABLED] ?: true }
    val autoBlockSpam: Flow<Boolean> = dataStore.data.map { it[AUTO_BLOCK_SPAM] ?: false }
    val spamThreshold: Flow<Int> = dataStore.data.map { it[SPAM_THRESHOLD] ?: 50 }
    
    suspend fun setSpamFilterEnabled(enabled: Boolean) {
        dataStore.edit { it[SPAM_FILTER_ENABLED] = enabled }
    }
    
    suspend fun setAutoBlockSpam(enabled: Boolean) {
        dataStore.edit { it[AUTO_BLOCK_SPAM] = enabled }
    }
    
    suspend fun setSpamThreshold(threshold: Int) {
        // ✅ FIX #16: Validate spam threshold range
        dataStore.edit { it[SPAM_THRESHOLD] = threshold.coerceIn(0, 100) }
    }
    
    // Call Blocking Settings
    val callBlockingEnabled: Flow<Boolean> = dataStore.data.map { it[CALL_BLOCKING_ENABLED] ?: false }
    val whitelistOnlyMode: Flow<Boolean> = dataStore.data.map { it[WHITELIST_ONLY_MODE] ?: false }
    val blockUnknownNumbers: Flow<Boolean> = dataStore.data.map { it[BLOCK_UNKNOWN_NUMBERS] ?: false }
    
    suspend fun setCallBlockingEnabled(enabled: Boolean) {
        dataStore.edit { it[CALL_BLOCKING_ENABLED] = enabled }
    }
    
    suspend fun setWhitelistOnlyMode(enabled: Boolean) {
        dataStore.edit { it[WHITELIST_ONLY_MODE] = enabled }
    }
    
    suspend fun setBlockUnknownNumbers(enabled: Boolean) {
        dataStore.edit { it[BLOCK_UNKNOWN_NUMBERS] = enabled }
    }

    /**
     * ✅ نطاق الشرائح المُطبَّق عليها الحظر.
     *  -1 = جميع الشرائح (افتراضي / جهاز شريحة واحدة)
     *  أي قيمة أخرى = subscriptionId محدد، الحظر يُطبَّق فقط على هذه الشريحة
     */
    val callBlockingSimSubscriptionId: Flow<Int> =
        dataStore.data.map { it[CALL_BLOCKING_SIM_SUBSCRIPTION_ID] ?: -1 }

    suspend fun setCallBlockingSimSubscriptionId(subscriptionId: Int) {
        dataStore.edit { it[CALL_BLOCKING_SIM_SUBSCRIPTION_ID] = subscriptionId }
    }
    
    // Privacy Settings
    val hideSensitiveNotifications: Flow<Boolean> = dataStore.data.map { it[HIDE_SENSITIVE_NOTIFICATIONS] ?: false }
    
    suspend fun setHideSensitiveNotifications(enabled: Boolean) {
        dataStore.edit { it[HIDE_SENSITIVE_NOTIFICATIONS] = enabled }
    }
    
    // Notification Settings
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val vibrationEnabled: Flow<Boolean> = dataStore.data.map { it[VIBRATION_ENABLED] ?: true }
    val notificationSound: Flow<String> = dataStore.data.map { it[NOTIFICATION_SOUND] ?: "default" }
    
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }
    
    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[VIBRATION_ENABLED] = enabled }
    }
    
    suspend fun setNotificationSound(sound: String) {
        dataStore.edit { it[NOTIFICATION_SOUND] = sound }
    }
    
    // First Run
    val isFirstRun: Flow<Boolean> = dataStore.data.map { it[FIRST_RUN] ?: true }
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }
    
    suspend fun setFirstRun(isFirst: Boolean) {
        dataStore.edit { it[FIRST_RUN] = isFirst }
    }
    
    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    // ✅ VIRAL #4: بيانات بطاقتي
    val myCardFirstName: Flow<String> = dataStore.data.map { it[MY_CARD_FIRST_NAME] ?: "" }
    val myCardLastName: Flow<String> = dataStore.data.map { it[MY_CARD_LAST_NAME] ?: "" }
    val myCardPhone: Flow<String> = dataStore.data.map { it[MY_CARD_PHONE] ?: "" }
    val myCardEmail: Flow<String> = dataStore.data.map { it[MY_CARD_EMAIL] ?: "" }
    val myCardCompany: Flow<String> = dataStore.data.map { it[MY_CARD_COMPANY] ?: "" }

    /** حفظ كل حقول البطاقة دفعة واحدة (ذرّي) */
    suspend fun saveMyCard(
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        company: String
    ) {
        dataStore.edit {
            it[MY_CARD_FIRST_NAME] = firstName.trim()
            it[MY_CARD_LAST_NAME] = lastName.trim()
            it[MY_CARD_PHONE] = phone.trim()
            it[MY_CARD_EMAIL] = email.trim()
            it[MY_CARD_COMPANY] = company.trim()
        }
    }

    // Clear all settings
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
