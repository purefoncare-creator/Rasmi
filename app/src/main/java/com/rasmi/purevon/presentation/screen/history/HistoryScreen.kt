package com.rasmi.purevon.presentation.screen.history

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasmi.purevon.R
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.presentation.component.ContactAvatar
import com.rasmi.purevon.presentation.component.ConfirmationDialog
import com.rasmi.purevon.presentation.component.UnifiedContactAvatar
import com.rasmi.purevon.presentation.component.PermissionRequiredState
import com.rasmi.purevon.presentation.component.SimSelectorDialog
import com.rasmi.purevon.util.sim.SimCallAction
import com.rasmi.purevon.util.sim.SimInfo
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.util.PhoneUtil
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Call History Screen - Professional and compact design
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateToContact: ((Long) -> Unit)? = null,
    onNavigateToNewConversation: ((String) -> Unit)? = null,
    onNavigateToAddContact: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCallLog by remember { mutableStateOf<CallLog?>(null) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingCallSubId by remember { mutableStateOf<Int?>(null) }     // ✅ subscriptionId المعلق لطلب الإذن
    var showSimPickerDialog by remember { mutableStateOf(false) }        // ✅ حوار ASK
    var simPickerSims by remember { mutableStateOf<List<SimInfo>>(emptyList()) }
    var simPickerNumber by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    var selectedGroupKey by remember { mutableStateOf<String?>(null) }
    
    // SnackbarHostState for showing messages
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Check READ_CALL_LOG permission
    var hasCallLogPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == 
            android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Call log permission launcher
    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCallLogPermission = isGranted
        if (!isGranted) {
            // Show snackbar with option to go to settings
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }
    
    // Call permission launcher - makes the call after permission is granted
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingCallNumber?.let { number ->
                PhoneUtil.makeCall(context, number, pendingCallSubId) // ✅ مع subscriptionId
            }
        }
        pendingCallNumber = null
        pendingCallSubId = null
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(HistoryUiEvent.DismissError)
        }
    }
    
    // ✅ معالجة نتيجة تحديد الشريحة
    LaunchedEffect(uiState.simCallAction) {
        when (val action = uiState.simCallAction) {
            is SimCallAction.MakeCall -> {
                viewModel.onEvent(HistoryUiEvent.ClearSimCallAction)
                if (PhoneUtil.hasCallPermission(context)) {
                    PhoneUtil.makeCall(context, action.phoneNumber, action.subscriptionId)
                } else {
                    pendingCallNumber = action.phoneNumber
                    pendingCallSubId = action.subscriptionId
                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            }
            is SimCallAction.ShowSimPicker -> {
                viewModel.onEvent(HistoryUiEvent.ClearSimCallAction)
                simPickerNumber = action.phoneNumber
                simPickerSims = action.availableSims
                showSimPickerDialog = true
            }
            null -> {}
        }
    }
    
    // Show success message
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(HistoryUiEvent.DismissSuccessMessage)
        }
    }
    
    // Undo snackbar (delayed block only)
    LaunchedEffect(uiState.showUndoSnackbar, uiState.blockedNumber) {
        if (uiState.showUndoSnackbar) {
            val message = when {
                uiState.blockedNumber != null -> context.getString(R.string.history_number_will_be_blocked)
                else -> return@LaunchedEffect
            }

            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.history_undo),
                duration = SnackbarDuration.Short
            )

            when (result) {
                SnackbarResult.ActionPerformed -> {
                    viewModel.onEvent(HistoryUiEvent.UndoBlock)
                }
                SnackbarResult.Dismissed -> {
                    viewModel.onEvent(HistoryUiEvent.DismissUndoSnackbar)
                }
            }
        }
    }
    
    // Unknown number options bottom sheet
    if (selectedCallLog != null) {
        // ✅ Load notes when opening bottom sheet
        LaunchedEffect(selectedCallLog!!.phoneNumber) {
            viewModel.loadNotesForNumber(selectedCallLog!!.phoneNumber)
        }
        
        UnknownNumberBottomSheet(
            callLog = selectedCallLog!!,
            notes = uiState.notesForSelectedNumber, // ✅ Pass notes
            onDismiss = {
                selectedCallLog = null
                viewModel.clearSelectedNotes() // ✅ Clear notes on dismiss
            },
            onCall = {
                PhoneUtil.playClickSound(context)
                val number = selectedCallLog!!.phoneNumber
                selectedCallLog = null
                viewModel.onEvent(HistoryUiEvent.PrepareCall(number)) // ✅ عبر نظام الشريحة
            },
            onMessage = {
                // ✅ إغلاق Bottom Sheet أولاً
                val phoneNumber = selectedCallLog!!.phoneNumber
                selectedCallLog = null
                // Navigate to new conversation using NavController
                onNavigateToNewConversation?.invoke(phoneNumber)
            },
            onAddContact = {
                // ✅ إغلاق Bottom Sheet أولاً
                val phoneNumber = selectedCallLog!!.phoneNumber
                selectedCallLog = null
                // Navigate to add contact using NavController
                onNavigateToAddContact?.invoke(phoneNumber)
            },
            onBlock = {
                // Show confirmation dialog instead of direct action
                viewModel.onEvent(HistoryUiEvent.ShowBlockConfirmation(selectedCallLog!!))
                selectedCallLog = null
            },
            onDelete = {
                val phone = selectedCallLog!!.phoneNumber
                viewModel.onEvent(HistoryUiEvent.ShowDeleteAllForNumber(phone))
                selectedCallLog = null
            }
        )
    }
    
    // Delete all entries for one phone number (from bottom sheet)
    val pendingDeletePhone = uiState.pendingDeleteAllPhoneNumber
    if (uiState.showDeleteAllForNumberConfirmation && pendingDeletePhone != null) {
        ConfirmationDialog(
            title = stringResource(R.string.history_delete_all_calls_title),
            message = stringResource(
                R.string.history_delete_all_calls_message,
                PhoneUtil.formatPhoneNumber(pendingDeletePhone)
            ),
            icon = Icons.Default.Delete,
            confirmText = stringResource(R.string.action_delete),
            cancelText = stringResource(R.string.cancel),
            confirmColor = PurevonError,
            onConfirm = { viewModel.onEvent(HistoryUiEvent.ConfirmDeleteAllForNumber) },
            onDismiss = { viewModel.onEvent(HistoryUiEvent.DismissDeleteAllForNumber) }
        )
    }
    
    // Block confirmation dialog
    if (uiState.showBlockConfirmation && uiState.pendingActionCallLog != null) {
        ConfirmationDialog(
            title = stringResource(R.string.history_block_number_title),
            message = stringResource(R.string.history_block_number_message, PhoneUtil.formatPhoneNumber(uiState.pendingActionCallLog!!.phoneNumber)),
            icon = Icons.Default.Block,
            confirmText = stringResource(R.string.action_block),
            cancelText = stringResource(R.string.cancel),
            confirmColor = PurevonWarning,
            onConfirm = { viewModel.onEvent(HistoryUiEvent.ConfirmBlock) },
            onDismiss = { viewModel.onEvent(HistoryUiEvent.DismissBlockConfirmation) }
        )
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                HistorySelectionTopBar(
                    selectedCount = uiState.selectedGroupKeys.size,
                    totalCount = uiState.groupedContactCalls.size,
                    onClose = { viewModel.onEvent(HistoryUiEvent.ExitSelectionMode) },
                    onSelectAll = { viewModel.onEvent(HistoryUiEvent.SelectAllCallLogs) },
                    onDeselectAll = { viewModel.onEvent(HistoryUiEvent.DeselectAllCallLogs) }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode && uiState.selectedGroupKeys.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                HistorySelectionBottomBar(
                    selectedCount = uiState.selectedGroupKeys.size,
                    onDelete = { viewModel.onEvent(HistoryUiEvent.DeleteSelectedCallLogs) },
                    onBlock = { viewModel.onEvent(HistoryUiEvent.BlockSelectedCallLogs) }
                )
            }
        }
    ) { paddingValues ->
        // Check if permission is granted
        if (!hasCallLogPermission) {
            PermissionRequiredState(
                permission = stringResource(R.string.history_permission_name),
                rationale = stringResource(R.string.history_permission_rationale),
                onRequestPermission = {
                    callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                },
                modifier = Modifier.padding(paddingValues)
            )
            return@Scaffold
        }
        
        // Main content (shown when permission is granted)
        val handleGroupClick: (GroupedContactCalls) -> Unit = { group ->
            if (uiState.isSelectionMode) {
                viewModel.onEvent(HistoryUiEvent.ToggleGroupedSelection(group.groupKey))
            } else if (isWide) {
                selectedGroupKey = group.groupKey
            } else {
                val contactId = uiState.getContactIdForNumber(group.phoneNumber)
                if (contactId != null && onNavigateToContact != null) {
                    onNavigateToContact(contactId)
                } else {
                    selectedCallLog = group.latestCall
                }
            }
        }
        val handleGroupLongClick: (GroupedContactCalls) -> Unit = { group ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.onEvent(HistoryUiEvent.GroupedRowLongPressed(group.groupKey))
        }
        val handleCallBack: (String) -> Unit = { number ->
            PhoneUtil.playClickSound(context)
            viewModel.onEvent(HistoryUiEvent.PrepareCall(number))
        }

        if (isWide) {
            HistoryWideLayout(
                uiState = uiState,
                listState = listState,
                selectedGroupKey = selectedGroupKey,
                onGroupClick = handleGroupClick,
                onGroupLongClick = handleGroupLongClick,
                onCallBack = handleCallBack,
                onQueryChange = { viewModel.onEvent(HistoryUiEvent.SearchQueryChanged(it)) },
                onFilterSelected = { viewModel.onEvent(HistoryUiEvent.FilterSelected(it)) },
                onRefresh = { viewModel.onEvent(HistoryUiEvent.RefreshLogs) },
                onPrepareCall = { viewModel.onEvent(HistoryUiEvent.PrepareCall(it)) },
                onMessage = { onNavigateToNewConversation?.invoke(it) },
                onAddContact = { onNavigateToAddContact?.invoke(it) },
                paddingValues = paddingValues
            )
        } else {
            HistoryCompactLayout(
                uiState = uiState,
                listState = listState,
                onGroupClick = handleGroupClick,
                onGroupLongClick = handleGroupLongClick,
                onCallBack = handleCallBack,
                onQueryChange = { viewModel.onEvent(HistoryUiEvent.SearchQueryChanged(it)) },
                onFilterSelected = { viewModel.onEvent(HistoryUiEvent.FilterSelected(it)) },
                onRefresh = { viewModel.onEvent(HistoryUiEvent.RefreshLogs) },
                paddingValues = paddingValues
            )
        }
    }

    // ✅ حوار اختيار الشريحة (وضع ASK)
    if (showSimPickerDialog) {
        SimSelectorDialog(
            availableSims = simPickerSims,
            selectedSimId = null,
            title = stringResource(com.rasmi.purevon.R.string.sim_picker_call_title),
            onSimSelected = { subscriptionId ->
                showSimPickerDialog = false
                if (PhoneUtil.hasCallPermission(context)) {
                    PhoneUtil.makeCall(context, simPickerNumber, subscriptionId)
                } else {
                    pendingCallNumber = simPickerNumber
                    pendingCallSubId = subscriptionId
                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            },
            onDismiss = { showSimPickerDialog = false }
        )
    }
}

/**
 * Compact Header with statistics (no title, just stats)
 */
@Composable
private fun CompactHistoryHeader(
    statistics: CallStatistics?,
    modifier: Modifier = Modifier
) {
    // Statistics row only - no title
    statistics?.let { stats ->
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CompactStatItem(
                icon = Icons.Default.Phone,
                value = stats.totalCalls.toString(),
                label = stringResource(R.string.history_total),
                color = PurevonTertiary
            )
            CompactStatItem(
                icon = Icons.AutoMirrored.Filled.CallMissed,
                value = stats.missedCalls.toString(),
                label = stringResource(R.string.history_missed),
                color = PurevonCallMissed
            )
            CompactStatItem(
                icon = Icons.Default.Timer,
                value = formatCompactDuration(stats.totalDuration),
                label = stringResource(R.string.history_duration),
                color = PurevonSecondary
            )
        }
    }
}

/**
 * Search bar + three-dot menu — same style as Messages/Contacts screens
 */
@Composable
private fun HistorySearchBarWithMenu(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: CallFilter,
    onFilterSelected: (CallFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Three-dot menu
            Box {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { menuExpanded = true }
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    CallFilter.entries.forEach { filter ->
                        val (icon, color) = getFilterIconAndColor(filter)
                        DropdownMenuItem(
                            text = { Text(filter.getDisplayName()) },
                            onClick = {
                                onFilterSelected(filter)
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selectedFilter == filter) MaterialTheme.colorScheme.primary else color,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (selectedFilter == filter) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Search icon
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Search text field
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.history_search_calls),
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.history_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

/**
 * Compact stat item
 */
@Composable
private fun CompactStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 9.sp
            )
        }
    }
}

/**
 * Compact filter row - all filters visible
 */
@Composable
private fun CompactFilterRow(
    selectedFilter: CallFilter,
    onFilterSelected: (CallFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(CallFilter.entries) { filter ->
            val isSelected = selectedFilter == filter
            val (icon, color) = getFilterIconAndColor(filter)
            
            Surface(
                onClick = { onFilterSelected(filter) },
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) color.copy(alpha = 0.15f) 
                       else PurevonSurfaceAlt,
                border = if (isSelected) null 
                        else BorderStroke(1.dp, PurevonBorder),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = filter.getDisplayName(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Slim sticky header — anchors each calendar section (replaces long unstructured scroll).
 */
@Composable
private fun HistoryDateSectionHeader(
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = java.util.Locale.getDefault()
    val dateText = when {
        date == LocalDate.now() -> context.getString(R.string.history_today)
        date == LocalDate.now().minusDays(1) -> context.getString(R.string.history_yesterday)
        date.isAfter(LocalDate.now().minusDays(7)) ->
            date.format(DateTimeFormatter.ofPattern("EEEE", locale))
        date.year == LocalDate.now().year ->
            date.format(DateTimeFormatter.ofPattern("MMM d", locale))
        else ->
            date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Text(
                text = dateText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * Empty state with filter context
 */
@Composable
private fun EmptyHistoryState(
    filter: CallFilter,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (icon, message) = when (filter) {
        CallFilter.ALL -> Icons.Default.History to context.getString(R.string.history_no_history)
        CallFilter.MISSED -> Icons.AutoMirrored.Filled.CallMissed to context.getString(R.string.history_no_missed_calls)
        CallFilter.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to context.getString(R.string.history_no_incoming_calls)
        CallFilter.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to context.getString(R.string.history_no_outgoing_calls)
        CallFilter.REJECTED -> Icons.Default.CallEnd to context.getString(R.string.history_no_rejected_calls)
        CallFilter.BLOCKED -> Icons.Default.Block to context.getString(R.string.history_no_blocked_calls)
    }
    
    Column(
        modifier = modifier.padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = context.getString(R.string.history_calls_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

/**
 * WIDE two-column master/detail layout.
 */
@Composable
private fun HistoryWideLayout(
    uiState: HistoryUiState,
    listState: LazyListState,
    selectedGroupKey: String?,
    onGroupClick: (GroupedContactCalls) -> Unit,
    onGroupLongClick: (GroupedContactCalls) -> Unit,
    onCallBack: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (CallFilter) -> Unit,
    onRefresh: () -> Unit,
    onPrepareCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onAddContact: (String) -> Unit,
    paddingValues: PaddingValues
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // ── Left pane: call log list ──
        HistoryLogPane(
            uiState = uiState,
            listState = listState,
            isWide = true,
            selectedGroupKey = selectedGroupKey,
            onGroupClick = onGroupClick,
            onGroupLongClick = onGroupLongClick,
            onCallBack = onCallBack,
            onQueryChange = onQueryChange,
            onFilterSelected = onFilterSelected,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .background(PurevonBackground)
        )

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp,
            color = PurevonBorder
        )

        // ── Right pane: detail (master/detail) ──
        Column(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .background(PurevonSurfaceAlt)
        ) {
            val selectedGroup = selectedGroupKey?.let { key ->
                uiState.groupedContactCalls.find { it.groupKey == key }
            }
            AnimatedVisibility(
                visible = selectedGroup != null,
                enter = fadeIn() + slideInHorizontally { it / 3 },
                exit = fadeOut()
            ) {
                selectedGroup?.let { group ->
                    HistoryDetailView(
                        group = group,
                        contactName = uiState.getContactNameForNumber(group.phoneNumber),
                        onCall = { onPrepareCall(group.phoneNumber) },
                        onMessage = { onMessage(group.phoneNumber) },
                        onAddContact = { onAddContact(group.phoneNumber) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (selectedGroup == null) {
                HistoryDetailPlaceholder(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * COMPACT single-column layout (list only; navigation via callbacks).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryCompactLayout(
    uiState: HistoryUiState,
    listState: LazyListState,
    onGroupClick: (GroupedContactCalls) -> Unit,
    onGroupLongClick: (GroupedContactCalls) -> Unit,
    onCallBack: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (CallFilter) -> Unit,
    onRefresh: () -> Unit,
    paddingValues: PaddingValues
) {
    HistoryLogPane(
        uiState = uiState,
        listState = listState,
        isWide = false,
        selectedGroupKey = null,
        onGroupClick = onGroupClick,
        onGroupLongClick = onGroupLongClick,
        onCallBack = onCallBack,
        onQueryChange = onQueryChange,
        onFilterSelected = onFilterSelected,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(PurevonBackground)
    )
}

/**
 * Shared call-log list pane (search bar + pull-to-refresh + grouped rows).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryLogPane(
    uiState: HistoryUiState,
    listState: LazyListState,
    isWide: Boolean,
    selectedGroupKey: String?,
    onGroupClick: (GroupedContactCalls) -> Unit,
    onGroupLongClick: (GroupedContactCalls) -> Unit,
    onCallBack: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (CallFilter) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(Modifier.statusBarsPadding().then(modifier)) {
        // Search bar + three-dot menu (same style as Messages/Contacts)
        HistorySearchBarWithMenu(
            query = uiState.searchQuery,
            onQueryChange = onQueryChange,
            selectedFilter = uiState.selectedFilter,
            onFilterSelected = onFilterSelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading && uiState.groupedContactCalls.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                uiState.groupedContactCalls.isEmpty() -> {
                    EmptyHistoryState(
                        filter = uiState.selectedFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    val sections = remember(uiState.groupedContactCalls) {
                        groupContactCallsByDay(uiState.groupedContactCalls)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        sections.forEach { (date, groupsForDay) ->
                            item(key = "header_${date.toEpochDay()}") {
                                HistoryDateSectionHeader(date = date)
                            }
                            itemsIndexed(
                                items = groupsForDay,
                                key = { _, g -> g.groupKey }
                            ) { index, group ->
                                val log = group.latestCall
                                val resolvedContactName =
                                    uiState.getContactNameForNumber(group.phoneNumber)

                                CompactGroupedCallRow(
                                    group = group,
                                    resolvedContactName = resolvedContactName,
                                    isSelectionMode = uiState.isSelectionMode,
                                    isSelected = group.groupKey in uiState.selectedGroupKeys,
                                    hasNote = uiState.hasNoteForNumber(group.phoneNumber),
                                    useShortTimeSubtitle = true,
                                    showBottomDivider = index < groupsForDay.lastIndex,
                                    onCallBack = { onCallBack(group.phoneNumber) },
                                    onClick = { onGroupClick(group) },
                                    onLongClick = { onGroupLongClick(group) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Inline call detail view shown in the wide right pane.
 */
@Composable
private fun HistoryDetailView(
    group: GroupedContactCalls,
    contactName: String?,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onAddContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val log = group.latestCall
    val (icon, color) = getCallTypeIconAndColor(log.callType, log.isBlocked)
    val name = contactName?.takeIf { it.isNotBlank() }
        ?: group.contactName?.takeIf { it.isNotBlank() }
        ?: PhoneUtil.formatPhoneNumber(group.phoneNumber)
    val dateTime = remember(log.timestamp) {
        val zdt = Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault())
        zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d • HH:mm"))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl)
            .animateContentSize(spring()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.rasmi.purevon.presentation.component.FavoriteContactAvatar(
            size = 96.dp,
            photoUri = log.contactPhotoUri,
            isFavorite = group.isFavorite
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = PurevonTextPrimary
        )
        Text(
            text = PhoneUtil.formatPhoneNumber(group.phoneNumber),
            style = MaterialTheme.typography.bodyMedium,
            color = PurevonTextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = color.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = getCallTypeLabel(log.callType, log.isBlocked),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = dateTime,
            style = MaterialTheme.typography.bodySmall,
            color = PurevonTextTertiary
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            HistoryDetailActionButton(
                icon = Icons.Default.Call,
                label = "Call",
                color = PurevonCallOutgoing,
                onClick = onCall
            )
            HistoryDetailActionButton(
                icon = Icons.AutoMirrored.Filled.Message,
                label = "Message",
                color = PurevonSecondary,
                onClick = onMessage
            )
            HistoryDetailActionButton(
                icon = Icons.Default.PersonAdd,
                label = "Add",
                color = PurevonPrimary,
                onClick = onAddContact
            )
        }
    }
}

@Composable
private fun HistoryDetailActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HistoryDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallReceived,
                contentDescription = null,
                tint = PurevonTextTertiary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Select a call",
                style = MaterialTheme.typography.bodyMedium,
                color = PurevonTextTertiary
            )
        }
    }
}

private fun getCallTypeLabel(callType: CallType, isBlocked: Boolean): String {
    return when {
        isBlocked -> "Blocked"
        callType == CallType.MISSED -> "Missed"
        callType == CallType.INCOMING -> "Incoming"
        callType == CallType.OUTGOING -> "Outgoing"
        callType == CallType.REJECTED -> "Rejected"
        callType == CallType.VOICEMAIL -> "Voicemail"
        else -> "Call"
    }
}

