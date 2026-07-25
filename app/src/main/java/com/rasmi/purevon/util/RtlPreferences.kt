package com.rasmi.purevon.util

import android.content.Context

/**
 * Lightweight synchronous accessor for the RTL layout-direction preference.
 *
 * The authoritative value lives in [com.rasmi.purevon.data.preferences.SettingsDataStore],
 * but DataStore is asynchronous. We mirror the boolean here in a tiny SharedPreferences
 * file so it can be read from places that need it synchronously
 * (e.g. composing the very first frame before the DataStore Flow emits).
 */
object RtlPreferences {

    private const val PREFS_NAME = "purevon_layout_prefs"
    private const val KEY_RTL = "rtl_enabled"

    fun isRtlEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RTL, false)

    fun setRtlEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RTL, enabled)
            .apply()
    }
}
