package com.rasmi.purevon.presentation.screen.conversation

import com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
import com.rasmi.purevon.domain.usecase.contact.GetContactByNumberUseCase
import com.rasmi.purevon.domain.usecase.message.GetAllConversationsUseCase
import com.rasmi.purevon.domain.usecase.message.ToggleReactionUseCase
import com.rasmi.purevon.domain.usecase.message.RemoveReactionUseCase
import com.rasmi.purevon.util.SoundManager
import javax.inject.Inject

/**
 * Aggregate holder for contact/conversation lookup and reaction use cases.
 * Injected into [ConversationViewModel] to reduce constructor parameter count (Finding D).
 */
class ConversationContactOps @Inject constructor(
    val getAllContacts: GetAllContactsUseCase,
    val getContactByNumber: GetContactByNumberUseCase,
    val getAllConversations: GetAllConversationsUseCase,
    val toggleReaction: ToggleReactionUseCase,
    val removeReaction: RemoveReactionUseCase,
    val soundManager: SoundManager,
)
