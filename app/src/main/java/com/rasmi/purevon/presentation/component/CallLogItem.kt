package com.rasmi.purevon.presentation.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.util.PhoneUtil
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * iOS-style Call Log Item with swipe actions
 */
@Composable
fun CallLogItem(
    callLog: CallLog,
    onCallBack: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !androidx.compose.foundation.isSystemInDarkTheme()
    var offsetX by remember { mutableFloatStateOf(0f) }
    val maxSwipeLeft = -200f
    val maxSwipeRight = 100f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Background actions (revealed on swipe)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left action - Call back
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(iOSGreen)
                    .clickable { onCallBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call back",
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Right actions - Delete & Block
            Row {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .background(iOSRed)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White
                    )
                }
                
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .background(iOSOrange)
                        .clickable { onBlock() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Block",
                        tint = Color.White
                    )
                }
            }
        }

        // Main content (swipeable)
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = if (isLightTheme) Color.White else MaterialTheme.colorScheme.background,
            border = if (isLightTheme) androidx.compose.foundation.BorderStroke(1.dp, LightBorder) else null,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            onClick = onClick
        ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Snap to position
                            offsetX = when {
                                offsetX < maxSwipeLeft / 2 -> maxSwipeLeft
                                offsetX > maxSwipeRight / 2 -> maxSwipeRight
                                else -> 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(maxSwipeLeft, maxSwipeRight)
                        }
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contact avatar with call type badge
            ContactAvatarWithBadge(
                name = callLog.contactName,
                photoUri = callLog.contactPhotoUri,
                callType = callLog.callType,
                isBlocked = callLog.isBlocked
            )

            Spacer(modifier = Modifier.width(12.dp))
            
            // Contact info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = callLog.contactName ?: PhoneUtil.formatPhoneNumber(callLog.phoneNumber),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (callLog.contactName != null) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = PhoneUtil.formatPhoneNumber(callLog.phoneNumber),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    Text(
                        text = formatCallDuration(callLog.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Time
            Text(
                text = formatCallTime(callLog.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        }
    }
}

@Composable
private fun ContactAvatarWithBadge(
    name: String?,
    photoUri: String?,
    callType: CallType,
    isBlocked: Boolean
) {
    val (badgeIcon, badgeColor) = when {
        isBlocked -> Icons.Default.Block to errorColor()
        callType == CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to missedCallColor()
        callType == CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to incomingCallColor()
        callType == CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to outgoingCallColor()
        callType == CallType.REJECTED -> Icons.Default.CallEnd to errorColor()
        else -> Icons.Default.Call to MaterialTheme.colorScheme.onSurface
    }

    // Deterministic avatar background color based on name
    val avatarColor = remember(name) {
        val colors = listOf(
            android.graphics.Color.parseColor("#E57373"),
            android.graphics.Color.parseColor("#64B5F6"),
            android.graphics.Color.parseColor("#81C784"),
            android.graphics.Color.parseColor("#FFB74D"),
            android.graphics.Color.parseColor("#BA68C8"),
            android.graphics.Color.parseColor("#4DB6AC"),
            android.graphics.Color.parseColor("#F06292")
        )
        val index = (name?.firstOrNull()?.code ?: 0) % colors.size
        Color(colors[index])
    }

    Box(modifier = Modifier.size(48.dp)) {
        // Avatar circle
        UnifiedContactAvatar(
            size = 48.dp,
            photoUri = photoUri
        )

        // Call type badge (bottom-right)
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(badgeColor)
                .align(Alignment.BottomEnd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = callType.name,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

private fun formatCallDuration(seconds: Long): String {
    if (seconds == 0L) return "Not answered"
    
    val minutes = seconds / 60
    val secs = seconds % 60
    
    return when {
        minutes == 0L -> "${secs}s"
        secs == 0L -> "${minutes}m"
        else -> "${minutes}m ${secs}s"
    }
}

private fun formatCallTime(timestamp: Long): String {
    // Format time to HH:mm
    return try {
        val instant = Instant.ofEpochMilli(timestamp)
        val localTime = LocalTime.ofInstant(instant, ZoneId.systemDefault())
        localTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        "--:--"
    }
}
