package com.rasmi.purevon.presentation.screen.contacts

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.presentation.theme.iOSBlue
import com.rasmi.purevon.presentation.theme.iOSGreen
import com.rasmi.purevon.presentation.theme.iOSYellow
import com.rasmi.purevon.presentation.theme.iOSRed
import com.rasmi.purevon.presentation.theme.iOSOrange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check

/**
 * Modern Professional Contacts Screen
 * Clean, compact design with iOS-inspired aesthetics
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel = hiltViewModel(),
    onContactClick: ((Contact) -> Unit)? = null,
    onAddContactClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    // ✅ استخدام displayedContacts مباشرة - ViewModel يرتبها حسب عدد المكالمات
    val displayedContacts = uiState.displayedContacts
    
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    
    // Handle Snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.message,
                actionLabel = message.actionLabel,
                duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            
            if (result == SnackbarResult.ActionPerformed) {
                when (message.action) {
                    SnackbarAction.UNDO_DELETE -> viewModel.onEvent(ContactsUiEvent.UndoDelete)
                    null -> {}
                }
            }
            
            viewModel.onEvent(ContactsUiEvent.DismissSnackbar)
        }
    }
    
    // Error handling
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(ContactsUiEvent.DismissError)
        }
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
                SelectionModeTopBar(
                    selectedCount = uiState.selectedContactIds.size,
                    totalCount = uiState.displayedContacts.size,
                    onClose = { viewModel.onEvent(ContactsUiEvent.ExitSelectionMode) },
                    onSelectAll = { viewModel.onEvent(ContactsUiEvent.SelectAll) },
                    onDeselectAll = { viewModel.onEvent(ContactsUiEvent.DeselectAll) }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode && uiState.selectedContactIds.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                SelectionModeBottomBar(
                    selectedCount = uiState.selectedContactIds.size,
                    onDelete = { viewModel.onEvent(ContactsUiEvent.DeleteSelectedContacts) },
                    onBlock = { viewModel.onEvent(ContactsUiEvent.BlockSelectedContacts) },
                    onAddToFavorites = { viewModel.onEvent(ContactsUiEvent.AddSelectedToFavorites) }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !uiState.isSelectionMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                SmallFloatingActionButton(
                    onClick = { onAddContactClick?.invoke() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.contacts_cd_add_contact),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
                // Search bar + three-dot menu (same style as Messages screen)
                ContactsSearchBarWithMenu(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onEvent(ContactsUiEvent.SearchQueryChanged(it)) },
                    selectedFilter = uiState.selectedFilter,
                    onFilterSelected = { viewModel.onEvent(ContactsUiEvent.FilterSelected(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                
                // Main Content
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading -> {
                            // Show skeleton loading in grid
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(12) {
                                    GridContactItemSkeleton()
                                }
                            }
                        }
                        
                        uiState.displayedContacts.isEmpty() -> {
                            ModernEmptyState(
                                filter = uiState.selectedFilter,
                                searchQuery = uiState.searchQuery,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 88.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                // ✅ عرض مباشر بدون تقسيم لمجموعات أو headers
                                gridItemsIndexed(
                                    items = displayedContacts,
                                    key = { _, contact -> "contact_${contact.id}" }
                                ) { _, contact ->
                                    GridContactCard(
                                        contact = contact,
                                        isSelectionMode = uiState.isSelectionMode,
                                        isSelected = contact.id in uiState.selectedContactIds,
                                        onClick = {
                                            if (uiState.isSelectionMode) {
                                                viewModel.onEvent(ContactsUiEvent.ToggleContactSelection(contact.id))
                                            } else {
                                                onContactClick?.invoke(contact)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.onEvent(ContactsUiEvent.ContactLongPressed(contact.id))
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
 * Search bar + three-dot menu — same style as MessagesSearchBarWithMenu
 */
@Composable
private fun ContactsSearchBarWithMenu(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: ContactFilter,
    onFilterSelected: (ContactFilter) -> Unit,
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
                    ContactFilter.entries.forEach { filter ->
                        val (icon, color) = getFilterIconAndColor(filter)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (filter) {
                                        ContactFilter.ALL       -> stringResource(R.string.contacts_filter_all)
                                        ContactFilter.FAVORITES -> stringResource(R.string.contacts_filter_favorites)
                                        ContactFilter.BLOCKED   -> stringResource(R.string.contacts_filter_blocked)
                                    }
                                )
                            },
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
                                text = stringResource(R.string.contacts_search_placeholder),
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
                    contentDescription = stringResource(R.string.contacts_cd_clear_search),
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
 * Get icon and color for each filter
 */
private fun getFilterIconAndColor(filter: ContactFilter): Pair<ImageVector, Color> {
    return when (filter) {
        ContactFilter.ALL -> Icons.Default.Contacts to iOSBlue
        ContactFilter.FAVORITES -> Icons.Default.Star to iOSYellow
        ContactFilter.BLOCKED -> Icons.Default.Block to iOSRed
    }
}




/**
 * Grid Contact Card - Twitter Spaces style: large circular avatar with name centered below
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridContactCard(
    contact: Contact,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        // Avatar with optional selection / favorite / blocked overlay
        Box(contentAlignment = Alignment.Center) {
            val avatarBorder = when {
                isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                contact.isFavorite -> BorderStroke(2.dp, iOSYellow)
                contact.isBlocked -> BorderStroke(2.dp, iOSRed)
                else -> null
            }

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .then(
                        if (avatarBorder != null) Modifier.border(avatarBorder, CircleShape)
                        else Modifier
                    )
                    .padding(if (avatarBorder != null) 2.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
                    size = if (avatarBorder != null) 48.dp else 52.dp,
                    photoUri = contact.photoUri
                )
            }

            // Selection check mark overlay
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.surface,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else if (contact.isBlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(iOSRed)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            } else if (contact.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(iOSYellow)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        // Name centered below avatar
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp
        )
    }
}


/**
 * Note: CompactFastScroll (alphabet sidebar) was removed as requested
 */

/**
 * Selection Mode Top Bar
 */
@Composable
private fun SelectionModeTopBar(
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
                modifier = Modifier
                    .size(36.dp)
                    .semantics {
                        contentDescription = context.getString(R.string.contacts_cd_exit_selection)
                    }
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = context.getString(R.string.contacts_selected, selectedCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            TextButton(
                onClick = if (selectedCount == totalCount) onDeselectAll else onSelectAll,
                modifier = Modifier.semantics {
                    contentDescription = if (selectedCount == totalCount) {
                        context.getString(R.string.contacts_cd_deselect_all)
                    } else {
                        context.getString(R.string.contacts_cd_select_all)
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (selectedCount == totalCount) stringResource(R.string.contacts_deselect_all) else stringResource(R.string.contacts_select_all),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Selection Mode Bottom Bar
 */
@Composable
private fun SelectionModeBottomBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    onAddToFavorites: () -> Unit,
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
            // Add to Favorites
            BottomBarAction(
                icon = Icons.Outlined.StarOutline,
                label = stringResource(R.string.contacts_action_favorite),
                color = iOSYellow,
                onClick = onAddToFavorites
            )
            
            // Block
            BottomBarAction(
                icon = Icons.Outlined.Block,
                label = stringResource(R.string.contacts_action_block),
                color = iOSOrange,
                onClick = onBlock
            )
            
            // Delete
            BottomBarAction(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.contacts_action_delete),
                color = iOSRed,
                onClick = { showDeleteDialog = true }
            )
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        val context = LocalContext.current
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
                Text(context.getString(R.string.contacts_delete_multiple, selectedCount))
            },
            text = {
                Text(stringResource(R.string.contacts_delete_warning))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.contacts_action_delete), color = MaterialTheme.colorScheme.error)
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
 * Bottom Bar Action Button
 */
@Composable
private fun BottomBarAction(
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label selected contacts"
            },
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
 * Grid Contact Card Skeleton - For Twitter Spaces style grid loading
 */
@Composable
private fun GridContactItemSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Skeleton Avatar
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                )
        )

        // Skeleton Name
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                )
        )
    }
}


/**
 * Modern Empty State with custom messages per filter
 */
@Composable
private fun ModernEmptyState(
    filter: ContactFilter,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val (icon, title, message) = when {
        searchQuery.isNotEmpty() -> Triple(
            Icons.Outlined.SearchOff,
            stringResource(R.string.contacts_no_results),
            stringResource(R.string.contacts_no_results_desc)
        )
        filter == ContactFilter.FAVORITES -> Triple(
            Icons.Outlined.StarOutline,
            stringResource(R.string.contacts_no_favorites),
            stringResource(R.string.contacts_no_favorites_desc)
        )
        filter == ContactFilter.BLOCKED -> Triple(
            Icons.Default.Block,
            stringResource(R.string.contacts_no_blocked),
            stringResource(R.string.contacts_no_blocked_desc)
        )
        else -> Triple(
            Icons.Outlined.ContactPhone,
            stringResource(R.string.contacts_no_contacts),
            stringResource(R.string.contacts_no_contacts_desc)
        )
    }
    
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .alpha(0.5f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
