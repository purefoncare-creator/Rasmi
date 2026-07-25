package com.rasmi.purevon.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.rasmi.purevon.MainActivity
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Conversation

/**
 * App Shortcuts Manager
 * إدارة اختصارات التطبيق (الاختصارات طويلة الضغط على أيقونة التطبيق)
 */
@RequiresApi(Build.VERSION_CODES.N_MR1)
class AppShortcutsManager(private val context: Context) {
    
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    
    companion object {
        private const val TAG = "AppShortcutsManager"
        private const val SHORTCUT_ID_NEW_MESSAGE = "shortcut_new_message"
        private const val SHORTCUT_ID_RECENT_PREFIX = "shortcut_recent_"
        private const val MAX_SHORTCUTS = 4
    }
    
    /**
     * تحديث الاختصارات الديناميكية
     * يعرض أحدث المحادثات كاختصارات
     */
    fun updateDynamicShortcuts(recentConversations: List<Conversation>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        
        try {
            val shortcuts = mutableListOf<ShortcutInfo>()
            
            // اختصار لإنشاء رسالة جديدة
            shortcuts.add(createNewMessageShortcut())
            
            // اختصارات للمحادثات الأخيرة (3 محادثات كحد أقصى)
            recentConversations.take(3).forEachIndexed { index, conversation ->
                shortcuts.add(createConversationShortcut(conversation, index))
            }
            
            shortcutManager?.dynamicShortcuts = shortcuts
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing dynamic shortcuts", e)
        }
    }
    
    /**
     * إنشاء اختصار لرسالة جديدة
     */
    private fun createNewMessageShortcut(): ShortcutInfo {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("action", "new_message")
        }
        
        return ShortcutInfo.Builder(context, SHORTCUT_ID_NEW_MESSAGE)
            .setShortLabel(context.getString(R.string.shortcut_new_message))
            .setLongLabel(context.getString(R.string.shortcut_create_new_message))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
            .setIntent(intent)
            .setRank(0)
            .build()
    }
    
    /**
     * إنشاء اختصار لمحادثة معينة
     */
    private fun createConversationShortcut(conversation: Conversation, rank: Int): ShortcutInfo {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("thread_id", conversation.threadId)
        }
        
        return ShortcutInfo.Builder(context, "$SHORTCUT_ID_RECENT_PREFIX${conversation.threadId}")
            .setShortLabel(conversation.contactName?.take(10) ?: conversation.phoneNumber.take(10))
            .setLongLabel(conversation.contactName ?: conversation.phoneNumber)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
            .setIntent(intent)
            .setRank(rank + 1)
            .build()
    }
    
    /**
     * إزالة اختصار معين
     */
    fun removeShortcut(threadId: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        
        try {
            shortcutManager?.removeDynamicShortcuts(
                listOf("$SHORTCUT_ID_RECENT_PREFIX$threadId")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error removing shortcuts", e)
        }
    }
    
    /**
     * إزالة كل الاختصارات الديناميكية
     */
    fun removeAllShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        
        try {
            shortcutManager?.removeAllDynamicShortcuts()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing all shortcuts", e)
        }
    }
    
    /**
     * Push shortcut - عرض محادثة في قائمة الاختصارات المشاركة
     * ✅ FIX: pushDynamicShortcut() requires API 30 — guard with version check.
     * On API 25-29, NoSuchMethodError (which is an Error, not Exception) would crash
     * without being caught by the generic catch block.
     */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun pushConversationShortcut(conversation: Conversation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return // API 30
        try {
            val shortcut = createConversationShortcut(conversation, 0)
            shortcutManager?.pushDynamicShortcut(shortcut)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating shortcut", e)
        }
    }
    
    /**
     * التحقق من دعم الاختصارات
     */
    fun areShortcutsSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
    }
}
