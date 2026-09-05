package com.rasmi.purevon.presentation.screen.addcontact

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    onNavigateBack: () -> Unit,
    onContactSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialPhoneNumber: String? = null,
    initialName: String? = null,
    initialEmail: String? = null,
    initialCompany: String? = null,
    contactId: Long? = null,
    viewModel: AddContactViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.initialize(initialPhoneNumber, initialName, initialEmail, initialCompany, contactId) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddContactEvent.ContactSaved -> onContactSaved(event.newContactId)
                is AddContactEvent.ShowSnackbar -> {
                    val msg = when (event.message) {
                        "permission_denied" -> context.getString(R.string.add_contact_failed)
                        "save_failed" -> context.getString(R.string.add_contact_failed)
                        else -> event.message
                    }
                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
                }
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Re-evaluate validation whenever uiState changes; a one-time derivedStateOf
    // would keep the initial disabled value because the ViewModel property is not
    // itself Compose state.
    val isValid = viewModel.isValid

    BackHandler { keyboardController?.hide(); onNavigateBack() }

    var showAvatarDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showBirthdayPicker by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setSelectedPhotoUri(it) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let { viewModel.setSelectedPhotoBitmap(it) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    LaunchedEffect(uiState.phoneEntries.firstOrNull()?.number) {
        viewModel.onPhoneChanged()
    }

    if (showBirthdayPicker) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d -> viewModel.updateBirthday("%04d-%02d-%02d".format(y, m + 1, d)); showBirthdayPicker = false },
            cal.get(Calendar.YEAR) - 25, cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { showBirthdayPicker = false }
            show()
        }
    }

    val isLightTheme = false
    val cardColor = if (isLightTheme) Color.White else MaterialTheme.colorScheme.background

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text(stringResource(R.string.add_contact_photo_title), fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { galleryLauncher.launch("image/*"); showAvatarDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.add_contact_choose_gallery))
                    }
                    TextButton(onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) cameraLauncher.launch(null) else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        showAvatarDialog = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.add_contact_take_photo))
                    }
                    if (uiState.selectedPhotoUri != null || uiState.selectedPhotoBitmap != null || uiState.existingPhotoUri != null) {
                        TextButton(onClick = { viewModel.clearPhoto(); showAvatarDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.add_contact_remove_photo), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAvatarDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showGroupDialog && uiState.availableGroups.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text(stringResource(R.string.add_contact_select_group), fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.updateGroup(null, null); showGroupDialog = false }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = uiState.selectedGroupId == null, onClick = { viewModel.updateGroup(null, null); showGroupDialog = false })
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_contact_no_group))
                    }
                    uiState.availableGroups.forEach { (id, title) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.updateGroup(id, title); showGroupDialog = false }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uiState.selectedGroupId == id, onClick = { viewModel.updateGroup(id, title); showGroupDialog = false })
                            Spacer(Modifier.width(8.dp)); Text(title)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showGroupDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showAccountDialog && uiState.availableAccounts.size > 1) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text(stringResource(R.string.add_contact_select_account), fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    uiState.availableAccounts.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateAccount(account); showAccountDialog = false }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedAccount == account,
                                onClick = { viewModel.updateAccount(account); showAccountDialog = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = when {
                                    account.isLocal -> Icons.Default.PhoneAndroid
                                    account.isSim -> Icons.Default.SimCard
                                    account.type?.contains("google", ignoreCase = true) == true -> Icons.Default.Cloud
                                    else -> Icons.Default.AccountCircle
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        account.isLocal -> stringResource(R.string.add_contact_account_device)
                                        account.isSim -> account.displayName
                                        else -> account.displayName
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (!account.isLocal && !account.isSim && account.name != null) {
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAccountDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (isLightTheme) LightBackgroundAlt else MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(90.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
                                    .clickable { showAvatarDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                val displayPhoto: Any? = uiState.selectedPhotoUri ?: uiState.selectedPhotoBitmap ?: uiState.existingPhotoUri
                                if (displayPhoto != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(displayPhoto).crossfade(true).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val initials = buildString {
                                        val parts = uiState.firstName.trim().split(" ")
                                        parts.getOrNull(0)?.firstOrNull()?.uppercaseChar()?.let { append(it) }
                                        parts.getOrNull(1)?.firstOrNull()?.uppercaseChar()?.let { append(it) }
                                    }.ifEmpty { "+" }
                                    Text(initials, style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 32.sp)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(cardColor)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                                    .clickable { showAvatarDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.add_contact_take_photo), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { viewModel.updateFavorite(!uiState.isFavorite) }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(
                                if (uiState.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = stringResource(R.string.add_contact_favorite),
                                tint = if (uiState.isFavorite) FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.nameSuggestion != null && uiState.firstName.isBlank()) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.add_contact_suggestion, uiState.nameSuggestion ?: ""), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                TextButton(onClick = { viewModel.useNameSuggestion() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text(stringResource(R.string.add_contact_use_suggestion), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    CompactTextField(
                        value = uiState.firstName,
                        onValueChange = { viewModel.updateFirstName(it) },
                        placeholder = stringResource(R.string.add_contact_full_name),
                        isRequired = true,
                        onClear = { viewModel.updateFirstName("") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    uiState.phoneEntries.forEachIndexed { idx, entry ->
                        Row(verticalAlignment = Alignment.Top) {
                            CompactTextField(
                                value = entry.number,
                                onValueChange = { v ->
                                    viewModel.updatePhoneEntry(idx, entry.copy(number = v))
                                },
                                placeholder = stringResource(R.string.add_contact_phone_number),
                                isRequired = idx == 0,
                                isError = uiState.phoneErrors.getOrNull(idx) != null,
                                errorMessage = uiState.phoneErrors.getOrNull(idx)?.let { err ->
                                    when (err) {
                                        "required" -> context.getString(R.string.add_contact_phone_required)
                                        "invalid" -> context.getString(R.string.add_contact_invalid_phone)
                                        else -> err
                                    }
                                },
                                onClear = { viewModel.updatePhoneEntry(idx, entry.copy(number = "")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.phoneEntries.size > 1) {
                                IconButton(onClick = { viewModel.removePhoneEntry(idx) }) {
                                    Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        PhoneTypeSelector(
                            selectedType = entry.type,
                            onTypeSelected = { t -> viewModel.updatePhoneEntry(idx, entry.copy(type = t)) },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        if (idx < uiState.phoneEntries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.addPhoneEntry() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.add_contact_add_phone), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    CompactTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.updateEmail(it) },
                        placeholder = stringResource(R.string.add_contact_email),
                        isError = uiState.emailError != null,
                        errorMessage = uiState.emailError?.let { context.getString(R.string.add_contact_invalid_email) },
                        onClear = { viewModel.updateEmail("") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(start = 16.dp))
                    CompactTextField(
                        value = uiState.company,
                        onValueChange = { viewModel.updateCompany(it) },
                        placeholder = stringResource(R.string.add_contact_company),
                        onClear = { viewModel.updateCompany("") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(start = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showBirthdayPicker = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cake, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = uiState.birthday ?: stringResource(R.string.add_contact_birthday),
                            color = if (uiState.birthday != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp, modifier = Modifier.weight(1f)
                        )
                        if (uiState.birthday != null) {
                            IconButton(onClick = { viewModel.updateBirthday(null) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (uiState.availableGroups.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(start = 16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showGroupDialog = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = uiState.selectedGroupTitle ?: stringResource(R.string.add_contact_group),
                                color = if (uiState.selectedGroupTitle != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp, modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        }
                    }
                    if (uiState.availableAccounts.size > 1) {
                        val selectedAcc = uiState.selectedAccount
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAccountDialog = true }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when {
                                    selectedAcc?.isLocal == true -> Icons.Default.PhoneAndroid
                                    selectedAcc?.isSim == true -> Icons.Default.SimCard
                                    selectedAcc?.type?.contains("google", ignoreCase = true) == true -> Icons.Default.Cloud
                                    else -> Icons.Default.AccountCircle
                                },
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.add_contact_save_to),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        selectedAcc == null -> stringResource(R.string.add_contact_account_device)
                                        selectedAcc.isLocal -> stringResource(R.string.add_contact_account_device)
                                        selectedAcc.isSim -> selectedAcc.displayName
                                        selectedAcc.type?.contains("google", ignoreCase = true) == true ->
                                            stringResource(R.string.add_contact_account_google) + " (${selectedAcc.name})"
                                        else -> selectedAcc.displayName
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { keyboardController?.hide(); viewModel.saveContact(contactId) },
                        enabled = isValid && !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(if (contactId != null) stringResource(R.string.add_contact_save_changes) else stringResource(R.string.add_contact_save), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    onClear: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Row {
                    Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 15.sp)
                    if (isRequired) Text(" *", color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
                }
            },
            trailingIcon = if (value.isNotEmpty() && onClear != null) {
                {
                    IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                cursorColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
        )
        if (isError && errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, top = 2.dp))
        }
    }
}

private object PhoneType {
    const val MOBILE = "Mobile"
    const val HOME = "Home"
    const val WORK = "Work"
    const val OTHER = "Other"
}

@Composable
private fun PhoneTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val types = listOf(PhoneType.MOBILE, PhoneType.HOME, PhoneType.WORK, PhoneType.OTHER)
    val typeLabels = mapOf(
        PhoneType.MOBILE to stringResource(R.string.add_contact_type_mobile),
        PhoneType.HOME to stringResource(R.string.add_contact_type_home),
        PhoneType.WORK to stringResource(R.string.add_contact_type_work),
        PhoneType.OTHER to stringResource(R.string.add_contact_type_other)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { type ->
            FilterChip(
                selected = type == selectedType,
                onClick = { onTypeSelected(type) },
                label = { Text(typeLabels[type] ?: type, fontSize = 12.sp) },
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    enabled = true,
                    selected = type == selectedType
                )
            )
        }
    }
}
