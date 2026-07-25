package com.rasmi.purevon.util

/**
 * Custom exception hierarchy for the application
 */
sealed class AppException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Network related exceptions
     */
    data class NetworkException(
        override val message: String = "Network error occurred",
        override val cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * Database related exceptions
     */
    data class DatabaseException(
        override val message: String = "Database error occurred",
        override val cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * Permission related exceptions
     */
    data class PermissionException(
        val permission: String,
        override val message: String = "Permission not granted: $permission",
        override val cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * Not found exceptions
     */
    data class NotFoundException(
        val itemType: String,
        override val message: String = "$itemType not found",
        override val cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * Validation exceptions
     */
    data class ValidationException(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * System call/SMS exceptions
     */
    data class SystemOperationException(
        val operation: String,
        override val message: String = "Failed to perform $operation",
        override val cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * File operation exceptions
     */
    data class FileException(
        override val message: String = "File operation failed",
        override val cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * Unknown/generic exceptions
     */
    data class UnknownException(
        override val message: String = "An unknown error occurred",
        override val cause: Throwable? = null
    ) : AppException(message, cause)
}

/**
 * Extension to convert regular exceptions to AppException
 */
fun Throwable.toAppException(): AppException {
    return when (this) {
        is AppException -> this
        is java.io.IOException -> AppException.NetworkException(
            message = this.message ?: "Network error",
            cause = this
        )
        is android.database.SQLException -> AppException.DatabaseException(
            message = this.message ?: "Database error",
            cause = this
        )
        is SecurityException -> AppException.PermissionException(
            permission = "unknown",
            message = this.message ?: "Permission denied",
            cause = this
        )
        is IllegalArgumentException -> AppException.ValidationException(
            message = this.message ?: "Invalid argument",
            cause = this
        )
        else -> AppException.UnknownException(
            message = this.message ?: "Unknown error",
            cause = this
        )
    }
}

/**
 * Get user-friendly error message
 */
fun AppException.getUserMessage(): String {
    return when (this) {
        is AppException.NetworkException -> "Network connection problem. Please check your internet."
        is AppException.DatabaseException -> "Storage problem. Please try again."
        is AppException.PermissionException -> "Permission required: ${this.permission}. Please grant permission in settings."
        is AppException.NotFoundException -> "Could not find ${this.itemType}."
        is AppException.ValidationException -> this.message
        is AppException.SystemOperationException -> "Could not perform ${this.operation}. Please try again."
        is AppException.FileException -> "File operation failed. Please check storage."
        is AppException.UnknownException -> "Something went wrong. Please try again."
    }
}
