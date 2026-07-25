package com.rasmi.purevon.domain.usecase.message

import android.util.Log
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.MessageRepository
import javax.inject.Inject

class DeleteMessageUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(messageId: Long) {
        try {
            repository.deleteMessage(messageId)
        } catch (e: Exception) {
            Log.e("DeleteMessageUseCase", "Failed to delete message $messageId", e)
            throw e
        }
    }
}

class ToggleMessageStarredUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(messageId: Long) {
        try {
            repository.toggleMessageStarred(messageId)
        } catch (e: Exception) {
            Log.e("ToggleMessageStarredUseCase", "Failed to toggle starred for $messageId", e)
            throw e
        }
    }
}

class RetryFailedMessageUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(messageId: Long): MessageResult<Long> {
        return repository.retryFailedMessage(messageId)
    }
}

class SendMmsMessageUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(
        phoneNumber: String,
        message: String?,
        attachmentUris: List<String>,
        simSlot: Int?
    ): MessageResult<Long> {
        return repository.sendMmsMessage(phoneNumber, message, attachmentUris, simSlot)
    }
}
