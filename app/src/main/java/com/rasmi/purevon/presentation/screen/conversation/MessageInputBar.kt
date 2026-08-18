package com.rasmi.purevon.presentation.screen.conversation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.presentation.theme.*

@Composable
fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onFilePickClick: () -> Unit = {},
    onImagePickClick: () -> Unit = {},
    onVideoPickClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onContactPickClick: () -> Unit = {},
    onLocationPickClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onTemplateClick: () -> Unit = {},
    attachments: List<com.rasmi.purevon.presentation.screen.conversation.AttachmentData> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    isRecording: Boolean = false,
    audioRecordingFile: String? = null,
    recordingDuration: Long = 0,
    recordingAmplitudes: List<Float> = emptyList(),
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onSendAudioMessage: () -> Unit = {},
    smsCharacterCounter: com.rasmi.purevon.util.message.SmsCharacterCounter? = null,
    isFetchingLocation: Boolean = false
) {

    // ✅ حالة إظهار لوحة المرفقات
    var showAttachmentPanel by remember { mutableStateOf(false) }
    
    val isDarkTheme = isSystemInDarkTheme()
    val barColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }

    // ✅ Input Bar - يلتصق مباشرة بالكيبورد
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barColor)
            // ✅ لا imePadding ولا navigationBarsPadding - Box الخارجي يتحكم بالكل
    ) {
        // Subtle top divider
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
            thickness = 0.5.dp
        )
        
        // ═══ Recording Bar — shown while actively recording ═══
        if (isRecording) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel (trash) button
                    IconButton(
                        onClick = onCancelRecording,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Red.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.msg_cd_cancel_recording),
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Pulsing red dot
                    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_pulse"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = pulseAlpha))
                    )

                    // Timer
                    Text(
                        text = String.format(java.util.Locale.getDefault(), 
                            "%d:%02d",
                            (recordingDuration / 1000) / 60,
                            (recordingDuration / 1000) % 60
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Real waveform bars
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val barsToShow = recordingAmplitudes.takeLast(30)
                        val padded = if (barsToShow.size < 30) {
                            List(30 - barsToShow.size) { 0.05f } + barsToShow
                        } else barsToShow
                        padded.forEach { amp ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(fraction = (amp * 0.8f + 0.1f).coerceIn(0.05f, 1f))
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                )
                            }
                        }
                    }

                    // Stop button (■) — stops recording and shows preview
                    IconButton(
                        onClick = onStopRecording,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialRed600, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.msg_cd_stop_recording),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // ═══ Audio Preview — shown after stop, before send ═══
        if (audioRecordingFile != null && !isRecording) {
            // Convert file path to proper file:// URI for AudioPlayerComponent
            val audioPreviewUri = remember(audioRecordingFile) {
                if (audioRecordingFile.startsWith("content://") || audioRecordingFile.startsWith("file://")) {
                    audioRecordingFile
                } else {
                    "file://$audioRecordingFile"
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete button
                    IconButton(
                        onClick = onCancelRecording,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Red.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.msg_cd_delete_recording),
                            tint = Color.Red,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Audio player (play/pause + waveform + duration)
                    com.rasmi.purevon.presentation.component.AudioPlayerComponent(
                        audioUri = audioPreviewUri,
                        durationMs = recordingDuration,
                        modifier = Modifier.weight(1f),
                        compact = true
                    )

                    // Send button
                    IconButton(
                        onClick = onSendAudioMessage,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.msg_cd_send_recording),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        // Attachments preview - horizontal scrollable row
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEach { attachment ->
                    Surface(
                        modifier = Modifier
                            .width(100.dp)
                            .height(100.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (attachment.isImage) {
                                AsyncImage(
                                    model = Uri.parse(attachment.uri),
                                    contentDescription = attachment.fileName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else if (attachment.mimeType?.startsWith("video/") == true) {
                                // 🎥 Video thumbnail with play icon overlay
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = Uri.parse(attachment.uri),
                                        contentDescription = attachment.fileName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    // Play icon overlay
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            } else if (attachment.mimeType?.startsWith("audio/") == true) {
                                // 🎵 Audio file icon
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialOrange,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = attachment.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        fontSize = 10.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            } else if (attachment.mimeType?.contains("vcard", ignoreCase = true) == true ||
                                       attachment.fileName.endsWith(".vcf", ignoreCase = true)) {
                                // ✅ vCard contact preview
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialCyan500,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = attachment.fileName.replace(".vcf", "").replace("_", " "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        fontSize = 10.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = attachment.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        fontSize = 10.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                            
                            // Remove button
                            IconButton(
                                onClick = { onRemoveAttachment(attachment.uri) },
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.msg_cd_remove_attachment),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // ✅ Attachment Panel - يظهر عند الضغط على زر المرفقات
        AnimatedVisibility(
            visible = showAttachmentPanel,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 📷 كاميرا
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { showAttachmentPanel = false; onCameraClick() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialBlue500.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = stringResource(R.string.msg_attachment_camera),
                                tint = MaterialBlue500,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // 🖼️ صور
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { showAttachmentPanel = false; onImagePickClick() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialGreen500.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = stringResource(R.string.msg_attachment_image),
                                tint = MaterialGreen500,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // 🎥 فيديو
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { showAttachmentPanel = false; onVideoPickClick() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialOrange.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = stringResource(R.string.msg_attachment_video),
                                tint = MaterialOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // 📎 ملف
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { showAttachmentPanel = false; onFilePickClick() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialPurple500.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = stringResource(R.string.msg_attachment_file),
                                tint = MaterialPurple500,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // 👤 جهة اتصال
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { showAttachmentPanel = false; onContactPickClick() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialCyan500.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = stringResource(R.string.msg_attachment_contact),
                                tint = MaterialCyan500,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // 📍 موقع
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { if (!isFetchingLocation) { showAttachmentPanel = false; onLocationPickClick() } },
                            enabled = !isFetchingLocation,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialRed500.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                        ) {
                            if (isFetchingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialRed500
                                )
                            } else {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = stringResource(R.string.msg_attachment_location),
                                    tint = MaterialRed500,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Main Input Row
        // Hide when recording is active or audio preview is showing
        if (!isRecording && audioRecordingFile == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            
            // Text Field Container - Telegram rounded style
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 46.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.58f else 0.82f),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 📎 Attachment button (left side inside text field)
                    IconButton(
                        onClick = { showAttachmentPanel = !showAttachmentPanel },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (showAttachmentPanel) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = stringResource(R.string.msg_cd_attachments),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    
                    // Text Input
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        enabled = enabled,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Send
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = {
                                if (text.isNotBlank()) {
                                    onSend()
                                    // ✅ FIX #48: Keep keyboard open for fast messaging
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.conversation_message_hint),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        },
                        maxLines = 7
                    )
                    
                    // 📅 Schedule button
                    IconButton(
                        onClick = onScheduleClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = stringResource(R.string.msg_cd_schedule),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 📝 Template button
                    IconButton(
                        onClick = onTemplateClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = stringResource(R.string.msg_cd_templates),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // 🎙️ Mic / Send button — simple tap to start recording
            if (text.isBlank() && attachments.isEmpty()) {
                IconButton(
                    onClick = onStartRecording,
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = stringResource(R.string.msg_cd_voice_recording),
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            } else {
                // Show send button when there's text or attachments
                IconButton(
                    onClick = {
                        onSend()
                        // ✅ FIX #48: Keep keyboard open for fast messaging
                    },
                    enabled = enabled && (text.isNotBlank() || attachments.isNotEmpty()),
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            color = if (enabled && (text.isNotBlank() || attachments.isNotEmpty()))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    if (!enabled) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.msg_cd_send),
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
        } // end if (!isRecording && audioRecordingFile == null)
        
        // ✅ جديد: SMS Character Counter (displayed only when needed)
        smsCharacterCounter?.let { counter ->
            com.rasmi.purevon.presentation.component.SmsCharCounter(
                text = text,
                characterCounter = counter,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
