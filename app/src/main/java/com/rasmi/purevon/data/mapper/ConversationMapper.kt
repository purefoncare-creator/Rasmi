package com.rasmi.purevon.data.mapper

import com.rasmi.purevon.data.local.entity.ConversationPreferencesEntity
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.model.ConversationData

/**
 * Mapper for converting between data layer and domain layer models
 */
object ConversationMapper {
    
    /**
     * Map ConversationData to Conversation
     */
    fun toConversation(
        data: ConversationData,
        preferences: ConversationPreferencesEntity? = null
    ): Conversation {
        return Conversation(
            threadId = data.threadId,
            phoneNumber = data.phoneNumber,
            contactName = null,
            contactPhotoUri = null,
            lastMessage = data.lastMessageBody ?: "",
            lastMessageTimestamp = data.lastMessageTimestamp,
            lastMessageType = if (data.lastMessageType == 1) "received" else "sent",
            unreadCount = data.unreadCount,
            messageCount = data.messageCount,
            isPinned = preferences?.isPinned ?: false,
            isMuted = preferences?.isMuted ?: false,
            isArchived = preferences?.isArchived ?: false,
            isGroup = false,
            groupParticipants = emptyList()
        )
    }
    
    /**
     * Map list of ConversationData to list of Conversations
     */
    fun toConversations(
        dataList: List<ConversationData>,
        preferencesMap: Map<Long, ConversationPreferencesEntity> = emptyMap()
    ): List<Conversation> {
        return dataList.map { data ->
            toConversation(data, preferencesMap[data.threadId])
        }
    }
}

// ✅ EXTENSION FUNCTIONS needed by MessageRepositoryImpl

fun com.rasmi.purevon.data.local.entity.CachedConversationEntity.toDomain(): Conversation {
    return Conversation(
        threadId = this.threadId,
        phoneNumber = this.phoneNumber,
        contactName = this.contactName,
        contactPhotoUri = this.contactPhotoUri,
        lastMessage = this.lastMessage,
        lastMessageTimestamp = this.lastMessageTimestamp,
        lastMessageType = this.lastMessageType,
        unreadCount = this.unreadCount,
        messageCount = this.messageCount,
        isPinned = this.isPinned,
        isMuted = this.isMuted,
        isArchived = this.isArchived,
        isGroup = this.isGroup,
        groupParticipants = this.groupParticipants
    )
}

fun Conversation.toEntity(): com.rasmi.purevon.data.local.entity.CachedConversationEntity {
    return com.rasmi.purevon.data.local.entity.CachedConversationEntity(
        threadId = this.threadId,
        phoneNumber = this.phoneNumber,
        contactName = this.contactName,
        contactPhotoUri = this.contactPhotoUri,
        lastMessage = this.lastMessage,
        lastMessageTimestamp = this.lastMessageTimestamp,
        lastMessageType = this.lastMessageType,
        unreadCount = this.unreadCount,
        messageCount = this.messageCount,
        isPinned = this.isPinned,
        isMuted = this.isMuted,
        isArchived = this.isArchived,
        isGroup = this.isGroup,
        groupParticipants = this.groupParticipants
    )
}
