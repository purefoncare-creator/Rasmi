package com.rasmi.purevon.data.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.Handler
import android.os.Looper
import android.provider.CallLog as SystemCallLog
import android.util.Log
import com.rasmi.purevon.data.local.dao.BlockedNumberDao
import com.rasmi.purevon.data.local.dao.CallMetadataDao
import com.rasmi.purevon.data.local.entity.CallMetadataEntity
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.repository.CallLogRepository
import com.rasmi.purevon.util.sim.SimManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementation of CallLogRepository
 * Reads directly from system CallLog.Calls (ContentProvider)
 * Stores only metadata (notes, spam scores) locally
 */
class CallLogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callMetadataDao: CallMetadataDao,
    private val blockedNumberDao: BlockedNumberDao,
    private val simManager: SimManager  // ✅ لتحديد الشريحة بشكل صحيح على كل الأجهزة
) : CallLogRepository {
    
    companion object {
        private const val TAG = "CallLogRepositoryImpl"
        private const val MAX_CALL_LOGS = 1000 // Pagination limit for performance
    }
    
    // ✅ Re-resolve contact names fresh from system (overrides stale CACHED_NAME)
    private val contactResolver = ContactResolver(context)
    
    // ✅ Singleton scope for shareIn — keeps cached data alive across ViewModel recreations
    private val repositoryJob = SupervisorJob()
    private val repositoryScope = CoroutineScope(repositoryJob + Dispatchers.IO)

    fun cleanup() {
        repositoryJob.cancelChildren()
    }

    /** Stable cache key for photo URI lookups (digits only). */
private fun normalizePhoneNumber(phone: String): String =
    phone.replace(Regex("[^0-9+]"), "")
    
    // ✅ Cached shared flow — replays last emission instantly to new subscribers
    private val cachedCallLogsFlow: Flow<List<CallLog>> = callbackFlow<List<CallLog>> {
        val queryAndSend = suspend {
            val calls = querySystemCallLog(resolveContacts = false)
            trySend(calls)
            launch {
                val enrichedCalls = enrichCallLogsWithContacts(calls)
                if (enrichedCalls != calls) {
                    trySend(enrichedCalls)
                }
            }
        }
        
        launch { queryAndSend() }
        
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                this@callbackFlow.launch { queryAndSend() }
            }
        }
        
        context.contentResolver.registerContentObserver(
            SystemCallLog.Calls.CONTENT_URI,
            true,
            observer
        )
        
        // Also re-query when contacts change (to pick up renamed contacts)
        val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Clear photo cache so stale photos are re-resolved
                photoUriCache.evictAll()
                this@callbackFlow.launch { queryAndSend() }
            }
        }
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            contactsObserver
        )
        
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
            context.contentResolver.unregisterContentObserver(contactsObserver)
        }
    }.shareIn(
        scope = repositoryScope,
        started = SharingStarted.WhileSubscribed(5000), // Stop observing if no one cares
        replay = 1 // ✅ Instant replay for new subscribers — no re-query
    )
    
    override fun getAllCallLogs(): Flow<List<CallLog>> = cachedCallLogsFlow
    
    override fun getCallLogsByType(type: CallType): Flow<List<CallLog>> = callbackFlow {
        val queryAndSend = suspend {
            val calls = querySystemCallLog(type = type)
            trySend(calls)
        }
        
        launch { queryAndSend() }
        
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                this@callbackFlow.launch { queryAndSend() }
            }
        }
        
        context.contentResolver.registerContentObserver(
            SystemCallLog.Calls.CONTENT_URI,
            true,
            observer
        )
        
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    
    override fun getCallLogsByNumber(phoneNumber: String): Flow<List<CallLog>> = callbackFlow {
        val queryAndSend = suspend {
            val calls = querySystemCallLog(phoneNumber = phoneNumber)
            trySend(calls)
        }
        
        launch { queryAndSend() }
        
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                this@callbackFlow.launch { queryAndSend() }
            }
        }
        
        context.contentResolver.registerContentObserver(
            SystemCallLog.Calls.CONTENT_URI,
            true,
            observer
        )
        
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    
    // ✅ FIXED: Pre-fetch spam IDs from DB, then query only those from ContentProvider
    override fun getSpamCalls(): Flow<List<CallLog>> = callbackFlow {
        val queryAndSend = suspend {
            val spamIds = callMetadataDao.getSpamCallIdsSync()
            if (spamIds.isEmpty()) {
                trySend(emptyList())
            } else {
                val calls = querySystemCallLog(filterIds = spamIds)
                trySend(calls)
            }
        }
        
        launch { queryAndSend() }
        
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                this@callbackFlow.launch { queryAndSend() }
            }
        }
        
        context.contentResolver.registerContentObserver(
            SystemCallLog.Calls.CONTENT_URI,
            true,
            observer
        )
        
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    
    // ✅ FIXED: Push search query to ContentProvider SQL instead of in-memory filtering
    override fun searchCallLogs(query: String): Flow<List<CallLog>> = callbackFlow {
        val queryAndSend = suspend {
            val filtered = querySystemCallLog(searchQuery = query)
            trySend(filtered)
        }
        
        launch { queryAndSend() }
        
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                this@callbackFlow.launch { queryAndSend() }
            }
        }
        
        context.contentResolver.registerContentObserver(
            SystemCallLog.Calls.CONTENT_URI,
            true,
            observer
        )
        
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    
    override suspend fun insertCallLog(callLog: CallLog): Long {
        // Call logs are inserted automatically by the system during calls
        Log.w(TAG, "insertCallLog called - call logs are managed by system")
        return -1L
    }
    
    override suspend fun getLastOutgoingCall(): CallLog? {
        val outgoingCalls = querySystemCallLog(type = CallType.OUTGOING)
        return outgoingCalls.firstOrNull() // القائمة مرتبة حسب التاريخ تنازلياً
    }
    
    override suspend fun updateCallLog(callLog: CallLog) {
        val existing = callMetadataDao.getMetadata(callLog.id)
        val metadata = CallMetadataEntity(
            systemCallLogId = callLog.id,
            notes = callLog.notes,
            customLabel = null,
            spamScore = callLog.spamScore,
            isMarkedAsSpam = callLog.isSpam,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        callMetadataDao.insertMetadata(metadata)
    }
    
    override suspend fun deleteCallLog(callLogId: Long) {
        context.contentResolver.delete(
            SystemCallLog.Calls.CONTENT_URI,
            "${SystemCallLog.Calls._ID} = ?",
            arrayOf(callLogId.toString())
        )
        callMetadataDao.deleteMetadata(callLogId)
    }
    
    override suspend fun deleteCallLogsByNumber(phoneNumber: String) = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        val selection = if (phoneNumber == "Unknown") {
            "(${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} IS NULL)"
        } else {
            "${SystemCallLog.Calls.NUMBER} = ?"
        }
        val selectionArgs = if (phoneNumber == "Unknown") {
            arrayOf("Unknown", "")
        } else {
            arrayOf(phoneNumber)
        }

        context.contentResolver.query(
            SystemCallLog.Calls.CONTENT_URI,
            arrayOf(SystemCallLog.Calls._ID),
            selection,
            selectionArgs, null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(SystemCallLog.Calls._ID)
            while (c.moveToNext()) ids.add(c.getLong(idIdx))
        }
        context.contentResolver.delete(
            SystemCallLog.Calls.CONTENT_URI,
            selection,
            selectionArgs
        )
        if (ids.isNotEmpty()) callMetadataDao.deleteByIds(ids)
    }

    override suspend fun deleteCallLogsByNumbers(phoneNumbers: List<String>) = withContext(Dispatchers.IO) {
        if (phoneNumbers.isEmpty()) return@withContext
        
        // Chunk to avoid SQLite limits on bind variables (999)
        phoneNumbers.chunked(500).forEach { batch ->
            val containsUnknown = batch.contains("Unknown")
            val cleanBatch = if (containsUnknown) batch.filter { it != "Unknown" } else batch
            
            val selectionParts = mutableListOf<String>()
            val selectionArgs = mutableListOf<String>()
            
            if (cleanBatch.isNotEmpty()) {
                val placeholders = cleanBatch.joinToString(",") { "?" }
                selectionParts.add("${SystemCallLog.Calls.NUMBER} IN ($placeholders)")
                selectionArgs.addAll(cleanBatch)
            }
            
            if (containsUnknown) {
                selectionParts.add("${SystemCallLog.Calls.NUMBER} = ?")
                selectionArgs.add("Unknown")
                selectionParts.add("${SystemCallLog.Calls.NUMBER} = ?")
                selectionArgs.add("")
                selectionParts.add("${SystemCallLog.Calls.NUMBER} IS NULL")
            }
            
            val selection = selectionParts.joinToString(" OR ").let { "($it)" }
            val argsArray = selectionArgs.toTypedArray()
            
            val ids = mutableListOf<Long>()
            try {
                // 1. Find all system call log IDs for these numbers to delete metadata
                context.contentResolver.query(
                    SystemCallLog.Calls.CONTENT_URI,
                    arrayOf(SystemCallLog.Calls._ID),
                    selection,
                    argsArray,
                    null
                )?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(SystemCallLog.Calls._ID)
                    while (c.moveToNext()) {
                        ids.add(c.getLong(idIdx))
                    }
                }
                
                // 2. Delete call logs from system database in one batch query
                context.contentResolver.delete(
                    SystemCallLog.Calls.CONTENT_URI,
                    selection,
                    argsArray
                )
                
                // 3. Delete metadata from local room database in one batch query
                if (ids.isNotEmpty()) {
                    callMetadataDao.deleteByIds(ids)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing batch delete for ${batch.size} numbers", e)
            }
        }
    }
    
    override suspend fun clearAllCallLogs() {
        try {
            val deletedRows = context.contentResolver.delete(
                SystemCallLog.Calls.CONTENT_URI,
                null,
                null
            )
            callMetadataDao.deleteAll()
            Log.d(TAG, "Cleared $deletedRows call logs")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all call logs", e)
            throw e
        }
    }
    
    // ✅ FIXED: Query count directly from ContentProvider with SQL instead of loading all calls
    override suspend fun getCallCountByType(type: CallType): Int = withContext(Dispatchers.IO) {
        var count = 0
        context.contentResolver.query(
            SystemCallLog.Calls.CONTENT_URI,
            arrayOf(SystemCallLog.Calls._ID),
            "${SystemCallLog.Calls.TYPE} = ?",
            arrayOf(type.toSystemType().toString()),
            null
        )?.use { cursor ->
            count = cursor.count
        }
        count
    }
    
    // ✅ FIXED: Query duration directly from ContentProvider with SQL type filter instead of loading all calls
    override suspend fun getTotalDuration(types: List<CallType>): Long = withContext(Dispatchers.IO) {
        if (types.isEmpty()) return@withContext 0L
        var totalDuration = 0L
        
        val systemTypes = types.map { it.toSystemType() }
        val placeholders = systemTypes.joinToString(",") { "?" }
        val selection = "${SystemCallLog.Calls.TYPE} IN ($placeholders)"
        val selectionArgs = systemTypes.map { it.toString() }.toTypedArray()
        
        context.contentResolver.query(
            SystemCallLog.Calls.CONTENT_URI,
            arrayOf(SystemCallLog.Calls.DURATION),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val durationIndex = cursor.getColumnIndexOrThrow(SystemCallLog.Calls.DURATION)
            while (cursor.moveToNext()) {
                totalDuration += cursor.getLong(durationIndex)
            }
        }
        totalDuration
    }
    
    override suspend fun syncWithSystemCallLog() {
        // Deprecated - we read directly from system
    }
    
    // ✅ System call log query methods (moved from ViewModels to Repository layer)
    
    override suspend fun getSystemCallStatisticsForNumber(phoneNumber: String): com.rasmi.purevon.domain.repository.SystemCallStatistics = withContext(Dispatchers.IO) {
        var incomingCalls = 0
        var outgoingCalls = 0
        var missedCalls = 0
        var totalDurationSeconds = 0L
        var lastCallTimestamp: Long? = null
        
        try {
            val selection: String
            val selectionArgs: Array<String>
            if (phoneNumber == "Unknown") {
                selection = "(${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} IS NULL)"
                selectionArgs = arrayOf("Unknown", "")
            } else {
                val normalized = phoneNumber.replace(Regex("[^0-9]"), "")
                val suffix = if (normalized.length > 10) normalized.takeLast(10) else normalized
                selection = "(${SystemCallLog.Calls.NUMBER} LIKE ? OR ${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} LIKE ?)"
                selectionArgs = arrayOf("%$suffix", phoneNumber, "%$suffix")
            }
            
            context.contentResolver.query(
                SystemCallLog.Calls.CONTENT_URI,
                arrayOf(SystemCallLog.Calls.TYPE, SystemCallLog.Calls.DURATION, SystemCallLog.Calls.DATE),
                selection, selectionArgs,
                "${SystemCallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val typeIndex = cursor.getColumnIndex(SystemCallLog.Calls.TYPE)
                val durationIndex = cursor.getColumnIndex(SystemCallLog.Calls.DURATION)
                val dateIndex = cursor.getColumnIndex(SystemCallLog.Calls.DATE)
                
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(typeIndex)
                    val duration = cursor.getLong(durationIndex)
                    val date = cursor.getLong(dateIndex)
                    
                    if (lastCallTimestamp == null) lastCallTimestamp = date
                    
                    when (type) {
                        SystemCallLog.Calls.INCOMING_TYPE -> { incomingCalls++; totalDurationSeconds += duration }
                        SystemCallLog.Calls.OUTGOING_TYPE -> { outgoingCalls++; totalDurationSeconds += duration }
                        SystemCallLog.Calls.MISSED_TYPE, SystemCallLog.Calls.REJECTED_TYPE -> missedCalls++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system call statistics for $phoneNumber", e)
        }
        
        com.rasmi.purevon.domain.repository.SystemCallStatistics(
            incomingCalls = incomingCalls,
            outgoingCalls = outgoingCalls,
            missedCalls = missedCalls,
            totalCalls = incomingCalls + outgoingCalls + missedCalls,
            totalDurationSeconds = totalDurationSeconds,
            lastCallTimestamp = lastCallTimestamp
        )
    }
    
    override suspend fun getLastSystemCallForNumber(phoneNumber: String): com.rasmi.purevon.domain.repository.LastSystemCall? = withContext(Dispatchers.IO) {
        try {
            val selection: String
            val selectionArgs: Array<String>
            if (phoneNumber == "Unknown") {
                selection = "(${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} IS NULL)"
                selectionArgs = arrayOf("Unknown", "")
            } else {
                selection = "${SystemCallLog.Calls.NUMBER} LIKE ? OR ${SystemCallLog.Calls.NUMBER} = ?"
                selectionArgs = arrayOf("%${phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)}", phoneNumber)
            }
            
            context.contentResolver.query(
                SystemCallLog.Calls.CONTENT_URI,
                arrayOf(SystemCallLog.Calls.TYPE, SystemCallLog.Calls.DATE, SystemCallLog.Calls.DURATION),
                selection,
                selectionArgs,
                "${SystemCallLog.Calls.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    com.rasmi.purevon.domain.repository.LastSystemCall(
                        type = cursor.getInt(cursor.getColumnIndexOrThrow(SystemCallLog.Calls.TYPE)),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(SystemCallLog.Calls.DATE)),
                        duration = cursor.getLong(cursor.getColumnIndexOrThrow(SystemCallLog.Calls.DURATION))
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last system call for $phoneNumber", e)
            null
        }
    }
    
    override suspend fun getRecentUniqueSystemCalls(limit: Int): List<com.rasmi.purevon.domain.repository.RecentSystemCall> = withContext(Dispatchers.IO) {
        val recentCalls = mutableListOf<com.rasmi.purevon.domain.repository.RecentSystemCall>()
        val seenNumbers = mutableSetOf<String>()
        
        try {
            context.contentResolver.query(
                SystemCallLog.Calls.CONTENT_URI,
                arrayOf(SystemCallLog.Calls.NUMBER, SystemCallLog.Calls.CACHED_NAME, SystemCallLog.Calls.TYPE, SystemCallLog.Calls.DATE),
                null, null,
                "${SystemCallLog.Calls.DATE} DESC LIMIT ${limit * 3}"
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(SystemCallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndex(SystemCallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndex(SystemCallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(SystemCallLog.Calls.DATE)
                
                while (cursor.moveToNext() && recentCalls.size < limit) {
                    val number = cursor.getString(numberIndex) ?: continue
                    val normalizedNumber = number.replace(Regex("[^0-9+]"), "")
                    if (normalizedNumber in seenNumbers) continue
                    seenNumbers.add(normalizedNumber)
                    
                    recentCalls.add(
                        com.rasmi.purevon.domain.repository.RecentSystemCall(
                            phoneNumber = number,
                            contactName = cursor.getString(nameIndex),
                            callType = cursor.getInt(typeIndex),
                            timestamp = cursor.getLong(dateIndex)
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to read call log", e)
        }
        
        // Re-resolve fresh contact names (CACHED_NAME may be stale after contact edits)
        val phoneNumbers = recentCalls.map { it.phoneNumber }.toSet()
        val freshNames = contactResolver.batchResolveContactNamesOptimized(phoneNumbers)
        val freshPhotos = contactResolver.batchResolveContactPhotoUrisOptimized(phoneNumbers)
        recentCalls.map { call ->
            val freshName = freshNames[call.phoneNumber]
            val freshPhoto = freshPhotos[call.phoneNumber]
            call.copy(
                contactName = freshName ?: call.contactName,
                photoUri = freshPhoto
            )
        }
    }
    
    private suspend fun querySystemCallLog(
        type: CallType? = null,
        phoneNumber: String? = null,
        searchQuery: String? = null,
        filterIds: List<Long>? = null,
        resolveContacts: Boolean = true
    ): List<CallLog> = withContext(Dispatchers.IO) {
        val calls = mutableListOf<CallLog>()
        
        // Get all blocked numbers for quick lookup
        val blockedNumbers = blockedNumberDao.getAllBlockedNumbersSync()
        val blockedNumbersSet = blockedNumbers.map {
            val digits = it.phoneNumber.replace(Regex("[^0-9]"), "")
            if (digits.length > 10) digits.takeLast(10) else digits
        }.toSet()
        
        // Optimize: Fetch all metadata at once to avoid N+1 problem (query inside loop)
        val metadataList = callMetadataDao.getAllMetadataSync()
        val metadataMap = metadataList.associateBy { it.systemCallLogId }

        // Build selection with flexible phone number matching
        val selectionParts = mutableListOf<String>()
        val selectionArgsList = mutableListOf<String>()
        
        if (type != null) {
            selectionParts.add("${SystemCallLog.Calls.TYPE} = ${type.toSystemType()}")
        }
        if (phoneNumber != null) {
            if (phoneNumber == "Unknown") {
                selectionParts.add("(${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} IS NULL)")
                selectionArgsList.add("Unknown")
                selectionArgsList.add("")
            } else {
                val normalized = phoneNumber.replace(Regex("[^0-9]"), "")
                val suffix = if (normalized.length > 10) normalized.takeLast(10) else normalized
                selectionParts.add("(${SystemCallLog.Calls.NUMBER} LIKE ? OR ${SystemCallLog.Calls.NUMBER} = ? OR ${SystemCallLog.Calls.NUMBER} LIKE ?)")
                selectionArgsList.add("%$suffix")
                selectionArgsList.add(phoneNumber)
                selectionArgsList.add("%$suffix")
            }
        }
        // ✅ FIXED: Push search query to SQL (CACHED_NAME LIKE or NUMBER LIKE)
        if (!searchQuery.isNullOrBlank()) {
            selectionParts.add("(${SystemCallLog.Calls.CACHED_NAME} LIKE ? OR ${SystemCallLog.Calls.NUMBER} LIKE ?)")
            selectionArgsList.add("%$searchQuery%")
            selectionArgsList.add("%$searchQuery%")
        }
        // ✅ FIXED: Filter by specific IDs (for getSpamCalls)
        if (!filterIds.isNullOrEmpty()) {
            filterIds.chunked(999).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                selectionParts.add("${SystemCallLog.Calls._ID} IN ($placeholders)")
                selectionArgsList.addAll(chunk.map { it.toString() })
            }
        }
        
        val selection = selectionParts.joinToString(" AND ").takeIf { it.isNotEmpty() }
        val selectionArgs = selectionArgsList.toTypedArray().takeIf { it.isNotEmpty() }
        
        val cursor = context.contentResolver.query(
            SystemCallLog.Calls.CONTENT_URI,
            arrayOf(
                SystemCallLog.Calls._ID,
                SystemCallLog.Calls.NUMBER,
                SystemCallLog.Calls.TYPE,
                SystemCallLog.Calls.DATE,
                SystemCallLog.Calls.DURATION,
                SystemCallLog.Calls.CACHED_NAME,
                SystemCallLog.Calls.PHONE_ACCOUNT_ID // For SIM slot identification
            ),
            selection,
            selectionArgs,
            "${SystemCallLog.Calls.DATE} DESC"
        )
        
        cursor?.use {
            try {
                val idIndex = it.getColumnIndexOrThrow(SystemCallLog.Calls._ID)
                val numberIndex = it.getColumnIndexOrThrow(SystemCallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndexOrThrow(SystemCallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndexOrThrow(SystemCallLog.Calls.DATE)
                val durationIndex = it.getColumnIndexOrThrow(SystemCallLog.Calls.DURATION)
                val nameIndex = it.getColumnIndexOrThrow(SystemCallLog.Calls.CACHED_NAME)
                val phoneAccountIdIndex = it.getColumnIndex(SystemCallLog.Calls.PHONE_ACCOUNT_ID)
            
                var count = 0
                while (it.moveToNext() && count < MAX_CALL_LOGS) {
                    count++
                    try {
                        val systemId = it.getLong(idIndex)
                        // FAST: O(1) Lookup from memory instead of DB Query
                        val metadata = metadataMap[systemId]
                
                        val phoneNum = it.getString(numberIndex)?.takeIf { num -> num.isNotBlank() } ?: "Unknown"
                        val cachedName = it.getString(nameIndex)?.takeIf { name -> name.isNotBlank() }
                        val duration = it.getLong(durationIndex)
                        val callType = mapSystemTypeToCallType(it.getInt(typeIndex), duration)
                        
                        // Extract SIM slot from phone account ID
                        val phoneAccountId = if (phoneAccountIdIndex != -1) it.getString(phoneAccountIdIndex) else null
                        val simSlot = extractSimSlotFromAccountId(phoneAccountId)
                        
                        // Check if number is blocked (either in blocked list or call type is BLOCKED)
                        val normalizedPhoneNum = phoneNum.replace(Regex("[^0-9]"), "")
                        val phoneSuffix = if (normalizedPhoneNum.length > 10) normalizedPhoneNum.takeLast(10) else normalizedPhoneNum
                        val isNumberBlocked = callType == CallType.BLOCKED ||
                            blockedNumbersSet.contains(phoneSuffix)
                    
                        calls.add(CallLog(
                            id = systemId,
                            phoneNumber = phoneNum,
                            contactName = cachedName,
                            contactPhotoUri = null, // resolved below in batch
                            callType = callType,
                            timestamp = it.getLong(dateIndex),
                            duration = duration,
                            simSlot = simSlot,
                            isSpam = (metadata?.spamScore ?: 0f) > 0.5f,
                            spamScore = metadata?.spamScore ?: 0f,
                            notes = metadata?.notes,
                            isBlocked = isNumberBlocked
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading call log at position ${it.position}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting column indices for call log", e)
            }
        }
        
        if (resolveContacts) enrichCallLogsWithContacts(calls) else calls
    }

    private suspend fun enrichCallLogsWithContacts(calls: List<CallLog>): List<CallLog> = withContext(Dispatchers.IO) {
        val phoneNumbers = calls.map { it.phoneNumber }.toSet()
        val freshNameMap = contactResolver.batchResolveContactNamesOptimized(phoneNumbers)
        val photoUriMap = batchResolveContactPhotoUris(phoneNumbers)
        calls.map { call ->
            call.copy(
                contactName = freshNameMap[call.phoneNumber] ?: call.contactName,
                contactPhotoUri = photoUriMap[call.phoneNumber] ?: call.contactPhotoUri
            )
        }
    }

    // LRU cache for contact photo URIs — avoids repeated ContentResolver queries
    private val photoUriCache = android.util.LruCache<String, String>(200)
    // Cache for numbers known NOT to have photos, preventing cache pollution
    private val noPhotoCache = android.util.LruCache<String, Boolean>(200)

    private fun batchResolveContactPhotoUris(phoneNumbers: Set<String>): Map<String, String?> {
        if (phoneNumbers.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, String?>()
        val numbersToQuery = mutableSetOf<String>()

        // 1. Check cache first
        for (number in phoneNumbers) {
            val normalized = normalizePhoneNumber(number)
            if (noPhotoCache.get(normalized) != null) {
                results[number] = null
            } else {
                val cachedUri = photoUriCache.get(normalized)
                if (cachedUri != null) {
                    results[number] = cachedUri
                } else {
                    numbersToQuery.add(number) // Needs query
                }
            }
        }
        
        // Only query uncached numbers
        for (phoneNumber in numbersToQuery) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber)
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                        val photo = if (idx != -1) cursor.getString(idx) else null
                        results[phoneNumber] = photo
                        if (photo != null) {
                            photoUriCache.put(normalizePhoneNumber(phoneNumber), photo)
                        } else {
                            noPhotoCache.put(normalizePhoneNumber(phoneNumber), true)
                        }
                    } else {
                        // 4. Update cache with nulls
                        results[phoneNumber] = null
                        noPhotoCache.put(normalizePhoneNumber(phoneNumber), true) // Remember it has no photo
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving photo for ${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(phoneNumber)}", e)
            }
        }
        return results
    }

    /**
     * Maps Android system call type to our CallType enum.
     *
     * OEM WORKAROUND (MIUI, Samsung, ColorOS, etc.):
     * Many manufacturers do NOT write REJECTED_TYPE (5) when the user declines a call.
     * Instead they log it as INCOMING_TYPE (1) with duration=0.
     * Answered incoming calls always have duration > 0, so:
     *   - INCOMING_TYPE + duration=0  → REJECTED
     *   - INCOMING_TYPE + duration>0  → INCOMING (answered)
     *   - MISSED_TYPE (3)             → MISSED (rang, caller hung up first)
     *   - REJECTED_TYPE (5)           → REJECTED (standard Android)
     *   - BLOCKED_TYPE (6)            → BLOCKED
     *   - VOICEMAIL_TYPE (4)          → VOICEMAIL
     *   - 7 (ANSWERED_EXTERNALLY)    → REJECTED (not answered on this device)
     */
    private fun mapSystemTypeToCallType(systemType: Int, duration: Long = 0L): CallType {
        return when (systemType) {
            SystemCallLog.Calls.INCOMING_TYPE -> {
                val isKnownOem = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
                    android.os.Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
                    android.os.Build.MANUFACTURER.equals("huawei", ignoreCase = true)
                if (isKnownOem && duration == 0L) CallType.REJECTED else CallType.INCOMING
            }
            SystemCallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
            SystemCallLog.Calls.MISSED_TYPE -> CallType.MISSED
            SystemCallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
            SystemCallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
            SystemCallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
            7 -> CallType.REJECTED // ANSWERED_EXTERNALLY_TYPE
            else -> {
                Log.w(TAG, "Unknown call type: $systemType, defaulting to INCOMING")
                CallType.INCOMING
            }
        }
    }
    
    private fun CallType.toSystemType(): Int {
        return when (this) {
            CallType.INCOMING -> SystemCallLog.Calls.INCOMING_TYPE
            CallType.OUTGOING -> SystemCallLog.Calls.OUTGOING_TYPE
            CallType.MISSED -> SystemCallLog.Calls.MISSED_TYPE
            CallType.REJECTED -> SystemCallLog.Calls.REJECTED_TYPE
            CallType.BLOCKED -> SystemCallLog.Calls.BLOCKED_TYPE
            CallType.VOICEMAIL -> SystemCallLog.Calls.VOICEMAIL_TYPE
        }
    }
    
    /**
     * Extract SIM slot number from phone account ID
     * Phone account IDs typically follow patterns like:
     * - "0", "1" for direct slot numbers
     * - "com.android.phone/[telecomAccountHandle]/0" or similar URIs
     * Returns: 0 for SIM 1, 1 for SIM 2, null if unknown
     */
    private fun extractSimSlotFromAccountId(phoneAccountId: String?): Int? {
        if (phoneAccountId.isNullOrBlank()) return null
        
        return try {
            // Try direct number parsing first (simplest case)
            when {
                phoneAccountId == "0" -> 0
                phoneAccountId == "1" -> 1
                // Handle URIs with slot at the end
                phoneAccountId.endsWith("/0") -> 0
                phoneAccountId.endsWith("/1") -> 1
                // Handle formats like "89014...@iccid/0" (iccid based)
                phoneAccountId.contains("@iccid/0") || phoneAccountId.contains("@sub/0") -> 0
                phoneAccountId.contains("@iccid/1") || phoneAccountId.contains("@sub/1") -> 1
                // Try parsing as integer (handles cases like "9", "10", "20")
                else -> {
                    // Try to parse the entire string as a number (subscription ID format)
                    val parsed = phoneAccountId.toIntOrNull()
                    if (parsed != null) {
                        // ✅ Use SimManager for device-agnostic slot lookup via SubscriptionManager API
                        simManager.getSlotForSubscriptionId(parsed)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract SIM slot from account ID: $phoneAccountId", e)
            null
        }
    }
}
