package com.rasmi.purevon.util

import android.content.Context
import android.net.Uri

object CallbackReminderScheduleManager {

    private const val PREFS_NAME = "callback_reminder_schedule"
    private const val KEY_SCHEDULED = "scheduled_reminders"
    private const val SEP_ENTRY = "\n"
    private const val SEP_FIELD = "|"

    data class ScheduledReminder(
        val requestCode: Int,
        val phoneNumber: String,
        val contactName: String,
        val triggerTimeMs: Long
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(
        context: Context,
        requestCode: Int,
        phoneNumber: String,
        contactName: String,
        triggerTimeMs: Long
    ) {
        val existing = loadRaw(context)
        val entry = "${requestCode}$SEP_FIELD${Uri.encode(phoneNumber)}$SEP_FIELD${Uri.encode(contactName)}$SEP_FIELD${triggerTimeMs}"
        val updated = (existing + entry).joinToString(SEP_ENTRY)
        prefs(context).edit().putString(KEY_SCHEDULED, updated).apply()
    }

    fun remove(context: Context, requestCode: Int) {
        val updated = loadRaw(context)
            .filter { it.split(SEP_FIELD).firstOrNull()?.toIntOrNull() != requestCode }
            .joinToString(SEP_ENTRY)
        prefs(context).edit().putString(KEY_SCHEDULED, updated).apply()
    }

    fun removeExpired(context: Context) {
        val now = System.currentTimeMillis()
        val updated = loadRaw(context)
            .filter { entry ->
                val parts = entry.split(SEP_FIELD)
                (parts.getOrNull(3)?.toLongOrNull() ?: 0L) > now
            }
            .joinToString(SEP_ENTRY)
        prefs(context).edit().putString(KEY_SCHEDULED, updated).apply()
    }

    fun getAll(context: Context): List<ScheduledReminder> {
        val now = System.currentTimeMillis()
        return loadRaw(context).mapNotNull { entry ->
            val p = entry.split(SEP_FIELD)
            if (p.size >= 4) {
                val code = p[0].toIntOrNull() ?: return@mapNotNull null
                val time = p[3].toLongOrNull() ?: return@mapNotNull null
                if (time > now) ScheduledReminder(code, Uri.decode(p[1]), Uri.decode(p[2]), time) else null
            } else null
        }.sortedBy { it.triggerTimeMs }
    }

    private fun loadRaw(context: Context): List<String> =
        prefs(context).getString(KEY_SCHEDULED, "")
            ?.split(SEP_ENTRY)?.filter { it.isNotBlank() }
            ?: emptyList()
}
