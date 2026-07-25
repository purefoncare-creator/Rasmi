package com.rasmi.purevon.service

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.presentation.theme.PurevonTheme
import com.rasmi.purevon.presentation.theme.iOSGreen

/**
 * Floating OTP Bubble Composable Content
 * Displays OTP code in a floating window with copy action
 */
@Composable
fun FloatingOtpBubbleContent(
    otpCode: String,
    sender: String,
    onCopyClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    var isPulsing by remember { mutableStateOf(true) }

    // Adaptive font size and letter spacing based on OTP length
    val (fontSize, letterSpacing) = when {
        otpCode.length <= 4 -> 42.sp to 6.sp
        otpCode.length == 5 -> 36.sp to 4.sp
        otpCode.length == 6 -> 32.sp to 3.sp
        otpCode.length == 7 -> 26.sp to 2.sp
        else                -> 22.sp to 1.sp  // 8 digits
    }

    // Stop pulsing after 3 seconds
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        isPulsing = false
    }

    PurevonTheme {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Card(
                modifier = Modifier
                    .wrapContentWidth()
                    .shadow(16.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isPulsing) 1.05f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    // OTP code — tap to copy
                    Box(
                        modifier = Modifier
                            .clickable(
                                onClick = onCopyClick,
                                onClickLabel = "Copy OTP"
                            )
                            .padding(vertical = 18.dp, horizontal = 20.dp)
                            .semantics { contentDescription = "OTP code $otpCode, tap to copy" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = otpCode,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = letterSpacing,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        )
                    }

                    // Close button
                    IconButton(
                        onClick = onDismissClick,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(end = 4.dp)
                            .semantics { contentDescription = "Dismiss OTP" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
