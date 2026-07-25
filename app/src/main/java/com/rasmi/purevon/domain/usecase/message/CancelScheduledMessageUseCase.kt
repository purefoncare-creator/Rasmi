package com.rasmi.purevon.domain.usecase.message

import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.util.message.MessageScheduler
import javax.inject.Inject

/**
 * Use case for canceling scheduled messages
 */
class CancelScheduledMessageUseCase @Inject constructor(
    private val messageScheduler: MessageScheduler
) {
    
    /**
     * Cancel a scheduled message
     * 
     * @param messageId ID of the message to cancel
     * @return MessageResult indicating success or failure
     */
    operator fun invoke(messageId: Long): MessageResult<Unit> {
        if (messageId <= 0) {
            return MessageResult.Failure(MessageError.UnknownError(IllegalArgumentException("Invalid message ID")))
        }
        
        return MessageResult.fromResult(messageScheduler.cancelScheduledMessage(messageId))
    }
}
