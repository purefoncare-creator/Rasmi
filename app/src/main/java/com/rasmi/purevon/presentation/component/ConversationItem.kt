package com.rasmi.purevon.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.iOSBlue
import com.rasmi.purevon.util.DateTimeUtils

/**
 * Card-style Conversation List Item — matches the app's card design language.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    showBottomDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUnread = conversation.unreadCount > 0
    val rowBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
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
                    onLongClick = onLongClick,
                    onClickLabel = stringResource(R.string.msg_cd_open_conversation),
                    onLongClickLabel = stringResource(R.string.msg_cd_conversation_options)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Checkbox (selection mode) or Avatar ──────────────────────────
            Box(contentAlignment = Alignment.Center) {
                UnifiedContactAvatar(
                    size = 48.dp,
                    photoUri = conversation.contactPhotoUri
                )
                
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.size(24.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Content ──────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                // Name + timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayText = conversation.contactName ?: conversation.phoneNumber
                    val isPhoneNumber = conversation.contactName == null
                    val parentLayoutDirection = LocalLayoutDirection.current

                    if (isPhoneNumber) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                textAlign = if (parentLayoutDirection == LayoutDirection.Rtl) TextAlign.End else TextAlign.Start
                            )
                        }
                    } else {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = DateTimeUtils.formatMessageTime(conversation.lastMessageTime, context),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUnread)
                            iOSBlue
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Preview + unread badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment detection via message content markers
                    // Note: Uses text tags from lastMessage as primary detection, emoji only at
                    // message start to avoid false positives from user-typed emojis.
                    val lastMsg = conversation.lastMessage
                    val attachmentIcon = when {
                        lastMsg.contains("[MMS]", ignoreCase = true) || 
                            lastMsg.contains("[Image]", ignoreCase = true) ||
                            lastMsg.startsWith("🖼") -> Icons.Default.Image
                        lastMsg.contains("[Audio]", ignoreCase = true) ||
                            lastMsg.startsWith("🎵") -> Icons.Default.Mic
                        lastMsg.contains("[Attachment]", ignoreCase = true) ||
                            lastMsg.contains("[Video]", ignoreCase = true) ||
                            lastMsg.startsWith("📎") || lastMsg.startsWith("🎥") -> Icons.Default.AttachFile
                        else -> null
                    }

                    if (attachmentIcon != null) {
                        Icon(
                            imageVector = attachmentIcon,
                            contentDescription = stringResource(R.string.msg_cd_attachment_type),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = conversation.lastMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (isUnread) 0.85f else 0.5f
                        ),
                        fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp
                    )

                    if (isUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(iOSBlue)
                        )
                    }
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
