package com.rasmi.purevon.domain.usecase.calllog

import com.rasmi.purevon.domain.service.AppFileProvider
import com.rasmi.purevon.domain.usecase.call.GetAllCallLogsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Export call logs to JSON file
 */
class ExportCallLogsUseCase @Inject constructor(
    private val appFileProvider: AppFileProvider,
    private val getAllCallLogsUseCase: GetAllCallLogsUseCase
) {
    suspend operator fun invoke(fileName: String? = null): File = withContext(Dispatchers.IO) {
        val callLogs = getAllCallLogsUseCase().first()
        val jsonArray = JSONArray()
        
        callLogs.forEach { callLog ->
            val jsonObject = JSONObject().apply {
                put("id", callLog.id)
                put("phoneNumber", callLog.phoneNumber)
                put("contactName", callLog.contactName)
                put("callType", callLog.callType.name)
                put("timestamp", callLog.timestamp)
                put("duration", callLog.duration)
                put("simSlot", callLog.simSlot)
                put("isSpam", callLog.isSpam)
                put("spamScore", callLog.spamScore)
            }
            jsonArray.put(jsonObject)
        }
        
        val backupFileName = fileName ?: "purevon_call_logs_${getCurrentTimestamp()}.json"
        val backupFile = File(appFileProvider.getExternalFilesDir(), backupFileName)
        backupFile.writeText(jsonArray.toString(2))
        
        backupFile
    }
    
    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
}
