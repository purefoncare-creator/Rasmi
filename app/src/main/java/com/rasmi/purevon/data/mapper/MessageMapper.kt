package com.rasmi.purevon.data.mapper

import com.rasmi.purevon.data.local.entity.CachedMessageEntity
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.MessageStatus

// ✅ Mappers for Message <-> CachedMessageEntity

fun CachedMessageEntity.toDomain(): Message {
    return Message(
        id = this.id, // Fixed: use 'id' not 'messageId' as defined in Entity
        threadId = this.threadId,
        phoneNumber = this.phoneNumber,
        contactName = this.contactName,
        body = this.body,
        timestamp = this.timestamp,
        type = this.type,
        category = this.category,
        isRead = this.isRead,
        isSent = this.isSent,
        isDelivered = this.isDelivered,
        simSlot = this.simSlot,
        isSpam = this.isSpam,
        spamScore = this.spamScore,
        isMms = this.isMms,
        attachmentUris = this.attachmentUris,
        attachmentTypes = this.attachmentTypes,
        status = this.status?.let { str -> 
            try { MessageStatus.valueOf(str) } catch (e: Exception) { null } 
        },
        isScheduled = this.isScheduled,
        scheduledTime = this.scheduledTime,
        scheduleId = this.scheduleId
    )
}

fun Message.toEntity(): CachedMessageEntity {
    return CachedMessageEntity(
        id = this.id,
        threadId = this.threadId,
        phoneNumber = this.phoneNumber,
        contactName = this.contactName,
        body = this.body,
        timestamp = this.timestamp,
        type = this.type,
        category = this.category,
        isRead = this.isRead,
        isSent = this.isSent,
        isDelivered = this.isDelivered,
        simSlot = this.simSlot,
        isSpam = this.isSpam,
        spamScore = this.spamScore,
        isMms = this.isMms,
        attachmentUris = this.attachmentUris,
        attachmentTypes = this.attachmentTypes,
        status = this.status?.name,
        isScheduled = this.isScheduled,
        scheduledTime = this.scheduledTime,
        scheduleId = this.scheduleId
    )
}
