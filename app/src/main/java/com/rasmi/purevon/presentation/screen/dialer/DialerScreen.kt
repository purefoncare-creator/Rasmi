package com.rasmi.purevon.presentation.screen.dialer

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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
 * Dialer Screen - Smart dialer with T9 search, redesigned two-column layout
 */
@Composable
fun DialerScreen(
    viewModel: DialerViewModel = hiltViewModel(),
    initialPhoneNumber: String? = null,
    shouldClearInput: Boolean = false,
    onAddToContacts: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showFakeCallDialog by remember { mutableStateOf(false) }
    var showSimPickerDialog by remember { mutableStateOf(false) }
    var pendingCallNumber by remember { mutableStateOf("") }

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

    LaunchedEffect(shouldClearInput) {
        if (shouldClearInput) {
            viewModel.onEvent(DialerUiEvent.ClearInput)
        }
    }

    LaunchedEffect(initialPhoneNumber) {
        initialPhoneNumber?.let { number ->
            if (number.isNotBlank()) {
                viewModel.onEvent(DialerUiEvent.NumberChanged(number))
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
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

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(DialerUiEvent.DismissError)
        }
    }

    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        if (isWide) {
            DialerWideLayout(
                uiState = uiState,
                viewModel = viewModel,
                context = context,
                onAddToContacts = onAddToContacts,
                onStarLongPressed = { showFakeCallDialog = true },
                paddingValues = paddingValues
            )
        } else {
            DialerCompactLayout(
                uiState = uiState,
                viewModel = viewModel,
                context = context,
                onAddToContacts = onAddToContacts,
                onStarLongPressed = { showFakeCallDialog = true },
                paddingValues = paddingValues
            )
        }
    }

    if (showFakeCallDialog) {
        FakeCallDialog(onDismiss = { showFakeCallDialog = false })
    }

    if (showSimPickerDialog) {
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

@Composable
private fun DialerWideLayout(
    uiState: DialerUiState,
    viewModel: DialerViewModel,
    context: android.content.Context,
    onAddToContacts: ((String) -> Unit)?,
    onStarLongPressed: () -> Unit = {},
    paddingValues: PaddingValues
) {
    val isNumberInContacts = uiState.searchResults.any { contact ->
        contact.phoneNumber.replace(NonDigitRegex, "") ==
                uiState.dialedNumber.replace(NonDigitRegex, "")
    }
    val showAddToContacts = uiState.dialedNumber.length >= 3 &&
            onAddToContacts != null &&
            !uiState.isContactSelected &&
            !isNumberInContacts

    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(paddingValues)
    ) {
        // ── Dialer column ──
        Column(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .background(PurevonBackground),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DialerNumberDisplay(
                dialedNumber = uiState.dialedNumber,
                selectedContactName = uiState.selectedContactName,
                onNumberChanged = { viewModel.onEvent(DialerUiEvent.NumberChanged(it)) },
                showAddToContacts = showAddToContacts,
                onAddToContacts = if (onAddToContacts != null) {{
                    onAddToContacts(uiState.dialedNumber)
                }} else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            )

            Spacer(modifier = Modifier.weight(1f))

            IOSDialpad(
                onDigitPressed = { viewModel.onEvent(DialerUiEvent.DigitPressed(it)) },
                onBackspacePressed = { viewModel.onEvent(DialerUiEvent.BackspacePressed) },
                onBackspaceLongPressed = { viewModel.onEvent(DialerUiEvent.BackspaceLongPressed) },
                onCallPressed = {
                    viewModel.onEvent(
                        DialerUiEvent.InitiateCall(hasPermission = PhoneUtil.hasCallPermission(context))
                    )
                },
                onPastePressed = {
                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.let { clipText ->
                        val numbersOnly = clipText.filter { it.isDigit() || it == '+' }
                        if (numbersOnly.isNotEmpty()) {
                            viewModel.onEvent(DialerUiEvent.NumberChanged(uiState.dialedNumber + numbersOnly))
                        }
                    }
                },
                onSimSwitchPressed = { viewModel.onEvent(DialerUiEvent.SimSwitchPressed) },
                onStarLongPressed = onStarLongPressed,
                currentSimLabel = uiState.currentSimLabel,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(bottom = Spacing.xl)
            )
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = PurevonBorder
        )

        // ── Side panel: recents / suggestions ──
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(PurevonSurfaceAlt)
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
        ) {
            Text(
                text = if (uiState.dialedNumber.isNotEmpty())
                    stringResource(R.string.dialer_results) else stringResource(R.string.dialer_recent),
                style = MaterialTheme.typography.titleMedium,
                color = PurevonTextPrimary,
                modifier = Modifier.padding(bottom = Spacing.md, start = Spacing.sm)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.activeCall.isActive -> {
                        ActiveCallCard(
                            call = uiState.activeCall,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.searchResults.isNotEmpty() -> {
                        SearchResultsList(
                            contacts = uiState.searchResults,
                            query = uiState.dialedNumber,
                            onContactSelected = { viewModel.onEvent(DialerUiEvent.ContactSelected(it)) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    uiState.dialedNumber.isEmpty() && uiState.recentContacts.isNotEmpty() -> {
                        RecentContactsList(
                            contacts = uiState.recentContacts,
                            onContactSelected = { viewModel.onEvent(DialerUiEvent.ContactSelected(it)) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.dialer_no_recent),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PurevonTextTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialerCompactLayout(
    uiState: DialerUiState,
    viewModel: DialerViewModel,
    context: android.content.Context,
    onAddToContacts: ((String) -> Unit)?,
    onStarLongPressed: () -> Unit = {},
    paddingValues: PaddingValues
) {
    val isNumberInContacts = uiState.searchResults.any { contact ->
        contact.phoneNumber.replace(NonDigitRegex, "") ==
                uiState.dialedNumber.replace(NonDigitRegex, "")
    }
    val showAddToContacts = uiState.dialedNumber.length >= 3 &&
            onAddToContacts != null &&
            !uiState.isContactSelected &&
            !isNumberInContacts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(paddingValues)
            .background(PurevonBackground)
    ) {
        DialerNumberDisplay(
            dialedNumber = uiState.dialedNumber,
            selectedContactName = uiState.selectedContactName,
            onNumberChanged = { viewModel.onEvent(DialerUiEvent.NumberChanged(it)) },
            showAddToContacts = showAddToContacts,
            onAddToContacts = if (onAddToContacts != null) {{
                onAddToContacts(uiState.dialedNumber)
            }} else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                uiState.activeCall.isActive -> {
                    ActiveCallCard(
                        call = uiState.activeCall,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.searchResults.isNotEmpty() -> {
                    SearchResultsList(
                        contacts = uiState.searchResults,
                        query = uiState.dialedNumber,
                        onContactSelected = { viewModel.onEvent(DialerUiEvent.ContactSelected(it)) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.dialedNumber.isEmpty() && uiState.recentContacts.isNotEmpty() -> {
                    RecentContactsList(
                        contacts = uiState.recentContacts,
                        onContactSelected = { viewModel.onEvent(DialerUiEvent.ContactSelected(it)) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        IOSDialpad(
            onDigitPressed = { viewModel.onEvent(DialerUiEvent.DigitPressed(it)) },
            onBackspacePressed = { viewModel.onEvent(DialerUiEvent.BackspacePressed) },
            onBackspaceLongPressed = { viewModel.onEvent(DialerUiEvent.BackspaceLongPressed) },
            onCallPressed = {
                viewModel.onEvent(
                    DialerUiEvent.InitiateCall(hasPermission = PhoneUtil.hasCallPermission(context))
                )
            },
            onPastePressed = {
                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.let { clipText ->
                    val numbersOnly = clipText.filter { it.isDigit() || it == '+' }
                    if (numbersOnly.isNotEmpty()) {
                        viewModel.onEvent(DialerUiEvent.NumberChanged(uiState.dialedNumber + numbersOnly))
                    }
                }
            },
            onSimSwitchPressed = { viewModel.onEvent(DialerUiEvent.SimSwitchPressed) },
            onStarLongPressed = onStarLongPressed,
            currentSimLabel = uiState.currentSimLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.lg)
        )
    }
}

@Composable
private fun DialerNumberDisplay(
    dialedNumber: String,
    selectedContactName: String?,
    onNumberChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    showAddToContacts: Boolean = false,
    onAddToContacts: (() -> Unit)? = null
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(dialedNumber, selection = TextRange(dialedNumber.length))) }

    LaunchedEffect(dialedNumber) {
        if (textFieldValue.text != dialedNumber) {
            textFieldValue = TextFieldValue(
                text = dialedNumber,
                selection = TextRange(dialedNumber.length)
            )
        }
    }

    val fontSize = when {
        dialedNumber.length > 15 -> 24.sp
        dialedNumber.length > 12 -> 28.sp
        dialedNumber.length > 9 -> 32.sp
        else -> 38.sp
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .padding(top = 4.dp, bottom = 4.dp)
                .animateContentSize(spring()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = !selectedContactName.isNullOrBlank(),
                enter = fadeIn() + slideInVertically { -20 },
                exit = fadeOut()
            ) {
                Text(
                    text = selectedContactName ?: "",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PurevonPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(40.dp))

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
                            PurevonTextTertiary else PurevonTextPrimary,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                                    color = PurevonTextTertiary,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 1.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (showAddToContacts && onAddToContacts != null) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onAddToContacts),
                        color = PurevonPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = stringResource(R.string.dialer_add_to_contacts),
                            tint = PurevonPrimary,
                            modifier = Modifier
                                .size(22.dp)
                                .wrapContentSize(Alignment.Center)
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
        contentPadding = PaddingValues(vertical = Spacing.sm)
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
                color = PurevonBorder
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.extraSmall)
    ) {
        itemsIndexed(
            items = contacts,
            key = { _, contact -> "${contact.id}_${contact.phoneNumber}" }
        ) { _, contact ->
            ContactSearchItem(
                contact = contact,
                highlightedName = contact.name,
                highlightedNumber = contact.phoneNumber,
                onClick = { onContactSelected(contact) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 72.dp),
                color = PurevonBorder
            )
        }
    }
}

private val NonDigitRegex = Regex("[^0-9+]")

// ═══════════════════════════════════════════════════════════════
// Active Call Card — بطاقة المكالمة النشطة أعلى اقتراحات جهات الاتصال
// ═══════════════════════════════════════════════════════════════

/**
 * بطاقة المكالمة النشطة تظهر مكان اقتراحات جهات الاتصال عندما تكون هناك
 * مكالمة جارية. تحتوي على صورة المتصل، الاسم، مؤقّت المكالمة، وأزرار
 * تحكم (مكبر الصوت / الكتم / إنهاء المكالمة / التوسعة للشاشة الكاملة).
 */
@Composable
private fun ActiveCallCard(
    call: ActiveCallInfo,
    viewModel: DialerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // مؤقّت المكالمة — يتحدّث كل ثانية
    var elapsedSecs by remember { mutableStateOf(0L) }
    LaunchedEffect(call.isActive) {
        if (!call.isActive) {
            elapsedSecs = 0L
            return@LaunchedEffect
        }
        elapsedSecs = ((android.os.SystemClock.elapsedRealtime() - viewModel.getActiveCallStartTime()) / 1000)
            .coerceAtLeast(0L)
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsedSecs = ((android.os.SystemClock.elapsedRealtime() - viewModel.getActiveCallStartTime()) / 1000)
                .coerceAtLeast(0L)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        shape = RoundedCornerShape(24.dp),
        color = PurevonSurface,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, PurevonBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.dialer_active_call_title),
                style = MaterialTheme.typography.labelMedium,
                color = PurevonPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            UnifiedContactAvatar(
                size = 72.dp,
                photoUri = call.photoUri,
                modifier = Modifier.clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = call.contactName ?: call.phoneNumber ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = PurevonTextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = formatCallDuration(elapsedSecs),
                style = MaterialTheme.typography.titleLarge,
                color = PurevonTextSecondary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            // صف أزرار التحكم
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActiveCallActionButton(
                    icon = { tint ->
                        Icon(
                            imageVector = if (call.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp
                            else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.incall_speaker),
                            tint = tint
                        )
                    },
                    label = stringResource(R.string.incall_speaker),
                    active = call.isSpeakerOn,
                    onClick = { viewModel.onEvent(DialerUiEvent.ToggleSpeakerCall) }
                )

                ActiveCallActionButton(
                    icon = { tint ->
                        Icon(
                            imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = stringResource(R.string.incall_mute),
                            tint = tint
                        )
                    },
                    label = stringResource(R.string.incall_mute),
                    active = call.isMuted,
                    onClick = { viewModel.onEvent(DialerUiEvent.ToggleMuteCall) }
                )

                ActiveCallActionButton(
                    icon = { tint ->
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = stringResource(R.string.incall_end_call),
                            tint = tint
                        )
                    },
                    label = stringResource(R.string.incall_end_call),
                    active = false,
                    destructive = true,
                    onClick = { viewModel.onEvent(DialerUiEvent.EndActiveCall) }
                )

                ActiveCallActionButton(
                    icon = { tint ->
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.dialer_active_call_expand),
                            tint = tint
                        )
                    },
                    label = stringResource(R.string.dialer_active_call_expand),
                    active = false,
                    onClick = {
                        val intent = android.content.Intent(context, com.rasmi.purevon.presentation.screen.incall.InCallActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveCallActionButton(
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
    label: String,
    active: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val container = when {
        destructive -> PurevonErrorContainer
        active -> PurevonPrimaryContainer
        else -> PurevonSurfaceAlt
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            icon(
                when {
                    destructive -> PurevonError
                    active -> PurevonPrimary
                    else -> PurevonTextSecondary
                }
            )
        }
    }
}

private fun formatCallDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, secs)
    }
}
