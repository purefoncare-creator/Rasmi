package com.rasmi.purevon.presentation.screen.contactdetail

import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.ContactNote
import com.rasmi.purevon.domain.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.presentation.theme.*

/**
 * Helper function to check if a package is installed
 */
private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        android.util.Log.e("ContactDetail", "Checking package: $packageName")
        context.packageManager.getPackageInfo(packageName, 0)
        android.util.Log.e("ContactDetail", "Package $packageName FOUND")
        true
    } catch (e: Exception) {
        android.util.Log.e("ContactDetail", "Package $packageName NOT FOUND: ${e.message}")
        false
    }
}

/**
 * Check if a contact has WhatsApp account
 */
private fun contactHasWhatsApp(context: Context, phoneNumber: String): Boolean {
    return try {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        android.util.Log.e("ContactDetail", "=== WhatsApp Query ===")
        android.util.Log.e("ContactDetail", "Original: $phoneNumber, Clean: $cleanNumber")
        
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf("vnd.android.cursor.item/vnd.com.whatsapp.profile", "%$cleanNumber%"),
            null
        )
        
        val count = cursor?.count ?: 0
        android.util.Log.e("ContactDetail", "WhatsApp cursor count: $count")
        
        if (count > 0 && cursor != null) {
            cursor.moveToFirst()
            for (i in 0 until cursor.columnCount) {
                val colName = cursor.getColumnName(i)
                val colValue = cursor.getString(i)
                android.util.Log.e("ContactDetail", "  Column[$i]: $colName = $colValue")
            }
        }
        
        val hasWhatsApp = count > 0
        cursor?.close()
        hasWhatsApp
    } catch (e: Exception) {
        android.util.Log.e("ContactDetail", "WhatsApp query exception: ${e.message}", e)
        false
    }
}

/**
 * Check if a contact has Telegram account
 */
private fun contactHasTelegram(context: Context, phoneNumber: String): Boolean {
    return try {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        android.util.Log.e("ContactDetail", "=== Telegram Query ===")
        android.util.Log.e("ContactDetail", "Original: $phoneNumber, Clean: $cleanNumber")
        
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf("vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile", "%$cleanNumber%"),
            null
        )
        
        val count = cursor?.count ?: 0
        android.util.Log.e("ContactDetail", "Telegram cursor count: $count")
        
        if (count > 0 && cursor != null) {
            cursor.moveToFirst()
            for (i in 0 until cursor.columnCount) {
                val colName = cursor.getColumnName(i)
                val colValue = cursor.getString(i)
                android.util.Log.e("ContactDetail", "  Column[$i]: $colName = $colValue")
            }
        }
        
        val hasTelegram = count > 0
        cursor?.close()
        hasTelegram
    } catch (e: Exception) {
        android.util.Log.e("ContactDetail", "Telegram query exception: ${e.message}", e)
        false
    }
}

/**
 * Contact Detail Screen - Modern, compact, professional design
 * with inline editing support, call statistics, and notes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contact: Contact,
    notes: List<ContactNote> = emptyList(),
    callStatistics: CallStatistics = CallStatistics(),
    recentCalls: List<com.rasmi.purevon.domain.model.CallLog> = emptyList(),
    showAllCalls: Boolean = false,
    onToggleShowAllCalls: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onDeleteContact: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBlockContact: () -> Unit = {},
    onEditContact: () -> Unit = {},
    onSaveContact: (String, String, String, String?, String?) -> Unit,
    onDeleteNote: ((Long) -> Unit)? = null,
    onCall: ((String) -> Unit)? = null,
    onMessage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    
    // ✅ معالجة زر الرجوع - إلغاء التعديل إذا كان في وضع التحرير
    BackHandler(enabled = isEditing) {
        // إلغاء التحرير والعودة للوضع العادي
        isEditing = false
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    
    // Editable fields
    var editFirstName by remember { mutableStateOf("") }
    var editLastName by remember { mutableStateOf("") }
    var editPhoneNumber by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var editCompany by remember { mutableStateOf("") }
    
    // Initialize edit fields when entering edit mode
    LaunchedEffect(isEditing) {
        if (isEditing) {
            // Get actual firstName and lastName from system
            val (firstName, lastName) = getContactStructuredName(context, contact.id)
            editFirstName = firstName
            editLastName = lastName
            editPhoneNumber = contact.phoneNumber
            editEmail = contact.email ?: ""
            editCompany = contact.company ?: ""
        }
    }
    
    // Validation
    val isValid = editFirstName.isNotBlank() && editPhoneNumber.isNotBlank()
    
    // Actions
    fun makeCall() {
        com.rasmi.purevon.util.PhoneUtil.playClickSound(context)
        if (onCall != null) {
            onCall(contact.phoneNumber)
        } else {
            try {
                com.rasmi.purevon.util.PhoneUtil.makeCall(context, contact.phoneNumber)
            } catch (e: Exception) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.fromParts("tel", contact.phoneNumber, null)
                }
                context.startActivity(intent)
            }
        }
    }
    
    fun sendMessage() {
        if (onMessage != null) {
            onMessage(contact.phoneNumber)
        } else {
            try {
                com.rasmi.purevon.util.PhoneUtil.sendMessage(context, contact.phoneNumber)
            } catch (e: Exception) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.fromParts("smsto", contact.phoneNumber, null)
                }
                context.startActivity(intent)
            }
        }
    }
    
    fun sendEmail() {
        contact.email?.let { email ->
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
            }
            context.startActivity(intent)
        }
    }
    
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("contact", text)
        clipboard.setPrimaryClip(clip)
        com.rasmi.purevon.util.SoundManager(context).playCopySound()
    }
    
    fun saveContact() {
        if (!isValid) return
        
        keyboardController?.hide()
        
        onSaveContact(
            editFirstName.trim(),
            editLastName.trim(),
            editPhoneNumber.trim(),
            editEmail.trim().takeIf { it.isNotEmpty() },
            editCompany.trim().takeIf { it.isNotEmpty() }
        )
    }

    val isLightTheme = !isSystemInDarkTheme()
    val cardColor = if (isLightTheme) Color.White else MaterialTheme.colorScheme.background

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (isLightTheme) LightBackgroundAlt else MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Top Edit Row (only visible when editing) ──────────────────────────────────
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isEditing = false
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.contact_detail_cancel_editing)
                        }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.contact_detail_edit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { saveContact() },
                        enabled = isValid && !isSaving,
                        modifier = Modifier.semantics {
                            contentDescription = if (isSaving) context.getString(R.string.contact_detail_saving) else context.getString(R.string.contact_detail_save_changes)
                        }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                stringResource(R.string.contact_detail_save),
                                fontWeight = FontWeight.Bold,
                                color = if (isValid) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // ── Card 1: Contact Info + Actions ────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isEditing) {
                        // Edit mode: name + all editable fields
                        EditableContactHeader(
                            firstName = editFirstName,
                            lastName = editLastName,
                            onFirstNameChange = { editFirstName = it },
                            onLastNameChange = { editLastName = it },
                            focusManager = focusManager
                        )
                        EditablePhoneSection(
                            phoneNumber = editPhoneNumber,
                            onPhoneChange = { editPhoneNumber = it },
                            focusManager = focusManager
                        )
                        EditableEmailSection(
                            email = editEmail,
                            onEmailChange = { editEmail = it },
                            focusManager = focusManager
                        )
                        EditableCompanySection(
                            company = editCompany,
                            onCompanyChange = { editCompany = it },
                            keyboardController = keyboardController,
                            focusManager = focusManager
                        )
                    } else {
                        // ── Avatar alongside Name + Phone ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Avatar
                            com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
                                size = 72.dp,
                                photoUri = contact.photoUri
                            )

                            // Name + Company + Phone (copyable)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                contact.company?.let { company ->
                                    Text(
                                        text = company,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        copyToClipboard(contact.phoneNumber)
                                    }
                                ) {
                                    Text(
                                        text = contact.phoneNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        // ── Action icons: Favorite / Edit / Delete / Block / Share ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Favorite
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.semantics {
                                    contentDescription = if (contact.isFavorite) context.getString(R.string.contact_detail_remove_favorite) else context.getString(R.string.contact_detail_add_favorite)
                                }
                            ) {
                                Icon(
                                    if (contact.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = null,
                                    tint = if (contact.isFavorite) FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            // Edit
                            IconButton(
                                onClick = onEditContact,
                                modifier = Modifier.semantics { contentDescription = context.getString(R.string.contact_detail_edit_action) }
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            // Delete
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.semantics { contentDescription = context.getString(R.string.contact_detail_delete_action) }
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            // Block / Unblock
                            IconButton(
                                onClick = onBlockContact,
                                modifier = Modifier.semantics {
                                    contentDescription = if (contact.isBlocked) context.getString(R.string.contact_detail_unblock) else context.getString(R.string.contact_detail_block)
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = if (contact.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            // Share
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${contact.displayName}\n${contact.phoneNumber}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.contact_detail_share)))
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        // ── Communication buttons: Call / Message / WhatsApp / Telegram ──
                        EnhancedQuickActionsRow(
                            phoneNumber = contact.phoneNumber,
                            onCall = { makeCall() },
                            onMessage = { sendMessage() }
                        )
                    }
                }
            }

            // ── Card 2: Call History ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.contact_detail_tab_history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (callStatistics.totalCalls > 0) {
                        CallStatisticsSection(statistics = callStatistics)
                    }
                    if (recentCalls.isNotEmpty()) {
                        RecentCallsSection(
                            calls = recentCalls,
                            showAll = showAllCalls,
                            onToggleShowAll = onToggleShowAllCalls,
                            onCallBack = { phoneNumber ->
                                if (onCall != null) onCall(phoneNumber) else makeCall()
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.contact_detail_no_history),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── Card 3: Notes ─────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.contact_detail_tab_notes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (notes.isNotEmpty()) {
                            Badge {
                                Text(
                                    notes.size.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    if (notes.isNotEmpty()) {
                        NotesSection(notes = notes, onDeleteNote = onDeleteNote)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.contact_detail_no_notes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    stringResource(R.string.contact_detail_delete_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    context.getString(R.string.contact_detail_delete_message, contact.displayName),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteContact()
                    }
                ) {
                    Text(stringResource(R.string.contact_detail_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Notes Dialog
    if (showNotesDialog) {
        ContactNotesDialog(
            notes = notes,
            contactName = contact.displayName,
            onDismiss = { showNotesDialog = false },
            onDeleteNote = onDeleteNote
        )
    }
}

/**
 * ✅ حوار عرض ملاحظات جهة الاتصال
 */
@Composable
private fun ContactNotesDialog(
    notes: List<ContactNote>,
    contactName: String,
    onDismiss: () -> Unit,
    onDeleteNote: ((Long) -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Note,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.note_dialog_title, contactName),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.note_dialog_count, notes.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.NoteAlt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.note_dialog_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes.size) { index ->
                        val note = notes[index]
                        NoteCard(
                            note = note,
                            onDelete = if (onDeleteNote != null) {
                                { onDeleteNote(note.id) }
                            } else null
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(R.string.note_dialog_close))
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * ✅ بطاقة عرض الملاحظة الواحدة
 */
@Composable
private fun NoteCard(
    note: ContactNote,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: تاريخ ووقت + نوع المكالمة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (note.isIncoming) Icons.AutoMirrored.Filled.CallReceived 
                        else Icons.AutoMirrored.Filled.CallMade,
                        contentDescription = null,
                        tint = if (note.isIncoming) MaterialBlue500 else MaterialGreen500,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        formatNoteDateTime(note.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (note.callDuration > 0) {
                        Text(
                            "• ${formatNoteDuration(note.callDuration)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.note_delete_cd),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // محتوى الملاحظة
            Text(
                note.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * تنسيق التاريخ والوقت
 */
private fun formatNoteDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * تنسيق مدة المكالمة
 */
private fun formatNoteDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) "${minutes}د ${secs}ث" else "${secs}ث"
}

/**
 * Editable Contact Header with name fields
 */
@Composable
private fun EditableContactHeader(
    firstName: String,
    lastName: String,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    modifier: Modifier = Modifier
) {
    val initials = buildString {
        if (firstName.isNotBlank()) append(firstName.first().uppercaseChar())
        if (lastName.isNotBlank()) append(lastName.first().uppercaseChar())
    }.ifEmpty { "+" }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
            size = 100.dp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Name fields
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = firstName,
                onValueChange = onFirstNameChange,
                label = { Text(stringResource(R.string.contact_detail_first_name)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Right) }
                )
            )
            
            OutlinedTextField(
                value = lastName,
                onValueChange = onLastNameChange,
                label = { Text(stringResource(R.string.contact_detail_last_name)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )
        }
    }
}

/**
 * Editable Phone Section
 */
@Composable
private fun EditablePhoneSection(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    modifier: Modifier = Modifier
) {
    EditableSectionCard(
        title = stringResource(R.string.contact_detail_phone),
        icon = Icons.Outlined.Phone,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.contact_detail_phone_number)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )
    }
}

/**
 * Editable Email Section
 */
@Composable
private fun EditableEmailSection(
    email: String,
    onEmailChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    modifier: Modifier = Modifier
) {
    EditableSectionCard(
        title = stringResource(R.string.contact_detail_email),
        icon = Icons.Outlined.Email,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.contact_detail_email_address)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )
    }
}

/**
 * Editable Company Section
 */
@Composable
private fun EditableCompanySection(
    company: String,
    onCompanyChange: (String) -> Unit,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    focusManager: androidx.compose.ui.focus.FocusManager,
    modifier: Modifier = Modifier
) {
    EditableSectionCard(
        title = stringResource(R.string.contact_detail_company),
        icon = Icons.Outlined.Business,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = company,
            onValueChange = onCompanyChange,
            label = { Text(stringResource(R.string.contact_detail_company_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            )
        )
    }
}

/**
 * Editable Section Card
 */
@Composable
private fun EditableSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 11.sp
            )
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

// updateContactInSystem removed in favor of Repository implementation


private fun getRawContactId(context: Context, contactId: Long): Long? {
    val projection = arrayOf(ContactsContract.RawContacts._ID)
    val selection = "${ContactsContract.RawContacts.CONTACT_ID} = ?"
    val selectionArgs = arrayOf(contactId.toString())
    
    context.contentResolver.query(
        ContactsContract.RawContacts.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
        }
    }
    return null
}

/**
 * Get structured name (first name and last name) from system
 */
private fun getContactStructuredName(context: Context, contactId: Long): Pair<String, String> {
    val rawContactId = getRawContactId(context, contactId) ?: return Pair("", "")
    
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
        ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME
    )
    val selection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val selectionArgs = arrayOf(
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
    )
    
    context.contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val givenNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
            val familyNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
            val displayNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)
            
            val givenName = if (givenNameIndex >= 0) cursor.getString(givenNameIndex) else null
            val familyName = if (familyNameIndex >= 0) cursor.getString(familyNameIndex) else null
            val displayName = if (displayNameIndex >= 0) cursor.getString(displayNameIndex) else null
            
            // If structured name exists, use it
            if (!givenName.isNullOrBlank() || !familyName.isNullOrBlank()) {
                return Pair(givenName ?: "", familyName ?: "")
            }
            
            // Fallback: split display name
            if (!displayName.isNullOrBlank()) {
                val parts = displayName.split(" ", limit = 2)
                return Pair(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "")
            }
        }
    }
    
    // Final fallback: return empty strings
    return Pair("", "")
}

/**
 * Contact Header with Avatar and Name
 */
@Composable
private fun ContactHeader(
    contact: Contact,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
            size = 100.dp,
            photoUri = contact.photoUri
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Name
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        // Company if available
        contact.company?.let { company ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = company,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Quick Action Buttons Row
 */
@Composable
private fun QuickActionsRow(
    onCall: () -> Unit,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Message),
            label = stringResource(R.string.contact_detail_message),
            color = iOSBlue,
            onClick = onMessage
        )
        
        QuickActionButton(
            icon = rememberVectorPainter(Icons.Filled.Call),
            label = stringResource(R.string.contact_detail_call),
            color = iOSGreen,
            onClick = onCall
        )
    }
}

/**
 * Enhanced Quick Action Buttons Row with WhatsApp and Telegram
 */
@Composable
private fun EnhancedQuickActionsRow(
    phoneNumber: String,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Check if WhatsApp/Telegram apps are installed on device
    val whatsAppInstalled = remember(context) {
        val result = isPackageInstalled(context, "com.whatsapp") || 
        isPackageInstalled(context, "com.whatsapp.w4b")
        android.util.Log.e("ContactDetail", "=== CHECKING APPS ===")
        android.util.Log.e("ContactDetail", "WhatsApp installed: $result")
        result
    }
    
    val telegramInstalled = remember(context) {
        val result = isPackageInstalled(context, "org.telegram.messenger") || 
        isPackageInstalled(context, "org.thunderdog.challegram")
        android.util.Log.e("ContactDetail", "Telegram installed: $result")
        result
    }
    
    // Show WhatsApp/Telegram buttons if apps are installed
    // Note: We don't check ContactsContract because it requires sync to be enabled
    val hasWhatsApp = whatsAppInstalled
    val hasTelegram = telegramInstalled
    
    android.util.Log.e("ContactDetail", "=== FINAL RESULT ===")
    android.util.Log.e("ContactDetail", "Show WhatsApp button: $hasWhatsApp")
    android.util.Log.e("ContactDetail", "Show Telegram button: $hasTelegram")
    
    val buttonCount = 2 + (if (hasWhatsApp) 1 else 0) + (if (hasTelegram) 1 else 0)
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Message),
            label = stringResource(R.string.contact_detail_message),
            color = iOSBlue,
            onClick = onMessage
        )
        
        QuickActionButton(
            icon = rememberVectorPainter(Icons.Filled.Call),
            label = stringResource(R.string.contact_detail_call),
            color = iOSGreen,
            onClick = onCall
        )
        
        if (hasWhatsApp) {
            QuickActionButton(
                icon = painterResource(R.drawable.ic_whatsapp),
                label = stringResource(R.string.contact_detail_whatsapp),
                color = WhatsAppGreen,
                tintIcon = false,
                onClick = {
                    try {
                        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://wa.me/$cleanNumber")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Show error
                    }
                }
            )
        }
        
        if (hasTelegram) {
            QuickActionButton(
                icon = painterResource(R.drawable.ic_telegram),
                label = stringResource(R.string.contact_detail_telegram),
                color = TelegramBlue,
                tintIcon = false,
                onClick = {
                    val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("tg://resolve?phone=$cleanNumber")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Try alternative method
                        try {
                            val altIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://t.me/$cleanNumber")
                            }
                            context.startActivity(altIntent)
                        } catch (e2: Exception) {
                            // Show error
                        }
                    }
                }
            )
        }
    }
}

/**
 * Single Quick Action Button
 */
@Composable
private fun QuickActionButton(
    icon: Painter,
    label: String,
    color: Color,
    tintIcon: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.13f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = label,
                    tint = if (tintIcon) color else androidx.compose.ui.graphics.Color.Unspecified,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Section Card
 */
@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
            fontSize = 12.sp
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = if (isLightTheme) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isLightTheme) 0.42f else 0.28f)
            )
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                content()
            }
        }
    }
}

/**
 * Contact Info Row with actions
 */
@Composable
private fun ContactInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onTap
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        trailingActions?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                it()
            }
        }
    }
}

/**
 * Small Action Button
 */
@Composable
private fun SmallActionButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .size(38.dp)
            .semantics {
                this.contentDescription = contentDescription ?: context.getString(R.string.contact_detail_action_button)
            },
        shape = CircleShape,
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * More Action Row
 */
@Composable
private fun MoreActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )
    }
}

/**
 * Call Statistics Section
 */
@Composable
private fun CallStatisticsSection(
    statistics: CallStatistics,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = stringResource(R.string.contact_detail_call_history),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Total Calls
            StatisticItem(
                icon = Icons.Outlined.Call,
                value = statistics.totalCalls.toString(),
                label = "Total",
                color = MaterialTheme.colorScheme.primary
            )
            
            // Incoming
            StatisticItem(
                icon = Icons.AutoMirrored.Filled.CallReceived,
                value = statistics.incomingCalls.toString(),
                label = "Incoming",
                color = iOSGreen
            )
            
            // Outgoing
            StatisticItem(
                icon = Icons.AutoMirrored.Filled.CallMade,
                value = statistics.outgoingCalls.toString(),
                label = "Outgoing",
                color = iOSBlue
            )
            
            // Missed
            StatisticItem(
                icon = Icons.AutoMirrored.Filled.CallMissed,
                value = statistics.missedCalls.toString(),
                label = "Missed",
                color = iOSRed
            )
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
        
        // Total Duration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total Talk Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    text = statistics.getFormattedDuration(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            
            // Last Call
            statistics.lastCallTimestamp?.let { timestamp ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Last Call",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatDate(timestamp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Statistic Item
 */
@Composable
private fun StatisticItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

/**
 * Notes Section
 */
@Composable
private fun NotesSection(
    notes: List<ContactNote>,
    onDeleteNote: ((Long) -> Unit)?,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "Notes (${notes.size})",
        modifier = modifier
    ) {
        notes.forEachIndexed { index, note ->
            NoteItem(
                note = note,
                onDelete = onDeleteNote?.let { { it(note.id) } }
            )
            
            if (index < notes.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            }
        }
    }
}

/**
 * Note Item
 */
@Composable
private fun NoteItem(
    note: ContactNote,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Note icon
        Icon(
            imageVector = Icons.Outlined.StickyNote2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(22.dp)
                .padding(top = 2.dp)
        )
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            // Note text
            Text(
                text = note.note,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Meta info
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call type indicator
                Icon(
                    imageVector = if (note.isIncoming) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                    contentDescription = null,
                    tint = if (note.isIncoming) iOSGreen else iOSBlue,
                    modifier = Modifier.size(12.dp)
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // Duration if available
                if (note.callDuration > 0) {
                    Text(
                        text = formatDuration(note.callDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                
                // Date
                Text(
                    text = formatDate(note.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        
        // Delete button
        onDelete?.let {
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete note",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete?.invoke()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Format timestamp to readable date
 */
private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * Recent Calls Section
 * Shows last 2 calls by default with "Show All" button to expand to 20
 */
@Composable
private fun RecentCallsSection(
    calls: List<com.rasmi.purevon.domain.model.CallLog>,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onCallBack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedCalls = if (showAll) calls.take(20) else calls.take(2)
    
    SectionCard(
        title = "Recent Calls",
        modifier = modifier
    ) {
        displayedCalls.forEachIndexed { index, call ->
            RecentCallItem(
                callLog = call,
                onCallBack = { onCallBack(call.phoneNumber) }
            )
            
            if (index < displayedCalls.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 48.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            }
        }
        
        // Show All / Show Less button
        if (calls.size > 2) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleShowAll)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showAll) "Show Less" else "Show All (${calls.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (showAll) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Single Recent Call Item
 */
@Composable
private fun RecentCallItem(
    callLog: com.rasmi.purevon.domain.model.CallLog,
    onCallBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (callLog.callType) {
        com.rasmi.purevon.data.local.entity.CallType.INCOMING -> 
            Icons.AutoMirrored.Filled.CallReceived to iOSGreen
        com.rasmi.purevon.data.local.entity.CallType.OUTGOING -> 
            Icons.AutoMirrored.Filled.CallMade to iOSBlue
        com.rasmi.purevon.data.local.entity.CallType.MISSED -> 
            Icons.AutoMirrored.Filled.CallMissed to iOSRed
        com.rasmi.purevon.data.local.entity.CallType.REJECTED -> 
            Icons.Default.CallEnd to iOSOrange
        com.rasmi.purevon.data.local.entity.CallType.BLOCKED -> 
            Icons.Default.Block to iOSRed
        else -> Icons.Default.Call to MaterialTheme.colorScheme.primary
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Call type icon
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Call info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = callLog.callType.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (callLog.callType == com.rasmi.purevon.data.local.entity.CallType.MISSED) 
                    FontWeight.SemiBold else FontWeight.Normal,
                color = if (callLog.callType == com.rasmi.purevon.data.local.entity.CallType.MISSED) 
                    iOSRed else MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatCallTime(callLog.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                
                // Show duration only for answered calls (INCOMING or OUTGOING)
                if (callLog.duration > 0 && 
                    (callLog.callType == com.rasmi.purevon.data.local.entity.CallType.INCOMING || 
                     callLog.callType == com.rasmi.purevon.data.local.entity.CallType.OUTGOING)) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatCallDuration(callLog.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
        
        // Call back button
        IconButton(
            onClick = onCallBack,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call back",
                tint = iOSGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Format call time (relative or absolute)
 */
private fun formatCallTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val calendar = java.util.Calendar.getInstance()
    val callCalendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    
    return when {
        // Today
        calendar.get(java.util.Calendar.DAY_OF_YEAR) == callCalendar.get(java.util.Calendar.DAY_OF_YEAR) &&
        calendar.get(java.util.Calendar.YEAR) == callCalendar.get(java.util.Calendar.YEAR) -> {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
        }
        // Yesterday
        diff < 86400_000 * 2 -> {
            "Yesterday"
        }
        // This week
        diff < 604800_000 -> {
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        }
        // Older
        else -> {
            SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

/**
 * Format call duration
 */
private fun formatCallDuration(durationSeconds: Long): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * Format duration in seconds to readable string
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
