package com.rasmi.purevon.presentation.screen.dialer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.presentation.component.*
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.util.PhoneUtil
import com.rasmi.purevon.util.T9SearchUtil
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Dialer Screen - Smart dialer with T9 search
 */
@Composable
fun DialerScreen(
    viewModel: DialerViewModel = hiltViewModel(),
    initialPhoneNumber: String? = null,
    shouldClearInput: Boolean = false, // ✅ Clear input when navigating from InCall
    onAddToContacts: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // ✅ State للتحكم في إظهار حوار الاتصال الوهمي
    var showFakeCallDialog by remember { mutableStateOf(false) }
    
    // ✅ State لحوار اختيار الشريحة (وضع ASK)
    var showSimPickerDialog by remember { mutableStateOf(false) }
    var pendingCallNumber by remember { mutableStateOf("") }
    
    // ✅ تحديث الاقتراحات عند العودة للشاشة (مثل بعد انتهاء مكالمة أو التنقل بين التبويبات)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSuggestions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // ✅ Clear input if requested (when navigating from InCall)
    LaunchedEffect(shouldClearInput) {
        if (shouldClearInput) {
            viewModel.onEvent(DialerUiEvent.ClearInput)
        }
    }
    
    // Set initial phone number if provided (from external tel: intent)
    LaunchedEffect(initialPhoneNumber) {
        initialPhoneNumber?.let { number ->
            if (number.isNotBlank()) {
                viewModel.onEvent(DialerUiEvent.NumberChanged(number))
            }
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // After permission result, try to make call again
        viewModel.onEvent(DialerUiEvent.InitiateCall(isGranted))
    }
    
    LaunchedEffect(Unit) {
        viewModel.uiAction.receiveAsFlow().collect { action: DialerUiAction ->
            when (action) {
                is DialerUiAction.MakePhoneCall -> {
                    val subscriptionId = if (action.subscriptionId != null && action.subscriptionId > 0) {
                        action.subscriptionId
                    } else {
                        null
                    }
                    PhoneUtil.makeCall(context, action.phoneNumber, subscriptionId)
                }
                is DialerUiAction.ShowSimPickerForCall -> {
                    pendingCallNumber = action.phoneNumber
                    showSimPickerDialog = true
                }
                is DialerUiAction.RequestPermission -> {
                    permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            }
        }
    }
    
    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(DialerUiEvent.DismissError)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top section with dialed number
            val isNumberInContacts = uiState.searchResults.any { contact ->
                contact.phoneNumber.replace(NonDigitRegex, "") ==
                uiState.dialedNumber.replace(NonDigitRegex, "")
            }
            val showAddToContacts = uiState.dialedNumber.length >= 3 &&
                onAddToContacts != null &&
                !uiState.isContactSelected &&
                !isNumberInContacts

            DialerNumberDisplay(
                dialedNumber = uiState.dialedNumber,
                selectedContactName = uiState.selectedContactName,
                onNumberChanged = { newNumber ->
                    viewModel.onEvent(DialerUiEvent.NumberChanged(newNumber))
                },
                showAddToContacts = showAddToContacts,
                onAddToContacts = if (onAddToContacts != null) {{
                    onAddToContacts(uiState.dialedNumber)
                }} else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.large, vertical = Spacing.default)
            )

            // Search results or recent contacts
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        uiState.isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        
                        uiState.searchResults.isNotEmpty() -> {
                            SearchResultsList(
                                contacts = uiState.searchResults,
                                query = uiState.dialedNumber,
                                onContactSelected = { contact ->
                                    viewModel.onEvent(DialerUiEvent.ContactSelected(contact))
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        uiState.dialedNumber.isEmpty() && uiState.recentContacts.isNotEmpty() -> {
                            RecentContactsList(
                                contacts = uiState.recentContacts,
                                onContactSelected = { contact ->
                                    viewModel.onEvent(DialerUiEvent.ContactSelected(contact))
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                }
            }

            // Dialpad
            IOSDialpad(
                onDigitPressed = { digit ->
                    viewModel.onEvent(DialerUiEvent.DigitPressed(digit))
                },
                onBackspacePressed = {
                    viewModel.onEvent(DialerUiEvent.BackspacePressed)
                },
                onBackspaceLongPressed = {
                    viewModel.onEvent(DialerUiEvent.BackspaceLongPressed)
                },
                onCallPressed = { 
                    viewModel.onEvent(
                        DialerUiEvent.InitiateCall(
                            hasPermission = PhoneUtil.hasCallPermission(context)
                        )
                    )
                },
                onPastePressed = {
                    // Paste from clipboard
                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.let { clipText ->
                        val numbersOnly = clipText.filter { it.isDigit() || it == '+' }
                        if (numbersOnly.isNotEmpty()) {
                            viewModel.onEvent(DialerUiEvent.NumberChanged(uiState.dialedNumber + numbersOnly))
                        }
                    }
                },
                onSimSwitchPressed = {
                    viewModel.onEvent(DialerUiEvent.SimSwitchPressed)
                },
                onStarLongPressed = {
                    showFakeCallDialog = true
                },
                currentSimLabel = uiState.currentSimLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.small,
                        end = Spacing.small,
                        bottom = Spacing.large
                    )
            )
        }
    }
    
    // ✅ حوار الاتصال الوهمي
    if (showFakeCallDialog) {
        FakeCallDialog(
            onDismiss = { showFakeCallDialog = false }
        )
    }
    
    // ✅ حوار اختيار الشريحة (ASK mode)
    if (showSimPickerDialog && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
        SimSelectorDialog(
            availableSims = uiState.availableSims,
            selectedSimId = null,
            title = stringResource(com.rasmi.purevon.R.string.sim_picker_call_title),
            onSimSelected = { subscriptionId ->
                showSimPickerDialog = false
                viewModel.onEvent(
                    DialerUiEvent.SimSelectedForCall(
                        subscriptionId = subscriptionId,
                        phoneNumber = pendingCallNumber
                    )
                )
            },
            onDismiss = {
                showSimPickerDialog = false
                pendingCallNumber = ""
            }
         )
     }
 }
}

@Composable
private fun DialerNumberDisplay(
    dialedNumber: String,
    selectedContactName: String?,
    onNumberChanged: (String) -> Unit,
    showAddToContacts: Boolean = false,
    onAddToContacts: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(dialedNumber, selection = TextRange(dialedNumber.length))) }
    
    // Sync textFieldValue with dialedNumber
    LaunchedEffect(dialedNumber) {
        if (textFieldValue.text != dialedNumber) {
            textFieldValue = TextFieldValue(
                text = dialedNumber,
                selection = TextRange(dialedNumber.length)
            )
        }
    }
    
    // Dynamic font size based on number length
    val fontSize = when {
        dialedNumber.length > 15 -> 24.sp
        dialedNumber.length > 12 -> 28.sp
        dialedNumber.length > 9 -> 32.sp
        else -> 38.sp
    }
    
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier.padding(top = 4.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // اسم جهة الاتصال المختارة
            if (!selectedContactName.isNullOrBlank()) {
                Text(
                    text = selectedContactName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }

            // صف الرقم: مساحة موازنة + الرقم في المنتصف + أيقونة الإضافة
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // مساحة يسار بحجم الأيقونة لتوسيط الرقم بصرياً
                Spacer(modifier = Modifier.size(40.dp))

                // حقل الرقم
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val filtered = newValue.text.filter { it.isDigit() || it in "+*#" }
                        textFieldValue = newValue.copy(text = filtered)
                        onNumberChanged(filtered)
                    },
                    textStyle = TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.W300,
                        color = if (dialedNumber.isEmpty())
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dialedNumber.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.dialer_enter_number),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.W300,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 1.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // أيقونة إضافة جهة اتصال — تظهر فقط عند الحاجة
                if (showAddToContacts && onAddToContacts != null) {
                    IconButton(
                        onClick = onAddToContacts,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = stringResource(R.string.dialer_add_to_contacts),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    contacts: List<Contact>,
    query: String,
    onContactSelected: (Contact) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = Spacing.small)
    ) {
        itemsIndexed(
            items = contacts,
            key = { _, contact -> "${contact.id}_${contact.phoneNumber}" }
        ) { _, contact ->
            val highlightedName = T9SearchUtil.getHighlightedText(contact.name, query)
            val highlightedNumber = T9SearchUtil.getHighlightedText(contact.phoneNumber, query)
            
            ContactSearchItem(
                contact = contact,
                highlightedName = highlightedName,
                highlightedNumber = highlightedNumber,
                onClick = { onContactSelected(contact) }
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun RecentContactsList(
    contacts: List<Contact>,
    onContactSelected: (Contact) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = Spacing.extraSmall)
        ) {
            itemsIndexed(
                items = contacts,
                key = { _, contact -> "${contact.id}_${contact.phoneNumber}" }
            ) { _, contact ->
                ContactListItem(
                    contact = contact,
                    onClick = { onContactSelected(contact) },
                    subtitle = contact.phoneNumber
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private val NonDigitRegex = Regex("[^0-9+]")