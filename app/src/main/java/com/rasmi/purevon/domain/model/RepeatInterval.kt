package com.rasmi.purevon.domain.model

/**
 * Domain model for message scheduling repeat interval.
 * Decoupled from data layer (Room entity).
 */
enum class RepeatInterval {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}
