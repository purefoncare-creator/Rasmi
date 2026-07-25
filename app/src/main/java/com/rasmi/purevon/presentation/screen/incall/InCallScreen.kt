package com.rasmi.purevon.presentation.screen.incall

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import android.content.res.Configuration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasmi.purevon.R
import com.rasmi.purevon.MainActivity
import com.rasmi.purevon.presentation.theme.*
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasmi.purevon.presentation.component.SimSelectorDialog

/**
 * InCall Screen - Shows active call UI with controls
 */
@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun InCallScreen(
    viewModel: InCallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // ✅ Update FloatingCallService with current state (including multi-call + conference)
    LaunchedEffect(
        uiState.callStartTime,
        uiState.isMuted,
        uiState.isSpeakerOn,
        uiState.contactName,
        uiState.isActive,
        uiState.callState,
        uiState.heldCall,
        uiState.waitingCall,
        uiState.hasWaitingCall,
        uiState.isConference,
        uiState.conferenceParticipants
    ) {
        val isRinging = uiState.isRinging && !uiState.isActive && !uiState.hasWaitingCall
        val isDialing = uiState.isOutgoing && !uiState.isActive && !uiState.isRinging
        
        // ✅ تحديد حالة المكالمة الثانية (أو المؤتمر)
        val secondCallState = when {
            uiState.isConference -> "conference"
            uiState.hasWaitingCall && uiState.waitingCall != null -> "waiting"
            uiState.heldCall != null -> "held"
            else -> null
        }
        val secondCallNumber = when {
            uiState.isConference -> uiState.conferenceParticipants.size.toString()
            else -> uiState.waitingCall ?: uiState.heldCall
        }
        val secondCallName = when {
            uiState.isConference -> null // سيتم عرض "Conference" في الـ overlay
            uiState.hasWaitingCall -> uiState.waitingCallName
            else -> uiState.heldCallName
        }
        
        com.rasmi.purevon.service.FloatingCallService.update(
            context = context,
            contactName = uiState.contactName,
            phoneNumber = uiState.phoneNumber,
            callStartTime = uiState.callStartTime,
            isMuted = uiState.isMuted,
            isSpeakerOn = uiState.isSpeakerOn,
            isRinging = isRinging,
            isDialing = isDialing,
            secondCallName = secondCallName,
            secondCallNumber = secondCallNumber,
            secondCallState = secondCallState,
            currentAudioRoute = uiState.currentAudioRoute
        )
    }
    
    // ✅ F4: إظهار/إخفاء الشريط العائم يُدار بالكامل من InCallActivity.onResume/onPause
    // لتجنب تضارب الأوامر (race condition) بين DisposableEffect و Activity lifecycle
    
    // ✅ Snackbar host state for user feedback
    val snackbarHostState = remember { SnackbarHostState() }
    
    // ✅ Observe UI events for feedback
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is UiEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Long,
                        actionLabel = "OK"
                    )
                }
                is UiEvent.ShowToast -> {
                    // Optional: could use Android Toast here
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is UiEvent.FinishActivity -> {
                    (context as? Activity)?.finish()
                }
                is UiEvent.MoveToBackground -> {
                    (context as? Activity)?.moveTaskToBack(true)
                }
            }
        }
    }
    
    BackHandler {
        // Minimize activity to background instead of closing
        (context as? Activity)?.moveTaskToBack(true)
    }
    
    // ✅ Box wrapper to position Snackbar at top
    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
        // ✅ واجهة موحدة للمكالمات - تتغير الأزرار حسب الحالة
        // مكالمة واردة أولى فقط (ليست مكالمة منتظرة أثناء مكالمة نشطة)
        // Fix #15: استخدام hasWaitingCall بدلاً من hasMultipleCalls
        // لأنه عند وجود مكالمة محتجزة + واردة جديدة:
        // hasMultipleCalls=true لكن hasWaitingCall=false → يجب إظهار أزرار القبول/الرفض
        val isIncomingCall = uiState.isRinging && !uiState.isActive && !uiState.hasWaitingCall
        
        ActiveCallUI(
            uiState = uiState,
            viewModel = viewModel,
            isIncomingRinging = isIncomingCall
        )
        
        // Show add call dialog
        if (uiState.showAddCallDialog) {
            ContactPickerDialog(
                title = stringResource(R.string.nav_tab_contacts),
                onDismiss = { viewModel.onEvent(InCallUiEvent.DismissAddCallDialog) },
                onContactSelected = { contact ->
                    viewModel.onEvent(InCallUiEvent.AddCallToContact(contact))
                },
                onManualNumber = { phoneNumber ->
                    viewModel.onEvent(InCallUiEvent.AddCallToNumber(phoneNumber))
                },
                contacts = uiState.contacts,
                showCallIcon = true,
                allowManualNumber = true
            )
        }
        
        // SIM picker for Add Call (ASK mode)
        if (uiState.showSimPickerForAddCall &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1
        ) {
            SimSelectorDialog(
                availableSims = uiState.availableSimsForAddCall,
                selectedSimId = null,
                title = stringResource(R.string.sim_picker_call_title),
                onSimSelected = { subscriptionId ->
                    viewModel.onEvent(
                        InCallUiEvent.SimSelectedForAddCall(
                            phoneNumber = uiState.pendingAddCallNumber,
                            subscriptionId = subscriptionId
                        )
                    )
                },
                onDismiss = { viewModel.onEvent(InCallUiEvent.DismissSimPickerForAddCall) }
            )
        }

        // ✅ حوار تذكير إعادة الاتصال
        if (uiState.showCallbackReminder) {
            CallbackReminderDialog(
                onDismiss = {
                    // ✅ إغلاق مباشر بدون toggle — فقط عند الضغط على إلغاء
                    viewModel.onEvent(InCallUiEvent.HideCallbackReminderDialog)
                },
                onSetReminder = { minutes ->
                    // ✅ الحدث يغلق الحوار بنفسه في ViewModel
                    viewModel.onEvent(InCallUiEvent.SetCallbackReminder(minutes))
                }
            )
        }
        }
        
        // ✅ Snackbar positioned at top
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )
    }
}


/**
 * UI for active call - Modern card-based design
 */
@Composable
private fun ActiveCallUI(
    uiState: InCallUiState,
    viewModel: InCallViewModel,
    isIncomingRinging: Boolean = false
) {
    val context = LocalContext.current
    val isLightTheme = !isSystemInDarkTheme()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isLightTheme) LightBackgroundAlt
                else MaterialTheme.colorScheme.background
            )
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ✅ Top Card - Contact Info (single call) or Dual Call View (multi-call) or Conference
        val hasSecondCall = uiState.heldCall != null || uiState.hasWaitingCall
        val isSingleCall = !hasSecondCall && !uiState.isConference
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLightTheme) Color.White else MaterialTheme.colorScheme.background
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            if (isSingleCall) {
                // ═══════════════════════════════════════════
                // ✨ SINGLE CALL: Centered Hero Layout
                // ═══════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // ── Hero Avatar with animated ring ──
                    val infiniteTransition = rememberInfiniteTransition(label = "avatar_ring")
                    val ringAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.7f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "ring_alpha"
                    )
                    val ringScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.06f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "ring_scale"
                    )
                    val ringColor = if (uiState.isActive) iOSGreen else MaterialTheme.colorScheme.primary
                    
                    Box(contentAlignment = Alignment.Center) {
                        // Outer glowing ring (only during active call)
                        if (uiState.isActive) {
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .scale(ringScale)
                                    .border(
                                        width = 2.5.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                ringColor.copy(alpha = ringAlpha),
                                                ringColor.copy(alpha = ringAlpha * 0.4f),
                                                ringColor.copy(alpha = ringAlpha)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            )
                        }
                        com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
                            size = 80.dp,
                            photoUri = uiState.contactPhotoUri
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // ── Contact Name + Quick Actions ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Block icon button
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { viewModel.onEvent(InCallUiEvent.ToggleBlock) },
                            shape = CircleShape,
                            color = if (uiState.isBlocked) iOSRed.copy(alpha = 0.15f)
                                   else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (uiState.isBlockLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = iOSRed
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (uiState.isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                                        contentDescription = if (uiState.isBlocked) stringResource(R.string.contacts_action_unblock) 
                                                            else stringResource(R.string.contacts_action_block),
                                        tint = if (uiState.isBlocked) iOSGreen else iOSRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Contact Name
                        Text(
                            text = uiState.contactName ?: uiState.phoneNumber,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Favorite icon button
                        val favColor = AmberLight
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { viewModel.onEvent(InCallUiEvent.ToggleFavorite) },
                            shape = CircleShape,
                            color = if (uiState.isFavorite) favColor.copy(alpha = 0.15f)
                                   else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (uiState.isFavoriteLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = favColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (uiState.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = if (uiState.isFavorite) stringResource(R.string.contacts_action_unfavorite)
                                                            else stringResource(R.string.contacts_action_favorite),
                                        tint = if (uiState.isFavorite) favColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // ── Phone number (only if different from name) ──
                    if (uiState.contactName != null && uiState.contactName != uiState.phoneNumber) {
                        Text(
                            text = uiState.phoneNumber,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // ── Status Pill ──
                    val statusColor = if (uiState.isActive) iOSGreen else MaterialTheme.colorScheme.primary
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_pulse"
                    )
                    
                    // Status Pill (duration/ringing/dialing)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pulsing dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(if (uiState.isActive) dotAlpha else 1f)
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = if (uiState.isActive) formatDuration(uiState.callDuration) else uiState.callState,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // ── Stats Row - compact icon chips ──
                    if (uiState.callStatistics != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MiniStatChip(
                                icon = Icons.Default.Phone,
                                value = uiState.callStatistics.totalCalls.toString(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            MiniStatChip(
                                icon = Icons.AutoMirrored.Filled.CallReceived,
                                value = uiState.callStatistics.incomingCalls.toString(),
                                color = iOSGreen
                            )
                            MiniStatChip(
                                icon = Icons.AutoMirrored.Filled.CallMade,
                                value = uiState.callStatistics.outgoingCalls.toString(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            MiniStatChip(
                                icon = Icons.AutoMirrored.Filled.CallMissed,
                                value = uiState.callStatistics.missedCalls.toString(),
                                color = iOSRed
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
            // ═══════════════════════════════════════════
            // Multi-call / Conference layout
            // ═══════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Active Call Row
                CallInfoRow(
                    name = uiState.contactName,
                    phoneNumber = uiState.phoneNumber,
                    statusText = if (uiState.isActive) formatDuration(uiState.callDuration) else uiState.callState,
                    isActive = uiState.isActive,
                    isHeld = false,
                    isWaiting = false,
                    photoUri = uiState.contactPhotoUri
                )
                
                if (uiState.isConference) {
                    // ═══════════════════════════════════════════
                    // 2a. CONFERENCE MODE: Show participants
                    // ═══════════════════════════════════════════
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                    
                    ConferenceCallView(
                        participants = uiState.conferenceParticipants,
                        participantNames = uiState.conferenceParticipantNames,
                        duration = formatDuration(uiState.callDuration),
                        isActive = uiState.isActive
                    )
                } else if (hasSecondCall) {
                    // ═══════════════════════════════════════════
                    // 2b. MULTI-CALL MODE: Second call + action buttons
                    // ═══════════════════════════════════════════
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                    
                    if (uiState.hasWaitingCall && uiState.waitingCall != null) {
                        // ── Waiting (Incoming) call ──
                        // Pulsing border for urgency
                        val infiniteTransition = rememberInfiniteTransition(label = "waiting_pulse")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = EaseInOut),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_alpha"
                        )
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = iOSGreen.copy(alpha = 0.08f),
                            border = BorderStroke(1.5.dp, iOSGreen.copy(alpha = pulseAlpha))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                CallInfoRow(
                                    name = uiState.waitingCallName,
                                    phoneNumber = uiState.waitingCall!!,
                                    statusText = stringResource(R.string.call_status_incoming),
                                    isActive = false,
                                    isHeld = false,
                                    isWaiting = true
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Accept / Reject buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Reject
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.onEvent(InCallUiEvent.RejectWaitingCall) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = iOSRed.copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CallEnd,
                                                contentDescription = null,
                                                tint = iOSRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.action_decline),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = iOSRed
                                            )
                                        }
                                    }
                                    
                                    // Accept (hold current + answer waiting)
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.onEvent(InCallUiEvent.AnswerAndHold) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = iOSGreen.copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = null,
                                                tint = iOSGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.action_accept),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = iOSGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (uiState.heldCall != null) {
                        // ── Held call ──
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                CallInfoRow(
                                    name = uiState.heldCallName,
                                    phoneNumber = uiState.heldCall ?: "",
                                    statusText = stringResource(R.string.call_status_on_hold),
                                    isActive = false,
                                    isHeld = true,
                                    isWaiting = false
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Swap / Merge buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Swap
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.onEvent(InCallUiEvent.SwapCalls) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SwapCalls,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.action_swap),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    
                                    // Merge
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.onEvent(InCallUiEvent.MergeCalls) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = iOSGreen.copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CallMerge,
                                                contentDescription = null,
                                                tint = iOSGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.action_merge),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = iOSGreen
                                            )
                                        }
                                    }

                                    // End Held Call
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.onEvent(InCallUiEvent.EndHeldCall) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = iOSRed.copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CallEnd,
                                                contentDescription = null,
                                                tint = iOSRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.action_end),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = iOSRed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Fallback empty state
                }
            }
            }
        }
        
        // ✅ Middle Card - Features (Notes, Last Call, Actions) OR Keypad
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLightTheme) Color.White else MaterialTheme.colorScheme.background
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = uiState.showKeypad,
                transitionSpec = {
                    if (targetState) {
                        (androidx.compose.animation.slideInVertically { it } +
                            androidx.compose.animation.fadeIn(tween(220)))
                            .togetherWith(androidx.compose.animation.slideOutVertically { -it } +
                                androidx.compose.animation.fadeOut(tween(180)))
                    } else {
                        (androidx.compose.animation.slideInVertically { -it } +
                            androidx.compose.animation.fadeIn(tween(220)))
                            .togetherWith(androidx.compose.animation.slideOutVertically { it } +
                                androidx.compose.animation.fadeOut(tween(180)))
                    }
                },
                label = "middle_card_content"
            ) { showingKeypad ->
                if (showingKeypad) {
                    InCallKeypadContent(
                        onDigitPressed = { digit ->
                            viewModel.onEvent(InCallUiEvent.SendDtmfTone(digit))
                        }
                    )
                } else {
                    MiddleCardContent(
                        uiState = uiState,
                        onEvent = viewModel::onEvent
                    )
                }
            }
        }
        
        // ✅ Bottom Card - Call Controls (6 buttons) or Incoming Call Buttons
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLightTheme) Color.White else MaterialTheme.colorScheme.background
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            if (isIncomingRinging) {
                    // ✅ أزرار المكالمة الواردة
                    IncomingCallButtons(
                        phoneNumber = uiState.phoneNumber,
                        onAnswer = { viewModel.onEvent(InCallUiEvent.AnswerCall) },
                        onReject = { viewModel.onEvent(InCallUiEvent.EndCall) },
                        onSilence = { viewModel.onEvent(InCallUiEvent.SilenceCall) },
                        onQuickMessage = { message -> viewModel.onEvent(InCallUiEvent.SendQuickMessage(message)) },
                        isSilenced = uiState.isSilenced
                    )
            } else {
                // ✅ أزرار المكالمة النشطة
                Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // First row - 3 buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallControlButton(
                        icon = if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        isActive = uiState.isMuted,
                        onClick = { viewModel.onEvent(InCallUiEvent.ToggleMute) }
                    )
                    
                    CallControlButton(
                        icon = Icons.Default.Dialpad,
                        onClick = { viewModel.onEvent(InCallUiEvent.ToggleKeypad) }
                    )
                    
                    // ✅ Smart Audio Route Button — changes icon based on BT/headset/speaker
                    AudioRouteButton(
                        currentRoute = uiState.currentAudioRoute,
                        availableRoutes = uiState.availableAudioRoutes,
                        showPicker = uiState.showAudioRoutePicker,
                        onToggleSpeaker = { viewModel.onEvent(InCallUiEvent.ToggleSpeaker) },
                        onShowPicker = { viewModel.onEvent(InCallUiEvent.ShowAudioRoutePicker) },
                        onDismissPicker = { viewModel.onEvent(InCallUiEvent.HideAudioRoutePicker) },
                        onSelectRoute = { route -> viewModel.onEvent(InCallUiEvent.SelectAudioRoute(route)) }
                    )
                }
                
                // Second row - 3 buttons including End Call
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallControlButton(
                        icon = Icons.Default.Add,
                        enabled = !uiState.isEndingCall,
                        onClick = { viewModel.onEvent(InCallUiEvent.ShowAddCall) },
                        backgroundColor = iOSBlue,
                        contentColor = Color.White
                    )
                    
                    // End call button (same size as others)
                    CallControlButton(
                        icon = Icons.Default.CallEnd,
                        isActive = false,
                        enabled = !uiState.isEndingCall,
                        onClick = { viewModel.onEvent(InCallUiEvent.EndCall) },
                        backgroundColor = iOSRed,
                        contentColor = Color.White
                    )
                    
                    CallControlButton(
                        icon = Icons.Default.Person,
                        onClick = {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                putExtra("navigate_to", "contacts")
                                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(intent)
                        },
                        backgroundColor = iOSBlue,
                        contentColor = Color.White
                    )
                }
            }
            }
        }
    }
}

