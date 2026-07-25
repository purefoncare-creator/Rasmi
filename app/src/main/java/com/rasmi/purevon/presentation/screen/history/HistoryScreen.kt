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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.*
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
    val uiState by viewModel.uiState.collectAsState()
    var selectedCallLog by remember { mutableStateOf<CallLog?>(null) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingCallSubId by remember { mutableStateOf<Int?>(null) }     // ✅ subscriptionId المعلق لطلب الإذن
    var showSimPickerDialog by remember { mutableStateOf(false) }        // ✅ حوار ASK
    var simPickerSims by remember { mutableStateOf<List<SimInfo>>(emptyList()) }
    var simPickerNumber by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    
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
            confirmColor = errorColor(),
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
            confirmColor = warningColor(),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search bar + three-dot menu (same style as Messages/Contacts)
            HistorySearchBarWithMenu(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.onEvent(HistoryUiEvent.SearchQueryChanged(it)) },
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { viewModel.onEvent(HistoryUiEvent.FilterSelected(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            
            // Pull to refresh + Call logs list
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onEvent(HistoryUiEvent.RefreshLogs) },
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
                                        onCallBack = {
                                            PhoneUtil.playClickSound(context)
                                            viewModel.onEvent(
                                                HistoryUiEvent.PrepareCall(group.phoneNumber)
                                            )
                                        },
                                        onClick = {
                                            if (uiState.isSelectionMode) {
                                                viewModel.onEvent(
                                                    HistoryUiEvent.ToggleGroupedSelection(group.groupKey)
                                                )
                                            } else {
                                                val contactId =
                                                    uiState.getContactIdForNumber(group.phoneNumber)
                                                if (contactId != null && onNavigateToContact != null) {
                                                    onNavigateToContact(contactId)
                                                } else {
                                                    selectedCallLog = log
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.onEvent(
                                                HistoryUiEvent.GroupedRowLongPressed(group.groupKey)
                                            )
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

    // ✅ حوار اختيار الشريحة (وضع ASK)
    if (showSimPickerDialog && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
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
                color = infoColor()
            )
            CompactStatItem(
                icon = Icons.AutoMirrored.Filled.CallMissed,
                value = stats.missedCalls.toString(),
                label = stringResource(R.string.history_missed),
                color = missedCallColor()
            )
            CompactStatItem(
                icon = Icons.Default.Timer,
                value = formatCompactDuration(stats.totalDuration),
                label = stringResource(R.string.history_duration),
                color = successColor()
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
            val isLightTheme = !isSystemInDarkTheme()
            val (icon, color) = getFilterIconAndColor(filter)
            
            Surface(
                onClick = { onFilterSelected(filter) },
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) color.copy(alpha = 0.15f) 
                       else if (isLightTheme) WireOtherBubbleLight
                       else Color.Transparent,
                border = if (isSelected) null 
                        else if (isLightTheme) BorderStroke(1.dp, LightBorder)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
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

private fun groupContactCallsByDay(
    groups: List<GroupedContactCalls>
): List<Pair<LocalDate, List<GroupedContactCalls>>> {
    val zone = ZoneId.systemDefault()
    return groups
        .groupBy { Instant.ofEpochMilli(it.lastTimestamp).atZone(zone).toLocalDate() }
        .entries
        .sortedByDescending { it.key }
        .map { (date, list) ->
            date to list.sortedByDescending { it.lastTimestamp }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactGroupedCallRow(
    group: GroupedContactCalls,
    resolvedContactName: String?,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    hasNote: Boolean = false,
    /** Header shows calendar day — subtitle shows clock only to reduce repetition and width */
    useShortTimeSubtitle: Boolean = false,
    showBottomDivider: Boolean = true,
    onCallBack: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val metaScroll = rememberScrollState()
    val log = group.latestCall
    val (icon, color) = getCallTypeIconAndColor(log.callType, log.isBlocked)

    val displayName = resolvedContactName?.takeIf { it.isNotBlank() }
        ?: group.contactName?.takeIf { it.isNotBlank() }
        ?: group.phoneNumber.takeIf { it.isNotBlank() && it != "Unknown" }
        ?: PhoneUtil.formatPhoneNumber(group.phoneNumber)

    val rowBg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        else -> Color.Transparent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(rowBg)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                ContactAvatar(
                    name = displayName,
                    photoUri = group.contactPhotoUri,
                    modifier = Modifier.size(48.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (log.callType == CallType.MISSED) {
                            missedCallColor()
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.horizontalScroll(metaScroll),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (useShortTimeSubtitle) {
                            formatHistoryClockTime(group.lastTimestamp)
                        } else {
                            formatRelativeDateTime(group.lastTimestamp, context)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        fontSize = 11.sp
                    )

                    Text(
                        text = log.callType.getDisplayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )

                    if (group.callCount > 1) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = stringResource(R.string.history_calls, group.callCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (log.callType == CallType.MISSED) missedCallColor()
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    val displaySimSlot = log.simSlot?.takeIf { it in 0..1 }
                    if (displaySimSlot != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = stringResource(R.string.history_sim, displaySimSlot + 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    if (log.duration > 0 &&
                        (log.callType == CallType.INCOMING || log.callType == CallType.OUTGOING)
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = formatCallDuration(log.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    if (hasNote) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            fontSize = 11.sp
                        )
                        Icon(
                            imageVector = Icons.Outlined.StickyNote2,
                            contentDescription = stringResource(R.string.history_notes_cd_has_note),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                        .clickable(onClick = onCallBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = stringResource(R.string.history_action_call),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (showBottomDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        }
    }
}

/**
 * Selection Mode Top Bar - slim design matching contacts screen
 */
@Composable
private fun HistorySelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = stringResource(R.string.history_selected_count, selectedCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            TextButton(
                onClick = if (selectedCount == totalCount) onDeselectAll else onSelectAll,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (selectedCount == totalCount) stringResource(R.string.history_deselect_all) else stringResource(R.string.history_select_all),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Selection Mode Bottom Bar - slim design matching contacts screen
 */
@Composable
private fun HistorySelectionBottomBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Block
            HistoryBottomBarAction(
                icon = Icons.Outlined.Block,
                label = stringResource(R.string.history_action_block),
                color = warningColor(),
                onClick = onBlock
            )
            
            // Delete
            HistoryBottomBarAction(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.history_action_delete),
                color = errorColor(),
                onClick = { showDeleteDialog = true }
            )
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(stringResource(R.string.history_delete_selected_numbers, selectedCount))
            },
            text = {
                Text(stringResource(R.string.history_delete_selected_warning))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Bottom Bar Action Button for History
 */
@Composable
private fun HistoryBottomBarAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp
        )
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

// Helper functions

@Composable
private fun getFilterIconAndColor(filter: CallFilter): Pair<ImageVector, Color> {
    return when (filter) {
        CallFilter.ALL -> Icons.Default.Phone to infoColor()
        CallFilter.MISSED -> Icons.AutoMirrored.Filled.CallMissed to missedCallColor()
        CallFilter.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to incomingCallColor()
        CallFilter.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to outgoingCallColor()
        CallFilter.REJECTED -> Icons.Default.CallEnd to rejectedCallColor()
        CallFilter.BLOCKED -> Icons.Default.Block to errorColor()
    }
}

@Composable
private fun getCallTypeIconAndColor(callType: CallType, isBlocked: Boolean): Pair<ImageVector, Color> {
    return when {
        isBlocked -> Icons.Default.Block to errorColor()
        callType == CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to missedCallColor()
        callType == CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to incomingCallColor()
        callType == CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to outgoingCallColor()
        callType == CallType.REJECTED -> Icons.Default.CallEnd to rejectedCallColor()
        else -> Icons.Default.Call to infoColor()
    }
}

@Composable
private fun CallFilter.getDisplayName(): String {
    val context = LocalContext.current
    return when (this) {
        CallFilter.ALL -> context.getString(R.string.history_filter_all)
        CallFilter.MISSED -> context.getString(R.string.history_filter_missed)
        CallFilter.INCOMING -> context.getString(R.string.history_filter_incoming)
        CallFilter.OUTGOING -> context.getString(R.string.history_filter_outgoing)
        CallFilter.REJECTED -> context.getString(R.string.history_filter_rejected)
        CallFilter.BLOCKED -> context.getString(R.string.history_filter_blocked)
    }
}

@Composable
private fun CallType.getDisplayName(): String {
    val context = LocalContext.current
    return when (this) {
        CallType.INCOMING -> context.getString(R.string.history_call_type_incoming)
        CallType.OUTGOING -> context.getString(R.string.history_call_type_outgoing)
        CallType.MISSED -> context.getString(R.string.history_call_type_missed)
        CallType.REJECTED -> context.getString(R.string.history_call_type_rejected)
        CallType.BLOCKED -> context.getString(R.string.history_call_type_blocked)
        CallType.VOICEMAIL -> context.getString(R.string.history_type_voicemail)
    }
}

private fun formatCompactDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    
    return when {
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

private fun formatCallDuration(seconds: Long): String {
    if (seconds == 0L) return ""
    
    val minutes = seconds / 60
    val secs = seconds % 60
    
    return when {
        minutes == 0L -> "${secs}s"
        secs == 0L -> "${minutes}m"
        else -> "${minutes}:${secs.toString().padStart(2, '0')}"
    }
}

private fun formatHistoryClockTime(timestamp: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(timestamp)
        val localTime = LocalTime.ofInstant(instant, ZoneId.systemDefault())
        localTime.format(DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.getDefault()))
    } catch (_: Exception) {
        "--:--"
    }
}

/**
 * Format timestamp with relative date (Today, Yesterday) or full date
 * Used in GroupedCallItem to show contextual date information
 */
private fun formatRelativeDateTime(timestamp: Long, context: android.content.Context): String {
    return try {
        val locale = java.util.Locale.ENGLISH
        val instant = Instant.ofEpochMilli(timestamp)
        val callDateTime = instant.atZone(ZoneId.systemDefault())
        val callDate = callDateTime.toLocalDate()
        val callTime = callDateTime.toLocalTime()
        val today = LocalDate.now()
        
        val dateText = when {
            callDate == today -> context.getString(R.string.history_today)
            callDate == today.minusDays(1) -> context.getString(R.string.history_yesterday)
            callDate.isAfter(today.minusDays(7)) -> {
                // Within last week - show day name
                callDate.format(DateTimeFormatter.ofPattern("EEEE", locale))
            }
            callDate.year == today.year -> {
                // Same year - show month and day
                callDate.format(DateTimeFormatter.ofPattern("MMM d", locale))
            }
            else -> {
                // Different year - show full date
                callDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
            }
        }
        
        val timeText = callTime.format(DateTimeFormatter.ofPattern("hh:mm a", locale))
        "$dateText, $timeText"
    } catch (e: Exception) {
        "--:--"
    }
}

/**
 * Bottom sheet for unknown number options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnknownNumberBottomSheet(
    callLog: CallLog,
    notes: List<com.rasmi.purevon.domain.model.ContactNote>, // ✅ Add notes parameter
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onAddContact: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val haptic = LocalHapticFeedback.current
    
    // Perform haptic feedback when sheet opens
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header with phone number
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    var copied by remember { mutableStateOf(false) }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("phone", callLog.phoneNumber)
                            clipboard.setPrimaryClip(clip)
                            com.rasmi.purevon.util.SoundManager(context).playCopySound()
                            copied = true
                        }
                    ) {
                        Text(
                            text = PhoneUtil.formatPhoneNumber(callLog.phoneNumber),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (copied) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    
                    // Reset "copied" indicator after 2 seconds
                    LaunchedEffect(copied) {
                        if (copied) {
                            kotlinx.coroutines.delay(2000)
                            copied = false
                        }
                    }
                }
                
                Text(
                    text = stringResource(R.string.history_unknown_number),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            
            // Quick actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionItem(
                    icon = Icons.Default.Call,
                    label = stringResource(R.string.history_action_call),
                    color = successColor(),
                    onClick = onCall
                )
                QuickActionItem(
                    icon = Icons.AutoMirrored.Filled.Message,
                    label = stringResource(R.string.history_action_message),
                    color = infoColor(),
                    onClick = onMessage
                )
                QuickActionItem(
                    icon = Icons.Default.PersonAdd,
                    label = stringResource(R.string.history_add_action),
                    color = infoColor(),
                    onClick = onAddContact
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            
            // ✅ Notes Section (if available)
            if (notes.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description, // ✅ Use Description icon for notes
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.history_notes, notes.size),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    notes.take(3).forEach { note -> // Show max 3 notes
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = note.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (note.isIncoming) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                                        contentDescription = null,
                                        tint = if (note.isIncoming) iOSGreen else iOSBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    if (note.callDuration > 0) {
                                        Text(
                                            text = formatDuration(note.callDuration),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = " • ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    Text(
                                        text = formatNoteDate(note.createdAt, context),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    if (notes.size > 3) {
                        Text(
                            text = stringResource(R.string.history_more_notes, notes.size - 3),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
            
            // Additional options
            BottomSheetOption(
                icon = Icons.Default.Block,
                label = stringResource(R.string.history_block_number),
                color = warningColor(),
                onClick = onBlock
            )
            
            BottomSheetOption(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.history_delete_from_history),
                color = errorColor(),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun BottomSheetOption(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

/**
 * Note: ViewModeTabRow, ViewModeTab, and GroupedCallItem were removed
 * as we now only use DETAILED view mode (All Calls)
 */

/**
 * ✅ Format note date (similar to ContactDetailScreen)
 */
private fun formatNoteDate(timestamp: Long, context: android.content.Context): String {
    return try {
        val noteTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        val now = Instant.now().atZone(ZoneId.systemDefault())
        
        val daysDiff = ChronoUnit.DAYS.between(noteTime.toLocalDate(), now.toLocalDate())
        
        when {
            daysDiff == 0L -> context.getString(R.string.history_note_today)
            daysDiff == 1L -> context.getString(R.string.history_note_yesterday)
            daysDiff < 7 -> noteTime.format(DateTimeFormatter.ofPattern("EEEE"))
            daysDiff < 365 -> noteTime.format(DateTimeFormatter.ofPattern("MMM d"))
            else -> noteTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }
    } catch (e: Exception) {
        ""
    }
}

/**
 * ✅ Format call duration (similar to ContactDetailScreen)
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return when {
        hours > 0 -> String.format("%dh %02dm", hours, minutes)
        minutes > 0 -> String.format("%dm %02ds", minutes, secs)
        else -> String.format("%ds", secs)
    }
}
