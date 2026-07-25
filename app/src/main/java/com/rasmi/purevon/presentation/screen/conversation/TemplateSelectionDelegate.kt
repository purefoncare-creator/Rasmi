package com.rasmi.purevon.presentation.screen.conversation

import android.util.Log
import com.rasmi.purevon.domain.model.MessageTemplate
import com.rasmi.purevon.domain.usecase.message.AddTemplateUseCase
import com.rasmi.purevon.domain.usecase.message.DeleteTemplateUseCase
import com.rasmi.purevon.domain.usecase.message.UpdateTemplateUsageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Handles template selection, creation, and deletion in conversation UI.
 * Extracted from ConversationViewModel to reduce class size.
 */
internal class TemplateSelectionDelegate(
    private val context: android.content.Context,
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope,
    private val addTemplateUseCase: AddTemplateUseCase,
    private val updateTemplateUsageUseCase: UpdateTemplateUsageUseCase,
    private val deleteTemplateUseCase: DeleteTemplateUseCase
) {
    companion object {
        private const val TAG = "ConversationViewModel"
    }

    /**
     * Select and insert template into message
     */
    fun selectTemplate(template: MessageTemplate) {
        viewModelScope.launch {
            try {
                updateTemplateUsageUseCase(template.id)

                val currentText = _uiState.value.messageText
                val newText = if (currentText.isBlank()) {
                    template.content
                } else {
                    "$currentText\n\n${template.content}"
                }

                _uiState.update {
                    it.copy(
                        messageText = newText,
                        showTemplateDialog = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting template", e)
                _uiState.update { it.copy(showTemplateDialog = false) }
            }
        }
    }

    /**
     * Delete template
     */
    fun deleteTemplate(template: MessageTemplate) {
        viewModelScope.launch {
            try {
                deleteTemplateUseCase(template)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting template", e)
                _uiState.update { it.copy(error = e.message ?: "Failed to delete template") }
            }
        }
    }

    /**
     * Create a new template and save it
     */
    fun createTemplate(title: String, content: String, emoji: String?) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            try {
                val template = MessageTemplate(
                    title = title.trim(),
                    content = content.trim(),
                    emoji = emoji?.takeIf { it.isNotBlank() },
                    category = "custom"
                )
                addTemplateUseCase(template)
                _uiState.update { it.copy(showCreateTemplateDialog = false, showTemplateDialog = true) }
                Log.d(TAG, "✅ Template created: $title")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating template", e)
                _uiState.update { it.copy(showCreateTemplateDialog = false, error = context.getString(com.rasmi.purevon.R.string.msg_template_delete_failed)) }
            }
        }
    }
}
