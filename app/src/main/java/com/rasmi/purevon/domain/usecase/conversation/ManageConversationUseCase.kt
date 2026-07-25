package com.rasmi.purevon.domain.usecase.conversation

import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.ConversationPreferencesRepository
import javax.inject.Inject

/**
 * Use case for managing conversation actions (pin, mute, archive).
 * Uses ConversationPreferencesRepository — no data-layer leaks.
 */
class ManageConversationUseCase @Inject constructor(
    private val preferencesRepository: ConversationPreferencesRepository
) {
    
    /**
     * Pin/Unpin conversation
     */
    suspend fun togglePin(threadId: Long): MessageResult<Boolean> {
        return try {
            MessageResult.Success(preferencesRepository.togglePin(threadId))
        } catch (e: Exception) {
            MessageResult.Failure(MessageError.UnknownError(e))
        }
    }
    
    /**
     * Mute conversation
     * ✅ FIX 2.4: Actually compute mutedUntil from duration instead of ignoring it
     */
    suspend fun mute(threadId: Long, duration: MuteDuration): MessageResult<Unit> {
        return try {
            val mutedUntil: Long? = when (duration) {
                MuteDuration.ONE_HOUR -> System.currentTimeMillis() + 3_600_000L
                MuteDuration.EIGHT_HOURS -> System.currentTimeMillis() + 28_800_000L
                MuteDuration.ONE_WEEK -> System.currentTimeMillis() + 604_800_000L
                MuteDuration.FOREVER -> null // null means muted forever
            }
            preferencesRepository.setMuted(threadId, true, mutedUntil)
            MessageResult.Success(Unit)
        } catch (e: Exception) {
            MessageResult.Failure(MessageError.UnknownError(e))
        }
    }
    
    /**
     * Unmute conversation
     */
    suspend fun unmute(threadId: Long): MessageResult<Unit> {
        return try {
            preferencesRepository.setMuted(threadId, false)
            MessageResult.Success(Unit)
        } catch (e: Exception) {
            MessageResult.Failure(MessageError.UnknownError(e))
        }
    }
    
    /**
     * Archive/Unarchive conversation
     */
    suspend fun toggleArchive(threadId: Long): MessageResult<Boolean> {
        return try {
            MessageResult.Success(preferencesRepository.toggleArchive(threadId))
        } catch (e: Exception) {
            MessageResult.Failure(MessageError.UnknownError(e))
        }
    }
    
    /**
     * Check if conversation is muted
     */
    suspend fun isMuted(threadId: Long): Boolean {
        return preferencesRepository.isMuted(threadId)
    }
}

/**
 * Mute duration options
 */
enum class MuteDuration {
    ONE_HOUR,
    EIGHT_HOURS,
    ONE_WEEK,
    FOREVER
}
