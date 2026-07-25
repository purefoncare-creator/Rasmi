package com.rasmi.purevon.presentation.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import com.rasmi.purevon.presentation.theme.iOSBlue

/**
 * Message Reaction Model
 */
data class MessageReaction(
    val emoji: String,
    val count: Int,
    val isReactedByMe: Boolean
)

/**
 * Available reaction emojis (like iMessage)
 */
val REACTION_EMOJIS = listOf(
    "❤️", // Heart
    "👍", // Thumbs up
    "👎", // Thumbs down
    "😂", // Laughing
    "😮", // Surprised
    "😢", // Sad
    "😡", // Angry
    "🎉"  // Party
)

/**
 * Reaction Picker Dialog
 */
@Composable
fun ReactionPickerDialog(
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "React to message",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(REACTION_EMOJIS) { emoji ->
                        ReactionButton(
                            emoji = emoji,
                            onClick = {
                                onReactionSelected(emoji)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single reaction button
 */
@Composable
private fun ReactionButton(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reaction_scale"
    )
    
    Box(
        modifier = modifier
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 32.sp
        )
    }
}

/**
 * Display reactions under message bubble
 */
@Composable
fun MessageReactionBar(
    reactions: List<MessageReaction>,
    onReactionClick: (String) -> Unit,
    onReactionLongClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        reactions.forEach { reaction ->
            ReactionChip(
                reaction = reaction,
                onClick = { onReactionClick(reaction.emoji) },
                onLongClick = if (onReactionLongClick != null) {
                    { onReactionLongClick(reaction.emoji) }
                } else null
            )
        }
    }
}

/**
 * Single reaction chip
 */
@Composable
private fun ReactionChip(
    reaction: MessageReaction,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var isAnimating by remember { mutableStateOf(false) }
    
    LaunchedEffect(reaction.count) {
        isAnimating = true
        kotlinx.coroutines.delay(300)
        isAnimating = false
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "chip_scale"
    )
    
    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp)),
        color = if (reaction.isReactedByMe) {
            iOSBlue.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(16.dp),
        border = if (reaction.isReactedByMe) {
            androidx.compose.foundation.BorderStroke(1.5.dp, iOSBlue)
        } else null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = reaction.emoji,
                fontSize = 16.sp
            )
            
            if (reaction.count > 1) {
                Text(
                    text = reaction.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (reaction.isReactedByMe) FontWeight.Bold else FontWeight.Normal,
                    color = if (reaction.isReactedByMe) {
                        iOSBlue
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Animated reaction pop (when adding reaction)
 */
@Composable
fun AnimatedReactionPop(
    emoji: String,
    visible: Boolean,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var animationPlayed by remember { mutableStateOf(false) }
    
    LaunchedEffect(visible) {
        if (visible && !animationPlayed) {
            animationPlayed = true
            kotlinx.coroutines.delay(1000)
            onAnimationEnd()
            animationPlayed = false
        }
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Text(
            text = emoji,
            fontSize = 48.sp,
            modifier = Modifier
                .background(Color.White, CircleShape)
                .padding(12.dp)
        )
    }
}
