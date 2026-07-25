package com.rasmi.purevon.data.repository

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import com.rasmi.purevon.data.local.dao.BlockedNumberDao
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Implementation of ContactRepository
 * Reads directly from system ContactsContract (ContentProvider)
 * No local storage - reads from system in real-time
 */
class ContactRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedNumberDao: BlockedNumberDao
) : ContactRepository {
    
    companion object {
        private const val TAG = "ContactRepositoryImpl"
    }
    
    /**
     * Helper function to create a Flow that observes ContentProvider changes
     * Reduces code duplication across query methods
     */
    private fun <T> observeContactsChanges(
        queryFn: suspend () -> T,
        errorTag: String,
        emptyValue: T
    ): Flow<T> = callbackFlow {
        val queryAndSend = suspend {
            try {
                val result = queryFn()
                trySend(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error in $errorTag", e)
                close(e)
            }
        }
        
        launch { queryAndSend() }
        
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                this@callbackFlow.launch { queryAndSend() }
            }
        }
        
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer
        )
        
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
        .conflate()
        .flowOn(Dispatchers.IO) // ✅ Ensure ContentResolver queries run off Main thread
        .catch { e ->
            Log.e(TAG, "Flow error in $errorTag", e)
            emit(emptyValue)
        }
    
    override fun getAllContacts(): Flow<List<Contact>> = observeContactsChanges(
        queryFn = { querySystemContacts() },
        errorTag = "getAllContacts",
        emptyValue = emptyList()
    )
    
    override fun getFavoriteContacts(): Flow<List<Contact>> = observeContactsChanges(
        queryFn = { querySystemContacts(starredOnly = true) },
        errorTag = "getFavoriteContacts",
        emptyValue = emptyList()
    )
    
    override fun getFrequentContacts(limit: Int): Flow<List<Contact>> = observeContactsChanges(
        queryFn = {
            // Query call log for recent call frequency — limited to last 30 days and 500 records
            val callFrequency = mutableMapOf<String, Long>()
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            try {
                context.contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    arrayOf(android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.DATE),
                    "${android.provider.CallLog.Calls.DATE} > ?",
                    arrayOf(thirtyDaysAgo.toString()),
                    // ✅ FIX #40: Use selection clause for date filter; LIMIT via ContentResolver
                    "${android.provider.CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    val numberIdx = cursor.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                    val dateIdx = cursor.getColumnIndex(android.provider.CallLog.Calls.DATE)
                    while (cursor.moveToNext()) {
                        // ✅ FIX #40: Enforce 500 record limit in code
                        if (callFrequency.size >= 500) break
                        val num = cursor.getString(numberIdx) ?: continue
                        val date = cursor.getLong(dateIdx)
                        // Keep the most recent call date per number
                        if (!callFrequency.containsKey(num)) {
                            callFrequency[num] = date
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying call log for frequent contacts", e)
            }
            
            // Build lookup set for quick matching — use last 7 digits to reduce false positives
            val callSuffixes = callFrequency.keys.map {
                it.filter { c -> c.isDigit() }.takeLast(7)
            }.toSet()
            
            val allContacts = querySystemContacts(limit = limit * 3)
            allContacts
                .filter { contact ->
                    val contactDigits = contact.phoneNumber.filter { c -> c.isDigit() }.takeLast(7)
                    callSuffixes.contains(contactDigits)
                }
                .sortedByDescending { contact -> callFrequency.entries.firstOrNull { android.telephony.PhoneNumberUtils.compare(it.key, contact.phoneNumber) }?.value ?: 0L }
                .take(limit)
        },
        errorTag = "getFrequentContacts",
        emptyValue = emptyList()
    )
    
    override fun searchContacts(query: String): Flow<List<Contact>> = flow {
        Log.d(TAG, "🔍 searchContacts Flow started for query: '$query'")
        val result = executeSearchQuery(query)
        Log.d(TAG, "✅ searchContacts emitting ${result.size} contacts")
        emit(result)
    }.catch { e ->
        Log.e(TAG, "❌ Error in searchContacts for query: '$query'", e)
        emit(emptyList())
    }
    
    /**
     * Execute optimized search query
     * 
     * For numeric-only queries (T9 dialing), we fetch all contacts and let
     * the ViewModel handle T9 matching and scoring. This is because SQL LIKE
     * cannot match T9 patterns (e.g., "5646" should match "John" via T9).
     * 
     * For text queries, we use SQL LIKE for efficiency.
     */
    private suspend fun executeSearchQuery(query: String): List<Contact> {
        Log.d(TAG, "📞 executeSearchQuery: query='$query'")
        val contactsMap = mutableMapOf<Long, Contact>() // Use Map to prevent duplicates
        
        // Check if query is numeric-only (T9 dialing mode)
        val isNumericOnly = query.all { it.isDigit() || it == '+' || it == '*' || it == '#' }
        Log.d(TAG, "🔢 isNumericOnly=$isNumericOnly")
        
        // For numeric queries, fetch more contacts to allow T9 name matching
        // For text queries, use SQL LIKE for efficiency
        val selection: String?
        val selectionArgs: Array<String>?
        val limit: Int
        
        if (isNumericOnly && query.isNotEmpty()) {
            // Numeric mode: search for both versions to handle local/international formats
            // If user types "0564..." we also search "564..." to match "+966564..."
            if (query.startsWith("0") && query.length >= 2) {
                val queryWithoutZero = query.substring(1) // Remove leading 0
                selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                selectionArgs = arrayOf("%$query%", "%$queryWithoutZero%")
                Log.d(TAG, "📱 Numeric mode (0-prefix): searching for '$query' OR '$queryWithoutZero'")
            } else {
                selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                selectionArgs = arrayOf("%$query%")
                Log.d(TAG, "📱 Numeric mode: searching for '$query'")
            }
            limit = 200 // Increased for better T9 matching
        } else if (query.isNotEmpty()) {
            // Text mode: use SQL LIKE for name/number matching
            selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            selectionArgs = arrayOf("%$query%", "%$query%")
            limit = 200 // Increased for better results
            Log.d(TAG, "📝 Text mode: using SQL LIKE (limit=$limit)")
        } else {
            selection = null
            selectionArgs = null
            limit = 50 // Reduced for empty query
            Log.d(TAG, "🌍 Empty query: fetching recent contacts (limit=$limit)")
        }
        
        // Pre-fetch all blocked numbers to avoid N+1 queries inside cursor loop
        val blockedNumbers = blockedNumberDao.getAllBlockedNumbersSync()
            .map { it.phoneNumber }
            .toSet()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.STARRED,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
            ),
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            // ✅ FIX #41: Guard against -1 index for required columns
            if (idIndex == -1 || nameIndex == -1 || numberIndex == -1) {
                Log.e("ContactRepo", "Required columns missing from cursor")
                return@use
            }
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
            val isPrimaryIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
            
            while (cursor.moveToNext()) {
                try {
                    val contactId = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Unknown"
                    val number = cursor.getString(numberIndex) ?: continue
                    val type = if (typeIndex != -1) cursor.getInt(typeIndex) else 0
                    val photo = if (photoIndex != -1) cursor.getString(photoIndex) else null
                    val starred = if (starredIndex != -1) cursor.getInt(starredIndex) == 1 else false
                    val isPrimary = if (isPrimaryIndex != -1) cursor.getInt(isPrimaryIndex) == 1 else false
                    
                    // Skip if we already have this contact and this isn't better
                    val existingContact = contactsMap[contactId]
                    if (existingContact != null) {
                        val existingIsMobile = existingContact.phoneType == "Mobile"
                        val currentIsMobile = type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        
                        // Only replace if: this is primary OR (existing is not mobile AND this is mobile)
                        if (!isPrimary && !(currentIsMobile && !existingIsMobile)) {
                            continue
                        }
                    }
                    
                    val phoneType = when (type) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
                        else -> "Other"
                    }
                    
                    // Check if this number is blocked (normalized comparison)
                    val isBlocked = blockedNumbers.any { blocked ->
                        android.telephony.PhoneNumberUtils.compare(blocked, number)
                    }
                    
                    contactsMap[contactId] = Contact(
                        id = contactId,
                        displayName = name,
                        phoneNumber = number,
                        phoneType = phoneType,
                        email = null,
                        company = null,
                        photoUri = photo,
                        isFavorite = starred,
                        isBlocked = isBlocked,
                        timesContacted = 0,
                        lastContactedTime = null,
                        preferredSimSlot = null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading contact in search", e)
                }
            }
        }
        
        val resultList = contactsMap.values.toList().take(limit)
        Log.d(TAG, "✅ executeSearchQuery: returning ${resultList.size} contacts")
        return resultList
    }
    
    override suspend fun getContactByNumber(phoneNumber: String): Contact? {
        return try {
            // Try PhoneLookup first for efficient lookup
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            
            var contact = context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.NUMBER,
                    ContactsContract.PhoneLookup.TYPE,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                    ContactsContract.PhoneLookup.STARRED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    buildContactFromCursor(cursor, phoneNumber)
                } else {
                    null
                }
            }
            
            // Fallback: Search by last 8 digits if PhoneLookup failed
            // This handles different formats: +966595458251, 0595458251, etc.
            if (contact == null) {
                val normalizedNumber = phoneNumber.replace(Regex("[^0-9]"), "")
                val last8 = normalizedNumber.takeLast(8)
                
                if (last8.length == 8) {
                    contact = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                            ContactsContract.CommonDataKinds.Phone.STARRED
                        ),
                        "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                        arrayOf("%$last8"),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            buildContactFromCursorPhone(cursor, phoneNumber)
                        } else {
                            null
                        }
                    }
                }
            }
            
            contact
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact by number: $phoneNumber", e)
            null
        }
    }
    
    /**
     * Build Contact from PhoneLookup cursor
     */
    private suspend fun buildContactFromCursor(cursor: android.database.Cursor, fallbackNumber: String): Contact {
        val contactId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
        val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)) ?: "Unknown"
        val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.NUMBER)) ?: fallbackNumber
        val typeIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.TYPE)
        val type = if (typeIndex != -1) cursor.getInt(typeIndex) else 0
        val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
        val photo = if (photoIndex != -1) cursor.getString(photoIndex) else null
        val starredIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.STARRED)
        val starred = if (starredIndex != -1) cursor.getInt(starredIndex) == 1 else false
        
        val phoneType = when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
            else -> "Other"
        }
        
        val isBlocked = blockedNumberDao.isNumberBlocked(number)
        
        return Contact(
            id = contactId,
            displayName = name,
            phoneNumber = number,
            phoneType = phoneType,
            email = null,
            company = null,
            photoUri = photo,
            isFavorite = starred,
            isBlocked = isBlocked,
            timesContacted = 0,
            lastContactedTime = null,
            preferredSimSlot = null
        )
    }
    
    /**
     * Build Contact from Phone.CONTENT_URI cursor
     */
    private suspend fun buildContactFromCursorPhone(cursor: android.database.Cursor, fallbackNumber: String): Contact {
        val contactId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
        val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "Unknown"
        val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: fallbackNumber
        val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
        val type = if (typeIndex != -1) cursor.getInt(typeIndex) else 0
        val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
        val photo = if (photoIndex != -1) cursor.getString(photoIndex) else null
        val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
        val starred = if (starredIndex != -1) cursor.getInt(starredIndex) == 1 else false
        
        val phoneType = when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
            else -> "Other"
        }
        
        val isBlocked = blockedNumberDao.isNumberBlocked(number)
        
        return Contact(
            id = contactId,
            displayName = name,
            phoneNumber = number,
            phoneType = phoneType,
            email = null,
            company = null,
            photoUri = photo,
            isFavorite = starred,
            isBlocked = isBlocked,
            timesContacted = 0,
            lastContactedTime = null,
            preferredSimSlot = null
        )
    }
    
    override suspend fun getContactById(contactId: Long): Contact? {
        return try {
            // Query contact by ID using Contacts.CONTENT_URI
            val uri = ContactsContract.Contacts.CONTENT_URI
            
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Contacts.STARRED
                ),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: "Unknown"
                    val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                    val photo = if (photoIndex != -1) cursor.getString(photoIndex) else null
                    val starredIndex = cursor.getColumnIndex(ContactsContract.Contacts.STARRED)
                    val starred = if (starredIndex != -1) cursor.getInt(starredIndex) == 1 else false
                    
                    // Get phone number for this contact
                    val phoneData = getPhoneNumberForContact(id)
                    
                    // Get email for this contact
                    val email = getEmailForContact(id)
                    
                    // Get company for this contact
                    val company = getCompanyForContact(id)
                    
                    // Check if blocked
                    val isBlocked = phoneData?.first?.let { blockedNumberDao.isNumberBlocked(it) } ?: false
                    
                    Contact(
                        id = id,
                        displayName = name,
                        phoneNumber = phoneData?.first ?: "",
                        phoneType = phoneData?.second,
                        email = email,
                        company = company,
                        photoUri = photo,
                        isFavorite = starred,
                        isBlocked = isBlocked,
                        timesContacted = 0,
                        lastContactedTime = null,
                        preferredSimSlot = null
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact by ID: $contactId", e)
            null
        }
    }
    
    /**
     * Get primary phone number and type for a contact
     */
    private fun getPhoneNumberForContact(contactId: Long): Pair<String, String>? {
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val type = if (typeIndex != -1) cursor.getInt(typeIndex) else 0
                    
                    val phoneType = when (type) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
                        else -> "Other"
                    }
                    
                    Pair(number, phoneType)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number for contact: $contactId", e)
            null
        }
    }
    
    /**
     * Get email for a contact
     */
    private fun getEmailForContact(contactId: Long): String? {
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting email for contact: $contactId", e)
            null
        }
    }
    
    /**
     * Get company for a contact
     */
    private fun getCompanyForContact(contactId: Long): String? {
        return try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.COMPANY))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting company for contact: $contactId", e)
            null
        }
    }
    
    override suspend fun updateContact(contact: Contact) {
        try {
            val operations = ArrayList<ContentProviderOperation>()
            val displayName = contact.displayName.trim()
            
            // Split display name into first/last for GIVEN_NAME and FAMILY_NAME
            val nameParts = displayName.split(" ", limit = 2)
            val firstName = nameParts.firstOrNull() ?: displayName
            val lastName = if (nameParts.size > 1) nameParts[1] else null
            
            // Get ALL writable raw contact IDs for this aggregated contact
            // Updating ALL raw contacts prevents Android from de-aggregating them
            val allRawContactIds = getAllWritableRawContactIds(contact.id)
            
            if (allRawContactIds.isEmpty()) {
                Log.e(TAG, "Could not find any writable raw contact for contactId: ${contact.id}")
                return
            }
            
            Log.d(TAG, "Updating ${allRawContactIds.size} raw contacts for contactId: ${contact.id}")
            
            // Update StructuredName on ALL raw contacts (prevents de-aggregation)
            for (rawContactId in allRawContactIds) {
                // Delete existing name row then insert new one (handles missing rows)
                operations.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        )
                        .build()
                )
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                        .build()
                )
            }
            
            // Use the primary raw contact for phone/email/company updates
            val primaryRawContactId = allRawContactIds.first()

            // Phone Update
             val primaryPhoneId = getPrimaryPhoneDataId(primaryRawContactId)
             if (primaryPhoneId != null) {
                 operations.add(
                     ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                         .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(primaryPhoneId.toString()))
                         .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phoneNumber)
                         .build()
                 )
             } else {
                 operations.add(
                     ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                         .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRawContactId)
                         .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                         .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phoneNumber)
                         .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                         .build()
                 )
             }
             
             // Email Update — delete old and insert new (on primary raw contact)
             operations.add(
                 ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                     .withSelection(
                         "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                         arrayOf(primaryRawContactId.toString(), ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                     )
                     .build()
             )
             if (!contact.email.isNullOrBlank()) {
                 operations.add(
                     ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                         .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRawContactId)
                         .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                         .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, contact.email)
                         .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                         .build()
                 )
             }
             
             // Company Update — delete old and insert new (on primary raw contact)
             operations.add(
                 ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                     .withSelection(
                         "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                         arrayOf(primaryRawContactId.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                     )
                     .build()
             )
             if (!contact.company.isNullOrBlank()) {
                 operations.add(
                     ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                         .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRawContactId)
                         .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                         .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, contact.company)
                         .build()
                 )
             }
             
             // Apply batch
             if (operations.isNotEmpty()) {
                 context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
                 Log.d(TAG, "Contact updated successfully: ${contact.id}, ${allRawContactIds.size} raw contacts updated")
             }
             
        } catch (e: Exception) {
            Log.e(TAG, "Error updating contact", e)
            throw e
        }
    }

    /**
     * Get ALL writable raw contact IDs for an aggregated contact.
     * Sorted so preferred accounts (Google, local) come first.
     * Updates must be applied to ALL raw contacts to prevent Android de-aggregation.
     */
    private fun getAllWritableRawContactIds(contactId: Long): List<Long> {
        val uri = ContactsContract.RawContacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )
        val selection = "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0"
        
        val preferred = mutableListOf<Long>()
        val others = mutableListOf<Long>()
        
        context.contentResolver.query(uri, projection, selection, arrayOf(contactId.toString()), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val accountType = cursor.getString(1)
                // Google and local accounts are preferred (most likely writable)
                if (accountType == null || accountType == "com.google") {
                    preferred.add(id)
                } else {
                    others.add(id)
                }
            }
        }
        
        return preferred + others
    }

    private fun getWritableRawContactId(contactId: Long): Long? {
         val uri = ContactsContract.RawContacts.CONTENT_URI
         val projection = arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE)
         val selection = "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0"
         
         context.contentResolver.query(uri, projection, selection, arrayOf(contactId.toString()), null)?.use { cursor ->
             // First pass: look for preferred account types
             while(cursor.moveToNext()) {
                  val id = cursor.getLong(0)
                  val accountType = cursor.getString(1)
                  // Prefer Google or Local accounts
                  if (accountType == null || accountType == "com.google") {
                      return id
                  }
             }
             // Second pass: return first non-read-only if possible, or just the first one
             if (cursor.moveToFirst()) return cursor.getLong(0)
         }
         return null
    }

    private fun getPrimaryPhoneDataId(rawContactId: Long): Long? {
        val selection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val args = arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }
    
    override suspend fun deleteContact(contactId: Long) {
        try {
            // Delete contact from system
            val uri = ContactsContract.RawContacts.CONTENT_URI
            val selection = "${ContactsContract.RawContacts.CONTACT_ID} = ?"
            val selectionArgs = arrayOf(contactId.toString())
            
            context.contentResolver.delete(uri, selection, selectionArgs)
            Log.d(TAG, "Contact deleted: $contactId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting contact", e)
            throw e
        }
    }
    
    override suspend fun setFavorite(contactId: Long, isFavorite: Boolean) {
        try {
            val values = ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
            }
            
            val updated = context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                values,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString())
            )
            
            Log.d(TAG, "Contact $contactId favorite status updated: $isFavorite (rows: $updated)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating favorite status", e)
            throw e
        }
    }
    
    private suspend fun querySystemContacts(limit: Int? = 5000, starredOnly: Boolean = false): List<Contact> {
        val contactsMap = mutableMapOf<Long, Contact>() // Use Map to prevent duplicates by contact ID
        
        // Pre-fetch blocked numbers to avoid N+1 queries
        val blockedNumbers = try {
            blockedNumberDao.getAllBlockedNumbersSync().map { it.phoneNumber }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        
        // Optimize query: only get contacts with valid phone numbers
        val selection = buildString {
            append("${ContactsContract.CommonDataKinds.Phone.NUMBER} IS NOT NULL")
            if (starredOnly) {
                append(" AND ${ContactsContract.CommonDataKinds.Phone.STARRED} = 1")
            }
        }
        val sortOrder = if (limit != null) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT $limit"
        } else {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        }
        
        // Get all phone numbers with contact info - optimized projection
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI, // Use thumbnail instead of full photo
                ContactsContract.CommonDataKinds.Phone.STARRED,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
            ),
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
            val isPrimaryIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
            
            // Validate column indices
            if (idIndex == -1 || nameIndex == -1 || numberIndex == -1) {
                Log.e(TAG, "Invalid column indices in contacts query")
                return@use
            }
            
            // Performance: Cache com.rasmi.purevon.util.PhoneUtil.normalizePhoneNumber if possible, 
            // but for now strict matching on what's in DB is faster.
            // If strict matching fails, users can rely on normalized matching in other parts of the app.
            
            while (cursor.moveToNext()) {
                try {
                    val contactId = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex)?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown"
                    val number = cursor.getString(numberIndex)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val type = if (typeIndex != -1) cursor.getInt(typeIndex) else 0
                    val photo = if (photoIndex != -1) cursor.getString(photoIndex) else null
                    val starred = if (starredIndex != -1) cursor.getInt(starredIndex) == 1 else false
                    val isPrimary = if (isPrimaryIndex != -1) cursor.getInt(isPrimaryIndex) == 1 else false
                    
                    // Skip if we already have this contact and this isn't the primary number
                    // Prefer: Primary number > Mobile > first number found
                    val existingContact = contactsMap[contactId]
                    if (existingContact != null) {
                        val existingIsMobile = existingContact.phoneType == "Mobile"
                        val currentIsMobile = type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        
                        // Only replace if: this is primary OR (existing is not mobile AND this is mobile)
                        if (!isPrimary && !(currentIsMobile && !existingIsMobile)) {
                            continue
                        }
                    }
                
                val phoneType = when (type) {
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                    ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
                    else -> "Other"
                }
                
                    // Check if this number is blocked (normalized comparison)
                    val isBlocked = blockedNumbers.any { blocked ->
                        android.telephony.PhoneNumberUtils.compare(blocked, number)
                    }
                
                    contactsMap[contactId] = Contact(
                        id = contactId,
                        displayName = name,
                        phoneNumber = number,
                        phoneType = phoneType,
                        email = null,
                        company = null,
                        photoUri = photo,
                        isFavorite = starred,
                        isBlocked = isBlocked,
                        timesContacted = 0,
                        lastContactedTime = null,
                        preferredSimSlot = null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading contact at position ${cursor.position}", e)
                }
            }
        }
        
        // Convert map to list, sort by name, and apply limit if specified
        val contacts = contactsMap.values.toList().sortedBy { it.displayName }
        Log.d(TAG, "✅ querySystemContacts: loaded ${contacts.size} contacts (limit=${limit ?: "none"})")
        return if (limit != null) contacts.take(limit) else contacts
    }
}
