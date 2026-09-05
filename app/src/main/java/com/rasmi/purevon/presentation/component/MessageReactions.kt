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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rasmi.purevon.presentation.theme.*

data class MessageReaction(
    val emoji: String,
    val count: Int,
    val isReactedByMe: Boolean
)

val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "😡", "🎉", "👎")

@Composable
fun ReactionPickerDialog(
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
        shape = RoundedCornerShape(MessagingDimensions.corner12x),
            color = PurevonSurface,
            shadowElevation = 0.dp
        ) {
            LazyRow(
                modifier = Modifier.padding(MessagingDimensions.spacing12x),
                horizontalArrangement = Arrangement.spacedBy(MessagingDimensions.spacing4x)
            ) {
                items(REACTION_EMOJIS) { emoji ->
                    ReactionButton(
                        emoji = emoji,
                        onClick = { onReactionSelected(emoji); onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionButton(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "reaction_scale"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(PurevonSurfaceMuted)
            .clickable { isPressed = true; onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 28.sp)
    }
}

@Composable
fun MessageReactionBar(
    reactions: List<MessageReaction>,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onReactionLongClick: ((String) -> Unit)? = null
) {
    if (reactions.isEmpty()) return
    Row(
        modifier = modifier.padding(start = MessagingDimensions.spacing10x, top = MessagingDimensions.spacing4x),
        horizontalArrangement = Arrangement.spacedBy(MessagingDimensions.spacing4x)
    ) {
        reactions.forEach { reaction ->
            ReactionChip(
                reaction = reaction,
                onClick = { onReactionClick(reaction.emoji) },
                onLongClick = onReactionLongClick?.let { { it(reaction.emoji) } }
            )
        }
    }
}

@Composable
private fun ReactionChip(
    reaction: MessageReaction,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var isAnimating by remember { mutableStateOf(false) }
    LaunchedEffect(reaction.count) { isAnimating = true; kotlinx.coroutines.delay(300); isAnimating = false }
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "chip_scale"
    )
    Surface(
        modifier = modifier.scale(scale),
        color = if (reaction.isReactedByMe) PurevonPrimary.copy(alpha = 0.15f) else PurevonSurfaceMuted,
        shape = RoundedCornerShape(MessagingDimensions.corner16x),
        border = if (reaction.isReactedByMe) androidx.compose.foundation.BorderStroke(1.dp, PurevonPrimary) else null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MessagingDimensions.spacing10x, vertical = MessagingDimensions.spacing4x),
            horizontalArrangement = Arrangement.spacedBy(MessagingDimensions.spacing4x),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = reaction.emoji, fontSize = 12.sp)
            if (reaction.count > 1) {
                Text(
                    text = reaction.count.toString(),
                    style = MessagingTypography.badge01,
                    color = if (reaction.isReactedByMe) PurevonPrimary else PurevonTextSecondary
                )
            }
        }
    }
}

@Composable
fun AnimatedReactionPop(
    emoji: String,
    visible: Boolean,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible && !animationPlayed) { animationPlayed = true; kotlinx.coroutines.delay(1000); onAnimationEnd(); animationPlayed = false }
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Text(
            text = emoji,
            fontSize = 48.sp,
            modifier = Modifier
                .background(PurevonSurface, CircleShape)
                .padding(12.dp)
        )
    }
}
