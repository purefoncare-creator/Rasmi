package com.rasmi.purevon.util

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.view.Window
import androidx.appcompat.app.AppCompatDelegate

/**
 * Lightweight synchronous accessor for the appearance preferences
 * (autoTheme / isDarkMode).
 *
 * The authoritative values live in [com.rasmi.purevon.data.preferences.SettingsDataStore],
 * but DataStore is asynchronous. We mirror the booleans here in a tiny SharedPreferences
 * file so the app's night mode can be forced synchronously in Activity.onCreate —
 * before the OS draws the starting window — so the window/splash background always
 * matches the app's actual theme and no white/black flash appears before Compose renders.
 */
object ThemePreferences {

    private const val PREFS_NAME = "purevon_theme_prefs"
    private const val KEY_AUTO = "auto_theme"
    private const val KEY_DARK = "dark_mode"

    /** Matches DarkBackground (#0F1117) in the Compose theme. */
    private const val DARK_BG = 0xFF0F1117.toInt()

    /** Matches LightBackground (#F0F1F5) in the Compose theme. */
    private const val LIGHT_BG = 0xFFF0F1F5.toInt()

    fun isAutoTheme(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO, true)

    fun isDarkMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, false)

    fun setAutoTheme(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO, enabled)
            .apply()
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, enabled)
            .apply()
    }

    /**
     * Forces the activity night mode to match the app's theme setting so the
     * starting window / splash background uses the correct values-night or
     * values/ theme before Compose takes over.
     *
     * Must be called before [android.app.Activity.super] onCreate, right after
     * installSplashScreen().
     *
     * On API 31+ it also syncs [UiModeManager.setApplicationNightMode] so the
     * SYSTEM draws the starting window / splash with the app's theme, not the
     * system-wide dark mode (fixes white/black flash even on cold starts).
     */
    fun applyAppNightMode(context: Context) {
        val mode = if (isAutoTheme(context)) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        } else {
            if (isDarkMode(context)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        }
        applyNightMode(context, mode)
    }

    /**
     * Forces the activity into dark night mode. Used by screens whose Compose
     * theme is always dark (e.g. FakeInCallActivity).
     */
    fun applyForceDarkNightMode(context: Context) {
        applyNightMode(context, AppCompatDelegate.MODE_NIGHT_YES)
    }

    private fun applyNightMode(context: Context, appCompatMode: Int) {
        AppCompatDelegate.setDefaultNightMode(appCompatMode)
        syncSystemNightMode(context, appCompatMode)
    }

    /**
     * Syncs the app's theme setting to the system-level application night mode
     * (UiModeManager.setApplicationNightMode). On API 31+ the SYSTEM resolves the
     * starting window / splash background using this per-app value, so calling it
     * the moment the user changes the theme ensures the next activity launch or
     * cold start is drawn with the correct background (no white/black flash).
     *
     * Called from Activity.onCreate AND from the settings screen when the theme
     * is toggled (so the value is persisted before the next starting window).
     */
    fun syncSystemNightMode(context: Context) {
        val mode = if (isAutoTheme(context)) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        } else {
            if (isDarkMode(context)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        }
        syncSystemNightMode(context, mode)
    }

    private fun syncSystemNightMode(context: Context, appCompatMode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiMode = when (appCompatMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> UiModeManager.MODE_NIGHT_NO
                AppCompatDelegate.MODE_NIGHT_YES -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            context.getSystemService(UiModeManager::class.java)
                ?.setApplicationNightMode(uiMode)
        }
    }

    /**
     * Returns the background color the app's own window should use before
     * Compose draws its first frame — based on the APP theme, not the system.
     */
    fun appBackgroundColor(context: Context): Int =
        if (isAppDark(context)) DARK_BG else LIGHT_BG

    fun isAppDark(context: Context): Boolean =
        if (isAutoTheme(context)) {
            val uiMode = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        } else {
            isDarkMode(context)
        }

    /**
     * Paints the activity's own window with the app-theme background before
     * setContent() runs, so anything shown between window creation and the first
     * Compose frame is already the correct color (no wrong-color flash).
     */
    fun applyWindowBackground(window: Window, context: Context) {
        window.decorView.setBackgroundColor(appBackgroundColor(context))
    }
}
