package com.rasmi.purevon.domain.repository

/**
 * Domain repository interface for conversation preferences (pin, mute, archive).
 * Decouples domain use cases from the data layer (DAO/Room entities).
 */
interface ConversationPreferencesRepository {

    /** Toggle pin status for a conversation. Returns new isPinned value. */
    suspend fun togglePin(threadId: Long): Boolean

    /** Mute a conversation. mutedUntil = null means muted forever. */
    suspend fun setMuted(threadId: Long, muted: Boolean, mutedUntil: Long? = null)

    /** Check if a conversation is muted. */
    suspend fun isMuted(threadId: Long): Boolean

    /** Toggle archive status. Returns new isArchived value. */
    suspend fun toggleArchive(threadId: Long): Boolean
}
