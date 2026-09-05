package com.rasmi.purevon.presentation.screen.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.presentation.component.ContactAvatar
import com.rasmi.purevon.presentation.theme.PurevonCallMissed
import com.rasmi.purevon.util.PhoneUtil

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactGroupedCallRow(
    group: GroupedContactCalls,
    resolvedContactName: String?,
    onCallBack: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    hasNote: Boolean = false,
    useShortTimeSubtitle: Boolean = false,
    showBottomDivider: Boolean = true,
    onLongClick: () -> Unit = {}
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
                com.rasmi.purevon.presentation.component.FavoriteContactAvatar(
                    size = 48.dp,
                    photoUri = group.contactPhotoUri,
                    isFavorite = group.isFavorite,
                    showBadge = false,
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
                            PurevonCallMissed
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                            text = pluralStringResource(R.plurals.history_calls, group.callCount, group.callCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (log.callType == CallType.MISSED) PurevonCallMissed
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
                            text = formatCallDurationHistory(log.duration),
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
