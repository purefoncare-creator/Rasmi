package com.rasmi.purevon.presentation.screen.messages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.presentation.component.ConversationItem
import com.rasmi.purevon.presentation.screen.conversation.ConversationScreen
import com.rasmi.purevon.presentation.theme.Spacing
import com.rasmi.purevon.presentation.theme.PurevonError
import com.rasmi.purevon.presentation.theme.PurevonWarning
import com.rasmi.purevon.presentation.theme.PurevonBackground
import com.rasmi.purevon.presentation.theme.PurevonSurfaceAlt
import com.rasmi.purevon.presentation.theme.PurevonBorder
import com.rasmi.purevon.presentation.theme.PurevonTextPrimary
import com.rasmi.purevon.presentation.theme.PurevonTextSecondary
import com.rasmi.purevon.presentation.theme.PurevonTextTertiary
import com.rasmi.purevon.presentation.theme.PurevonPrimary
import com.rasmi.purevon.util.DateTimeUtils

/**
 * Messages Screen - SMS/MMS inbox with conversations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onConversationClick: (Long) -> Unit = {},
    onStarredMessageClick: (threadId: Long, messageId: Long) -> Unit = { threadId, _ -> onConversationClick(threadId) },
    onNewMessageClick: () -> Unit = {},
    onScheduledMessagesClick: () -> Unit = {}, // ✅ Fix #5: Navigate to scheduled messages
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // ✅ FINAL FIX: Use direct Flow like Realm's live queries
    // Data stays in memory and updates automatically via ContentObserver
    // No loading indicators on return - instant display!
    val conversations by remember(viewModel) {
        viewModel.conversationsFlow
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    
    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    // ✅ NEW: master/detail selection state for wide screens
    var selectedConversationId by remember { mutableStateOf<Long?>(null) }

    // ✅ NEW: responsive layout switch
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(MessagesUiEvent.DismissError)
        }
    }
    
    // Back button exits selection mode
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.onEvent(MessagesUiEvent.ExitSelectionMode)
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
                MessagesSelectionTopBar(
                    selectedCount = uiState.selectedConversationIds.size,
                    totalCount = conversations.size,
                    onClose = { viewModel.onEvent(MessagesUiEvent.ExitSelectionMode) },
                    onSelectAll = { viewModel.selectAllFromVisible(conversations.map { it.threadId }.toSet()) },
                    onDeselectAll = { viewModel.onEvent(MessagesUiEvent.DeselectAllConversations) }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode && uiState.selectedConversationIds.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                MessagesSelectionBottomBar(
                    selectedCount = uiState.selectedConversationIds.size,
                    onDelete = { viewModel.onEvent(MessagesUiEvent.DeleteSelectedConversations) },
                    onArchive = { viewModel.onEvent(MessagesUiEvent.ArchiveSelectedConversations) },
                    onMarkAsRead = { viewModel.onEvent(MessagesUiEvent.MarkSelectedAsRead) }
                )
            }
        },
        floatingActionButton = {
            // FAB lives at scaffold level only on compact screens.
            // On wide screens it sits inside the left master pane.
            if (!isWide) {
                AnimatedVisibility(
                    visible = !uiState.isSelectionMode,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = onNewMessageClick,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Message")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isWide) {
            MessagesWideLayout(
                uiState = uiState,
                conversations = conversations,
                haptic = haptic,
                selectedConversationId = selectedConversationId,
                onSelectConversation = { selectedConversationId = it },
                viewModel = viewModel,
                onQueryChange = { viewModel.onEvent(MessagesUiEvent.SearchQueryChanged(it)) },
                onTabSelected = { viewModel.onEvent(MessagesUiEvent.TabChanged(it)) },
                onScheduledMessagesClick = onScheduledMessagesClick,
                onStarredMessageClick = onStarredMessageClick,
                onNewMessageClick = onNewMessageClick,
                paddingValues = paddingValues
            )
        } else {
            MessagesCompactLayout(
                uiState = uiState,
                conversations = conversations,
                haptic = haptic,
                onConversationClick = onConversationClick,
                viewModel = viewModel,
                onQueryChange = { viewModel.onEvent(MessagesUiEvent.SearchQueryChanged(it)) },
                onTabSelected = { viewModel.onEvent(MessagesUiEvent.TabChanged(it)) },
                onScheduledMessagesClick = onScheduledMessagesClick,
                onStarredMessageClick = onStarredMessageClick,
                paddingValues = paddingValues
            )
        }
    }
}

/**
 * ✅ NEW: Two-column master/detail layout for wide screens (>= 600dp).
 * Left pane: conversation list (+ search + FAB). Right pane: inline thread.
 */
@Composable
private fun MessagesWideLayout(
    uiState: MessagesUiState,
    conversations: List<Conversation>,
    haptic: HapticFeedback,
    selectedConversationId: Long?,
    onSelectConversation: (Long) -> Unit,
    viewModel: MessagesViewModel,
    onQueryChange: (String) -> Unit,
    onTabSelected: (MessageTab) -> Unit,
    onScheduledMessagesClick: () -> Unit,
    onStarredMessageClick: (Long, Long) -> Unit,
    onNewMessageClick: () -> Unit,
    paddingValues: PaddingValues
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // ── Left pane: master list ──
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .background(PurevonBackground)
        ) {
            MessagesContent(
                uiState = uiState,
                conversations = conversations,
                haptic = haptic,
                viewModel = viewModel,
                onQueryChange = onQueryChange,
                onTabSelected = onTabSelected,
                onScheduledMessagesClick = onScheduledMessagesClick,
                onConversationItemClick = onSelectConversation,
                onStarredMessageClick = onStarredMessageClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = PurevonBorder
        )

        // ── Right pane: detail thread ──
        Column(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .background(PurevonSurfaceAlt)
        ) {
            if (selectedConversationId != null) {
                ConversationScreen(
                    conversationId = selectedConversationId,
                    onNavigateBack = {}
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = PurevonTextTertiary
                        )
                        Text(
                            text = "Select a conversation",
                            style = MaterialTheme.typography.titleMedium,
                            color = PurevonTextTertiary
                        )
                    }
                }
            }
        }
    }

    // FAB overlaid on the left master pane
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        AnimatedVisibility(
            visible = !uiState.isSelectionMode,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp)
        ) {
            FloatingActionButton(
                onClick = onNewMessageClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Message")
            }
        }
    }
}

/**
 * ✅ NEW: Single-column layout for compact screens (< 600dp).
 * List + navigate on item click via onConversationClick.
 */
@Composable
private fun MessagesCompactLayout(
    uiState: MessagesUiState,
    conversations: List<Conversation>,
    haptic: HapticFeedback,
    onConversationClick: (Long) -> Unit,
    viewModel: MessagesViewModel,
    onQueryChange: (String) -> Unit,
    onTabSelected: (MessageTab) -> Unit,
    onScheduledMessagesClick: () -> Unit,
    onStarredMessageClick: (Long, Long) -> Unit,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(PurevonBackground)
    ) {
        MessagesContent(
            uiState = uiState,
            conversations = conversations,
            haptic = haptic,
            viewModel = viewModel,
            onQueryChange = onQueryChange,
            onTabSelected = onTabSelected,
            onScheduledMessagesClick = onScheduledMessagesClick,
            onConversationItemClick = onConversationClick,
            onStarredMessageClick = onStarredMessageClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * ✅ NEW: Shared conversation list + search content used by both layouts.
 * `onConversationItemClick` differs per layout (select on wide, navigate on compact).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesContent(
    uiState: MessagesUiState,
    conversations: List<Conversation>,
    haptic: HapticFeedback,
    viewModel: MessagesViewModel,
    onQueryChange: (String) -> Unit,
    onTabSelected: (MessageTab) -> Unit,
    onScheduledMessagesClick: () -> Unit,
    onConversationItemClick: (Long) -> Unit,
    onStarredMessageClick: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
    ) {
        // Search Bar with Menu (at the top)
        MessagesSearchBarWithMenu(
            query = uiState.searchQuery,
            onQueryChange = onQueryChange,
            selectedTab = uiState.selectedTab,
            onTabSelected = onTabSelected,
            unreadCount = uiState.unreadCount,
            onScheduledMessagesClick = onScheduledMessagesClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // Content - auto-updates via ContentObserver like Realm
        // Pull-to-refresh for manual sync
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.onEvent(MessagesUiEvent.RefreshConversations) },
            modifier = Modifier
                .fillMaxSize()
                .background(PurevonBackground)
        ) {
            when (uiState.selectedTab) {
                MessageTab.ALL -> {
                    // ✅ FIX #13: Show loading spinner until first data emission
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (conversations.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.searchQuery.isBlank()) {
                                EmptyMessagesState()
                            } else {
                                EmptySearchState(query = uiState.searchQuery)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            itemsIndexed(
                                items = conversations,
                                key = { _, conversation -> "thread_${conversation.threadId}" }
                            ) { index, conversation ->
                                ConversationItem(
                                    conversation = conversation,
                                    isSelectionMode = uiState.isSelectionMode,
                                    isSelected = conversation.threadId in uiState.selectedConversationIds,
                                    showBottomDivider = index < conversations.lastIndex,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.onEvent(MessagesUiEvent.ToggleConversationSelection(conversation.threadId))
                                        } else {
                                            onConversationItemClick(conversation.threadId)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.onEvent(MessagesUiEvent.ConversationLongPressed(conversation.threadId))
                                    }
                                )
                            }
                        }
                    }
                }

                MessageTab.STARRED -> {
                    if (uiState.starredMessages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStarredState()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(
                                items = uiState.starredMessages,
                                key = { message ->
                                    // SMS and MMS can have same ID, use type prefix
                                    // NEVER allow empty key - use timestamp as fallback
                                    if (message.id != 0L) {
                                        "${if (message.isMms) "mms" else "sms"}_${message.id}"
                                    } else {
                                        "msg_${message.timestamp}_${message.body?.hashCode() ?: 0}"
                                    }
                                }
                            ) { message ->
                                StarredMessageItem(
                                    message = message,
                                    onClick = { onStarredMessageClick(message.threadId, message.id) }
                                )
                            }
                        }
                    }
                }

                MessageTab.ARCHIVED -> {
                    if (uiState.archivedConversations.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyArchivedState()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            itemsIndexed(
                                items = uiState.archivedConversations,
                                key = { _, conversation -> "archived_${conversation.threadId}" }
                            ) { index, conversation ->
                                ConversationItem(
                                    conversation = conversation,
                                    isSelectionMode = uiState.isSelectionMode,
                                    isSelected = conversation.threadId in uiState.selectedConversationIds,
                                    showBottomDivider = index < uiState.archivedConversations.lastIndex,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.onEvent(MessagesUiEvent.ToggleConversationSelection(conversation.threadId))
                                        } else {
                                            onConversationItemClick(conversation.threadId)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.onEvent(MessagesUiEvent.ConversationLongPressed(conversation.threadId))
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

/**
 * Top bar shown during multi-select mode
 */
@Composable
private fun MessagesSelectionTopBar(
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
                Icon(Icons.Default.Close, contentDescription = "Close selection", modifier = Modifier.size(20.dp))
            }
            Text(
                text = context.resources.getQuantityString(R.plurals.contacts_selected, selectedCount, selectedCount),
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
                    text = if (selectedCount == totalCount)
                        stringResource(R.string.contacts_deselect_all)
                    else
                        stringResource(R.string.contacts_select_all),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Bottom bar shown during multi-select mode with bulk actions
 */
@Composable
private fun MessagesSelectionBottomBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onMarkAsRead: () -> Unit,
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
            MessagesBottomBarAction(
                icon = Icons.Outlined.Archive,
                label = stringResource(R.string.messages_archive),
                color = MaterialTheme.colorScheme.primary,
                onClick = onArchive
            )
            MessagesBottomBarAction(
                icon = Icons.Outlined.MarkEmailRead,
                label = stringResource(R.string.messages_mark_as_read),
                color = MaterialTheme.colorScheme.primary,
                onClick = onMarkAsRead
            )
            MessagesBottomBarAction(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.messages_delete_conversation),
                color = PurevonError,
                onClick = { showDeleteDialog = true }
            )
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(pluralStringResource(R.plurals.msg_delete_conversations_title, selectedCount, selectedCount)) },
            text = { Text(stringResource(R.string.messages_delete_conversation)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text(stringResource(R.string.messages_delete_conversation), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun MessagesBottomBarAction(
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
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyMessagesState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.messages_no_messages),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.messages_start_conversation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun EmptySearchState(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.msg_no_results),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.msg_search_try_different, query),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyStarredState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.messages_no_starred),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = stringResource(R.string.messages_starred_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun StarredMessageItem(
    message: com.rasmi.purevon.domain.model.Message,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Starred",
                modifier = Modifier.size(18.dp),
                                    tint = PurevonWarning
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.contactName ?: message.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = message.body ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Text(
                text = DateTimeUtils.formatStarredMessageTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Search Bar with Menu - Same style as Contacts with dropdown menu
 */
@Composable
private fun MessagesSearchBarWithMenu(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedTab: MessageTab,
    onTabSelected: (MessageTab) -> Unit,
    unreadCount: Int,
    modifier: Modifier = Modifier,
    onScheduledMessagesClick: () -> Unit = {}
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
            // Three-dot menu icon (left for Arabic, right for English)
            Box {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { menuExpanded = true }
                )
                
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.messages_all))
                                if (unreadCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text(unreadCount.toString())
                                    }
                                }
                            }
                        },
                        onClick = {
                            onTabSelected(MessageTab.ALL)
                            menuExpanded = false
                        },
                        leadingIcon = {
                            if (selectedTab == MessageTab.ALL) {
                                Icon(
                                    Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                tint = PurevonWarning
                                )
                                Text(stringResource(R.string.messages_starred_tab))
                            }
                        },
                        onClick = {
                            onTabSelected(MessageTab.STARRED)
                            menuExpanded = false
                        },
                        leadingIcon = {
                            if (selectedTab == MessageTab.STARRED) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(stringResource(R.string.messages_archived_tab))
                            }
                        },
                        onClick = {
                            onTabSelected(MessageTab.ARCHIVED)
                            menuExpanded = false
                        },
                        leadingIcon = {
                            if (selectedTab == MessageTab.ARCHIVED) {
                                Icon(
                                    Icons.Default.Archive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                    // ✅ Fix #5: Scheduled Messages menu item
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(stringResource(R.string.scheduled_title))
                            }
                        },
                        onClick = {
                            onScheduledMessagesClick()
                            menuExpanded = false
                        }
                    )
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
                                text = stringResource(R.string.search_messages_hint),
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
            
            // Clear button
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.search_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

@Composable
private fun EmptyArchivedState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Archive,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Text(
            text = stringResource(R.string.msg_no_archived_conversations),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = stringResource(R.string.msg_archive_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
