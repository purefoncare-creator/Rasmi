package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.util.DateTimeUtils

/**
 * Wire-style Conversation List Item — flat, 32dp avatar, clean typography
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    showBottomDivider: Boolean = true
) {
    val context = LocalContext.current
    val isUnread = conversation.unreadCount > 0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MessagingDimensions.conversationItemHeight)
                .padding(horizontal = MessagingDimensions.spacing8x)
                .then(
                    if (isSelected) {
                        Modifier
                            .clip(RoundedCornerShape(MessagingDimensions.corner8x))
                            .background(PurevonPrimary.copy(alpha = 0.12f))
                            .border(
                                width = MessagingDimensions.spacing1x,
                                color = PurevonPrimary,
                                shape = RoundedCornerShape(MessagingDimensions.corner8x)
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
                .padding(horizontal = MessagingDimensions.spacing4x, vertical = MessagingDimensions.spacing4x),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                com.rasmi.purevon.presentation.component.FavoriteContactAvatar(
                    size = MessagingDimensions.avatarConversationList,
                    photoUri = conversation.contactPhotoUri,
                    isFavorite = conversation.isFavorite
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.size(24.dp),
                        colors = CheckboxDefaults.colors(checkedColor = PurevonPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.width(MessagingDimensions.spacing12x))

            Column(modifier = Modifier.weight(1f)) {
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
                                style = MessagingTypography.body02,
                                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = PurevonTextPrimary,
                                textAlign = if (parentLayoutDirection == LayoutDirection.Rtl) TextAlign.End else TextAlign.Start
                            )
                        }
                    } else {
                        Text(
                            text = displayText,
                            style = MessagingTypography.body02,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            color = PurevonTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(MessagingDimensions.spacing8x))

                    Text(
                        text = DateTimeUtils.formatMessageTime(conversation.lastMessageTime, context),
                        style = MessagingTypography.subline01,
                        color = if (isUnread) PurevonPrimary else PurevonTextTertiary,
                        fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(MessagingDimensions.spacing2x))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lastMsg = conversation.lastMessage
                    val attachmentIcon = when {
                        lastMsg.contains("[MMS]", ignoreCase = true) ||
                            lastMsg.contains("[Image]", ignoreCase = true) ||
                            lastMsg.contains("\uD83D\uDDBC") -> Icons.Default.Image
                        lastMsg.contains("[Audio]", ignoreCase = true) ||
                            lastMsg.contains("\uD83C\uDFB5") -> Icons.Default.Mic
                        lastMsg.contains("[Attachment]", ignoreCase = true) ||
                            lastMsg.contains("[Video]", ignoreCase = true) ||
                            lastMsg.contains("\uD83D\uDCCE") -> Icons.Default.AttachFile
                        else -> null
                    }

                    if (attachmentIcon != null) {
                        Icon(
                            imageVector = attachmentIcon,
                            contentDescription = stringResource(R.string.msg_cd_attachment_type),
                            modifier = Modifier.size(14.dp),
                            tint = PurevonTextTertiary
                        )
                        Spacer(modifier = Modifier.width(MessagingDimensions.spacing4x))
                    }

                    Text(
                        text = conversation.lastMessage,
                        style = MessagingTypography.subline01,
                        color = if (isUnread) PurevonTextPrimary.copy(alpha = 0.85f) else PurevonTextTertiary,
                        fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isUnread && conversation.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(MessagingDimensions.spacing8x))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PurevonTextPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                                style = MessagingTypography.badge01,
                                color = PurevonBackground,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showBottomDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = MessagingDimensions.conversationItemDividerStartPadding),
                thickness = 0.5.dp,
                color = PurevonBorder
            )
        }
    }
}
