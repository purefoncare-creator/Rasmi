package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.rasmi.purevon.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages message draft persistence via SharedPreferences.
 * Extracted from ConversationViewModel to reduce class size.
 */
internal class DraftManager(context: Context) {

    companion object {
        private const val TAG = "ConversationViewModel"
    }

    private val draftPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("purevon_drafts", Context.MODE_PRIVATE)
    }

    /** Save draft text for a key (threadId or phoneNumber). Must be called from a coroutine on IO. */
    fun saveDraft(key: String, text: String, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                if (text.isBlank()) {
                    draftPrefs.edit().remove("draft_$key").apply()
                } else {
                    draftPrefs.edit().putString("draft_$key", text).apply()
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Draft saved for $key")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception saving draft", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Illegal state saving draft", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error saving draft", e)
            }
        }
    }

    /**
     * ✅ FIXED: Save draft synchronously — used in onCleared() where viewModelScope is already cancelled.
     * Uses commit() instead of apply() to guarantee write completes before the method returns.
     */
    fun saveDraftSync(key: String, text: String) {
        try {
            if (text.isBlank()) {
                draftPrefs.edit().remove("draft_$key").commit()
            } else {
                draftPrefs.edit().putString("draft_$key", text).commit()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save draft synchronously", e)
        }
    }

    /** Clear draft for a key. Must be called from a coroutine on IO. */
    fun clearDraft(key: String, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                draftPrefs.edit().remove("draft_$key").apply()
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Draft cleared for $key")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear draft", e)
            }
        }
    }

    /** Load draft (synchronous, call from IO context). Returns null if no draft saved. */
    fun loadDraft(key: String): String? {
        return try {
            draftPrefs.getString("draft_$key", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load draft", e)
            null
        }
    }
}
