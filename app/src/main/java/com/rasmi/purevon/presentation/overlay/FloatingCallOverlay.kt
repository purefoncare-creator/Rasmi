package com.rasmi.purevon.presentation.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.screen.incall.getAudioRouteIcon
import com.rasmi.purevon.presentation.theme.*

/**
 * شريط عائم احترافي للمكالمات النشطة والواردة
 * ✅ يظهر كشريط كامل لمدة 3 ثوانٍ ثم ينكمش إلى فقاعة صغيرة قابلة للسحب
 * ✅ يدعم المكالمات المتعددة (محتجزة / منتظرة)
 * ✅ النقر على الفقاعة يفتح شاشة المكالمة الكاملة
 * ✅ الضغط المطول على الفقاعة يوسعها مؤقتاً
 */
@Composable
fun FloatingCallOverlay(
    contactName: String?,
    phoneNumber: String,
    callStartTime: Long,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    currentAudioRoute: Int = 0,
    isRinging: Boolean = false,
    isDialing: Boolean = false,
    // ✅ Multi-call support
    secondCallName: String? = null,
    secondCallNumber: String? = null,
    secondCallState: String? = null,
    // ✅ Bubble mode support
    isCollapsed: Boolean = false,
    onCollapsedChange: (Boolean) -> Unit = {},
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onAnswer: () -> Unit = {},
    onReject: () -> Unit = {},
    onSilence: () -> Unit = {},
    onAnswerWaiting: () -> Unit = {},
    onRejectWaiting: () -> Unit = {},
    onSwapCalls: () -> Unit = {},
    darkThemeOverride: Boolean? = null
) {
    val isLightTheme = !(darkThemeOverride ?: isSystemInDarkTheme())
    // ✅ إعادة فتح الشريط عند مكالمة واردة جديدة
    LaunchedEffect(isRinging, secondCallState) {
        if (isRinging || secondCallState == "waiting") {
            onCollapsedChange(false)
        }
    }

    AnimatedContent(
        targetState = isCollapsed,
        transitionSpec = {
            if (targetState) {
                // Collapsing: bar → bubble
                (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.5f, animationSpec = tween(300)))
                    .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.3f, animationSpec = tween(200)))
            } else {
                // Expanding: bubble → bar
                (fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)))
                    .togetherWith(fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)))
            }
        },
        label = "overlay_mode"
    ) { collapsed ->
        if (collapsed) {
            // ═══════════════════════════════════════
            // ✅ وضع الفقاعة المنكمشة (Collapsed Bubble)
            // النقر عليها يعيد الشريط الكامل
            // ═══════════════════════════════════════
            CollapsedBubble(
                contactName = contactName,
                phoneNumber = phoneNumber,
                callStartTime = callStartTime,
                isRinging = isRinging,
                isDialing = isDialing,
                onTap = { onCollapsedChange(false) },
                onLongPress = onTap,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                isLightTheme = isLightTheme,
                modifier = modifier
            )
        } else {
            // ═══════════════════════════════════════
            // ✅ وضع الشريط الكامل (Expanded Bar)
            // ═══════════════════════════════════════
            ExpandedBar(
                contactName = contactName,
                phoneNumber = phoneNumber,
                callStartTime = callStartTime,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                currentAudioRoute = currentAudioRoute,
                isRinging = isRinging,
                isDialing = isDialing,
                secondCallName = secondCallName,
                secondCallNumber = secondCallNumber,
                secondCallState = secondCallState,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onAnswer = onAnswer,
                onReject = onReject,
                onSilence = onSilence,
                onEndCall = onEndCall,
                onAnswerWaiting = onAnswerWaiting,
                onRejectWaiting = onRejectWaiting,
                onSwapCalls = onSwapCalls,
                onCollapse = { onCollapsedChange(true) },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onTap = onTap,
                isLightTheme = isLightTheme,
                modifier = modifier
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// ✅ الفقاعة المنكمشة - صغيرة وقابلة للسحب
// ═══════════════════════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollapsedBubble(
    contactName: String?,
    phoneNumber: String,
    callStartTime: Long,
    isRinging: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    isLightTheme: Boolean,
    modifier: Modifier = Modifier,
    isDialing: Boolean = false
) {
    val bubbleShape = RoundedCornerShape(28.dp)
    val bubbleContainer = if (isLightTheme) {
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.96f),
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
            )
        )
    }

    val callingText = stringResource(R.string.call_status_calling)
    val incomingText = stringResource(R.string.call_status_incoming)
    var callDuration by remember { mutableStateOf("00:00") }
    LaunchedEffect(callStartTime, isRinging, isDialing) {
        if (callStartTime > 0 && !isRinging && !isDialing) {
            while (true) {
                val durationMillis = android.os.SystemClock.elapsedRealtime() - callStartTime
                val seconds = (durationMillis / 1000) % 60
                val minutes = (durationMillis / (1000 * 60)) % 60
                val hours = (durationMillis / (1000 * 60 * 60))
                callDuration = if (hours > 0) {
                    String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
                }
                kotlinx.coroutines.delay(1000)
            }
        } else {
            callDuration = when {
                isDialing -> callingText
                isRinging -> incomingText
                else -> "00:00"
            }
        }
    }

    // ✅ تأثير النبض للمؤشر الأخضر
    val infiniteTransition = rememberInfiniteTransition(label = "bubble_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubble_pulse_scale"
    )

    Box(
        modifier = modifier
            .padding(8.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = onDragEnd
                )
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        // ═══════════════════════════════════════
        // الفقاعة مع شريط المدة
        // ═══════════════════════════════════════
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 18.dp,
                    shape = bubbleShape,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.22f)
                )
                .clip(bubbleShape)
                .background(bubbleContainer)
                .border(
                    1.dp,
                    if (isLightTheme) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.14f),
                    bubbleShape
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                    onLongClick = onLongPress,
                    onLongClickLabel = "Open full call screen"
                )
                .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ✅ الدائرة الرئيسية مع أيقونة الهاتف
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(iOSGreen.copy(alpha = 0.95f), iOSGreen.copy(alpha = 0.72f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // مؤشر النبض في الخلفية
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(iOSGreen.copy(alpha = 0.22f))
                )

                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Active call",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ✅ الاسم + المدة
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = contactName ?: phoneNumber.takeLast(4),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isLightTheme) DarkSurface else Color.White,
                    modifier = Modifier.widthIn(max = 92.dp)
                )
                Text(
                    text = callDuration,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = iOSGreen,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════
// ✅ الشريط الكامل الموسع (الكود الأصلي)
// ═══════════════════════════════════════════════════════
@Composable
private fun ExpandedBar(
    contactName: String?,
    phoneNumber: String,
    callStartTime: Long,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isRinging: Boolean,
    secondCallName: String?,
    secondCallNumber: String?,
    secondCallState: String?,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onSilence: () -> Unit,
    onEndCall: () -> Unit,
    onAnswerWaiting: () -> Unit,
    onRejectWaiting: () -> Unit,
    onSwapCalls: () -> Unit,
    onCollapse: () -> Unit,
    onTap: () -> Unit,
    isLightTheme: Boolean,
    modifier: Modifier = Modifier,
    currentAudioRoute: Int = 0,
    isDialing: Boolean = false,
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val hasSecondCall = secondCallState != null && secondCallNumber != null
    val isWaitingCall = secondCallState == "waiting"
    val isConferenceCall = secondCallState == "conference"
    val barShape = RoundedCornerShape(30.dp)
    val activeAccent = when {
        isConferenceCall -> iOSIndigo
        hasSecondCall && isWaitingCall -> MaterialOrange
        isRinging -> iOSGreen
        else -> iOSGreen
    }
    val barGradient = if (isLightTheme) {
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.98f),
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f)
            )
        )
    }
    val primaryTextColor = if (isLightTheme) DarkSurface else Color.White
    val secondaryTextColor = if (isLightTheme) MaterialTheme.colorScheme.onSurfaceVariant else iOSSystemGray2

    // ✅ حساب مدة المكالمة محلياً
    val callingText = stringResource(R.string.call_status_calling)
    val incomingText = stringResource(R.string.call_status_incoming)
    var callDuration by remember { mutableStateOf("00:00") }

    LaunchedEffect(callStartTime, isRinging, isDialing) {
        if (callStartTime > 0 && !isRinging && !isDialing) {
            while (true) {
                val durationMillis = android.os.SystemClock.elapsedRealtime() - callStartTime
                val seconds = (durationMillis / 1000) % 60
                val minutes = (durationMillis / (1000 * 60)) % 60
                val hours = (durationMillis / (1000 * 60 * 60))

                callDuration = if (hours > 0) {
                    String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
                }
                kotlinx.coroutines.delay(1000)
            }
        } else {
            callDuration = when {
                isDialing -> callingText
                isRinging -> incomingText
                else -> "00:00"
            }
        }
    }

    // Animation for pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(0f, dragAmount.y)
                    },
                    onDragEnd = onDragEnd
                )
            }
            .shadow(
                elevation = 20.dp,
                shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = activeAccent.copy(alpha = 0.22f)
            )
            .clip(barShape)
            .background(barGradient)
            .border(
                1.dp,
                if (isLightTheme) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.13f),
                barShape
            )
            .clickable(onClick = onTap)
    ) {
            // الصف الرئيسي
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Call info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(activeAccent.copy(alpha = if (isLightTheme) 0.13f else 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(activeAccent)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = contactName ?: phoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = primaryTextColor
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(activeAccent)
                            )
                            Text(
                                text = callDuration,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = secondaryTextColor
                            )
                        }
                    }
                }

                // Right: Control buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRinging) {
                        // ✅ زر التصغير متاح أيضاً أثناء المكالمة الواردة
                        FloatingControlButton(
                            icon = Icons.Default.CloseFullscreen,
                            isActive = false,
                            onClick = onCollapse,
                            isLightTheme = isLightTheme,
                            backgroundColor = iOSSystemGray
                        )
                        FloatingControlButton(
                            icon = Icons.AutoMirrored.Filled.VolumeDown,
                            isActive = false,
                            onClick = onSilence,
                            isLightTheme = isLightTheme,
                            backgroundColor = AmberLight
                        )
                        FloatingControlButton(
                            icon = Icons.Default.CallEnd,
                            isActive = false,
                            onClick = onReject,
                            isLightTheme = isLightTheme,
                            backgroundColor = iOSRed
                        )
                        FloatingControlButton(
                            icon = Icons.Default.Call,
                            isActive = false,
                            onClick = onAnswer,
                            isLightTheme = isLightTheme,
                            backgroundColor = iOSGreen
                        )
                    } else {
                        FloatingControlButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            isActive = isMuted,
                            onClick = onToggleMute,
                            isLightTheme = isLightTheme
                        )

                        FloatingControlButton(
                            icon = getAudioRouteIcon(currentAudioRoute),
                            isActive = isSpeakerOn,
                            onClick = onToggleSpeaker,
                            isLightTheme = isLightTheme
                        )

                        if (hasSecondCall && !isWaitingCall) {
                            FloatingControlButton(
                                icon = Icons.Default.SwapCalls,
                                isActive = false,
                                onClick = onSwapCalls,
                                isLightTheme = isLightTheme,
                                backgroundColor = MaterialTheme.colorScheme.primary
                            )
                        }

                        // ✅ زر التصغير - يحول الشريط إلى فقاعة
                        FloatingControlButton(
                            icon = Icons.Default.CloseFullscreen,
                            isActive = false,
                            onClick = onCollapse,
                            isLightTheme = isLightTheme,
                            backgroundColor = iOSSystemGray
                        )

                        FloatingControlButton(
                            icon = Icons.Default.CallEnd,
                            isActive = false,
                            onClick = onEndCall,
                            isLightTheme = isLightTheme,
                            backgroundColor = iOSRed
                        )
                    }
                }
            }

            // ✅ صف المكالمة الثانية
            if (hasSecondCall) {
                val conferenceColor = iOSIndigo

                val secondCallBgColor = when {
                    isConferenceCall -> conferenceColor.copy(alpha = if (isLightTheme) 0.12f else 0.16f)
                    isWaitingCall -> iOSGreen.copy(alpha = if (isLightTheme) 0.12f else 0.16f)
                    else -> if (isLightTheme) MaterialTheme.colorScheme.surface.copy(alpha = 0.56f) else Color.White.copy(alpha = 0.06f)
                }

                val secondCallAccentColor = when {
                    isConferenceCall -> conferenceColor
                    isWaitingCall -> iOSGreen
                    else -> iOSSystemGray2
                }

                val waitingPulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "waiting_pulse"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = secondCallBgColor,
                    border = when {
                        isWaitingCall -> androidx.compose.foundation.BorderStroke(
                            1.dp,
                            iOSGreen.copy(alpha = waitingPulseAlpha)
                        )
                        isConferenceCall -> androidx.compose.foundation.BorderStroke(
                            1.dp,
                            conferenceColor.copy(alpha = 0.32f)
                        )
                        else -> null
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isConferenceCall) Icons.Default.Groups else Icons.Default.Person,
                            contentDescription = null,
                            tint = secondCallAccentColor,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = when {
                                isConferenceCall -> stringResource(R.string.conference_label)
                                else -> secondCallName ?: secondCallNumber ?: ""
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = primaryTextColor,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = when {
                                isConferenceCall -> pluralStringResource(R.plurals.conference_participants_count, (secondCallNumber ?: "0").toIntOrNull() ?: 0, (secondCallNumber ?: "0").toIntOrNull() ?: 0)
                                isWaitingCall -> stringResource(R.string.overlay_status_incoming)
                                else -> stringResource(R.string.overlay_status_hold)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = secondCallAccentColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        if (isWaitingCall) {
                            FloatingControlButton(
                                icon = Icons.Default.CallEnd,
                                isActive = false,
                                onClick = onRejectWaiting,
                                isLightTheme = isLightTheme,
                                backgroundColor = iOSRed,
                                size = 28.dp,
                                iconSize = 14.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FloatingControlButton(
                                icon = Icons.Default.Call,
                                isActive = false,
                                onClick = onAnswerWaiting,
                                isLightTheme = isLightTheme,
                                backgroundColor = iOSGreen,
                                size = 28.dp,
                                iconSize = 14.dp
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
private fun FloatingControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    isLightTheme: Boolean,
    backgroundColor: Color? = null,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp
) {
    val bgColor = when {
        backgroundColor != null && isActive -> backgroundColor.copy(alpha = 0.85f)
        backgroundColor != null && !isActive -> backgroundColor.copy(alpha = if (isLightTheme) 0.88f else 0.72f)
        isActive -> iOSGreen.copy(alpha = if (isLightTheme) 0.18f else 0.25f)
        else -> if (isLightTheme) MaterialTheme.colorScheme.surface.copy(alpha = 0.84f) else Color.White.copy(alpha = 0.10f)
    }

    val iconColor = when {
        backgroundColor != null -> Color.White
        isActive -> iOSGreen
        else -> if (isLightTheme) MaterialTheme.colorScheme.onSurfaceVariant else iOSSystemGray2
    }

    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = if (backgroundColor != null) 8.dp else 0.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = (backgroundColor ?: iOSGreen).copy(alpha = 0.18f)
            )
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (backgroundColor != null) Color.White.copy(alpha = 0.16f)
                else Color.White.copy(alpha = if (isLightTheme) 0.68f else 0.08f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
