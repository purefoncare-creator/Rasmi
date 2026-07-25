package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.domain.usecase.conversation.MuteDuration
import com.rasmi.purevon.presentation.theme.Spacing

/**
 * Bottom sheet for conversation actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationActionsSheet(
    isPinned: Boolean,
    isMuted: Boolean,
    isArchived: Boolean,
    onPin: () -> Unit,
    onMute: (MuteDuration) -> Unit,
    onUnmute: () -> Unit,
    onArchive: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var showMuteOptions by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.medium)
        ) {
            Text(
                text = "Conversation Actions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))
            
            if (!showMuteOptions) {
                // Main actions
                ActionItem(
                    icon = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    text = if (isPinned) "Unpin" else "Pin conversation",
                    onClick = {
                        onPin()
                        onDismiss()
                    }
                )
                
                if (isMuted) {
                    ActionItem(
                        icon = Icons.Default.Notifications,
                        text = "Unmute",
                        onClick = {
                            onUnmute()
                            onDismiss()
                        }
                    )
                } else {
                    ActionItem(
                        icon = Icons.Default.NotificationsOff,
                        text = "Mute notifications",
                        onClick = { showMuteOptions = true }
                    )
                }
                
                ActionItem(
                    icon = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    text = if (isArchived) "Unarchive" else "Archive",
                    onClick = {
                        onArchive()
                        onDismiss()
                    }
                )
                
                ActionItem(
                    icon = Icons.Default.Block,
                    text = "Block contact",
                    onClick = {
                        onBlock()
                        onDismiss()
                    }
                )
                
                ActionItem(
                    icon = Icons.Default.Delete,
                    text = "Delete conversation",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = {
                        onDelete()
                        onDismiss()
                    }
                )
            } else {
                // Mute duration options
                Text(
                    text = "Mute for",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small)
                )
                
                ActionItem(
                    icon = Icons.Default.AccessTime,
                    text = "1 hour",
                    onClick = {
                        onMute(MuteDuration.ONE_HOUR)
                        onDismiss()
                    }
                )
                
                ActionItem(
                    icon = Icons.Default.AccessTime,
                    text = "8 hours",
                    onClick = {
                        onMute(MuteDuration.EIGHT_HOURS)
                        onDismiss()
                    }
                )
                
                ActionItem(
                    icon = Icons.Default.DateRange,
                    text = "1 week",
                    onClick = {
                        onMute(MuteDuration.ONE_WEEK)
                        onDismiss()
                    }
                )
                
                ActionItem(
                    icon = Icons.Default.NotificationsOff,
                    text = "Until I turn it back on",
                    onClick = {
                        onMute(MuteDuration.FOREVER)
                        onDismiss()
                    }
                )
                
                Spacer(modifier = Modifier.height(Spacing.small))
                
                TextButton(
                    onClick = { showMuteOptions = false },
                    modifier = Modifier.padding(horizontal = Spacing.medium)
                ) {
                    Text("Back")
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.medium))
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium, vertical = Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = iconTint
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
