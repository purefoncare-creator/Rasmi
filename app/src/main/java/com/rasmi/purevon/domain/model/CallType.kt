package com.rasmi.purevon.domain.model

/**
 * Domain-level Call Type Enum
 * Pure enum without Android framework dependencies.
 * For Android-specific mappings (toInt/fromInt), see data layer CallTypeMapper.
 */
enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    VOICEMAIL;

    companion object {
        private val SYSTEM_INCOMING = 1
        private val SYSTEM_OUTGOING = 2
        private val SYSTEM_MISSED = 3
        private val SYSTEM_REJECTED = 5
        private val SYSTEM_BLOCKED = 6
        private val SYSTEM_VOICEMAIL = 4

        fun fromSystemType(systemType: Int): CallType = when (systemType) {
            SYSTEM_OUTGOING -> OUTGOING
            SYSTEM_MISSED -> MISSED
            SYSTEM_REJECTED -> REJECTED
            SYSTEM_BLOCKED -> BLOCKED
            SYSTEM_VOICEMAIL -> VOICEMAIL
            else -> INCOMING
        }
    }

    fun toSystemType(): Int = when (this) {
        INCOMING -> 1
        OUTGOING -> 2
        MISSED -> 3
        REJECTED -> 5
        BLOCKED -> 6
        VOICEMAIL -> 4
    }
}
