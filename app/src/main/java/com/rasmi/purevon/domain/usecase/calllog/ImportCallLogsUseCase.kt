package com.rasmi.purevon.domain.usecase.calllog

import android.util.Log
import com.rasmi.purevon.domain.service.SystemCallLogWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import javax.inject.Inject

/**
 * Import call logs from JSON file
 */
class ImportCallLogsUseCase @Inject constructor(
    private val systemCallLogWriter: SystemCallLogWriter
) {

    companion object {
        private const val TAG = "ImportCallLogsUseCase"
        private const val MAX_IMPORT_FILE_SIZE = 10 * 1024 * 1024L // 10 MB
        private const val MIN_VALID_TIMESTAMP = 946684800000L // 2000-01-01
        private const val MAX_DURATION_SECONDS = 86400L // 24 hours
    }

    suspend operator fun invoke(file: File): Int = withContext(Dispatchers.IO) {
        if (file.length() > MAX_IMPORT_FILE_SIZE) {
            Log.e(TAG, "Import file too large: ${file.length()} bytes")
            return@withContext 0
        }

        val jsonContent = file.readText()
        val jsonArray = JSONArray(jsonContent)
        var importedCount = 0
        val now = System.currentTimeMillis()

        for (i in 0 until jsonArray.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)

                val phoneNumber = jsonObject.getString("phoneNumber")
                if (phoneNumber.isBlank() || phoneNumber.replace(Regex("[^0-9+]"), "").length < 3) {
                    Log.w(TAG, "Skipping entry $i: invalid phone number")
                    continue
                }

                val timestamp = jsonObject.getLong("timestamp")
                if (timestamp < MIN_VALID_TIMESTAMP || timestamp > now + 86_400_000L) {
                    Log.w(TAG, "Skipping entry $i: timestamp out of range ($timestamp)")
                    continue
                }

                val duration = jsonObject.getLong("duration")
                if (duration < 0 || duration > MAX_DURATION_SECONDS) {
                    Log.w(TAG, "Skipping entry $i: invalid duration ($duration)")
                    continue
                }

                val callType = getCallTypeInt(jsonObject.getString("callType"))
                val simSlot = if (jsonObject.has("simSlot") && !jsonObject.isNull("simSlot")) {
                    jsonObject.getInt("simSlot")
                } else null

                val inserted = systemCallLogWriter.insertCallLogEntry(
                    phoneNumber = phoneNumber.trim(),
                    timestamp = timestamp,
                    duration = duration,
                    callType = callType,
                    simSlot = simSlot
                )
                if (inserted) importedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error importing call log entry", e)
            }
        }

        importedCount
    }

    private fun getCallTypeInt(callTypeName: String): Int {
        return when (callTypeName) {
            "INCOMING" -> 1
            "OUTGOING" -> 2
            "MISSED" -> 3
            "REJECTED" -> 5
            "BLOCKED" -> 6
            else -> 1
        }
    }
}
