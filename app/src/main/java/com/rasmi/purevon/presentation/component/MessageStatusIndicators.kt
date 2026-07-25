package com.rasmi.purevon.presentation.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.domain.model.MessageStatus
import com.rasmi.purevon.presentation.theme.*
import kotlinx.coroutines.delay
import java.util.*

/**
 * ⚠️ UNUSED FILE: These composables are NOT imported or used anywhere in the Purevon app.
 * Status indicators are rendered inline in MessageBubble.kt instead.
 * Consider removing this file or integrating these components to replace inline status rendering.
 * 
 * Read Receipt Component
 * Shows "Read at 10:30 PM" under message
 */
@Composable
fun ReadReceipt(
    isRead: Boolean,
    readTime: Long?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isRead && readTime != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        if (readTime != null) {
            Text(
                text = "Read ${formatTime(readTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Delivered Status with Animation
 */
@Composable
fun DeliveredStatus(
    status: MessageStatus,
    deliveredTime: Long?,
    modifier: Modifier = Modifier
) {
    var showAnimation by remember { mutableStateOf(false) }
    
    LaunchedEffect(status) {
        if (status == MessageStatus.DELIVERED) {
            showAnimation = true
            delay(2000)
            showAnimation = false
        }
    }
    
    AnimatedVisibility(
        visible = status == MessageStatus.DELIVERED && deliveredTime != null,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DoneAll,
                contentDescription = "Delivered",
                modifier = Modifier.size(12.dp),
                tint = iOSBlue
            )
            
            if (deliveredTime != null) {
                Text(
                    text = "Delivered ${formatTime(deliveredTime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = iOSBlue,
                    fontSize = 10.sp
                )
            }
        }
    }
    
    // Pop animation
    if (showAnimation) {
        DeliveryAnimation()
    }
}

/**
 * Delivery success animation
 */
@Composable
private fun DeliveryAnimation() {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
        delay(1500)
        visible = false
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iOSBlue.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DoneAll,
                contentDescription = "Delivered",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Message sending progress indicator
 */
@Composable
fun SendingProgressIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = Color.White.copy(alpha = 0.7f)
        )
        
        Text(
            text = "Sending...",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

/**
 * Message failed indicator
 */
@Composable
fun FailedIndicator(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = iOSRedDark.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        onClick = onRetry
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = "Failed",
                tint = iOSRedDark,
                modifier = Modifier.size(14.dp)
            )
            
            Text(
                text = "Failed • Tap to retry",
                style = MaterialTheme.typography.labelSmall,
                color = iOSRedDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Format time (e.g., "10:30 PM")
 */
private val timeFormatter: java.time.format.DateTimeFormatter by lazy {
    java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
}

private fun formatTime(timestamp: Long): String {
    val instant = java.time.Instant.ofEpochMilli(timestamp)
    val localTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    return localTime.format(timeFormatter)
}
