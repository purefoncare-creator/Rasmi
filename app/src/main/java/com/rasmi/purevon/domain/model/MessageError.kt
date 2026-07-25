package com.rasmi.purevon.domain.model

/**
 * Sealed class representing all possible messaging errors
 * Provides detailed error information for better user feedback
 */
sealed class MessageError(
    open val message: String,
    open val code: String,
    open val isRetryable: Boolean = false
) {
    
    /**
     * Network connectivity errors
     */
    data class NetworkError(
        override val message: String = "No internet connection",
        val requiresWifi: Boolean = false
    ) : MessageError(message, "NETWORK_ERROR", isRetryable = true)
    
    /**
     * Permission-related errors
     */
    data class PermissionError(
        val permission: String,
        override val message: String = "Missing required permission: $permission"
    ) : MessageError(message, "PERMISSION_ERROR", isRetryable = false)
    
    /**
     * Insufficient SMS/MMS balance
     */
    data class InsufficientBalanceError(
        val carrier: String? = null,
        override val message: String = "Insufficient SMS balance"
    ) : MessageError(message, "BALANCE_ERROR", isRetryable = true)
    
    /**
     * Invalid phone number format
     */
    data class InvalidNumberError(
        val number: String,
        override val message: String = "Invalid phone number: $number"
    ) : MessageError(message, "INVALID_NUMBER", isRetryable = false)
    
    /**
     * Device storage full
     */
    data class StorageFullError(
        val availableBytes: Long,
        override val message: String = "Storage full, only ${availableBytes / 1024 / 1024}MB available"
    ) : MessageError(message, "STORAGE_FULL", isRetryable = false)
    
    /**
     * Rate limiting (too many messages)
     */
    data class RateLimitError(
        val retryAfterSeconds: Long,
        override val message: String = "Too many messages, retry after ${retryAfterSeconds}s"
    ) : MessageError(message, "RATE_LIMIT", isRetryable = true)
    
    /**
     * MMS-specific errors
     */
    data class MmsError(
        val reason: String,
        override val message: String = "MMS error: $reason"
    ) : MessageError(message, "MMS_ERROR", isRetryable = true)
    
    /**
     * Message size too large
     */
    data class MessageTooLargeError(
        val actualSize: Long,
        val maxSize: Long,
        override val message: String = "Message too large: ${actualSize / 1024}KB (max: ${maxSize / 1024}KB)"
    ) : MessageError(message, "MESSAGE_TOO_LARGE", isRetryable = false)
    
    /**
     * Attachment-related errors
     */
    data class AttachmentError(
        val filename: String,
        val reason: String,
        override val message: String = "Attachment error for $filename: $reason"
    ) : MessageError(message, "ATTACHMENT_ERROR", isRetryable = false)
    
    /**
     * SIM card errors
     */
    data class SimCardError(
        val simSlot: Int?,
        override val message: String = "SIM card error for slot $simSlot"
    ) : MessageError(message, "SIM_ERROR", isRetryable = true)
    
    /**
     * Thread/conversation not found
     */
    data class ThreadNotFoundError(
        val threadId: Long,
        override val message: String = "Conversation not found: $threadId"
    ) : MessageError(message, "THREAD_NOT_FOUND", isRetryable = false)
    
    /**
     * Message delivery failed
     */
    data class DeliveryFailedError(
        val reason: String,
        override val message: String = "Delivery failed: $reason"
    ) : MessageError(message, "DELIVERY_FAILED", isRetryable = true)
    
    /**
     * Database operation failed
     */
    data class DatabaseError(
        val operation: String,
        override val message: String = "Database error during $operation"
    ) : MessageError(message, "DATABASE_ERROR", isRetryable = true)
    
    /**
     * Generic unknown error
     */
    data class UnknownError(
        val exception: Throwable,
        override val message: String = "Unknown error: ${exception.message ?: "No details"}"
    ) : MessageError(message, "UNKNOWN", isRetryable = true)

    /**
     * Empty message body error
     */
    data class EmptyBodyError(
        override val message: String = "Message body is empty"
    ) : MessageError(message, "EMPTY_BODY", isRetryable = false)

    /**
     * Get user-friendly message in current locale
     */
    fun getUserMessage(): String = when (this) {
        is NetworkError -> if (requiresWifi) {
            "📶 MMS requires Wi-Fi or mobile data. Please check your connection."
        } else {
            "📶 No internet connection. Please check your network settings."
        }
        
        is PermissionError -> "🔒 Permission required: $permission. Please grant in settings."
        
        is InsufficientBalanceError -> "💳 Insufficient SMS balance. Please recharge your SIM card."
        
        is InvalidNumberError -> "📱 Invalid phone number. Please check and try again."
        
        is StorageFullError -> "💾 Storage full. Please free up some space."
        
        is RateLimitError -> "⏱️ Too many messages sent. Please wait $retryAfterSeconds seconds."
        
        is MmsError -> "📎 MMS error: $reason. Please try again."
        
        is MessageTooLargeError -> "📏 Message too large. Maximum size is ${maxSize / 1024}KB."
        
        is AttachmentError -> "📎 Cannot attach $filename: $reason"
        
        is SimCardError -> "📱 SIM card error. Please check your SIM card."
        
        is ThreadNotFoundError -> "💬 Conversation not found. It may have been deleted."
        
        is DeliveryFailedError -> "❌ Message delivery failed: $reason"
        
        is DatabaseError -> "💾 Database error. Please try again."
        
        is UnknownError -> "⚠️ An error occurred: ${exception.message ?: "Unknown"}"
        is EmptyBodyError -> "✍️ Message body is empty. Please type a message."
    }
    
    /**
     * Get action suggestion for the error
     */
    fun getSuggestedAction(): String? = when (this) {
        is NetworkError -> "Check your internet connection and try again"
        is PermissionError -> "Go to Settings → Apps → Purevon → Permissions"
        is InsufficientBalanceError -> "Recharge your SIM card balance"
        is InvalidNumberError -> "Verify the phone number format"
        is StorageFullError -> "Delete some files to free up space"
        is RateLimitError -> "Wait a moment before sending more messages"
        is MmsError -> "Try sending as SMS without attachments"
        is MessageTooLargeError -> "Remove some attachments or compress them"
        is SimCardError -> "Check SIM card is properly inserted"
        is EmptyBodyError -> "Type a message or attach a file"
        else -> null
    }

    /**
     * Get recommended retry delay based on error type
     */
    fun getRetryDelay(): Long = when (this) {
        is NetworkError -> 15_000L
        is RateLimitError -> 300_000L
        is MmsError -> 5_000L
        is SimCardError -> 30_000L
        is DeliveryFailedError -> 10_000L
        else -> 10_000L
    }

    companion object {
        /**
         * Map exception to MessageError
         */
        fun fromException(exception: Throwable): MessageError {
            return when {
                exception.message?.contains("no signal", ignoreCase = true) == true -> NetworkError()
                exception.message?.contains("network", ignoreCase = true) == true -> NetworkError()
                exception.message?.contains("permission", ignoreCase = true) == true -> PermissionError(permission = "SMS")
                exception.message?.contains("too large", ignoreCase = true) == true -> MessageTooLargeError(actualSize = 0, maxSize = 0)
                else -> UnknownError(exception = exception as? Exception ?: Exception(exception))
            }
        }
    }
}

/**
 * Result type for messaging operations
 */
sealed class MessageResult<out T> {
    // threadId is available for SMS/MMS sends so the caller can open the conversation
    // without an extra round-trip to the content provider.
    data class Success<T>(val data: T, val threadId: Long = 0L) : MessageResult<T>()
    data class Failure(val error: MessageError) : MessageResult<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }
    
    fun errorOrNull(): MessageError? = when (this) {
        is Success -> null
        is Failure -> error
    }
    
    inline fun onSuccess(action: (T) -> Unit): MessageResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onFailure(action: (MessageError) -> Unit): MessageResult<T> {
        if (this is Failure) action(error)
        return this
    }

    companion object {
        /**
         * Convert a kotlin.Result<T> to MessageResult<T>.
         * Maps Throwable to MessageError.UnknownError.
         */
        fun <T> fromResult(result: Result<T>): MessageResult<T> =
            result.fold(
                onSuccess = { Success(it) },
                onFailure = { Failure(MessageError.UnknownError(it)) }
            )
    }
}
