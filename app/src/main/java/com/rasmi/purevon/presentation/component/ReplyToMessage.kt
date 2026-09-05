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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.data.local.entity.MessageType
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*
import androidx.compose.ui.res.stringResource

@Composable
fun ReplyPreview(
    replyToMessage: Message,
    onCancelReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PurevonComposerBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MessagingDimensions.spacing16x, vertical = MessagingDimensions.spacing10x),
            horizontalArrangement = Arrangement.spacedBy(MessagingDimensions.spacing10x),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = PurevonPrimary,
                modifier = Modifier.size(10.dp)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(PurevonPrimary)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (replyToMessage.type == MessageType.SENT.value) stringResource(R.string.msg_you) else (replyToMessage.contactName ?: replyToMessage.phoneNumber),
                    style = MessagingTypography.subline01,
                    color = PurevonPrimary
                )
                Text(
                    text = replyToMessage.body ?: "Attachment",
                    style = MessagingTypography.subline01,
                    color = PurevonTextSecondary,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onCancelReply, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    tint = PurevonTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

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
        color = PurevonSurfaceMuted,
        shape = RoundedCornerShape(MessagingDimensions.corner12x)
    ) {
        Row(
            modifier = Modifier.padding(MessagingDimensions.spacing8x),
            horizontalArrangement = Arrangement.spacedBy(MessagingDimensions.spacing8x),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(PurevonPrimary)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (replyToMessage.type == MessageType.SENT.value) stringResource(R.string.msg_you) else (replyToMessage.contactName ?: replyToMessage.phoneNumber),
                    style = MessagingTypography.subline01,
                    color = PurevonPrimary
                )
                Text(
                    text = replyToMessage.body ?: "Attachment",
                    style = MessagingTypography.subline01,
                    color = PurevonTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AnimatedReplyPreview(
    replyToMessage: Message?,
    onCancelReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = replyToMessage != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        if (replyToMessage != null) {
            ReplyPreview(replyToMessage = replyToMessage, onCancelReply = onCancelReply)
        }
    }
}
