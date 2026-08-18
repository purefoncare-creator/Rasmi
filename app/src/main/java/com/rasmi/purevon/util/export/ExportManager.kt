package com.rasmi.purevon.util.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.rasmi.purevon.data.local.PurevonDatabase
import com.rasmi.purevon.data.local.dao.*
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.model.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import com.rasmi.purevon.util.security.DataEncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for exporting and importing data to CSV/JSON.
 * All exports are AES-256-GCM encrypted and imports are decrypted; there is
 * no plaintext fallback path.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PurevonDatabase,
    private val callMetadataDao: CallMetadataDao,
    private val messageMetadataDao: MessageMetadataDao,
    private val blockedNumberDao: BlockedNumberDao,
    private val spamNumberDao: SpamNumberDao
) {
    
    private val encryptionManager by lazy { DataEncryptionManager(context) }
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    companion object {
        private const val MAX_IMPORT_FILE_SIZE = 10 * 1024 * 1024L // 10 MB
    }
    
    /**
     * Export call logs to CSV (encrypted)
     */
    suspend fun exportCallsToCSV(calls: List<CallLog>, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val csvContent = buildString {
                    appendLine("Name,Number,Type,Date,Duration,Notes")
                    
                    calls.forEach { call ->
                        val type = when (call.type) {
                            android.provider.CallLog.Calls.INCOMING_TYPE -> "Incoming"
                            android.provider.CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                            android.provider.CallLog.Calls.MISSED_TYPE -> "Missed"
                            else -> "Unknown"
                        }
                        
                        appendLine(
                            "${escapeCsv(call.name ?: "")}," +
                                    "${escapeCsv(call.number)}," +
                                    "$type," +
                                    "${call.date}," +
                                    "${call.duration}," +
                                    "${escapeCsv(call.notes ?: "")}"
                        )
                    }
                }
                
                encryptionManager.encryptToFile(csvContent, outputFile).getOrThrow()
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    /**
     * Export messages to CSV (encrypted)
     */
    suspend fun exportMessagesToCSV(messages: List<Message>, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val csvContent = buildString {
                    appendLine("Address,Body,Type,Date")
                    
                    messages.forEach { message ->
                        val type = when (message.type) {
                            1 -> "Received"
                            2 -> "Sent"
                            else -> "Unknown"
                        }
                        
                        appendLine(
                            "${escapeCsv(message.address)}," +
                                    "${escapeCsv(message.body ?: "")}," +
                                    "$type," +
                                    "${message.timestamp}"
                        )
                    }
                }
                
                encryptionManager.encryptToFile(csvContent, outputFile).getOrThrow()
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    /**
     * Export all data to JSON (encrypted)
     */
    suspend fun exportToJSON(outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val exportData = ExportData(
                callMetadata = callMetadataDao.getAllMetadata().first(),
                messageMetadata = messageMetadataDao.getAllMetadata().first(),
                blockedNumbers = blockedNumberDao.getAllBlockedNumbers().first(),
                spamNumbers = spamNumberDao.getAllSpamNumbers().first(),
                exportDate = System.currentTimeMillis()
            )

            val jsonString = json.encodeToString(exportData)
            encryptionManager.encryptToFile(jsonString, outputFile).getOrThrow()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Import data from JSON (decrypted)
     */
    suspend fun importFromJSON(inputFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (inputFile.length() > MAX_IMPORT_FILE_SIZE) {
                return@withContext Result.failure(
                    SecurityException("Import file exceeds maximum allowed size of 10 MB")
                )
            }
            
            val jsonString = encryptionManager.decryptFromFile(inputFile).getOrThrow()
            val exportData = json.decodeFromString<ExportData>(jsonString)
            
            database.withTransaction {
                exportData.callMetadata.forEach { metadata ->
                    callMetadataDao.insertMetadata(metadata)
                }
                exportData.messageMetadata.forEach { metadata ->
                    messageMetadataDao.insertMetadata(metadata)
                }
                exportData.blockedNumbers.forEach { blocked ->
                    blockedNumberDao.insertBlockedNumber(blocked)
                }
                exportData.spamNumbers.forEach { spam ->
                    spamNumberDao.insertSpamNumber(spam)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Import data from JSON using Uri (for file picker).
     * The selected file is an encrypted export and must be decrypted.
     */
    suspend fun importFromJSON(inputUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        // Copy the Uri content to a local temp file so EncryptedFile can read it.
        val tempFile = File.createTempFile("purevon_import_", ".json", context.cacheDir)
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(Exception("Failed to read file"))
            
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        totalBytes += bytesRead
                        if (totalBytes > MAX_IMPORT_FILE_SIZE) {
                            throw SecurityException("Import file exceeds maximum allowed size of 10 MB")
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            if (tempFile.length() > MAX_IMPORT_FILE_SIZE) {
                return@withContext Result.failure(
                    SecurityException("Import file exceeds maximum allowed size of 10 MB")
                )
            }
            
            val jsonString = encryptionManager.decryptFromFile(tempFile).getOrThrow()
            val exportData = json.decodeFromString<ExportData>(jsonString)
            
            database.withTransaction {
                exportData.callMetadata.forEach { metadata ->
                    callMetadataDao.insertMetadata(metadata)
                }
                exportData.messageMetadata.forEach { metadata ->
                    messageMetadataDao.insertMetadata(metadata)
                }
                exportData.blockedNumbers.forEach { blocked ->
                    blockedNumberDao.insertBlockedNumber(blocked)
                }
                exportData.spamNumbers.forEach { spam ->
                    spamNumberDao.insertSpamNumber(spam)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }
    
    /**
     * Escape CSV values
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
    
    /**
     * Get export directory — uses internal storage for security
     */
    fun getExportDirectory(): File {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}

/**
 * Export data container
 */
@Serializable
data class ExportData(
    val callMetadata: List<com.rasmi.purevon.data.local.entity.CallMetadataEntity>,
    val messageMetadata: List<com.rasmi.purevon.data.local.entity.MessageMetadataEntity>,
    val blockedNumbers: List<com.rasmi.purevon.data.local.entity.BlockedNumberEntity>,
    val spamNumbers: List<com.rasmi.purevon.data.local.entity.SpamNumberEntity>,
    val exportDate: Long
)
