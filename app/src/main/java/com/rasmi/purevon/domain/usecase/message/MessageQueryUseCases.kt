package com.rasmi.purevon.domain.usecase.message

import android.net.Uri
import androidx.paging.PagingData
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMessagesByThreadPagedUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(threadId: Long): Flow<PagingData<Message>> {
        return repository.getMessagesByThreadPaged(threadId)
    }
}

class GetAddressFromThreadIdUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(threadId: Long): String? {
        return repository.getAddressFromThreadId(threadId)
    }
}

class GetOrCreateThreadIdUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(phoneNumber: String): Long {
        return repository.getOrCreateThreadIdForNumber(phoneNumber)
    }
}

class GetMessageChangeEventsUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(): Flow<Unit> {
        return repository.getMessageChangeEvents()
    }
}

class SyncThreadMessagesUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(threadId: Long) {
        repository.syncMessages(threadId)
    }
}

class SaveScheduledMessageUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(
        phoneNumber: String,
        message: String,
        scheduledTimeMillis: Long,
        attachmentUris: List<String> = emptyList(),
        simSlot: Int? = null,
        repeatInterval: com.rasmi.purevon.domain.model.RepeatInterval = com.rasmi.purevon.domain.model.RepeatInterval.NONE
    ): Long {
        return repository.scheduleMessage(phoneNumber, message, scheduledTimeMillis, attachmentUris, simSlot, repeatInterval)
    }
}
