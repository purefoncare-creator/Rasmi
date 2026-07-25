package com.rasmi.purevon.util

import android.content.Context
import android.net.Uri

object FakeCallScheduleManager {

    private const val PREFS_NAME = "fake_call_schedule"
    private const val KEY_SCHEDULED = "scheduled_calls"
    private const val SEPARATOR_ENTRY = "\n"
    private const val SEPARATOR_FIELD = "|"

    data class ScheduledFakeCall(
        val requestCode: Int,
        val callerName: String,
        val triggerTimeMs: Long
    )

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(context: Context, requestCode: Int, callerName: String, triggerTimeMs: Long) {
        val prefs = getPrefs(context)
        val existing = loadRaw(prefs)
        val entry = "${requestCode}$SEPARATOR_FIELD${Uri.encode(callerName)}$SEPARATOR_FIELD${triggerTimeMs}"
        val updated = (existing + entry).joinToString(SEPARATOR_ENTRY)
        prefs.edit().putString(KEY_SCHEDULED, updated).apply()
    }

    fun remove(context: Context, requestCode: Int) {
        val prefs = getPrefs(context)
        val updated = loadRaw(prefs)
            .filter { entry ->
                val code = entry.split(SEPARATOR_FIELD).firstOrNull()?.toIntOrNull()
                code != requestCode
            }
            .joinToString(SEPARATOR_ENTRY)
        prefs.edit().putString(KEY_SCHEDULED, updated).apply()
    }

    fun removeExpired(context: Context) {
        val now = System.currentTimeMillis()
        val prefs = getPrefs(context)
        val updated = loadRaw(prefs)
            .filter { entry ->
                val parts = entry.split(SEPARATOR_FIELD)
                val triggerTime = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                triggerTime > now
            }
            .joinToString(SEPARATOR_ENTRY)
        prefs.edit().putString(KEY_SCHEDULED, updated).apply()
    }

    fun getAll(context: Context): List<ScheduledFakeCall> {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        return loadRaw(prefs)
            .mapNotNull { entry ->
                val parts = entry.split(SEPARATOR_FIELD)
                if (parts.size >= 3) {
                    val code = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val name = Uri.decode(parts[1])
                    val time = parts[2].toLongOrNull() ?: return@mapNotNull null
                    if (time > now) ScheduledFakeCall(code, name, time) else null
                } else null
            }
            .sortedBy { it.triggerTimeMs }
    }

    private fun loadRaw(prefs: android.content.SharedPreferences): List<String> {
        return prefs.getString(KEY_SCHEDULED, "")
            ?.split(SEPARATOR_ENTRY)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }
}
