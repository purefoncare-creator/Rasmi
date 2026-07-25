package com.rasmi.purevon.domain.usecase.message

import android.util.Log
import com.rasmi.purevon.domain.repository.MessageRepository
import javax.inject.Inject

class ClearAllMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke() {
        try {
            messageRepository.clearAllMessages()
        } catch (e: Exception) {
            Log.e("ClearAllMessagesUseCase", "Failed to clear all messages", e)
            throw e
        }
    }
}
