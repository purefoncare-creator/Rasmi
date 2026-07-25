package com.rasmi.purevon.data.local.entity

/**
 * Message Type Enum
 */
enum class MessageType(val value: Int) {
    SENT(2),
    RECEIVED(1),
    DRAFT(3),
    OUTBOX(4),
    FAILED(5),
    QUEUED(6);
    
    companion object {
        fun fromInt(value: Int): MessageType {
            return entries.find { it.value == value } ?: RECEIVED
        }
    }
}
