package com.rasmi.purevon.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.rasmi.purevon.presentation.theme.ButtonHeight
import com.rasmi.purevon.presentation.theme.CornerRadius
import com.rasmi.purevon.presentation.theme.Spacing

/**
 * iOS-style Primary Button with scale animation on press
 */
@Composable
fun IOSPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    height: Dp = ButtonHeight.Medium,
    cornerRadius: Dp = CornerRadius.Medium
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "button_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        label = "button_alpha"
    )
    
    Box(
        modifier = modifier
            .heightIn(min = height)
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = Spacing.default, vertical = Spacing.medium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * iOS-style Secondary Button (outlined)
 */
@Composable
fun IOSSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = ButtonHeight.Medium,
    cornerRadius: Dp = CornerRadius.Medium
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "button_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        label = "button_alpha"
    )
    
    Box(
        modifier = modifier
            .heightIn(min = height)
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(cornerRadius))
            .background(contentColor.copy(alpha = 0.1f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = Spacing.default, vertical = Spacing.medium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * iOS-style Text Button (no background)
 */
@Composable
fun IOSTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.5f
            isPressed -> 0.6f
            else -> 1f
        },
        label = "button_alpha"
    )
    
    Box(
        modifier = modifier
            .alpha(alpha)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = Spacing.default, vertical = Spacing.medium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
