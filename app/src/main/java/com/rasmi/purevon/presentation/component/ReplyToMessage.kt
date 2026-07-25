package com.rasmi.purevon.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.data.local.entity.MessageType
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.iOSBlue
import androidx.compose.ui.res.stringResource

/**
 * Reply Preview Component
 * Shows the message being replied to (above input bar)
 */
@Composable
fun ReplyPreview(
    replyToMessage: Message,
    onCancelReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reply icon
            Icon(
                Icons.Default.Reply,
                contentDescription = null,
                tint = iOSBlue,
                modifier = Modifier.size(20.dp)
            )
            
            // Divider line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(iOSBlue)
            )
            
            // Message preview
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    // ✅ FIX #8/#27: Use MessageType enum and string resource
                    text = if (replyToMessage.type == MessageType.SENT.value) stringResource(R.string.msg_you) else (replyToMessage.contactName ?: replyToMessage.phoneNumber),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = iOSBlue,
                    fontSize = 13.sp
                )
                
                Text(
                    text = replyToMessage.body ?: "Attachment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
            
            // Cancel button
            IconButton(
                onClick = onCancelReply,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Reply indicator shown in message bubble
 * Shows which message this is replying to
 */
@Composable
fun ReplyIndicator(
    replyToMessage: Message,
    onReplyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onReplyClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vertical line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(iOSBlue)
            )
            
            // Reply content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    // ✅ FIX #8/#27: Use MessageType enum and string resource
                    text = if (replyToMessage.type == MessageType.SENT.value) stringResource(R.string.msg_you) else (replyToMessage.contactName ?: replyToMessage.phoneNumber),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = iOSBlue,
                    fontSize = 11.sp
                )
                
                Text(
                    text = replyToMessage.body ?: "Attachment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Animated Reply Preview with slide animation
 */
@Composable
fun AnimatedReplyPreview(
    replyToMessage: Message?,
    onCancelReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = replyToMessage != null,
        enter = slideInVertically(
            initialOffsetY = { it }
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it }
        ) + fadeOut(),
        modifier = modifier
    ) {
        if (replyToMessage != null) {
            ReplyPreview(
                replyToMessage = replyToMessage,
                onCancelReply = onCancelReply
            )
        }
    }
}
