package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.domain.model.Message

/**
 * Message Actions Bottom Sheet
 * Shows actions for long-pressed message
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: Message,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onReact: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            // Header
            Text(
                text = "Message Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            
            // Actions
            val actions = buildList {
                add(
                    MessageAction(
                        icon = Icons.Default.AddReaction,
                        title = "React",
                        subtitle = "Add emoji reaction",
                        onClick = {
                            onReact()
                            onDismiss()
                        }
                    )
                )
                
                add(
                    MessageAction(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        title = "Reply",
                        subtitle = "Reply to this message",
                        onClick = {
                            onReply()
                            onDismiss()
                        }
                    )
                )
                
                add(
                    MessageAction(
                        icon = Icons.AutoMirrored.Filled.Forward,
                        title = "Forward",
                        subtitle = "Forward to another contact",
                        onClick = {
                            onForward()
                            onDismiss()
                        }
                    )
                )
                
                if (!message.body.isNullOrBlank()) {
                    add(
                        MessageAction(
                            icon = Icons.Default.ContentCopy,
                            title = "Copy",
                            subtitle = "Copy message text",
                            onClick = {
                                onCopy()
                                onDismiss()
                            }
                        )
                    )
                }
                
                add(
                    MessageAction(
                        icon = if (message.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        title = if (message.isStarred) "Unfavorite" else "Favorite",
                        subtitle = if (message.isStarred) "Remove from favorites" else "Add to favorites",
                        onClick = {
                            onFavorite()
                            onDismiss()
                        }
                    )
                )
                
                add(
                    MessageAction(
                        icon = Icons.Default.Delete,
                        title = "Delete",
                        subtitle = "Delete this message",
                        destructive = true,
                        onClick = {
                            onDelete()
                            onDismiss()
                        }
                    )
                )
            }
            
            LazyColumn {
                items(actions) { action ->
                    MessageActionItem(action = action)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Message action data class
 */
private data class MessageAction(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Single action item
 */
@Composable
private fun MessageActionItem(
    action: MessageAction,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = action.onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (action.destructive) {
                        Color.Red.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                action.icon,
                contentDescription = action.title,
                tint = if (action.destructive) {
                    Color.Red
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.size(22.dp)
            )
        }
        
        // Text
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (action.destructive) {
                    Color.Red
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontSize = 16.sp
            )
            
            Text(
                text = action.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
        
        // Arrow
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}
