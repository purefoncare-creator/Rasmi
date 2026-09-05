package com.rasmi.purevon.presentation.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockedListDialog(
    blockedNumbers: List<String>,
    contacts: List<Contact>,
    recentCalls: List<RecentCallItem>,
    onAddNumber: (String, String?) -> Unit,
    onRemoveNumber: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

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

                when (selectedTab) {
                    0 -> {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhitelistDialog(
    whitelistNumbers: List<WhitelistItem>,
    contacts: List<Contact>,
    recentCalls: List<RecentCallItem>,
    onAddNumber: (String, String?) -> Unit,
    onRemoveNumber: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

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

                when (selectedTab) {
                    0 -> {
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

/**
 * ✅ FIX M32b: نافذة إعدادات حظر المكالمات.
 * كل التغييرات محلية (staged) ولا تُطبَّق إلا بالضغط على Save —
 * بجانب زر Close. تشمل خيارين كانا مخفيين: حظر المجهول ووضع القائمة البيضاء.
 */
@Composable
internal fun CallBlockingSettingsDialog(
    callBlockingEnabled: Boolean,
    blockUnknownNumbers: Boolean,
    whitelistOnlyMode: Boolean,
    simSubscriptionId: Int,
    availableSims: List<com.rasmi.purevon.util.sim.SimInfo>,
    onSave: (enabled: Boolean, blockUnknown: Boolean, whitelistOnly: Boolean, simSubId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // حالة مرحلية — لا تمس DataStore حتى Save
    var stagedEnabled by remember { mutableStateOf(callBlockingEnabled) }
    var stagedBlockUnknown by remember { mutableStateOf(blockUnknownNumbers) }
    var stagedWhitelistOnly by remember { mutableStateOf(whitelistOnlyMode) }
    var stagedSimSubId by remember { mutableIntStateOf(simSubscriptionId) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.call_blocking_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── التفعيل الرئيسي ──
                SettingSwitchRow(
                    title = stringResource(R.string.call_blocking_enable),
                    checked = stagedEnabled,
                    onCheckedChange = { stagedEnabled = it },
                    tint = MaterialTheme.colorScheme.error
                )

                // ── اختيار الشريحة (إن وُجدت أكثر من واحدة) ──
                if (availableSims.size > 1 && stagedEnabled) {
                    CardItemDivider()
                    Text(
                        text = stringResource(R.string.sim_select_for_blocking),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    SimChoiceRow(
                        label = stringResource(R.string.sim_all),
                        selected = stagedSimSubId == -1,
                        onClick = { stagedSimSubId = -1 }
                    )
                    availableSims.forEach { sim ->
                        SimChoiceRow(
                            label = sim.displayName,
                            selected = stagedSimSubId == sim.subscriptionId,
                            onClick = { stagedSimSubId = sim.subscriptionId }
                        )
                    }
                }

                // ── حظر الأرقام غير المعروفة ──
                if (stagedEnabled) {
                    CardItemDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.call_blocking_block_unknown),
                        subtitle = stringResource(R.string.call_blocking_block_unknown_desc),
                        checked = stagedBlockUnknown,
                        onCheckedChange = { stagedBlockUnknown = it },
                        tint = MaterialTheme.colorScheme.error
                    )

                    // ── وضع القائمة البيضاء فقط ──
                    CardItemDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.call_blocking_whitelist_only),
                        subtitle = stringResource(R.string.call_blocking_whitelist_only_desc),
                        checked = stagedWhitelistOnly,
                        onCheckedChange = { stagedWhitelistOnly = it },
                        tint = MaterialTheme.colorScheme.primary
                    )
                    if (stagedWhitelistOnly) {
                        Text(
                            text = stringResource(R.string.call_blocking_whitelist_only_warning),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── ✅ أزرار Close + Save ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(stagedEnabled, stagedBlockUnknown, stagedWhitelistOnly, stagedSimSubId) }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SimChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
