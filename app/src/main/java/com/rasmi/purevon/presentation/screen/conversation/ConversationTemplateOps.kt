package com.rasmi.purevon.presentation.screen.conversation

import com.rasmi.purevon.domain.usecase.message.GetAllTemplatesUseCase
import com.rasmi.purevon.domain.usecase.message.AddTemplateUseCase
import com.rasmi.purevon.domain.usecase.message.UpdateTemplateUsageUseCase
import com.rasmi.purevon.domain.usecase.message.DeleteTemplateUseCase
import javax.inject.Inject

/**
 * Aggregate holder for message template CRUD use cases.
 * Injected into [ConversationViewModel] to reduce constructor parameter count (Finding D).
 */
class ConversationTemplateOps @Inject constructor(
    val getAllTemplates: GetAllTemplatesUseCase,
    val addTemplate: AddTemplateUseCase,
    val updateTemplateUsage: UpdateTemplateUsageUseCase,
    val deleteTemplate: DeleteTemplateUseCase,
)
