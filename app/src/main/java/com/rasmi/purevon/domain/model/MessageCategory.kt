package com.rasmi.purevon.domain.model

/**
 * Domain-level Message Category Enum
 * Pure enum without data-layer dependencies.
 */
enum class MessageCategory {
    PERSONAL,
    TRANSACTIONS,
    PROMOTIONS,
    OTP,
    SPAM,
    UNKNOWN;

    companion object {
        fun fromString(category: String?): MessageCategory {
            return when (category?.uppercase()?.trim()) {
                "PERSONAL" -> PERSONAL
                "TRANSACTIONS" -> TRANSACTIONS
                "PROMOTIONS", "PROMOTIONAL" -> PROMOTIONS  // Both map to PROMOTIONS
                "OTP" -> OTP
                "SPAM" -> SPAM
                else -> UNKNOWN
            }
        }
    }
}
