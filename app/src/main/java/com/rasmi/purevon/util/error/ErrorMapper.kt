package com.rasmi.purevon.util.error

import android.database.sqlite.SQLiteException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps exceptions to user-friendly error messages
 * Helps maintain consistent error handling across the app
 */
object ErrorMapper {
    
    /**
     * Convert an exception to a user-friendly message
     */
    fun mapToMessage(exception: Throwable): String {
        return when (exception) {
            // Network errors
            is UnknownHostException -> "No internet connection. Please check your network."
            is SocketTimeoutException -> "Connection timeout. Please try again."
            is IOException -> "Network error. Please check your connection."
            
            // Database errors
            is SQLiteException -> "Database error. Please restart the app."
            
            // Security errors
            is SecurityException -> "Permission denied. Please grant required permissions."
            
            // Illegal state/argument
            is IllegalStateException -> "Something went wrong. Please try again."
            is IllegalArgumentException -> "Invalid input. Please check and try again."
            
            // Custom app exceptions
            is AppException -> exception.userMessage
            
            // Default
            else -> exception.message ?: "An unexpected error occurred. Please try again."
        }
    }
    
    /**
     * Convert exception to error type for analytics/logging
     */
    fun mapToErrorType(exception: Throwable): ErrorType {
        return when (exception) {
            is UnknownHostException, is SocketTimeoutException, is IOException -> ErrorType.NETWORK
            is SQLiteException -> ErrorType.DATABASE
            is SecurityException -> ErrorType.PERMISSION
            is IllegalStateException, is IllegalArgumentException -> ErrorType.VALIDATION
            is AppException -> ErrorType.BUSINESS_LOGIC
            else -> ErrorType.UNKNOWN
        }
    }
    
    /**
     * Check if error is recoverable (user can retry)
     */
    fun isRecoverable(exception: Throwable): Boolean {
        return when (exception) {
            is UnknownHostException, 
            is SocketTimeoutException,
            is IOException -> true
            is SecurityException -> false // Need to go to settings
            else -> true
        }
    }
    
    /**
     * Get suggested action for the error
     */
    fun getSuggestedAction(exception: Throwable): ErrorAction {
        return when (exception) {
            is UnknownHostException, 
            is SocketTimeoutException,
            is IOException -> ErrorAction.RETRY
            is SecurityException -> ErrorAction.GO_TO_SETTINGS
            is SQLiteException -> ErrorAction.RESTART_APP
            else -> ErrorAction.DISMISS
        }
    }
}

/**
 * Custom app exception with user-friendly message
 */
class AppException(
    val userMessage: String,
    val technicalMessage: String? = null,
    cause: Throwable? = null
) : Exception(technicalMessage ?: userMessage, cause)

/**
 * Extension function to convert any exception to AppException
 */
fun Throwable.toAppException(): AppException {
    return if (this is AppException) {
        this
    } else {
        AppException(
            userMessage = ErrorMapper.mapToMessage(this),
            technicalMessage = this.message,
            cause = this
        )
    }
}


