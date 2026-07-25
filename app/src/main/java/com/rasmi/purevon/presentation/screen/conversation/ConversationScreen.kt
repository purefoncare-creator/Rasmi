package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.component.AudioPreviewPlayer
import com.rasmi.purevon.presentation.component.ContactSelectionDialog
import com.rasmi.purevon.presentation.component.MessageBubble
import com.rasmi.purevon.presentation.component.ImageViewerDialog
import com.rasmi.purevon.presentation.component.MessageDateSeparator
import com.rasmi.purevon.presentation.component.EditScheduledMessageDialog
import com.rasmi.purevon.presentation.component.ScheduleMessageDialog
import com.rasmi.purevon.presentation.component.SimSelectorDialog
import com.rasmi.purevon.presentation.component.TemplatePickerDialog
import com.rasmi.purevon.presentation.theme.*
import kotlinx.coroutines.launch

/**
 * Conversation Thread Screen - Display messages for a specific conversation
 * ✅ Merged: يدعم المحادثات الجديدة والموجودة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversationId: Long? = null, // ✅ اختياري للمحادثات الجديدة
    initialPhoneNumber: String? = null, // ✅ رقم الهاتف للمحادثة الجديدة
    scrollToMessageId: Long? = null, // ✅ التمرير إلى رسالة مفضلة محددة
    onNavigateBack: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isImeVisible = imeBottom > 0
    
    // ✅ تتبع موضع الـscroll لعرض زر "العودة للأسفل"
    val showScrollToBottomButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 5 // إظهار الزر عند الابتعاد عن الأسفل
        }
    }
    
    // ✅ جديد: Snackbar لعرض الأخطاء
    val snackbarHostState = remember { SnackbarHostState() }
    
    // ✅ معالجة زر الرجوع - حفظ المسودة قبل المغادرة
    BackHandler {
        // إذا كان هناك نص في حقل الإدخال، احفظه كمسودة
        if (uiState.messageText.isNotBlank()) {
            viewModel.onEvent(ConversationUiEvent.MessageTextChanged(uiState.messageText))
        }
        onNavigateBack()
    }
    
    // عرض الأخطاء في Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long,
                withDismissAction = true
            )
            viewModel.onEvent(ConversationUiEvent.ClearError)
        }
    }
    
    // ✅ Fix #6: Show success snackbar for scheduled messages
    LaunchedEffect(uiState.scheduledSuccess) {
        uiState.scheduledSuccess?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            viewModel.onEvent(ConversationUiEvent.ClearScheduledSuccess)
        }
    }
    
    // File picker launcher - supports multiple files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            // Get filename and mime type from URI
            val fileName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)
                    } else null
                }
            } catch (e: Exception) {
                android.util.Log.e("ConversationScreen", "Error getting filename", e)
                null
            } ?: "File"
            
            val mimeType = context.contentResolver.getType(uri)
            val isImage = mimeType?.startsWith("image/") == true
            
            viewModel.onEvent(
                ConversationUiEvent.AttachFile(
                    com.rasmi.purevon.presentation.screen.conversation.AttachmentData(
                        uri = uri.toString(),
                        fileName = fileName,
                        mimeType = mimeType,
                        isImage = isImage
                    )
                )
            )
        }
    }
    
    // ✅ Image picker launcher - صور فقط
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val fileName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)
                    } else null
                }
            } catch (e: Exception) { null } ?: "image.jpg"
            
            val mimeType = context.contentResolver.getType(uri) ?: "image/*"
            viewModel.onEvent(
                ConversationUiEvent.AttachFile(
                    AttachmentData(uri = uri.toString(), fileName = fileName, mimeType = mimeType, isImage = true)
                )
            )
        }
    }
    
    // ✅ Video picker launcher - فيديو فقط
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val fileName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)
                    } else null
                }
            } catch (e: Exception) { null } ?: "video.mp4"
            
            val mimeType = context.contentResolver.getType(uri) ?: "video/*"
            viewModel.onEvent(
                ConversationUiEvent.AttachFile(
                    AttachmentData(uri = uri.toString(), fileName = fileName, mimeType = mimeType, isImage = false)
                )
            )
        }
    }
    
    // ✅ Camera launcher - التقاط صورة بالكاميرا
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraPhotoUri != null) {
            viewModel.onEvent(
                ConversationUiEvent.AttachFile(
                    AttachmentData(
                        uri = cameraPhotoUri.toString(),
                        fileName = "صورة_كاميرا.jpg",
                        mimeType = "image/jpeg",
                        isImage = true
                    )
                )
            )
        }
    }

    // ✅ Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Permission granted, launch camera
            val photoFile = java.io.File(
                context.cacheDir,
                "camera_${System.currentTimeMillis()}.jpg"
            )
            cameraPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraLauncher.launch(cameraPhotoUri!!)
        }
    }

    // Helper to launch camera with permission check
    val launchCameraWithPermission: () -> Unit = {
        val photoFile = java.io.File(
            context.cacheDir,
            "camera_${System.currentTimeMillis()}.jpg"
        )
        cameraPhotoUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            cameraLauncher.launch(cameraPhotoUri!!)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }
    
    // Contact picker launcher
    
    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            // ✅ Permission granted → delegate to ViewModel
            viewModel.onEvent(ConversationUiEvent.ShareLocation(hasPermission = true))
        } else {
            // ✅ Permission denied - show feedback
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.location_permission_denied),
                    duration = SnackbarDuration.Long
                )
            }
        }
    }
    
    // ✅ Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onEvent(ConversationUiEvent.StartRecording)
        }
    }
    
    // Load conversation when screen opens
    LaunchedEffect(conversationId, initialPhoneNumber) {
        if (conversationId != null) {
            viewModel.onEvent(ConversationUiEvent.LoadConversation(conversationId))
            
            // ✅ Cancel notification for this conversation (using Hilt singleton)
            try {
                val appContext = context.applicationContext
                val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    appContext,
                    com.rasmi.purevon.receiver.SmsReceiverEntryPoint::class.java
                )
                entryPoint.enhancedNotificationManager().cancelNotification(conversationId)
                android.util.Log.d("ConversationScreen", "Notification cancelled for thread: $conversationId")
            } catch (e: Exception) {
                android.util.Log.e("ConversationScreen", "Error cancelling notification", e)
            }
            
            // ✅ Set active conversation for AppStateHelper
            com.rasmi.purevon.util.AppStateHelper.setActiveConversation(conversationId)
        } else {
            // ✅ محادثة جديدة - مع أو بدون رقم هاتف مسبق
            viewModel.onEvent(ConversationUiEvent.SetPhoneNumber(initialPhoneNumber ?: ""))
        }
    }
    
    // ✅ Clear active conversation when screen is disposed
    DisposableEffect(conversationId) {
        onDispose {
            com.rasmi.purevon.util.AppStateHelper.setActiveConversation(null)
            android.util.Log.d("ConversationScreen", "Active conversation cleared")
        }
    }
    
    // Scroll to newest message (index 0 because of reverseLayout)
    // ✅ تحسين: فحص موضع المستخدم قبل التمرير التلقائي
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            // تمرير تلقائي فقط إذا كان المستخدم بالقرب من الأسفل (أحدث الرسائل)
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val isNearBottom = firstVisibleItemIndex <= 3 // ضمن أول 3 رسائل
            
            if (isNearBottom) {
                listState.animateScrollToItem(0)
            }
            // إذا كان المستخدم يتصفح رسائل قديمة، لا تزعجه بالتمرير
        }
    }

    // ✅ تمرير إلى رسالة مفضلة محددة (من شاشة المفضلة)
    var hasScrolledToTarget by remember(scrollToMessageId) { mutableStateOf(false) }
    LaunchedEffect(scrollToMessageId, uiState.messages.size) {
        if (scrollToMessageId != null && !hasScrolledToTarget && uiState.messages.isNotEmpty()) {
            val reversedMessages = uiState.messages.reversed()
            // Calculate index accounting for date separator items
            var lazyIndex = -1
            var runningIndex = 0
            for ((index, msg) in reversedMessages.withIndex()) {
                if (msg.id == scrollToMessageId) {
                    lazyIndex = runningIndex
                    break
                }
                runningIndex++ // message item
                val next = reversedMessages.getOrNull(index + 1)
                if (next == null || !isSameDay(msg.timestamp, next.timestamp)) {
                    runningIndex++ // date separator item
                }
            }
            if (lazyIndex >= 0) {
                listState.animateScrollToItem(lazyIndex)
                hasScrolledToTarget = true
            }
        }
    }
    
    // ✅ جديد: تمرير تلقائي عند إضافة مرفقات (لرؤية المعاينة)
    LaunchedEffect(uiState.attachments.size) {
        if (uiState.attachments.isNotEmpty()) {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val isNearBottom = firstVisibleItemIndex <= 3
            
            if (isNearBottom) {
                kotlinx.coroutines.delay(100) // انتظار صغير لضمان تحديث UI
                listState.animateScrollToItem(0)
            }
        }
    }
    
    val navBarPadding = getNavigationBarPadding()
    
    val isDarkTheme = isSystemInDarkTheme()
    val barColor = if (isDarkTheme) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface

    // ✅ Box خارجي - يحتوي على المحتوى + الحقل العائم
    Box(modifier = Modifier
        .fillMaxSize()
        .background(barColor) // ✅ يملأ الفراغ السفلي بلون الحقل
        .padding(bottom = navBarPadding) // ✅ يتجنب أزرار التنقل فقط (وليس الإيماءات)
        .imePadding()            // ✅ يرفع المحتوى فوق الكيبورد عند ظهوره
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            snackbarHost = { },
            topBar = {
            // iOS-style Navigation Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = barColor,
                shadowElevation = 0.5.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Button - iOS style (icon only)
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = iOSBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Contact Info - Left aligned after back button
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = uiState.contactName ?: uiState.phoneNumber ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            val phoneNumber = uiState.phoneNumber
                            if (uiState.contactName != null && phoneNumber != null) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = phoneNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // SIM chip — tap to cycle through SIMs
                        if (uiState.availableSims.size >= 2) {
                            Surface(
                                onClick = { viewModel.onEvent(ConversationUiEvent.SwitchSmsSim) },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = uiState.smsSimLabel,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    // Subtle divider
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)

        ) {
            // ✅ حقل "To:" للمحادثات الجديدة مع اقتراحات تلقائية
            if (uiState.isNewConversation) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.conversation_to_label),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                BasicTextField(
                                    value = uiState.phoneNumber ?: "",
                                    onValueChange = { viewModel.onEvent(ConversationUiEvent.SetPhoneNumber(it)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
                                    ),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (uiState.phoneNumber.isNullOrBlank()) {
                                                Text(
                                                    text = stringResource(R.string.new_conversation_enter_number),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // ➕ Pick contact button
                            IconButton(
                                onClick = { viewModel.onEvent(ConversationUiEvent.ShowRecipientPicker) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = stringResource(R.string.new_conversation_pick),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                
                // ✅ Autocomplete Suggestions Dropdown
                if (uiState.recipientSuggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                count = uiState.recipientSuggestions.size,
                                key = { index -> 
                                    val s = uiState.recipientSuggestions[index]
                                    "${s.phoneNumber}_${s.contactName}" 
                                }
                            ) { index ->
                                val suggestion = uiState.recipientSuggestions[index]
                                RecipientSuggestionItem(
                                    suggestion = suggestion,
                                    onClick = {
                                        viewModel.onEvent(
                                            ConversationUiEvent.SelectRecipientSuggestion(suggestion)
                                        )
                                    }
                                )
                                if (index < uiState.recipientSuggestions.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 64.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = 0.5.dp
                )
            }
            
            // Schedule Message Dialog
            if (uiState.showScheduleDialog) {
                ScheduleMessageDialog(
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.HideScheduleDialog) },
                    onSchedule = { scheduledTimeMillis, repeatInterval ->
                        viewModel.onEvent(ConversationUiEvent.ScheduleMessage(scheduledTimeMillis, repeatInterval))
                    }
                )
            }

            // ✅ Fix #14: Edit Scheduled Message Dialog
            uiState.editingScheduledMessage?.let { editData ->
                EditScheduledMessageDialog(
                    data = editData,
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.DismissEditScheduledMessage) },
                    onConfirm = { newBody, newTime, newRepeat ->
                        viewModel.onEvent(
                            ConversationUiEvent.ConfirmEditScheduledMessage(
                                scheduleId = editData.scheduleId,
                                newBody = newBody,
                                newScheduledTime = newTime,
                                newRepeatInterval = newRepeat
                            )
                        )
                    }
                )
            }
            
            // Template Picker Dialog
            if (uiState.showTemplateDialog) {
                val templates by viewModel.templates.collectAsState(initial = emptyList())
                TemplatePickerDialog(
                    templates = templates,
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.HideTemplateDialog) },
                    onTemplateSelected = { template ->
                        viewModel.onEvent(ConversationUiEvent.SelectTemplate(template))
                    },
                    onDeleteTemplate = { template ->
                        viewModel.onEvent(ConversationUiEvent.DeleteTemplate(template))
                    },
                    onCreateNew = { viewModel.onEvent(ConversationUiEvent.ShowCreateTemplateDialog) }
                )
            }

            // Create Template Dialog (extracted to ConversationDialogs.kt)
            if (uiState.showCreateTemplateDialog) {
                CreateTemplateDialog(
                    onConfirm = { title, content ->
                        viewModel.onEvent(
                            ConversationUiEvent.CreateTemplate(
                                title = title,
                                content = content
                            )
                        )
                    },
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.HideCreateTemplateDialog) }
                )
            }
            
            // ✅ P2: Forward Dialog
            if (uiState.showForwardDialog && uiState.messageToForward != null) {
                val contacts by viewModel.contacts.collectAsState()
                ContactSelectionDialog(
                    contacts = contacts,
                    onContactsSelected = { selectedContacts ->
                        viewModel.onEvent(ConversationUiEvent.ConfirmForward(selectedContacts))
                    },
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.HideForwardDialog) }
                )
            }
            
            // ✅ Recipient Picker Dialog (for new conversation "To:" field)
            if (uiState.showRecipientPickerDialog) {
                val contacts by viewModel.contacts.collectAsState()
                com.rasmi.purevon.presentation.screen.incall.ContactPickerDialog(
                    title = stringResource(R.string.new_conversation_pick),
                    contacts = contacts,
                    showCallIcon = false,
                    allowManualNumber = false,
                    onContactSelected = { contact ->
                        viewModel.onEvent(ConversationUiEvent.RecipientContactSelected(contact))
                    },
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.HideRecipientPicker) }
                )
            }

            // ✅ Contact Picker Dialog (for sharing contact)
            if (uiState.showContactPickerDialog) {
                val contacts by viewModel.contacts.collectAsState()
                com.rasmi.purevon.presentation.screen.incall.ContactPickerDialog(
                    title = stringResource(R.string.contact_share_title),
                    contacts = contacts,
                    showCallIcon = false,
                    allowManualNumber = false,
                    isLoading = contacts.isEmpty(), // ✅ FIX: Show loading while contacts load (Issue #9)
                    onContactSelected = { contact ->
                        viewModel.onEvent(ConversationUiEvent.ContactSelected(contact))
                    },
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.HideContactPicker) }
                )
            }

            // Contact Preview Dialog (extracted to ConversationDialogs.kt)
            uiState.pendingContactPreview?.let { contact ->
                ContactPreviewDialog(
                    contact = contact,
                    onConfirm = { viewModel.onEvent(ConversationUiEvent.ConfirmContactShare) },
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.DismissContactPreview) }
                )
            }

            // ✅ SIM Picker Dialog for ASK mode (shown before sending a message)
            if (uiState.showSmsSimPickerForSend &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1 &&
                uiState.availableSims.isNotEmpty()
            ) {
                SimSelectorDialog(
                    availableSims = uiState.availableSims,
                    selectedSimId = null,
                    title = "Select SIM for Message",
                    onSimSelected = { subscriptionId ->
                        viewModel.onEvent(ConversationUiEvent.SimSelectedForMessage(subscriptionId))
                    },
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.DismissSmsSimPicker) }
                )
            }
            
            // Background

            // ✅ Image Viewer Dialog
            if (uiState.showImageViewer && uiState.imageViewerUrls.isNotEmpty()) {
                ImageViewerDialog(
                    images = uiState.imageViewerUrls,
                    initialIndex = uiState.imageViewerInitialIndex,
                    onDismiss = { viewModel.onEvent(ConversationUiEvent.HideImageViewer) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
                // ✅ INSPIRED BY QUIK-MASTER: No loading indicator!
                // Data returns instantly from cache, just like Realm's live queries
                when {
                    uiState.messages.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Sms,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Text(
                                text = stringResource(R.string.conversation_no_messages),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = stringResource(R.string.conversation_start_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                    
                    else -> {
                        // ✅ حساب ارتفاع المحتوى الديناميكي للحقل العائم
                        val isShowingInputRow = !uiState.isRecording && uiState.audioRecordingFile == null
                        val baseInputHeight = if (isShowingInputRow) 88.dp else 16.dp
                        val attachmentPreviewHeight = if (uiState.attachments.isNotEmpty()) 116.dp else 0.dp
                        val audioPreviewHeight = if (uiState.audioRecordingFile != null && !uiState.isRecording) 72.dp else 0.dp
                        val recordingHeight = if (uiState.isRecording) 72.dp else 0.dp
                        val totalBottomPadding = baseInputHeight + attachmentPreviewHeight + audioPreviewHeight + recordingHeight
                        
                        val reversedMessages = remember(uiState.messages) { uiState.messages.reversed() }
                        
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            reverseLayout = true,
                            contentPadding = PaddingValues(
                                start = 10.dp,
                                end = 10.dp,
                                top = 12.dp,
                                bottom = totalBottomPadding // ✅ يرتفع تلقائياً حسب المحتوى
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Reversed list to show newest at bottom
                            // Use composite key to avoid SMS/MMS ID collision
                            
                            reversedMessages.forEachIndexed { index, message ->
                                val messageKey = if (message.id != 0L) {
                                    "${if (message.isMms) "mms" else "sms"}_${message.id}"
                                } else {
                                    "msg_${message.timestamp}_${message.body?.hashCode() ?: 0}"
                                }

                                item(key = messageKey) {
                                    MessageBubble(
                                        message = message,
                                        modifier = Modifier.animateItem(),
                                        onFavoriteClick = { msg ->
                                            viewModel.onEvent(ConversationUiEvent.ToggleMessageFavorite(msg))
                                        },
                                        onRetry = {
                                            viewModel.onEvent(ConversationUiEvent.RetryMessage(message.id))
                                        },
                                        onCancel = {
                                            viewModel.onEvent(ConversationUiEvent.CancelSend)
                                        },
                                        onForward = { msg ->
                                            viewModel.onEvent(ConversationUiEvent.ShowForwardDialog(msg))
                                        },
                                        onImageClick = { urls, index ->
                                            viewModel.onEvent(ConversationUiEvent.ShowImageViewer(urls, index))
                                        },
                                        onCancelScheduled = { scheduleId ->
                                            viewModel.onEvent(ConversationUiEvent.CancelScheduledMessage(scheduleId))
                                        },
                                        onEditScheduled = { msg ->
                                            viewModel.onEvent(ConversationUiEvent.EditScheduledMessage(msg))
                                        }
                                    )
                                }

                                // ✅ Date separator between different days
                                val nextMessage = reversedMessages.getOrNull(index + 1)
                                if (nextMessage == null || !isSameDay(message.timestamp, nextMessage.timestamp)) {
                                    item(key = "date_sep_${messageKey}") {
                                        MessageDateSeparator(
                                            date = formatDateLabel(
                                                timestamp = message.timestamp,
                                                todayLabel = stringResource(R.string.msg_date_today),
                                                yesterdayLabel = stringResource(R.string.msg_date_yesterday)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        
                        // ✅ زر العودة للأسفل - يظهر عند التمرير لأعلى
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollToBottomButton,
                            enter = fadeIn() + slideInVertically { it },
                            exit = fadeOut() + slideOutVertically { it },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = totalBottomPadding + 8.dp)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 6.dp,
                                    pressedElevation = 2.dp
                                ),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll to bottom",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        } // Column end
        } // Scaffold end

        // ✅ SnackbarHost خارج Scaffold وفوق حقل الإدخال حتى لا يختبئ خلفه
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )
        
        // ✅ حقل عائم - يلتصق مباشرة بالكيبورد
        MessageInputBar(
            text = uiState.messageText,
            onTextChange = { viewModel.onEvent(ConversationUiEvent.MessageTextChanged(it)) },
            onSend = { viewModel.onEvent(ConversationUiEvent.SendMessage) },
            enabled = !uiState.isSending && !uiState.isCompressingAttachments && !uiState.isFetchingLocation,
            modifier = Modifier
                .align(Alignment.BottomCenter), // ✅ عائم في الأسفل - يلتصق بالكيبورد مباشرة
            onFilePickClick = { filePickerLauncher.launch("*/*") },
            onImagePickClick = { imagePickerLauncher.launch("image/*") },
            onVideoPickClick = { videoPickerLauncher.launch("video/*") },
            onCameraClick = { launchCameraWithPermission() },
            onContactPickClick = { 
                viewModel.onEvent(ConversationUiEvent.ShowContactPicker)
            },
            onLocationPickClick = {
                // ✅ Check if already have permission
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                if (hasFine || hasCoarse) {
                    // Already have permission → go directly to ViewModel
                    viewModel.onEvent(ConversationUiEvent.ShareLocation(hasPermission = true))
                } else {
                    // Request permission
                    locationPermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onScheduleClick = { viewModel.onEvent(ConversationUiEvent.ShowScheduleDialog) },
            onTemplateClick = { viewModel.onEvent(ConversationUiEvent.ShowTemplateDialog) },
            attachments = uiState.attachments,
            onRemoveAttachment = { viewModel.onEvent(ConversationUiEvent.RemoveAttachment(it)) },
            isRecording = uiState.isRecording,
            audioRecordingFile = uiState.audioRecordingFile,
            recordingDuration = uiState.recordingDuration,
            recordingAmplitudes = uiState.recordingAmplitudes,
            onStartRecording = {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.onEvent(ConversationUiEvent.StartRecording)
                } else {
                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            },
            onStopRecording = { viewModel.onEvent(ConversationUiEvent.StopRecording) },
            onCancelRecording = { viewModel.onEvent(ConversationUiEvent.CancelRecording) },
            onSendAudioMessage = { viewModel.onEvent(ConversationUiEvent.SendAudioMessage) },
            smsCharacterCounter = viewModel.smsCharacterCounter,
            isFetchingLocation = uiState.isFetchingLocation
        )
    } // Box end
}
