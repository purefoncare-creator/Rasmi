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
    onNavigateBack: () -> Unit,
    onDeleteContact: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveContact: (String, String, String, String?, String?) -> Unit,
    modifier: Modifier = Modifier,
    notes: List<ContactNote> = emptyList(),
    callStatistics: CallStatistics = CallStatistics(),
    recentCalls: List<com.rasmi.purevon.domain.model.CallLog> = emptyList(),
    showAllCalls: Boolean = false,
    onToggleShowAllCalls: () -> Unit = {},
    onBlockContact: () -> Unit = {},
    onEditContact: () -> Unit = {},
    onDeleteNote: ((Long) -> Unit)? = null,
    onCall: ((String) -> Unit)? = null,
    onMessage: ((String) -> Unit)? = null
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

// updateContactInSystem removed in favor of Repository implementation

