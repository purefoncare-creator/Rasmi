package com.rasmi.purevon.presentation.screen.incall

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.data.local.entity.ContactNoteEntity
import com.rasmi.purevon.presentation.theme.*

/**
 * ✅ محتوى البطاقة الوسطى - 3 تبويبات
 */
@Composable
internal fun MiddleCardContent(
    uiState: InCallUiState,
    onEvent: (InCallUiEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row
        TabRow(
            selectedTabIndex = uiState.middleCardTab,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = uiState.middleCardTab == 0,
                onClick = { onEvent(InCallUiEvent.ChangeMiddleCardTab(0)) },
                icon = { 
                    Icon(
                        imageVector = Icons.Default.Note,
                        contentDescription = null
                    ) 
                }
            )
            Tab(
                selected = uiState.middleCardTab == 1,
                onClick = { onEvent(InCallUiEvent.ChangeMiddleCardTab(1)) },
                icon = { 
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null
                    ) 
                }
            )
            Tab(
                selected = uiState.middleCardTab == 2,
                onClick = { onEvent(InCallUiEvent.ChangeMiddleCardTab(2)) },
                icon = { 
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null
                    ) 
                }
            )
        }
        
        // Tab Content
        when (uiState.middleCardTab) {
            0 -> NotesTab(
                notes = uiState.callNotes,
                existingNotes = uiState.existingNotes,
                showNewNoteInput = uiState.showNewNoteInput,
                onNotesChange = { onEvent(InCallUiEvent.UpdateCallNotes(it)) },
                onSaveNote = { onEvent(InCallUiEvent.SaveCallNote) },
                onShowNewNoteInput = { onEvent(InCallUiEvent.ShowNewNoteInput) },
                onHideNewNoteInput = { onEvent(InCallUiEvent.HideNewNoteInput) }
            )
            1 -> LastCallTab(
                lastCallStatus = uiState.lastCallStatus,
                lastCallType = uiState.lastCallType,
                lastCallTime = uiState.lastCallTime
            )
            2 -> ActionsTab(
                phoneNumber = uiState.phoneNumber,
                onEvent = onEvent
            )
        }
    }
}

/**
 * تبويب الملاحظات:
 * - إذا وُجدت ملاحظات سابقة → عرضها مع زر + لإضافة جديدة
 * - إذا لا توجد ملاحظات أو المستخدم ضغط + → عرض حقل الإدخال
 */
@Composable
private fun NotesTab(
    notes: String,
    existingNotes: List<ContactNoteEntity>,
    showNewNoteInput: Boolean,
    onNotesChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onShowNewNoteInput: () -> Unit,
    onHideNewNoteInput: () -> Unit
) {
    if (existingNotes.isNotEmpty() && !showNewNoteInput) {
        // عرض الملاحظات السابقة
        ExistingNotesView(
            notes = existingNotes,
            onAddNew = onShowNewNoteInput
        )
    } else {
        // حقل إدخال ملاحظة جديدة
        NewNoteInputView(
            notes = notes,
            showBackButton = existingNotes.isNotEmpty(),
            onNotesChange = onNotesChange,
            onSaveNote = onSaveNote,
            onBack = onHideNewNoteInput
        )
    }
}

@Composable
private fun ExistingNotesView(
    notes: List<ContactNoteEntity>,
    onAddNew: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                NoteItem(note = note)
            }
        }

        // زر + في الزاوية السفلية
        SmallFloatingActionButton(
            onClick = onAddNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.action_add_note),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NoteItem(note: ContactNoteEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = note.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatCallTime(note.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun NewNoteInputView(
    notes: String,
    showBackButton: Boolean,
    onNotesChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (showBackButton) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.action_add_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = { Text("...") },
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSaveNote,
                modifier = Modifier.weight(1f),
                enabled = notes.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_save))
            }

            OutlinedButton(
                onClick = { onNotesChange("") },
                modifier = Modifier.weight(1f),
                enabled = notes.isNotBlank()
            ) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
}

/**
 * ✅ تبويب آخر مكالمة
 */
@Composable
private fun LastCallTab(
    lastCallStatus: String?,
    lastCallType: Int?,
    lastCallTime: Long?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (lastCallStatus != null && lastCallTime != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = when (lastCallType) {
                        android.provider.CallLog.Calls.MISSED_TYPE, android.provider.CallLog.Calls.REJECTED_TYPE -> Icons.Default.CallMissed
                        android.provider.CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade
                        else -> Icons.Default.CallReceived
                    },
                    contentDescription = null,
                    tint = when (lastCallType) {
                        android.provider.CallLog.Calls.MISSED_TYPE, android.provider.CallLog.Calls.REJECTED_TYPE -> Color.Red
                        android.provider.CallLog.Calls.OUTGOING_TYPE -> iOSGreen
                        else -> MaterialBlue500
                    },
                    modifier = Modifier.size(48.dp)
                )
                
                Text(
                    text = lastCallStatus,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = formatCallTime(lastCallTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.last_call_no_history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * ✅ تبويب الإجراءات السريعة
 */
@Composable
private fun ActionsTab(
    phoneNumber: String,
    onEvent: (InCallUiEvent) -> Unit
) {
    val context = LocalContext.current
    val isLightTheme = !isSystemInDarkTheme()
    
    // التحقق من تثبيت واتساب وتيليجرام
    val whatsAppInstalled = remember(context) {
        isPackageInstalled(context, "com.whatsapp") ||
        isPackageInstalled(context, "com.whatsapp.w4b")
    }
    val telegramInstalled = remember(context) {
        isPackageInstalled(context, "org.telegram.messenger") ||
        isPackageInstalled(context, "org.thunderdog.challegram")
    }
    
    val cleanNumber = remember(phoneNumber) {
        phoneNumber.replace(Regex("[^0-9+]"), "")
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // صف أول: تذكير + واتساب + تيليجرام
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // T1: تذكير إعادة الاتصال
            ActionCircleButton(
                icon = rememberVectorPainter(Icons.Default.AccessTime),
                isLightTheme = isLightTheme,
                activeColor = iOSBlue,
                onClick = { onEvent(InCallUiEvent.ShowCallbackReminderDialog) }
            )
            
            // T2: واتساب
            ActionCircleButton(
                icon = painterResource(R.drawable.ic_whatsapp),
                isLightTheme = isLightTheme,
                isPlaceholder = !whatsAppInstalled,
                tintIcon = false,
                activeColor = WhatsAppGreen,
                onClick = {
                    if (whatsAppInstalled) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://wa.me/$cleanNumber")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.action_whatsapp_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            
            // T3: تيليجرام
            ActionCircleButton(
                icon = painterResource(R.drawable.ic_telegram),
                isLightTheme = isLightTheme,
                isPlaceholder = !telegramInstalled,
                tintIcon = false,
                activeColor = TelegramBlue,
                onClick = {
                    if (telegramInstalled) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("tg://resolve?phone=$cleanNumber")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val altIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://t.me/$cleanNumber")
                                }
                                context.startActivity(altIntent)
                            } catch (e2: Exception) {
                                Toast.makeText(context, context.getString(R.string.action_telegram_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
        
        // صف ثاني: مكان محجوز + نسخ الرقم + مكان محجوز
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // T4: إضافة إلى جهات الاتصال
            ActionCircleButton(
                icon = rememberVectorPainter(Icons.Default.PersonAdd),
                isLightTheme = isLightTheme,
                activeColor = iOSBlue,
                onClick = {
                    val intent = Intent(context, com.rasmi.purevon.MainActivity::class.java).apply {
                        putExtra("navigate_to", "add_contact")
                        putExtra("phone_number", cleanNumber)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(intent)
                }
            )
            
            // T5: نسخ الرقم
            ActionCircleButton(
                icon = rememberVectorPainter(Icons.Default.ContentCopy),
                isLightTheme = isLightTheme,
                activeColor = iOSBlue,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("phone_number", phoneNumber)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.action_number_copied), Toast.LENGTH_SHORT).show()
                }
            )
            
            // T6: إرسال رسالة نصية
            ActionCircleButton(
                icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Message),
                isLightTheme = isLightTheme,
                activeColor = iOSBlue,
                onClick = {
                    val intent = Intent(context, com.rasmi.purevon.MainActivity::class.java).apply {
                        putExtra("navigate_to", "new_conversation")
                        putExtra("phone_number", cleanNumber)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

/**
 * Helper function to check if a package is installed
 */
private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * ✅ زر دائري لقسم الإجراءات
 */
@Composable
private fun ActionCircleButton(
    icon: Painter,
    isLightTheme: Boolean,
    isPlaceholder: Boolean = false,
    tintIcon: Boolean = true,
    activeColor: Color = iOSBlue,
    onClick: () -> Unit
) {
    val bgColor = if (isPlaceholder) {
        if (isLightTheme) LightSurface else DarkBackground
    } else {
        if (isLightTheme) activeColor.copy(alpha = 0.1f) else activeColor.copy(alpha = 0.15f)
    }
    
    val contentColor = if (isPlaceholder) {
        if (isLightTheme) LightTertiary else DarkTertiary
    } else {
        activeColor
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val audioManager = LocalContext.current.getSystemService(AudioManager::class.java)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(enabled = !isPlaceholder, onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
                    onClick()
                }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (tintIcon) contentColor else androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * ✅ Format call time to readable format
 */
internal fun formatCallTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)
    
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "منذ $minutes دقيقة"
        hours < 24 -> "منذ $hours ساعة"
        days < 7 -> "منذ $days يوم"
        else -> {
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            dateFormat.format(java.util.Date(timestamp))
        }
    }
}
