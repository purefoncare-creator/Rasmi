package com.rasmi.purevon.presentation.screen.scheduled

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasmi.purevon.R
import com.rasmi.purevon.data.local.entity.ScheduleStatus
import com.rasmi.purevon.presentation.component.EditScheduledMessageDialog
import com.rasmi.purevon.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen showing all scheduled messages with status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScheduledMessagesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduled_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            uiState.scheduledMessages.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No scheduled messages",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    items(uiState.scheduledMessages) { message ->
                        ScheduledMessageItem(
                            recipient = message.recipient,
                            messageBody = message.messageBody,
                            scheduledTime = message.scheduledTime,
                            status = message.status,
                            onCancel = {
                                viewModel.onEvent(ScheduledMessagesUiEvent.CancelMessage(message.id))
                            },
                            onEdit = {
                                viewModel.onEvent(ScheduledMessagesUiEvent.EditMessage(message.id))
                            }
                        )
                    }
                }

                // ✅ Fix #14: Edit dialog
                uiState.editingMessage?.let { editData ->
                    EditScheduledMessageDialog(
                        data = editData,
                        onDismiss = { viewModel.onEvent(ScheduledMessagesUiEvent.DismissEdit) },
                        onConfirm = { newBody, newTime, newRepeat ->
                            viewModel.onEvent(
                                ScheduledMessagesUiEvent.ConfirmEdit(
                                    scheduleId = editData.scheduleId,
                                    newBody = newBody,
                                    newTime = newTime,
                                    newRepeat = newRepeat
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledMessageItem(
    recipient: String,
    messageBody: String,
    scheduledTime: Long,
    status: ScheduleStatus,
    onCancel: () -> Unit,
    onEdit: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    val statusColor = when (status) {
        ScheduleStatus.PENDING -> MaterialTheme.colorScheme.primary
        ScheduleStatus.SENT -> iOSGreen
        ScheduleStatus.FAILED -> iOSRedDark
        ScheduleStatus.CANCELLED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    val statusIcon = when (status) {
        ScheduleStatus.PENDING -> Icons.Default.Schedule
        ScheduleStatus.SENT -> Icons.Default.CheckCircle
        ScheduleStatus.FAILED -> Icons.Default.Error
        ScheduleStatus.CANCELLED -> Icons.Default.Cancel
    }
    val statusText = when (status) {
        ScheduleStatus.PENDING -> stringResource(R.string.scheduled_status_pending)
        ScheduleStatus.SENT -> stringResource(R.string.scheduled_status_sent)
        ScheduleStatus.FAILED -> stringResource(R.string.scheduled_status_failed)
        ScheduleStatus.CANCELLED -> stringResource(R.string.scheduled_status_cancelled)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = recipient,
                        style = MaterialTheme.typography.titleMedium
                    )
                    // Status badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = statusColor
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = messageBody,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dateFormatter.format(Date(scheduledTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Only show edit/cancel buttons for PENDING messages
            if (status == ScheduleStatus.PENDING) {
                Column {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.scheduled_message_edit),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.scheduled_message_cancel),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
