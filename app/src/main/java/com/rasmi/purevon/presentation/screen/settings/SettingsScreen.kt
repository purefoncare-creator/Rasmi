package com.rasmi.purevon.presentation.screen.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.component.SimSelectorDialog

import androidx.compose.foundation.isSystemInDarkTheme
import com.rasmi.purevon.util.CallbackReminderScheduleManager
import com.rasmi.purevon.util.FakeCallScheduleManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.rasmi.purevon.presentation.theme.*
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
                                   else stringResource(R.string.fake_call_active_count, uiState.scheduledFakeCalls.size),
                        onClick = { viewModel.onEvent(SettingsUiEvent.ShowFakeCalls) },
                        iconTint = MaterialCyan400
                    )
                    CardItemDivider()
                    // ✅ إدارة تذكيرات إعادة الاتصال المجدولة
                    ClickableSettingItem(
                        icon = Icons.Default.Alarm,
                        title = stringResource(R.string.callback_reminders_manage_title),
                        subtitle = if (uiState.scheduledCallbackReminders.isEmpty()) ""
                                   else stringResource(R.string.callback_reminder_active_count, uiState.scheduledCallbackReminders.size),
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
    if (uiState.showCallBlockingSimSelectorDialog &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1
    ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedListDialog(
    blockedNumbers: List<String>,
    contacts: List<com.rasmi.purevon.domain.model.Contact>,
    recentCalls: List<RecentCallItem>,
    onAddNumber: (String, String?) -> Unit,
    onRemoveNumber: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Contacts, 1 = Recent
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.blocked_list_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                
                // Blocked list (if any)
                if (blockedNumbers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.blocked_list_count, blockedNumbers.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                    ) {
                        items(blockedNumbers) { number ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = number,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onRemoveNumber(number) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.remove),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                
                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.blocked_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.blocked_tab_contacts)) },
                        icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.blocked_tab_recent)) },
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Content
                when (selectedTab) {
                    0 -> {
                        // Contacts
                        val filteredContacts = if (searchQuery.isEmpty()) contacts
                        else contacts.filter { 
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.phoneNumber.contains(searchQuery)
                        }
                        
                        if (filteredContacts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_contacts_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredContacts) { contact ->
                                    val isBlocked = blockedNumbers.any { 
                                        it.replace(Regex("[^0-9+]"), "") == contact.phoneNumber.replace(Regex("[^0-9+]"), "")
                                    }
                                    ContactPickerItem(
                                        name = contact.name,
                                        phoneNumber = contact.phoneNumber,
                                        isSelected = isBlocked,
                                        selectedIcon = Icons.Default.Block,
                                        selectedColor = MaterialTheme.colorScheme.error,
                                        onClick = {
                                            if (!isBlocked) {
                                                onAddNumber(contact.phoneNumber, contact.name)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Recent calls
                        val filteredRecent = if (searchQuery.isEmpty()) recentCalls
                        else recentCalls.filter { 
                            (it.contactName?.contains(searchQuery, ignoreCase = true) ?: false) ||
                            it.phoneNumber.contains(searchQuery)
                        }
                        
                        if (filteredRecent.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_recent_calls), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredRecent) { call ->
                                    val isBlocked = blockedNumbers.any { 
                                        it.replace(Regex("[^0-9+]"), "") == call.phoneNumber.replace(Regex("[^0-9+]"), "")
                                    }
                                    ContactPickerItem(
                                        name = call.contactName ?: call.phoneNumber,
                                        phoneNumber = if (call.contactName != null) call.phoneNumber else "",
                                        isSelected = isBlocked,
                                        selectedIcon = Icons.Default.Block,
                                        selectedColor = MaterialTheme.colorScheme.error,
                                        callTypeIcon = when (call.callType) {
                                            1 -> Icons.Default.CallReceived
                                            2 -> Icons.Default.CallMade
                                            3 -> Icons.Default.CallMissed
                                            else -> null
                                        },
                                        onClick = {
                                            if (!isBlocked) {
                                                onAddNumber(call.phoneNumber, call.contactName)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactPickerItem(
    name: String,
    phoneNumber: String,
    isSelected: Boolean,
    selectedIcon: ImageVector = Icons.Default.Check,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    callTypeIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSelected, onClick = onClick)
            .alpha(if (isSelected) 0.5f else 1f),
        color = if (isSelected) selectedColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (isSelected) selectedColor.copy(alpha = 0.2f) 
                       else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Icon(
                            selectedIcon,
                            contentDescription = null,
                            tint = selectedColor,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (callTypeIcon != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            callTypeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when (callTypeIcon) {
                                Icons.Default.CallMissed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                if (phoneNumber.isNotEmpty()) {
                    Text(
                        text = phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (!isSelected) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhitelistDialog(
    whitelistNumbers: List<WhitelistItem>,
    contacts: List<com.rasmi.purevon.domain.model.Contact>,
    recentCalls: List<RecentCallItem>,
    onAddNumber: (String, String?) -> Unit,
    onRemoveNumber: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Contacts, 1 = Recent
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.whitelist_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                
                // Whitelist (if any)
                if (whitelistNumbers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.whitelist_count, whitelistNumbers.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                    ) {
                        items(whitelistNumbers) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (item.contactName != null) {
                                        Text(
                                            text = item.contactName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = item.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = item.phoneNumber,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveNumber(item.phoneNumber) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.remove),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                
                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.whitelist_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.contacts_label)) },
                        icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.recent_calls_label)) },
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Content
                when (selectedTab) {
                    0 -> {
                        // Contacts
                        val filteredContacts = if (searchQuery.isEmpty()) contacts
                        else contacts.filter { 
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.phoneNumber.contains(searchQuery)
                        }
                        
                        if (filteredContacts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_contacts_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredContacts) { contact ->
                                    val isWhitelisted = whitelistNumbers.any { 
                                        it.phoneNumber.replace(Regex("[^0-9+]"), "") == contact.phoneNumber.replace(Regex("[^0-9+]"), "")
                                    }
                                    ContactPickerItem(
                                        name = contact.name,
                                        phoneNumber = contact.phoneNumber,
                                        isSelected = isWhitelisted,
                                        selectedIcon = Icons.Default.VerifiedUser,
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        onClick = {
                                            if (!isWhitelisted) {
                                                onAddNumber(contact.phoneNumber, contact.name)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Recent calls
                        val filteredRecent = if (searchQuery.isEmpty()) recentCalls
                        else recentCalls.filter { 
                            (it.contactName?.contains(searchQuery, ignoreCase = true) ?: false) ||
                            it.phoneNumber.contains(searchQuery)
                        }
                        
                        if (filteredRecent.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_recent_calls), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredRecent) { call ->
                                    val isWhitelisted = whitelistNumbers.any { 
                                        it.phoneNumber.replace(Regex("[^0-9+]"), "") == call.phoneNumber.replace(Regex("[^0-9+]"), "")
                                    }
                                    ContactPickerItem(
                                        name = call.contactName ?: call.phoneNumber,
                                        phoneNumber = if (call.contactName != null) call.phoneNumber else "",
                                        isSelected = isWhitelisted,
                                        selectedIcon = Icons.Default.VerifiedUser,
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        callTypeIcon = when (call.callType) {
                                            1 -> Icons.Default.CallReceived
                                            2 -> Icons.Default.CallMade
                                            3 -> Icons.Default.CallMissed
                                            else -> null
                                        },
                                        onClick = {
                                            if (!isWhitelisted) {
                                                onAddNumber(call.phoneNumber, call.contactName)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLightTheme) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isLightTheme) 0.42f else 0.28f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun CardItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 18.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    )
}

@Composable
private fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    iconTint: Color = Indigo400
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.13f * alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint.copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f * alpha)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun ClickableSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    tint: Color? = null,
    iconTint: Color = Indigo400
) {
    val resolvedIconTint = tint ?: iconTint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(resolvedIconTint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = resolvedIconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tint ?: MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
    }
}
/**
 * ✅ نافذة منبثقة لإدارة الاتصالات الوهمية المجدولة
 * تعرض قائمة بالاتصالات النشطة مع زر الإلغاء لكل واحدة
 */
@Composable
private fun ScheduledFakeCallsDialog(
    scheduledCalls: List<FakeCallScheduleManager.ScheduledFakeCall>,
    onCancelCall: (Int) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // ─── Header ─────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialCyan400.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneCallback,
                                contentDescription = null,
                                tint = MaterialCyan400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.fake_call_manage_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row {
                        // زر تحديث القائمة
                        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ─── قائمة فارغة ────────────────────────────────────────
                if (scheduledCalls.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneDisabled,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.fake_call_no_scheduled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                } else {
                    // ─── عناصر الاتصالات النشطة ──────────────────────────
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = scheduledCalls,
                            key = { it.requestCode }
                        ) { call ->
                            ScheduledFakeCallItem(
                                call = call,
                                formattedTime = timeFormat.format(Date(call.triggerTimeMs)),
                                onCancel = { onCancelCall(call.requestCode) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // زر إلغاء الكل
                    OutlinedButton(
                        onClick = {
                            scheduledCalls.forEach { onCancelCall(it.requestCode) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.fake_call_cancel_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledFakeCallItem(
    call: FakeCallScheduleManager.ScheduledFakeCall,
    formattedTime: String,
    onCancel: () -> Unit
) {
    // ✅ تحديث يتم كل ثانية تلقائياً
    var remainingMs by remember { mutableLongStateOf(call.triggerTimeMs - System.currentTimeMillis()) }

    LaunchedEffect(call.triggerTimeMs) {
        while (remainingMs > 0) {
            kotlinx.coroutines.delay(1_000L)
            remainingMs = call.triggerTimeMs - System.currentTimeMillis()
        }
    }

    val remainingText = when {
        remainingMs <= 0 -> ""
        remainingMs < 60_000 -> "${remainingMs / 1000}s"
        remainingMs < 3_600_000 -> "${remainingMs / 60_000}m ${(remainingMs % 60_000) / 1000}s"
        else -> "${remainingMs / 3_600_000}h ${(remainingMs % 3_600_000) / 60_000}m"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة المتصل
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialCyan400.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialCyan400,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // معلومات الاتصال
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.callerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    if (remainingText.isNotEmpty()) {
                        Text(
                            text = "($remainingText)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialCyan400.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            // زر الإلغاء
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = stringResource(R.string.fake_call_cancel_call),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * ✅ نافذة منبثقة لإدارة تذكيرات إعادة الاتصال المجدولة
 */
@Composable
private fun ScheduledCallbackRemindersDialog(
    scheduledReminders: List<CallbackReminderScheduleManager.ScheduledReminder>,
    onCancelReminder: (Int) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // ─── Header ────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MaterialOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.callback_reminders_manage_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (scheduledReminders.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlarmOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.callback_reminder_no_scheduled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = scheduledReminders,
                            key = { it.requestCode }
                        ) { reminder ->
                            ScheduledCallbackReminderItem(
                                reminder = reminder,
                                formattedTime = timeFormat.format(Date(reminder.triggerTimeMs)),
                                onCancel = { onCancelReminder(reminder.requestCode) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            scheduledReminders.forEach { onCancelReminder(it.requestCode) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.callback_reminder_cancel_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledCallbackReminderItem(
    reminder: CallbackReminderScheduleManager.ScheduledReminder,
    formattedTime: String,
    onCancel: () -> Unit
) {
    var remainingMs by remember { mutableLongStateOf(reminder.triggerTimeMs - System.currentTimeMillis()) }

    LaunchedEffect(reminder.triggerTimeMs) {
        while (remainingMs > 0) {
            kotlinx.coroutines.delay(1_000L)
            remainingMs = reminder.triggerTimeMs - System.currentTimeMillis()
        }
    }

    val remainingText = when {
        remainingMs <= 0 -> ""
        remainingMs < 60_000 -> "${remainingMs / 1000}s"
        remainingMs < 3_600_000 -> "${remainingMs / 60_000}m ${(remainingMs % 60_000) / 1000}s"
        else -> "${remainingMs / 3_600_000}h ${(remainingMs % 3_600_000) / 60_000}m"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialOrange,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (reminder.contactName.isNotBlank()) reminder.contactName else reminder.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (reminder.contactName.isNotBlank() && reminder.contactName != reminder.phoneNumber) {
                    Text(
                        text = reminder.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    if (remainingText.isNotEmpty()) {
                        Text(
                            text = "($remainingText)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialOrange.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = stringResource(R.string.callback_reminder_cancel_item),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
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
