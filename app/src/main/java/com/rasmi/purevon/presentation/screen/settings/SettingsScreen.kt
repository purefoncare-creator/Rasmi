package com.rasmi.purevon.presentation.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.component.SimSelectorDialog
import com.rasmi.purevon.presentation.theme.*

/**
 * Settings Screen - App settings and preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToAbout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show success/error messages
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.onEvent(SettingsUiEvent.ClearMessage)
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.onEvent(SettingsUiEvent.ClearError)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
    // ✅ FIX: Respect user's theme preference instead of only system setting
    val isLightTheme = if (uiState.autoTheme) !isSystemInDarkTheme() else !uiState.isDarkMode
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLightTheme) LightBackgroundAlt else MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Card 1: Appearance ──
            item {
                SettingsCard(title = stringResource(R.string.settings_appearance)) {
                    SwitchSettingItem(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(R.string.settings_theme_dark),
                        checked = uiState.isDarkMode,
                        enabled = !uiState.autoTheme,
                        onCheckedChange = { viewModel.onEvent(SettingsUiEvent.ThemeChanged(it)) },
                        iconTint = DeepPurpleAccent
                    )
                    CardItemDivider()
                    SwitchSettingItem(
                        icon = Icons.Default.Brightness4,
                        title = stringResource(R.string.settings_theme_system),
                        checked = uiState.autoTheme,
                        onCheckedChange = { viewModel.onEvent(SettingsUiEvent.AutoThemeToggled(it)) },
                        iconTint = MaterialOrange
                    )
                    CardItemDivider()
                    ClickableSettingItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = when (uiState.appLanguage) {
                            "system" -> stringResource(R.string.language_system)
                            else -> LanguageConfig.getDisplayName(uiState.appLanguage)
                        },
                        onClick = { viewModel.onEvent(SettingsUiEvent.ShowLanguageSelector) },
                        iconTint = MaterialBlue400
                    )
                }
            }
            
            // ── Card 2: Calls & Security ──
            item {
                SettingsCard(title = stringResource(R.string.call_blocking_title)) {
                    SwitchSettingItem(
                        icon = Icons.Default.Block,
                        title = stringResource(R.string.call_blocking_enable),
                        subtitle = if (uiState.callBlockingEnabled && uiState.availableSims.size > 1 && uiState.callBlockingSimSubscriptionId != -1) {
                                       val simName = uiState.availableSims.find { it.subscriptionId == uiState.callBlockingSimSubscriptionId }?.displayName ?: ""
                                       stringResource(R.string.sim_slot_format, simName)
                                   } else "", // Empty string instead of null
                        checked = uiState.callBlockingEnabled,
                        onCheckedChange = { viewModel.onEvent(SettingsUiEvent.CallBlockingToggled(it)) },
                        iconTint = MaterialRed400
                    )
                    if (uiState.callBlockingEnabled && uiState.availableSims.size > 1) {
                        CardItemDivider()
                        ClickableSettingItem(
                            icon = Icons.Default.SimCard,
                            title = stringResource(R.string.sim_select_for_blocking),
                            subtitle = if (uiState.callBlockingSimSubscriptionId == -1) 
                                           stringResource(R.string.sim_all) 
                                       else uiState.availableSims.find { it.subscriptionId == uiState.callBlockingSimSubscriptionId }?.displayName ?: stringResource(R.string.sim_all),
                            onClick = { viewModel.onEvent(SettingsUiEvent.ShowCallBlockingSimSelector) },
                            iconTint = MaterialRed400
                        )
                    }
                    CardItemDivider()
                    ClickableSettingItem(
                        icon = Icons.Default.Block,
                        title = stringResource(R.string.call_blocking_blocked_list),
                        onClick = { viewModel.onEvent(SettingsUiEvent.ShowBlockedList) },
                        iconTint = MaterialRed400
                    )
                    CardItemDivider()
                    ClickableSettingItem(
                        icon = Icons.Default.VerifiedUser,
                        title = stringResource(R.string.call_blocking_whitelist),
                        onClick = { viewModel.onEvent(SettingsUiEvent.ShowWhitelist) },
                        iconTint = MaterialBlue400
                    )
                    CardItemDivider()
                    SwitchSettingItem(
                        icon = Icons.Default.Password,
                        title = stringResource(R.string.otp_settings_title),
                        checked = uiState.otpEnabled,
                        onCheckedChange = { viewModel.onEvent(SettingsUiEvent.OtpToggled(it)) },
                        iconTint = MaterialGreen400
                    )
                    CardItemDivider()
                    SwitchSettingItem(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.settings_incoming_call_banner),
                        subtitle = stringResource(R.string.settings_incoming_call_banner_desc),
                        checked = uiState.incomingCallBannerOnly,
                        onCheckedChange = { viewModel.onEvent(SettingsUiEvent.IncomingCallBannerOnlyToggled(it)) },
                        iconTint = MaterialCyan400
                    )
                }
            }
            
            // ── Card 3: Privacy & About ──
            item {
                SettingsCard(title = stringResource(R.string.spam_privacy_title)) {
                    SwitchSettingItem(
                        icon = Icons.Default.VisibilityOff,
                        title = stringResource(R.string.privacy_hide_notifications),
                        checked = uiState.hideSensitiveNotifications,
                        onCheckedChange = { viewModel.onEvent(SettingsUiEvent.HideSensitiveNotificationsToggled(it)) },
                        iconTint = MaterialPurple400
                    )
                    CardItemDivider()
                    // ✅ عنصر إدارة الاتصالات الوهمية المجدولة
                    ClickableSettingItem(
                        icon = Icons.Default.PhoneCallback,
                        title = stringResource(R.string.fake_call_manage_title),
                        subtitle = if (uiState.scheduledFakeCalls.isEmpty()) ""
                                   else pluralStringResource(R.plurals.fake_call_active_count, uiState.scheduledFakeCalls.size, uiState.scheduledFakeCalls.size),
                        onClick = { viewModel.onEvent(SettingsUiEvent.ShowFakeCalls) },
                        iconTint = MaterialCyan400
                    )
                    CardItemDivider()
                    // ✅ إدارة تذكيرات إعادة الاتصال المجدولة
                    ClickableSettingItem(
                        icon = Icons.Default.Alarm,
                        title = stringResource(R.string.callback_reminders_manage_title),
                        subtitle = if (uiState.scheduledCallbackReminders.isEmpty()) ""
                                   else pluralStringResource(R.plurals.callback_reminder_active_count, uiState.scheduledCallbackReminders.size, uiState.scheduledCallbackReminders.size),
                        onClick = { viewModel.onEvent(SettingsUiEvent.ShowCallbackReminders) },
                        iconTint = MaterialOrange
                    )
                    CardItemDivider()
                    ClickableSettingItem(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.about_title),
                        onClick = onNavigateToAbout,
                        iconTint = BlueGrey400
                    )
                }
            }
        }
    }
    } // End Scaffold
    
    // Blocked List Dialog
    if (uiState.showBlockedListDialog) {
        BlockedListDialog(
            blockedNumbers = uiState.blockedNumbers,
            contacts = uiState.contactsForPicker,
            recentCalls = uiState.recentCallsForPicker,
            onAddNumber = { number, name ->
                viewModel.onEvent(SettingsUiEvent.AddToBlockedList(number, name))
            },
            onRemoveNumber = { number ->
                viewModel.onEvent(SettingsUiEvent.RemoveFromBlockedList(number))
            },
            onDismiss = {
                viewModel.onEvent(SettingsUiEvent.HideBlockedList)
            }
        )
    }
    
    // Whitelist Dialog
    if (uiState.showWhitelistDialog) {
        WhitelistDialog(
            whitelistNumbers = uiState.whitelistNumbers,
            contacts = uiState.contactsForPicker,
            recentCalls = uiState.recentCallsForPicker,
            onAddNumber = { number, name ->
                viewModel.onEvent(SettingsUiEvent.AddToWhitelist(number, name))
            },
            onRemoveNumber = { number ->
                viewModel.onEvent(SettingsUiEvent.RemoveFromWhitelist(number))
            },
            onDismiss = {
                viewModel.onEvent(SettingsUiEvent.HideWhitelist)
            }
        )
    }

    // ✅ حوار إدارة الاتصالات الوهمية المجدولة
    if (uiState.showFakeCallsDialog) {
        ScheduledFakeCallsDialog(
            scheduledCalls = uiState.scheduledFakeCalls,
            onCancelCall = { requestCode ->
                viewModel.onEvent(SettingsUiEvent.CancelFakeCall(requestCode))
            },
            onRefresh = {
                viewModel.onEvent(SettingsUiEvent.RefreshFakeCalls)
            },
            onDismiss = {
                viewModel.onEvent(SettingsUiEvent.HideFakeCalls)
            }
        )
    }

    // ✅ حوار إدارة تذكيرات إعادة الاتصال المجدولة
    if (uiState.showCallbackRemindersDialog) {
        ScheduledCallbackRemindersDialog(
            scheduledReminders = uiState.scheduledCallbackReminders,
            onCancelReminder = { requestCode ->
                viewModel.onEvent(SettingsUiEvent.CancelCallbackReminder(requestCode))
            },
            onRefresh = {
                viewModel.onEvent(SettingsUiEvent.RefreshCallbackReminders)
            },
            onDismiss = {
                viewModel.onEvent(SettingsUiEvent.HideCallbackReminders)
            }
        )
    }

    // ✅ حوار اختيار شريحة الـ SIM لتطبيق حظر المكالمات عليها
    if (uiState.showCallBlockingSimSelectorDialog) {
        SimSelectorDialog(
            availableSims = uiState.availableSims,
            selectedSimId = uiState.callBlockingSimSubscriptionId,
            title = stringResource(R.string.sim_select_for_blocking),
            allowAllSimsOption = true,
            allSimsText = stringResource(R.string.sim_all),
            onSimSelected = { subscriptionId ->
                viewModel.onEvent(SettingsUiEvent.CallBlockingSimChanged(subscriptionId))
            },
            onDismiss = { viewModel.onEvent(SettingsUiEvent.HideCallBlockingSimSelector) }
        )
    }

    // Language Selector Dialog
    if (uiState.showLanguageSelectorDialog) {
        LanguageSelectorDialog(
            currentLanguage = uiState.appLanguage,
            onLanguageSelected = { langCode ->
                viewModel.onEvent(SettingsUiEvent.LanguageChanged(langCode))
            },
            onDismiss = {
                viewModel.onEvent(SettingsUiEvent.HideLanguageSelector)
            }
        )
    }

}










@Composable
private fun LanguageSelectorDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val languages = listOf(
                    "system" to stringResource(R.string.language_system)
                ) + LanguageConfig.SUPPORTED_LANGUAGES
                
                languages.forEach { (code, label) ->
                    val isSelected = currentLanguage == code
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLanguageSelected(code) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onLanguageSelected(code) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
