package com.rasmi.purevon.domain.usecase.message

import com.rasmi.purevon.domain.model.MessageTemplate
import com.rasmi.purevon.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllTemplatesUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(): Flow<List<MessageTemplate>> {
        return repository.getAllTemplates()
    }
}

class AddTemplateUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(template: MessageTemplate) {
        repository.addTemplate(template)
    }
}

class UpdateTemplateUsageUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(templateId: Long) {
        repository.updateTemplateUsage(templateId)
    }
}

class DeleteTemplateUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(template: MessageTemplate) {
        repository.deleteTemplate(template)
    }
}
