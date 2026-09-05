package com.rasmi.purevon.presentation.component

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.rasmi.purevon.presentation.theme.*

/**
 * Premium Professional Dialpad Button
 * Modern glass-morphism design with elegant animations
 */
@Composable
fun DialpadButton(
    digit: String,
    letters: String = "",
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null
) {
    val isLightTheme = false
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    // Smooth scale animation with spring effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "button_scale"
    )
    
    // Glow effect when pressed
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.3f else 0f,
        animationSpec = tween(100),
        label = "glow_alpha"
    )
    
    Box(
        modifier = modifier
            .size(60.dp)
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 1.dp else if (isLightTheme) 3.dp else 2.dp,
                shape = CircleShape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.7f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.7f)
            )
            .clip(CircleShape)
            .background(
                color = if (isLightTheme) MaterialTheme.colorScheme.surface else backgroundColor
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isLightTheme) listOf(
                        LightBorder,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) else listOf(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                    )
                ),
                shape = CircleShape
            )
            .pointerInput(onClick, onLongPress) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongPress?.invoke() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                fontSize = 31.sp,
                fontWeight = FontWeight.W400,
                color = contentColor,
                letterSpacing = (-0.5).sp
            )
            
            // Show icon or letters
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
            } else if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.5f),
                    letterSpacing = 2.sp
                )
            }
        }
    }
}/**
 * Premium Call Button with gradient and glow effect
 */
@Composable
private fun PremiumCallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "call_scale"
    )
    
    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )
    
    Box(
        modifier = modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle glow ring behind
        Box(
            modifier = Modifier
                .size(60.dp)
                .scale(glowScale)
                .alpha(0.2f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DialpadGreen,
                            DialpadGreenLight
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Main button
        Box(
            modifier = Modifier
                .size(60.dp)
                .scale(scale)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            DialpadGreen, // Vibrant green
                            DialpadGreenLight  // Lighter green
                        )
                    )
                )
.pointerInput(onClick) {
    detectTapGestures(
        onPress = {
                            isPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { 
                            com.rasmi.purevon.util.PhoneUtil.playClickSound(context)
                            onClick() 
                        }
                    )
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
}

/**
 * Circular Action Button for SIM (text only)
 */
@Composable
private fun CircularSimButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sim_scale"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.3f else 0f,
        animationSpec = tween(100),
        label = "glow_alpha"
    )
    
    Box(
        modifier = modifier
            .size(60.dp)
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 2.dp else 4.dp,
                shape = CircleShape,
                ambientColor = com.rasmi.purevon.presentation.theme.iOSOrange.copy(alpha = glowAlpha),
                spotColor = com.rasmi.purevon.presentation.theme.iOSOrange.copy(alpha = glowAlpha)
            )
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        com.rasmi.purevon.presentation.theme.iOSOrange.copy(alpha = 0.85f),
                        com.rasmi.purevon.presentation.theme.iOSOrange.copy(alpha = 0.7f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        com.rasmi.purevon.presentation.theme.iOSOrange.copy(alpha = 0.3f),
                        com.rasmi.purevon.presentation.theme.iOSOrange.copy(alpha = 0.1f)
                    )
                ),
                shape = CircleShape
            )
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

/**
 * Circular Action Button for Paste (icon only)
 */
@Composable
private fun CircularPasteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "paste_scale"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.3f else 0f,
        animationSpec = tween(100),
        label = "glow_alpha"
    )
    
    Box(
        modifier = modifier
            .size(60.dp)
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 2.dp else 4.dp,
                shape = CircleShape,
                ambientColor = com.rasmi.purevon.presentation.theme.iOSBlue.copy(alpha = glowAlpha),
                spotColor = com.rasmi.purevon.presentation.theme.iOSBlue.copy(alpha = glowAlpha)
            )
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        com.rasmi.purevon.presentation.theme.iOSBlue.copy(alpha = 0.85f),
                        com.rasmi.purevon.presentation.theme.iOSBlue.copy(alpha = 0.7f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        com.rasmi.purevon.presentation.theme.iOSBlue.copy(alpha = 0.3f),
                        com.rasmi.purevon.presentation.theme.iOSBlue.copy(alpha = 0.1f)
                    )
                ),
                shape = CircleShape
            )
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = "Paste",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Circular Backspace Button (icon only)
 */
@Composable
private fun CircularBackspaceButton(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "backspace_scale"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.3f else 0f,
        animationSpec = tween(100),
        label = "glow_alpha"
    )
    
    Box(
        modifier = modifier
            .size(60.dp)
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 2.dp else 4.dp,
                shape = CircleShape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha)
            )
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )
                ),
                shape = CircleShape
            )
            .pointerInput(onClick, onLongPress) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress() 
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Backspace",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Premium Professional Dialpad
 * Modern, elegant design with smooth animations
 */
@Composable
fun IOSDialpad(
    onDigitPressed: (String) -> Unit,
    onBackspacePressed: () -> Unit,
    onBackspaceLongPressed: () -> Unit,
    onCallPressed: () -> Unit,
    modifier: Modifier = Modifier,
    onPastePressed: (() -> Unit)? = null,
    onSimSwitchPressed: (() -> Unit)? = null,
    onStarLongPressed: (() -> Unit)? = null,
    currentSimLabel: String = "SIM1"
) {
    val context = LocalContext.current
    val soundPool = remember {
        android.media.SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var dtmfLoaded by remember { mutableStateOf(false) }
    val dtmfSoundId = remember { soundPool.load(context, com.rasmi.purevon.R.raw.dtmf, 1) }
    
    DisposableEffect(Unit) {
        soundPool.setOnLoadCompleteListener { _, sampleId, _ ->
            if (sampleId == dtmfSoundId) dtmfLoaded = true
        }
        onDispose {
            soundPool.release()
        }
    }
    
    fun playDTMFTone(digit: String) {
        if (dtmfLoaded) {
            soundPool.play(dtmfSoundId, 1f, 1f, 1, 0, 1f)
        }
    }
    
    data class ButtonConfig(
        val digit: String,
        val letters: String,
        val longPressChar: String?,
        val icon: ImageVector? = null,
        val hasSpecialLongPress: Boolean = false
    )

    val dialpadData = remember {
        listOf(
            ButtonConfig("1", "", null),
            ButtonConfig("2", "", null),
            ButtonConfig("3", "", null),
            ButtonConfig("4", "", null),
            ButtonConfig("5", "", null),
            ButtonConfig("6", "", null),
            ButtonConfig("7", "", null),
            ButtonConfig("8", "", null),
            ButtonConfig("9", "", null),
            ButtonConfig("*", "", null, Icons.Default.PhoneInTalk, hasSpecialLongPress = true),
            ButtonConfig("0", "+", "+"),
            ButtonConfig("#", "", null)
        )
    }
    
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val isLightTheme = false
    
    // Wrap everything in a Surface card
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isLightTheme) MaterialTheme.colorScheme.surface
               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isLightTheme) androidx.compose.foundation.BorderStroke(1.5.dp, LightBorder) else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        // Side action buttons column (right side for RTL, left for LTR)
        if (isRtl) {
            Column(
                modifier = Modifier.width(60.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row 1: Call button (aligned with 1-2-3)
                PremiumCallButton(onClick = onCallPressed)
                
                // Row 2: SIM Switch button (aligned with 4-5-6)
                if (onSimSwitchPressed != null) {
                    CircularSimButton(
                        text = currentSimLabel,
                        onClick = onSimSwitchPressed
                    )
                } else {
                    Spacer(modifier = Modifier.size(60.dp))
                }
                
                // Row 3: Paste button (aligned with 7-8-9)
                if (onPastePressed != null) {
                    CircularPasteButton(
                        onClick = onPastePressed
                    )
                } else {
                    Spacer(modifier = Modifier.size(60.dp))
                }
                
                // Row 4: Backspace button (aligned with *-0-#)
                CircularBackspaceButton(
                    onClick = onBackspacePressed,
                    onLongPress = onBackspaceLongPressed
                )
            }
        }
        
        // Main dialpad grid
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Digit rows
            dialpadData.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val displayRow = if (isRtl) row.reversed() else row
                    displayRow.forEach { config ->
                        DialpadButton(
                            digit = config.digit,
                            letters = config.letters,
                            icon = config.icon,
                            onClick = {
                                playDTMFTone(config.digit)
                                onDigitPressed(config.digit)
                            },
                            onLongPress = when {
                                config.hasSpecialLongPress && config.digit == "*" -> {
                                    onStarLongPressed
                                }
                                config.longPressChar != null -> {
                                    {
                                        playDTMFTone(config.longPressChar)
                                        onDigitPressed(config.longPressChar)
                                    }
                                }
                                else -> null
                            }
                        )
                    }
                }
            }
        }
        
        // Side action buttons column (left side for RTL, right for LTR)
        if (!isRtl) {
            Column(
                modifier = Modifier.width(60.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row 1: Call button (aligned with 1-2-3)
                PremiumCallButton(onClick = onCallPressed)
                
                // Row 2: SIM Switch button (aligned with 4-5-6)
                if (onSimSwitchPressed != null) {
                    CircularSimButton(
                        text = currentSimLabel,
                        onClick = onSimSwitchPressed
                    )
                } else {
                    Spacer(modifier = Modifier.size(60.dp))
                }
                
                // Row 3: Paste button (aligned with 7-8-9)
                if (onPastePressed != null) {
                    CircularPasteButton(
                        onClick = onPastePressed
                    )
                } else {
                    Spacer(modifier = Modifier.size(60.dp))
                }
                
                // Row 4: Backspace button (aligned with *-0-#)
                CircularBackspaceButton(
                    onClick = onBackspacePressed,
                    onLongPress = onBackspaceLongPressed
                )
            }
        }
    }
    }
}
