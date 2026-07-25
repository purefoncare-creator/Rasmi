package com.rasmi.purevon.data.local.entity

import kotlinx.serialization.Serializable

@Serializable
data class MultipartMessageStatus(
    val totalParts: Int,
    val parts: List<MessagePartStatus>
)

@Serializable
data class MessagePartStatus(
    val partIndex: Int,
    val isSent: Boolean,
    val errorCode: Int? = null // Optional: store error code
)
