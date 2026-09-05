package com.rasmi.purevon.presentation.screen.addcontact

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Calendar
import javax.inject.Inject

private val NON_PHONE_CHARS = Regex("[\\s\\-()\\+]")
private const val MAX_PHOTO_DIMENSION = 512
private const val PHOTO_QUALITY = 70

data class PhoneEntry(val number: String = "", val type: String = "Mobile")

data class SaveAccount(
    val name: String?,
    val type: String?,
    val displayName: String,
    val isLocal: Boolean = false,
    val isSim: Boolean = false
)

data class AddContactUiState(
    val firstName: String = "",
    val phoneEntries: List<PhoneEntry> = listOf(PhoneEntry()),
    val email: String = "",
    val company: String = "",
    val birthday: String? = null,
    val isFavorite: Boolean = false,
    val isSaving: Boolean = false,
    val selectedPhotoUri: Uri? = null,
    val selectedPhotoBitmap: Bitmap? = null,
    val existingPhotoUri: Uri? = null,
    val removeExistingPhoto: Boolean = false,
    val selectedGroupId: Long? = null,
    val selectedGroupTitle: String? = null,
    val availableGroups: List<Pair<Long, String>> = emptyList(),
    val nameSuggestion: String? = null,
    val phoneErrors: List<String?> = emptyList(),
    val emailError: String? = null,
    val isLoaded: Boolean = false,
    val availableAccounts: List<SaveAccount> = emptyList(),
    val selectedAccount: SaveAccount? = null
)

sealed class AddContactEvent {
    data class ShowSnackbar(val message: String) : AddContactEvent()
    data class ContactSaved(val newContactId: Long) : AddContactEvent()
}

@HiltViewModel
class AddContactViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddContactUiState())
    val uiState: StateFlow<AddContactUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddContactEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<AddContactEvent> = _events

    fun initialize(
        initialPhoneNumber: String?,
        initialName: String?,
        initialEmail: String?,
        company: String?,
        contactId: Long?
    ) {
        if (_uiState.value.isLoaded) return

        val phones = if (!initialPhoneNumber.isNullOrBlank()) listOf(PhoneEntry(initialPhoneNumber)) else listOf(PhoneEntry())
        _uiState.update { it.copy(
            firstName = initialName ?: "",
            phoneEntries = phones,
            email = initialEmail ?: "",
            company = company ?: "",
            isLoaded = true
        ) }

        loadGroups()
        loadAccounts {
            if (contactId != null) loadContactForEdit(contactId)
        }
    }

    private fun loadAccounts(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val accounts = withContext(Dispatchers.IO) { detectSaveAccounts(context) }
            val defaultAccount = accounts.firstOrNull()
            _uiState.update { it.copy(
                availableAccounts = accounts,
                selectedAccount = defaultAccount
            ) }
            onComplete()
        }
    }

    fun updateAccount(account: SaveAccount) {
        _uiState.update { it.copy(selectedAccount = account) }
    }

    private fun loadGroups() {
        viewModelScope.launch {
            val groups = withContext(Dispatchers.IO) { loadContactGroups(context) }
            _uiState.update { it.copy(availableGroups = groups) }
        }
    }

    private fun loadContactForEdit(contactId: Long) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { ContactDataLoader.loadContactForEdit(context, contactId) }
            val matchedAccount = _uiState.value.availableAccounts.find {
                it.name == data.accountName && it.type == data.accountType
            }
            _uiState.update { it.copy(
                firstName = data.firstName,
                phoneEntries = data.phones,
                email = data.email,
                company = data.company,
                birthday = data.birthday,
                isFavorite = data.isFavorite,
                selectedGroupId = data.groupId,
                selectedGroupTitle = data.groupTitle,
                existingPhotoUri = data.photoUri,
                removeExistingPhoto = false,
                selectedAccount = matchedAccount ?: it.selectedAccount
            ) }
        }
    }

    fun onPhoneChanged() {
        val state = _uiState.value
        val phone = state.phoneEntries.firstOrNull()?.number ?: ""
        if (isValidPhone(phone) && state.firstName.isBlank()) {
            viewModelScope.launch {
                delay(600)
                if (_uiState.value.firstName.isBlank()) {
                    val suggestion = withContext(Dispatchers.IO) { lookupNameFromCallLog(context, phone) }
                    _uiState.update { it.copy(nameSuggestion = suggestion) }
                }
            }
        } else {
            _uiState.update { it.copy(nameSuggestion = null) }
        }
    }

    fun updateFirstName(name: String) {
        _uiState.update { it.copy(firstName = name, nameSuggestion = if (name.isNotBlank()) null else it.nameSuggestion) }
    }

    fun useNameSuggestion() {
        _uiState.update { it.copy(firstName = it.nameSuggestion ?: "", nameSuggestion = null) }
    }

    fun updatePhoneEntry(index: Int, entry: PhoneEntry) {
        _uiState.update { state ->
            val updated = state.phoneEntries.toMutableList().also { it[index] = entry }
            state.copy(phoneEntries = updated)
        }
        validatePhones()
    }

    fun addPhoneEntry() {
        _uiState.update { it.copy(phoneEntries = it.phoneEntries + PhoneEntry(), phoneErrors = it.phoneErrors + null) }
    }

    fun removePhoneEntry(index: Int) {
        _uiState.update { state ->
            val phones = state.phoneEntries.toMutableList().also { it.removeAt(index) }
            val errors = state.phoneErrors.toMutableList().also { if (index < it.size) it.removeAt(index) }
            state.copy(phoneEntries = phones, phoneErrors = errors)
        }
        validatePhones()
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(
            email = email,
            emailError = if (email.isNotBlank() && !isValidEmail(email)) "invalid_email" else null
        ) }
    }

    fun updateCompany(company: String) { _uiState.update { it.copy(company = company) } }
    fun updateBirthday(birthday: String?) { _uiState.update { it.copy(birthday = birthday) } }
    fun updateFavorite(isFavorite: Boolean) { _uiState.update { it.copy(isFavorite = isFavorite) } }
    fun updateGroup(groupId: Long?, groupTitle: String?) { _uiState.update { it.copy(selectedGroupId = groupId, selectedGroupTitle = groupTitle) } }

    fun setSelectedPhotoUri(uri: Uri?) { _uiState.update { it.copy(selectedPhotoUri = uri, selectedPhotoBitmap = null, existingPhotoUri = if (uri != null) null else it.existingPhotoUri, removeExistingPhoto = false) } }
    fun setSelectedPhotoBitmap(bitmap: Bitmap?) { _uiState.update { it.copy(selectedPhotoBitmap = bitmap, selectedPhotoUri = null, existingPhotoUri = if (bitmap != null) null else it.existingPhotoUri, removeExistingPhoto = false) } }
    fun clearPhoto() { _uiState.update { it.copy(selectedPhotoUri = null, selectedPhotoBitmap = null, existingPhotoUri = null, removeExistingPhoto = true) } }

    val isValid: Boolean
        get() {
            val s = _uiState.value
            return s.firstName.isNotBlank() &&
                s.phoneEntries.any { it.number.isNotBlank() && isValidPhone(it.number) } &&
                s.phoneErrors.all { it == null } &&
                s.emailError == null
        }

    fun saveContact(contactId: Long?) {
        val state = _uiState.value
        if (state.isSaving || !isValid) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            if (!withContext(Dispatchers.IO) { hasContactsWritePermission() }) {
                _uiState.update { it.copy(isSaving = false) }
                _events.tryEmit(AddContactEvent.ShowSnackbar("permission_denied"))
                return@launch
            }
            val photoBytes: ByteArray? = withContext(Dispatchers.IO) {
                when {
                    state.selectedPhotoUri != null -> compressPhotoUri(context, state.selectedPhotoUri!!)
                    state.selectedPhotoBitmap != null -> compressPhoto(state.selectedPhotoBitmap!!)
                    else -> null
                }
            }
            val savedId: Long? = withContext(Dispatchers.IO) {
                try {
                    val entries = state.phoneEntries.filter { it.number.isNotBlank() }
                    val account = state.selectedAccount
                    if (contactId != null) {
                        updateContactInSystem(
                            context = context, contactId = contactId,
                            firstName = state.firstName.trim(), phoneEntries = entries,
                            email = state.email.trim().takeIf { it.isNotEmpty() },
                            company = state.company.trim().takeIf { it.isNotEmpty() },
                            birthday = state.birthday, groupId = state.selectedGroupId,
                            isFavorite = state.isFavorite, photoBytes = photoBytes,
                            targetAccountName = account?.name, targetAccountType = account?.type,
                            removeExistingPhoto = state.removeExistingPhoto
                        )
                    } else {
                        val created = saveContactToSystem(
                            context = context, firstName = state.firstName.trim(),
                            phoneEntries = entries,
                            email = state.email.trim().takeIf { it.isNotEmpty() },
                            company = state.company.trim().takeIf { it.isNotEmpty() },
                            birthday = state.birthday, groupId = state.selectedGroupId,
                            isFavorite = state.isFavorite, photoBytes = photoBytes,
                            accountName = account?.name, accountType = account?.type
                        )
                        if (created) contactId else null
                    }
                } catch (e: Exception) { Log.e("AddContact", "Error saving", e); null }
            }
            _uiState.update { it.copy(isSaving = false) }
            if (savedId != null) {
                _events.tryEmit(AddContactEvent.ContactSaved(savedId))
            } else {
                _events.tryEmit(AddContactEvent.ShowSnackbar("save_failed"))
            }
        }
    }

    private fun validatePhones() {
        val state = _uiState.value
        val errors = state.phoneEntries.map { entry ->
            when {
                entry.number.isBlank() && state.phoneEntries.size == 1 -> "required"
                entry.number.isNotBlank() && !isValidPhone(entry.number) -> "invalid"
                else -> null
            }
        }
        _uiState.update { it.copy(phoneErrors = errors) }
    }

    private fun isValidPhone(p: String) = p.replace(NON_PHONE_CHARS, "").let {
        it.length in 3..20 && it.all { c -> c.isDigit() }
    }
    private fun isValidEmail(e: String) = e.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()
    private fun hasContactsWritePermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
}

// ── Photo compression ──────────────────────────────────────────────────────

private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
    if (bitmap.width <= MAX_PHOTO_DIMENSION && bitmap.height <= MAX_PHOTO_DIMENSION) return bitmap
    val scale = minOf(MAX_PHOTO_DIMENSION.toFloat() / bitmap.width, MAX_PHOTO_DIMENSION.toFloat() / bitmap.height)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postScale(scale, scale) }, true)
}

private fun compressPhoto(bitmap: Bitmap): ByteArray {
    val scaled = scaleBitmapIfNeeded(bitmap)
    return ByteArrayOutputStream().use { stream ->
        scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, stream)
        stream.toByteArray()
    }
}

private fun compressPhotoUri(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmap = android.graphics.BitmapFactory.decodeStream(input) ?: return null
            compressPhoto(bitmap)
        }
    } catch (e: Exception) {
        Log.e("AddContact", "Failed to read photo from URI", e)
        null
    }
}

private fun detectSaveAccounts(context: Context): List<SaveAccount> {
    val accounts = mutableListOf<SaveAccount>()

    try {
        val accountManager = AccountManager.get(context)
        val googleAccounts = accountManager.getAccountsByType("com.google")
        for (account in googleAccounts) {
            accounts.add(
                SaveAccount(
                    name = account.name,
                    type = account.type,
                    displayName = account.name
                )
            )
        }

        accounts.sortBy { it.name }

        detectSimAccounts(context).let { accounts.addAll(it) }

        accounts.add(SaveAccount(name = null, type = null, displayName = "Device", isLocal = true))
    } catch (e: Exception) {
        Log.w("AddContact", "Failed to detect accounts", e)
        accounts.add(SaveAccount(name = null, type = null, displayName = "Device", isLocal = true))
    }

    return accounts
}

private fun detectSimAccounts(context: Context): List<SaveAccount> {
    val simAccounts = mutableListOf<SaveAccount>()
    try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val simAccountsInSettings = mutableMapOf<String, Pair<String?, String?>>()
        context.contentResolver.query(
            ContactsContract.Settings.CONTENT_URI,
            arrayOf(
                ContactsContract.Settings.ACCOUNT_NAME,
                ContactsContract.Settings.ACCOUNT_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                val type = cursor.getString(1)
                if (name != null && type != null) {
                    val isSimType = type.equals("com.android.contacts.sim", ignoreCase = true) ||
                        type.equals("usim", ignoreCase = true) ||
                        type.contains("sim", ignoreCase = true)
                    if (isSimType) {
                        simAccountsInSettings[type.lowercase()] = name to type
                    }
                }
            }
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val subscriptions = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                subscriptionManager?.activeSubscriptionInfoList
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager?.activeSubscriptionInfoList
            }
        } catch (_: SecurityException) { null }

        if (subscriptions != null && subscriptions.isNotEmpty()) {
            val knownSimTypes = listOf("com.android.contacts.sim", "usim")
            val fallbackType = knownSimTypes.firstOrNull { t ->
                simAccountsInSettings.containsKey(t.lowercase())
            } ?: "com.android.contacts.sim"

            for (info in subscriptions) {
                val slotIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.simSlotIndex
                } else {
                    @Suppress("DEPRECATION")
                    info.simSlotIndex
                }
                val carrierName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.carrierName
                } else {
                    @Suppress("DEPRECATION")
                    info.carrierName
                } ?: "SIM"

                val matchedEntry = simAccountsInSettings.entries.firstOrNull()
                val accountName = matchedEntry?.value?.first ?: "sim_${slotIndex}"
                val accountType = matchedEntry?.value?.second ?: fallbackType

                simAccounts.add(
                    SaveAccount(
                        name = accountName,
                        type = accountType,
                        displayName = "SIM ${slotIndex + 1} ($carrierName)",
                        isSim = true
                    )
                )
            }
        } else if (simAccountsInSettings.isNotEmpty()) {
            for ((_, pair) in simAccountsInSettings) {
                simAccounts.add(
                    SaveAccount(
                        name = pair.first,
                        type = pair.second,
                        displayName = pair.first ?: "SIM",
                        isSim = true
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.w("AddContact", "Failed to detect SIM accounts", e)
    }

    return simAccounts
}

// ── Account helper ──────────────────────────────────────────────────────────

private data class ContactAccount(val name: String?, val type: String?)

private fun getDefaultContactAccount(context: Context): ContactAccount {
    return try {
        context.contentResolver.query(
            ContactsContract.Settings.CONTENT_URI,
            arrayOf(ContactsContract.Settings.ACCOUNT_NAME, ContactsContract.Settings.ACCOUNT_TYPE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Settings.ACCOUNT_NAME))
                val type = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Settings.ACCOUNT_TYPE))
                if (name != null && type != null) return ContactAccount(name, type)
            }
        }
        val accounts = AccountManager.get(context).getAccountsByType("com.google")
        if (accounts.isNotEmpty()) return ContactAccount(accounts[0].name, accounts[0].type)
        ContactAccount(null, null)
    } catch (e: Exception) {
        Log.w("AddContact", "Could not determine default account", e)
        ContactAccount(null, null)
    }
}

// ── Group loader ────────────────────────────────────────────────────────────

private fun loadContactGroups(context: Context): List<Pair<Long, String>> {
    return try {
        val groups = mutableListOf<Pair<Long, String>>()
        context.contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups.DELETED} = 0 AND ${ContactsContract.Groups.GROUP_VISIBLE} = 1",
            null, ContactsContract.Groups.TITLE
        )?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.Groups._ID)
            val titleIdx = c.getColumnIndex(ContactsContract.Groups.TITLE)
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val title = c.getString(titleIdx) ?: continue
                if (title.isNotBlank()) groups.add(id to title)
            }
            groups.distinctBy { it.second }
        } ?: emptyList()
    } catch (e: Exception) { emptyList() }
}

// ── Name lookup ─────────────────────────────────────────────────────────────

private fun lookupNameFromCallLog(context: Context, phone: String): String? {
    return try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone)
        )
        val freshName = context.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val col = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (col != -1) c.getString(col)?.takeIf { it.isNotBlank() } else null
            } else null
        }
        if (freshName != null) return freshName

        context.contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            arrayOf(android.provider.CallLog.Calls.CACHED_NAME),
            "${android.provider.CallLog.Calls.NUMBER} = ?",
            arrayOf(phone), "${android.provider.CallLog.Calls.DATE} DESC"
        )?.use { c ->
            if (c.moveToFirst()) {
                val col = c.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                if (col != -1) c.getString(col)?.takeIf { it.isNotBlank() } else null
            } else null
        }
    } catch (e: Exception) { null }
}

// ── Save contact ────────────────────────────────────────────────────────────

private fun saveContactToSystem(
    context: Context, firstName: String, phoneEntries: List<PhoneEntry>,
    email: String?, company: String?, birthday: String?, groupId: Long?,
    isFavorite: Boolean, photoBytes: ByteArray?,
    accountName: String? = null, accountType: String? = null
): Boolean {
    return try {
        val ops = buildContactOperations(
            firstName = firstName, phoneEntries = phoneEntries,
            email = email, company = company, birthday = birthday,
            groupId = groupId, isFavorite = isFavorite, photoBytes = photoBytes,
            accountName = accountName, accountType = accountType
        )
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        Log.d("AddContact", "Contact saved successfully to ${accountType ?: "local"}")
        true
    } catch (e: Exception) {
        Log.e("AddContact", "Failed to save contact with account=$accountType", e)
        if (accountName == null && accountType == null) {
            trySaveWithFallbackAccount(context, firstName, phoneEntries, email, company, birthday, groupId, isFavorite, photoBytes)
        } else false
    }
}

private fun trySaveWithFallbackAccount(
    context: Context, firstName: String, phoneEntries: List<PhoneEntry>,
    email: String?, company: String?, birthday: String?, groupId: Long?,
    isFavorite: Boolean, photoBytes: ByteArray?
): Boolean {
    return try {
        val fallback = getDefaultContactAccount(context)
        if (fallback.name == null && fallback.type == null) return false
        Log.w("AddContact", "Retrying save with fallback account: ${fallback.type}")
        val ops = buildContactOperations(
            firstName = firstName, phoneEntries = phoneEntries,
            email = email, company = company, birthday = birthday,
            groupId = groupId, isFavorite = isFavorite, photoBytes = photoBytes,
            accountName = fallback.name, accountType = fallback.type
        )
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        true
    } catch (e: Exception) {
        Log.e("AddContact", "Fallback save also failed", e)
        false
    }
}

private fun buildContactOperations(
    firstName: String, phoneEntries: List<PhoneEntry>,
    email: String?, company: String?, birthday: String?, groupId: Long?,
    isFavorite: Boolean, photoBytes: ByteArray?,
    accountName: String?, accountType: String?
): ArrayList<ContentProviderOperation> {
    val ops = ArrayList<ContentProviderOperation>()

    ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
        .withValue(ContactsContract.RawContacts.STARRED, if (isFavorite) 1 else 0)
        .build())

    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, firstName)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
        .build())

    phoneEntries.forEach { entry ->
        val typeInt = when (entry.type) {
            "Mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            "Home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
            "Work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
            else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
        }
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, entry.number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, typeInt)
            .build())
    }

    email?.let {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, it)
            .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
            .build())
    }

    company?.let {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, it)
            .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
            .build())
    }

    birthday?.let {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, it)
            .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
            .build())
    }

    groupId?.let {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, it)
            .build())
    }

    photoBytes?.let {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, it)
            .build())
    }

    return ops
}

// ── Update contact ──────────────────────────────────────────────────────────

private fun updateContactInSystem(
    context: Context, contactId: Long, firstName: String,
    phoneEntries: List<PhoneEntry>, email: String?, company: String?,
    birthday: String?, groupId: Long?, isFavorite: Boolean, photoBytes: ByteArray?,
    targetAccountName: String? = null, targetAccountType: String? = null,
    removeExistingPhoto: Boolean = false
): Long? {
    return try {
        val allRawContactIds = mutableListOf<Long>()
        val preferredRawContactIds = mutableListOf<Long>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val accountType = c.getString(1)
                allRawContactIds.add(id)
                if (accountType == null || accountType == "com.google") {
                    preferredRawContactIds.add(id)
                }
            }
        }
        if (allRawContactIds.isEmpty()) return null

        val rawContactId = preferredRawContactIds.firstOrNull() ?: allRawContactIds.first()

        val currentAccount = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString()), null
        )?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)
                val type = c.getString(1)
                if (name != null && type != null) name to type else null
            } else null
        }

        val needsAccountMove = targetAccountName != null && targetAccountType != null &&
            currentAccount != null &&
            (currentAccount.first != targetAccountName || currentAccount.second != targetAccountType)

        if (needsAccountMove) {
            try {
                val values = ContentValues().apply {
                    put(ContactsContract.RawContacts.ACCOUNT_NAME, targetAccountName)
                    put(ContactsContract.RawContacts.ACCOUNT_TYPE, targetAccountType)
                }
                context.contentResolver.update(
                    ContactsContract.RawContacts.CONTENT_URI, values,
                    "${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString())
                )
            } catch (e: Exception) {
                Log.w("AddContact", "Could not move contact to different account", e)
            }
        }

        val ops = ArrayList<ContentProviderOperation>()

        val mimeTypesToDelete = buildList {
            add(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            add(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            add(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            add(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
            add(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
            if (photoBytes != null || removeExistingPhoto) add(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
        }
        mimeTypesToDelete.forEach { mime ->
            ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId.toString(), mime)
                ).build())
        }

        for (rcId in allRawContactIds) {
            ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rcId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                ).build())
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rcId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, firstName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                .build())
        }

        phoneEntries.forEach { entry ->
            val typeInt = when (entry.type) {
                "Mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                "Home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                "Work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
            }
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, entry.number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, typeInt)
                .build())
        }

        email?.let {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, it)
                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                .build())
        }

        company?.let {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, it)
                .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                .build())
        }

        birthday?.let {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, it)
                .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                .build())
        }

        groupId?.let {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, it)
                .build())
        }

        photoBytes?.let {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, it)
                .build())
        }

        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)

        val cv = ContentValues().apply { put(ContactsContract.RawContacts.STARRED, if (isFavorite) 1 else 0) }
        for (rcId in allRawContactIds) {
            context.contentResolver.update(
                ContactsContract.RawContacts.CONTENT_URI, cv,
                "${ContactsContract.RawContacts._ID} = ?", arrayOf(rcId.toString())
            )
        }

        val newContactId = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString()), null
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1 } ?: -1

        Log.d("AddContact", "Contact updated successfully old=$contactId newAgg=$newContactId raw=$rawContactId")
        newContactId
    } catch (e: Exception) {
        Log.e("AddContact", "Failed to update contact", e)
        null
    }
}

// ── Load contact for edit ───────────────────────────────────────────────────

private data class ContactEditData(
    val firstName: String, val phones: List<PhoneEntry>, val email: String,
    val company: String, val birthday: String?, val isFavorite: Boolean,
    val photoUri: Uri?, val groupId: Long?, val groupTitle: String?,
    val accountName: String?, val accountType: String?
)

private object ContactDataLoader {
    fun loadContactForEdit(context: Context, contactId: Long): ContactEditData {
        var firstName = ""
        val phones = mutableListOf<PhoneEntry>()
        var email = ""
        var company = ""
        var birthday: String? = null
        var isFavorite = false
        var groupId: Long? = null
        var groupTitle: String? = null
        var accountName: String? = null
        var accountType: String? = null

        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            ),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()), null
        )?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val typeIdx = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                if (nameIdx >= 0) accountName = c.getString(nameIdx)
                if (typeIdx >= 0) accountType = c.getString(typeIdx)
            }
        }

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, null,
            "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(contactId.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(c.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)) ?: continue
                when (mime) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        firstName = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)) ?: ""
                    }
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val number = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: continue
                        val type = when (c.getInt(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                            else -> "Other"
                        }
                        phones.add(PhoneEntry(number, type))
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        email = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)) ?: ""
                    }
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        company = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.COMPANY)) ?: ""
                    }
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        val eventType = c.getInt(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.TYPE))
                        if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) {
                            birthday = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE))
                        }
                    }
                    ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> {
                        groupId = c.getLong(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID))
                    }
                }
            }
        }

        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.STARRED),
            "${ContactsContract.Contacts._ID} = ?", arrayOf(contactId.toString()), null
        )?.use { c ->
            if (c.moveToFirst()) {
                isFavorite = c.getInt(c.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1
            }
        }

        if (groupId != null) {
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI, arrayOf(ContactsContract.Groups.TITLE),
                "${ContactsContract.Groups._ID} = ?", arrayOf(groupId.toString()), null
            )?.use { c ->
                if (c.moveToFirst()) {
                    groupTitle = c.getString(c.getColumnIndexOrThrow(ContactsContract.Groups.TITLE))
                }
            }
        }

        val photoUri = Uri.withAppendedPath(
            android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
        )

        return ContactEditData(
            firstName = firstName,
            phones = phones.distinctBy { it.number.replace(Regex("[\\s\\-()]+"), "") }.ifEmpty { listOf(PhoneEntry()) },
            email = email, company = company, birthday = birthday,
            isFavorite = isFavorite, photoUri = photoUri, groupId = groupId, groupTitle = groupTitle,
            accountName = accountName, accountType = accountType
        )
    }
}
