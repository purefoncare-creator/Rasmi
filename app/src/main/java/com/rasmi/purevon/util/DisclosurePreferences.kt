package com.rasmi.purevon.util

import android.content.Context

/**
 * Synchronous accessor for the prominent data-disclosure consent flag,
 * required by Google Play's User Data policy. The flag must be checked
 * before MainActivity composes any UI that triggers data access (contacts,
 * call log, SMS, phone numbers).
 */
object DisclosurePreferences {

    private const val PREFS_NAME = "purevon_disclosure_prefs"
    private const val KEY_ACCEPTED = "data_disclosure_accepted_v2"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCEPTED, false)

    fun setAccepted(context: Context, accepted: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCEPTED, accepted)
            .apply()
    }
}
