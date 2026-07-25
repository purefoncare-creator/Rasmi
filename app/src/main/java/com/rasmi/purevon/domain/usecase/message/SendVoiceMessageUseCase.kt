package com.rasmi.purevon.domain.usecase.message

import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.domain.service.AppFileProvider
import com.rasmi.purevon.domain.service.StringProvider
import com.rasmi.purevon.util.SoundManager
import java.io.File
import javax.inject.Inject

/**
 * Use case to send voice message as MMS
 */
class SendVoiceMessageUseCase @Inject constructor(
    private val appFileProvider: AppFileProvider,
    private val stringProvider: StringProvider,
    private val messageRepository: MessageRepository,
    private val soundManager: SoundManager
) {
    /**
     * Send voice message as MMS
     * 
     * @param phoneNumber Recipient's phone number
     * @param audioFile Audio file to send
     * @param simSlot Optional SIM slot for dual SIM devices
     * @return MessageResult with message ID or error
     */
    suspend operator fun invoke(
        phoneNumber: String,
        audioFile: File,
        simSlot: Int? = null
    ): MessageResult<Long> {
        if (phoneNumber.isBlank()) {
            return MessageResult.Failure(MessageError.InvalidNumberError(phoneNumber, "Phone number is empty"))
        }
        
        if (!audioFile.exists()) {
            return MessageResult.Failure(MessageError.AttachmentError(audioFile.name, "Audio file not found: ${audioFile.absolutePath}"))
        }
        
        // Convert file to content URI using FileProvider
        val audioUri = try {
            appFileProvider.getContentUri(audioFile)
        } catch (e: Exception) {
            return MessageResult.Failure(MessageError.AttachmentError(audioFile.name, e.message ?: "Unknown error"))
        }
        
        // Send as MMS with audio attachment
        val result = messageRepository.sendMmsMessage(
            phoneNumber = phoneNumber,
            message = stringProvider.getString(com.rasmi.purevon.R.string.msg_voice_message), // Optional text
            attachmentUris = listOf(audioUri),
            simSlot = simSlot
        )
        
        // Play message sent sound
        result.onSuccess {
            soundManager.playMessageSentSound()
        }
        
        return result
    }
    
    /**
     * Send voice message with custom message text
     */
    suspend fun sendWithMessage(
        phoneNumber: String,
        audioFile: File,
        messageText: String?,
        simSlot: Int? = null
    ): MessageResult<Long> {
        if (phoneNumber.isBlank() || !audioFile.exists()) {
            return MessageResult.Failure(MessageError.InvalidNumberError(phoneNumber, "Invalid input"))
        }
        
        val audioUri = try {
            appFileProvider.getContentUri(audioFile)
        } catch (e: Exception) {
            return MessageResult.Failure(MessageError.AttachmentError(audioFile.name, e.message ?: "Unknown error"))
        }
        
        val result = messageRepository.sendMmsMessage(
            phoneNumber = phoneNumber,
            message = messageText,
            attachmentUris = listOf(audioUri),
            simSlot = simSlot
        )
        
        result.onSuccess {
            soundManager.playMessageSentSound()
        }
        
        return result
    }
}
