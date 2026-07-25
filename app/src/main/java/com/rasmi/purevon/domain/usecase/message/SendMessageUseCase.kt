package com.rasmi.purevon.domain.usecase.message

import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.util.SoundManager
import javax.inject.Inject

/**
 * Use case to send a message
 */
class SendMessageUseCase @Inject constructor(
    private val repository: MessageRepository,
    private val soundManager: SoundManager
) {
    /**
     * Send a text message
     * 
     * @param phoneNumber Recipient's phone number
     * @param message Message body to send
     * @param simSlot Optional SIM slot for dual SIM devices (null for default)
     * @return MessageResult with message ID on success, or error details on failure
     */
    suspend operator fun invoke(phoneNumber: String, message: String, simSlot: Int? = null): MessageResult<Long> {
        if (phoneNumber.isBlank()) {
            return MessageResult.Failure(
                MessageError.InvalidNumberError(phoneNumber, "Phone number is empty")
            )
        }
        
        if (message.isBlank()) {
            return MessageResult.Failure(
                MessageError.EmptyBodyError()
            )
        }
        
        val result = repository.sendMessage(phoneNumber, message, simSlot)
        
        // Play message sent sound (like iMessage) on success
        result.onSuccess {
            soundManager.playMessageSentSound()
        }
        
        return result
    }
}
