package com.rasmi.purevon.presentation.screen.incall

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.presentation.theme.iOSGreen

/**
 * ✅ In-call DTMF keypad embedded inside the middle card.
 * Digits typed are shown in a display row at the top.
 * Replaces DialpadDialog (no popup — stays in context).
 */
@Composable
internal fun InCallKeypadContent(
    onDigitPressed: (Char) -> Unit
) {
    val isLightTheme = false
    var typedDigits by remember { mutableStateOf("") }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // ── 3 × 4 layout: special key on left (LTR) / right (RTL auto-mirror)
    // # 1 2 3
    // 0 4 5 6
    // * 7 8 9
    val dialpadRows = listOf(
        listOf("#" to "", "1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("0" to "+", "4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("*" to "", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Digit display ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = typedDigits.ifEmpty { "· · ·" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Light,
                color = if (typedDigits.isEmpty())
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                else
                    iOSGreen,
                letterSpacing = 6.sp,
                maxLines = 1
            )
            if (typedDigits.isNotEmpty()) {
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(
                    onClick = { typedDigits = typedDigits.dropLast(1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ── 4 × 3 button grid — each row gets equal space ─
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            dialpadRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { (digit, subLabel) ->
                        KeypadInlineButton(
                            digit = digit,
                            subLabel = subLabel,
                            isLightTheme = isLightTheme,
                            onClick = {
                                typedDigits += digit
                                onDigitPressed(digit[0])
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadInlineButton(
    digit: String,
    subLabel: String,
    isLightTheme: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "key_scale"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else if (isLightTheme) 0.75f else 0.45f,
        animationSpec = tween(80),
        label = "key_bg"
    )

    Box(
        modifier = Modifier
            .size(54.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isLightTheme)
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPressed) iOSGreen else MaterialTheme.colorScheme.onSurface
            )
            if (subLabel.isNotEmpty()) {
                Text(
                    text = subLabel,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
