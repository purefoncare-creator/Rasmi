package com.rasmi.purevon.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.presentation.theme.iOSGreen
import com.rasmi.purevon.presentation.theme.iOSRed

/**
 * Modern Professional Call Button - Elegant & Responsive
 */
@Composable
fun IOSCallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = iOSGreen,
    enabled: Boolean = true
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
        ),
        label = "call_button_scale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f))
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Call",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Modern Professional Backspace Button - Minimalist & Elegant
 */
@Composable
fun IOSBackspaceButton(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
        ),
        label = "backspace_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.6f else 1f,
        label = "backspace_alpha"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Backspace",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * iOS-style End Call Button
 */
@Composable
fun IOSEndCallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IOSCallButton(
        onClick = onClick,
        modifier = modifier,
        backgroundColor = iOSRed
    )
}

/**
 * Generic iOS-style Icon Button
 */
@Composable
fun IOSIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = Color.White
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "icon_button_scale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}
