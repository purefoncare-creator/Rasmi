package com.rasmi.purevon.data.mapper

import com.rasmi.purevon.data.local.entity.MessageTemplateEntity
import com.rasmi.purevon.domain.model.MessageTemplate
import com.rasmi.purevon.domain.model.RepeatInterval as DomainRepeatInterval
import com.rasmi.purevon.data.local.entity.RepeatInterval as DataRepeatInterval

// ============================================
// MessageTemplate ↔ MessageTemplateEntity
// ============================================

fun MessageTemplateEntity.toDomain(): MessageTemplate = MessageTemplate(
    id = id,
    title = title,
    content = content,
    category = category,
    emoji = emoji,
    useCount = useCount,
    createdAt = createdAt,
    lastUsed = lastUsed,
    isFavorite = isFavorite
)

fun MessageTemplate.toEntity(): MessageTemplateEntity = MessageTemplateEntity(
    id = id,
    title = title,
    content = content,
    category = category,
    emoji = emoji,
    useCount = useCount,
    createdAt = createdAt,
    lastUsed = lastUsed,
    isFavorite = isFavorite
)

// ============================================
// RepeatInterval domain ↔ data
// ============================================

fun DomainRepeatInterval.toEntity(): DataRepeatInterval = when (this) {
    DomainRepeatInterval.NONE -> DataRepeatInterval.NONE
    DomainRepeatInterval.DAILY -> DataRepeatInterval.DAILY
    DomainRepeatInterval.WEEKLY -> DataRepeatInterval.WEEKLY
    DomainRepeatInterval.MONTHLY -> DataRepeatInterval.MONTHLY
}

fun DataRepeatInterval.toDomain(): DomainRepeatInterval = when (this) {
    DataRepeatInterval.NONE -> DomainRepeatInterval.NONE
    DataRepeatInterval.DAILY -> DomainRepeatInterval.DAILY
    DataRepeatInterval.WEEKLY -> DomainRepeatInterval.WEEKLY
    DataRepeatInterval.MONTHLY -> DomainRepeatInterval.MONTHLY
}
