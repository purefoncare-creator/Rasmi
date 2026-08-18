package com.rasmi.purevon.presentation.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.data.local.entity.ReactionEmojis

/**
 * Reaction Picker UI
 * Inspired by iMessage/Telegram reaction picker
 */
@Composable
fun ReactionPicker(
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currentReaction: String? = null
) {
    var selectedEmoji by remember { mutableStateOf(currentReaction) }
    
    Surface(
        modifier = modifier
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionEmojis.QUICK_REACTIONS.forEach { emoji ->
                ReactionButton(
                    emoji = emoji,
                    isSelected = emoji == selectedEmoji,
                    onClick = {
                        selectedEmoji = if (selectedEmoji == emoji) null else emoji
                        onReactionSelected(emoji)
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * Single Reaction Button
 */
@Composable
fun ReactionButton(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reactionScale"
    )
    
    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 24.sp
        )
    }
}

/**
 * Reaction Summary Display (below message)
 */
@Composable
fun ReactionSummaryBubble(
    reactions: Map<String, Int>,
    myReaction: String?,
    onReactionClick: (String) -> Unit,
    onAddReactionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return
    
    Row(
        modifier = modifier
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { (emoji, count) ->
            ReactionChip(
                emoji = emoji,
                count = count,
                isMyReaction = emoji == myReaction,
                onClick = { onReactionClick(emoji) }
            )
        }
        
        // Add reaction button
        Surface(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onAddReactionClick),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "➕",
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Single Reaction Chip
 */
@Composable
fun ReactionChip(
    emoji: String,
    count: Int,
    isMyReaction: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (isMyReaction) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(14.dp),
        border = if (isMyReaction) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        } else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                fontSize = 14.sp
            )
            
            if (count > 1) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isMyReaction) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Animated Reaction Popup
 */
@Composable
fun AnimatedReactionPopup(
    visible: Boolean,
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    currentReaction: String? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + 
                scaleIn(initialScale = 0.8f, animationSpec = spring()),
        exit = fadeOut(animationSpec = tween(150)) + 
               scaleOut(targetScale = 0.8f)
    ) {
        ReactionPicker(
            onReactionSelected = onReactionSelected,
            onDismiss = onDismiss,
            currentReaction = currentReaction
        )
    }
}
