package com.rasmi.purevon.presentation.screen.incall

import android.media.AudioManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*

// ═══════════════════════════════════════════════════════════════
// Call Control Buttons
// ═══════════════════════════════════════════════════════════════

@Composable
internal fun CallControlButton(
    icon: ImageVector,
    label: String? = null,
    isActive: Boolean = false,
    enabled: Boolean = true,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 3.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.4f
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val audioManager = LocalContext.current.getSystemService(AudioManager::class.java)
    
    // Determine colors based on parameters or defaults
    val buttonBackgroundColor = when {
        backgroundColor != null -> backgroundColor
        isActive -> iOSBlue
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val buttonContentColor = when {
        contentColor != null -> contentColor
        isActive -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .alpha(alpha)
            .semantics(mergeDescendants = true) {
                contentDescription = if (isActive) "$label active, button" else "$label button"
                role = Role.Button
                stateDescription = when {
                    !enabled -> "معطّل"
                    isActive -> "مفعّل"
                    else -> "غير مفعّل"
                }
            }
    ) {
        Box(
            modifier = Modifier
                .size(72.dp) // Same size as End Call button
                .then(
                    if (borderColor != null) Modifier.border(borderWidth, borderColor, CircleShape)
                    else Modifier
                )
                .clip(CircleShape)
                .background(buttonBackgroundColor)
                .clickable(
                    enabled = enabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
                        onClick()
                    },
                    role = Role.Button
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = buttonContentColor,
                modifier = Modifier.size(32.dp)
            )
        }
        
        if (label != null) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * ✅ أزرار المكالمة الواردة - تظهر في البطاقة السفلية
 */
@Composable
internal fun IncomingCallButtons(
    phoneNumber: String,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onSilence: () -> Unit,
    onQuickMessage: (String) -> Unit,
    isSilenced: Boolean = false // ✅ Fix #11: تعطيل زر الإسكات بعد الإسكات
) {
    var showQuickMessageDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // صف أول - رفض وقبول
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // زر الرفض
            IncomingUnifiedButton(
                icon = Icons.Default.CallEnd,
                label = stringResource(R.string.incall_btn_decline),
                backgroundColor = MaterialRed400,
                onClick = onReject,
                modifier = Modifier.weight(1f)
            )
            
            // زر القبول
            IncomingUnifiedButton(
                icon = Icons.Default.Call,
                label = stringResource(R.string.incall_btn_accept),
                backgroundColor = MaterialGreen500,
                onClick = onAnswer,
                modifier = Modifier.weight(1f)
            )
        }
        
        // صف ثاني - إسكات ورسالة
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // زر الإسكات - ذهبي باهت
            IncomingUnifiedButton(
                icon = if (isSilenced) Icons.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeOff,
                label = if (isSilenced) stringResource(R.string.incall_btn_silenced) else stringResource(R.string.incall_btn_silence),
                backgroundColor = if (isSilenced) MaterialGrey500 else AmberLight,
                onClick = onSilence,
                enabled = !isSilenced,
                modifier = Modifier.weight(1f)
            )
            
            // زر الرسالة - أزرق باهت
            IncomingUnifiedButton(
                icon = Icons.AutoMirrored.Filled.Message,
                label = stringResource(R.string.incall_btn_reply),
                backgroundColor = MaterialBlue400,
                onClick = { showQuickMessageDialog = true },
                modifier = Modifier.weight(1f)
            )
        }
    }
    
    // Quick Message Dialog
    if (showQuickMessageDialog) {
        QuickMessageDialog(
            phoneNumber = phoneNumber,
            onDismiss = { showQuickMessageDialog = false },
            onSelectMessage = { message ->
                showQuickMessageDialog = false
                onQuickMessage(message)
            }
        )
    }
}

/**
 * زر موحد للمكالمة الواردة - مستطيل منحني الزوايا
 */
@Composable
private fun IncomingUnifiedButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true // ✅ Fix #11: دعم التعطيل
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor = if (isLightTheme) Color.White else MaterialTheme.colorScheme.background
    val contentColor = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.4f)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val audioManager = LocalContext.current.getSystemService(AudioManager::class.java)

    Card(
        modifier = modifier
            .fillMaxHeight()
            .then(if (enabled) Modifier.clickable(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
                onClick()
            }) else Modifier.alpha(0.5f)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor // لون البطاقة العادي
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // دائرة خلفية خفيفة للأيقونة بنفس لونها
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Call Info & Multi-Call Cards
// ═══════════════════════════════════════════════════════════════

/**
 * ✅ Compact row showing a single call's info (avatar + name/number + status badge)
 * Used inside the top card for both active and secondary (held/waiting) calls.
 */
@Composable
internal fun CallInfoRow(
    name: String?,
    phoneNumber: String,
    statusText: String,
    isActive: Boolean,
    isHeld: Boolean,
    isWaiting: Boolean,
    photoUri: String? = null
) {
    val statusColor = when {
        isActive -> iOSGreen
        isWaiting -> MaterialOrange // Orange for incoming
        isHeld -> LightSecondary    // Gray for held
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when {
        isActive -> Icons.Default.Phone
        isWaiting -> Icons.Default.PhoneInTalk
        isHeld -> Icons.Default.Pause
        else -> Icons.Default.PhoneForwarded
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
            size = 40.dp,
            photoUri = photoUri
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Name + Number
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name ?: phoneNumber,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontSize = 18.sp
            )
            if (name != null && name != phoneNumber) {
                Text(
                    text = phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    fontSize = 16.sp
                )
            }
        }

        // Status badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusColor.copy(alpha = 0.12f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * ✅ عرض المكالمة الجماعية (Conference Call)
 * يعرض أيقونة مجموعة + عدد المشاركين + أسمائهم
 */
@Composable
internal fun ConferenceCallView(
    participants: List<String>,
    participantNames: List<String?>,
    duration: String,
    isActive: Boolean
) {
    val conferenceColor = iOSIndigo // بنفسجي أنيق للمؤتمر
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = conferenceColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, conferenceColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── رأس المؤتمر: أيقونة + "مكالمة جماعية" + عدد المشاركين ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // أيقونة مجموعة
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = conferenceColor.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = conferenceColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(10.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.conference_call_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.conference_participants_count, participants.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = conferenceColor
                    )
                }
                
                // شارة المدة
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) iOSGreen.copy(alpha = 0.12f) else conferenceColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = if (isActive) iOSGreen else conferenceColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) iOSGreen else conferenceColor
                        )
                    }
                }
            }
            
            // ── قائمة المشاركين ──
            if (participants.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    participants.forEachIndexed { index, number ->
                        val name = participantNames.getOrNull(index)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // أيقونة شخص صغيرة
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = conferenceColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = name ?: number,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            
                            if (name != null && name != number) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = number,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// HeldCallCard و WaitingCallCard تم حذفهما - كود غير مستخدم (Fix #13)


@Composable
internal fun CallStatItem(
    label: String,
    value: String,
    isError: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isError) MaterialRed600 else MaterialTheme.colorScheme.onSurface 
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * Compact stat chip with icon, value, and label - used in the hero top card
 */
@Composable
internal fun MiniStatChip(
    icon: ImageVector,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = color.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
internal fun ActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Helper Functions
// ═══════════════════════════════════════════════════════════════

internal fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

// ═══════════════════════════════════════════════════════════════
// Audio Route Picker (like Google Phone app)
// ═══════════════════════════════════════════════════════════════

/**
 * Get the icon for the current audio route
 */
@Suppress("DEPRECATION")
internal fun getAudioRouteIcon(currentRoute: Int, availableRoutes: Int = 0): ImageVector {
    return when (currentRoute) {
        android.telecom.CallAudioState.ROUTE_BLUETOOTH -> Icons.Default.BluetoothAudio
        android.telecom.CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Default.Headset
        android.telecom.CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
        else -> Icons.AutoMirrored.Filled.VolumeDown // Earpiece
    }
}

/**
 * Get the label for the current audio route
 */
@Suppress("DEPRECATION")
@Composable
internal fun getAudioRouteLabel(currentRoute: Int): String {
    return when (currentRoute) {
        android.telecom.CallAudioState.ROUTE_BLUETOOTH -> stringResource(R.string.incall_btn_speaker) // Will show BT icon
        android.telecom.CallAudioState.ROUTE_WIRED_HEADSET -> stringResource(R.string.incall_btn_speaker)
        android.telecom.CallAudioState.ROUTE_SPEAKER -> stringResource(R.string.incall_btn_speaker)
        else -> stringResource(R.string.incall_btn_speaker)
    }
}

/**
 * Check if multiple audio routes are available (needs picker instead of simple toggle)
 */
@Suppress("DEPRECATION")
internal fun hasMultipleAudioRoutes(availableRoutes: Int): Boolean {
    var count = 0
    if (availableRoutes and android.telecom.CallAudioState.ROUTE_EARPIECE != 0) count++
    if (availableRoutes and android.telecom.CallAudioState.ROUTE_SPEAKER != 0) count++
    if (availableRoutes and android.telecom.CallAudioState.ROUTE_BLUETOOTH != 0) count++
    if (availableRoutes and android.telecom.CallAudioState.ROUTE_WIRED_HEADSET != 0) count++
    return count > 2 // earpiece + speaker is default, 3+ means external device
}

/**
 * ✅ Smart Audio Route Button — shows current route icon and opens picker popup
 * Like Google Phone app: icon changes based on BT/headset/speaker/earpiece
 */
@Suppress("DEPRECATION")
@Composable
internal fun AudioRouteButton(
    currentRoute: Int,
    availableRoutes: Int,
    showPicker: Boolean,
    onToggleSpeaker: () -> Unit,
    onShowPicker: () -> Unit,
    onDismissPicker: () -> Unit,
    onSelectRoute: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = currentRoute == android.telecom.CallAudioState.ROUTE_SPEAKER
    val icon = getAudioRouteIcon(currentRoute, availableRoutes)
    val showMultipleRoutes = hasMultipleAudioRoutes(availableRoutes)

    Box(modifier = modifier) {
        CallControlButton(
            icon = icon,
            isActive = isActive,
            onClick = {
                if (showMultipleRoutes) {
                    onShowPicker()
                } else {
                    onToggleSpeaker()
                }
            }
        )

        // ✅ Popup menu for audio route selection
        if (showPicker) {
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = onDismissPicker,
                properties = PopupProperties(focusable = true)
            ) {
                AudioRoutePickerContent(
                    currentRoute = currentRoute,
                    availableRoutes = availableRoutes,
                    onSelectRoute = onSelectRoute,
                    onDismiss = onDismissPicker
                )
            }
        }
    }
}

/**
 * ✅ Audio Route Picker Content — dropdown-style popup
 */
@Suppress("DEPRECATION")
@Composable
private fun AudioRoutePickerContent(
    currentRoute: Int,
    availableRoutes: Int,
    onSelectRoute: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val bluetoothLabel = stringResource(R.string.audio_route_bluetooth)
    val wiredHeadsetLabel = stringResource(R.string.audio_route_wired_headset)
    val earpieceLabel = stringResource(R.string.audio_route_earpiece)
    val speakerLabel = stringResource(R.string.audio_route_speaker)
    
    val routes = buildList {
        if (availableRoutes and android.telecom.CallAudioState.ROUTE_BLUETOOTH != 0) {
            add(Triple(android.telecom.CallAudioState.ROUTE_BLUETOOTH, Icons.Default.BluetoothAudio, bluetoothLabel))
        }
        if (availableRoutes and android.telecom.CallAudioState.ROUTE_WIRED_HEADSET != 0) {
            add(Triple(android.telecom.CallAudioState.ROUTE_WIRED_HEADSET, Icons.Default.Headset, wiredHeadsetLabel))
        }
        if (availableRoutes and android.telecom.CallAudioState.ROUTE_EARPIECE != 0) {
            add(Triple(android.telecom.CallAudioState.ROUTE_EARPIECE, Icons.Default.PhoneInTalk, earpieceLabel))
        }
        // Speaker is always available
        add(Triple(android.telecom.CallAudioState.ROUTE_SPEAKER, Icons.AutoMirrored.Filled.VolumeUp, speakerLabel))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .padding(bottom = 80.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            routes.forEach { (route, icon, label) ->
                val isSelected = currentRoute == route
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectRoute(route)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) iOSGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) iOSGreen else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = iOSGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
