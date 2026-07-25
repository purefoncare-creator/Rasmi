package com.rasmi.purevon.domain.usecase.message

import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.ReactionRepository
import javax.inject.Inject

class ToggleReactionUseCase @Inject constructor(
    private val repository: ReactionRepository
) {
    suspend operator fun invoke(messageId: Long, emoji: String): MessageResult<Unit> {
        return MessageResult.fromResult(repository.toggleReaction(messageId, emoji))
    }
}

class RemoveReactionUseCase @Inject constructor(
    private val repository: ReactionRepository
) {
    suspend operator fun invoke(messageId: Long): MessageResult<Unit> {
        return MessageResult.fromResult(repository.removeReaction(messageId))
    }
}
