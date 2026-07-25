package com.rasmi.purevon.util.error

/**
 * Represents a user-facing error with type classification and suggested action.
 */
data class AppError(
    val message: String,
    val type: ErrorType,
    val action: ErrorAction? = null
)

/**
 * Classification of error types for icon/analytics mapping.
 */
enum class ErrorType {
    PERMISSION,
    NETWORK,
    DATABASE,
    VALIDATION,
    STATE,
    BUSINESS_LOGIC,
    UNKNOWN
}

/**
 * Suggested user action in response to an error.
 */
enum class ErrorAction {
    RETRY,
    REQUEST_PERMISSION,
    GO_TO_SETTINGS,
    RESTART_APP,
    DISMISS
}
