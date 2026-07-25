package com.rasmi.purevon.presentation.screen.conversation

import com.rasmi.purevon.data.local.dao.ScheduledMessageDao
import com.rasmi.purevon.domain.usecase.message.SaveScheduledMessageUseCase
import com.rasmi.purevon.util.message.MessageScheduler
import javax.inject.Inject

/**
 * Aggregate holder for scheduled-message dependencies.
 * Injected into [ConversationViewModel] to reduce constructor parameter count (Finding D).
 */
class ConversationScheduleOps @Inject constructor(
    val saveScheduledMessage: SaveScheduledMessageUseCase,
    val messageScheduler: MessageScheduler,
    val scheduledMessageDao: ScheduledMessageDao,
)
