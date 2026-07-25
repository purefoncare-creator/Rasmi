package com.rasmi.purevon.data.repository

import android.util.Log
import com.rasmi.purevon.data.local.dao.MessageTemplateDao
import com.rasmi.purevon.data.local.entity.DefaultTemplates
import com.rasmi.purevon.data.local.entity.MessageTemplateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Handles message template CRUD operations.
 * Extracted from MessageRepositoryImpl to reduce class size.
 */
internal class MessageTemplateDelegate(
    private val messageTemplateDao: MessageTemplateDao
) {
    companion object {
        private const val TAG = "MessageRepository"
    }

    @Volatile
    private var defaultTemplatesSeeded = false

    fun getAllTemplates(): Flow<List<MessageTemplateEntity>> = flow {
        // Seed default templates on first run (once per app session)
        if (!defaultTemplatesSeeded) {
            val count = messageTemplateDao.getTemplateCount()
            if (count == 0) {
                Log.d(TAG, "🌱 Seeding ${DefaultTemplates.TEMPLATES.size} default templates")
                messageTemplateDao.insertTemplates(DefaultTemplates.TEMPLATES)
            }
            defaultTemplatesSeeded = true
        }
        emitAll(messageTemplateDao.getAllTemplates())
    }.flowOn(Dispatchers.IO)

    suspend fun addTemplate(template: MessageTemplateEntity) = withContext(Dispatchers.IO) {
        messageTemplateDao.insertTemplate(template)
        Log.d(TAG, "✅ Template added: ${template.title}")
    }

    suspend fun updateTemplateUsage(templateId: Long) = withContext(Dispatchers.IO) {
        try {
            val template = messageTemplateDao.getTemplateById(templateId)
            template?.let {
                messageTemplateDao.updateTemplate(
                    it.copy(
                        useCount = it.useCount + 1,
                        lastUsed = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating template usage", e)
        }
    }

    suspend fun deleteTemplate(template: MessageTemplateEntity) = withContext(Dispatchers.IO) {
        messageTemplateDao.deleteTemplate(template)
    }
}
