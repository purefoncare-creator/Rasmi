package com.rasmi.purevon.presentation.util

import android.content.Context
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.MessageError

/**
 * Presentation-layer extension to get localized user messages from [MessageError].
 * Keeps domain layer free of Android Context dependencies.
 */
fun MessageError.getLocalizedMessage(context: Context): String = when (this) {
    is MessageError.NetworkError -> if (requiresWifi) {
        context.getString(R.string.msg_error_network_wifi)
    } else {
        context.getString(R.string.msg_error_network)
    }
    is MessageError.PermissionError -> context.getString(R.string.msg_error_permission, permission)
    is MessageError.InsufficientBalanceError -> context.getString(R.string.msg_error_balance)
    is MessageError.InvalidNumberError -> context.getString(R.string.msg_error_invalid_number)
    is MessageError.StorageFullError -> context.getString(R.string.msg_error_storage_full)
    is MessageError.RateLimitError -> context.resources.getQuantityString(R.plurals.msg_error_rate_limit, retryAfterSeconds.toInt(), retryAfterSeconds.toInt())
    is MessageError.MmsError -> context.getString(R.string.msg_error_mms, reason)
    is MessageError.MessageTooLargeError -> context.getString(R.string.msg_error_too_large, (maxSize / 1024).toInt())
    is MessageError.AttachmentError -> context.getString(R.string.msg_error_attachment, filename, reason)
    is MessageError.SimCardError -> context.getString(R.string.msg_error_sim)
    is MessageError.ThreadNotFoundError -> context.getString(R.string.msg_error_thread_not_found)
    is MessageError.DeliveryFailedError -> context.getString(R.string.msg_error_delivery_failed, reason)
    is MessageError.DatabaseError -> context.getString(R.string.msg_error_database)
    is MessageError.UnknownError -> context.getString(R.string.msg_error_unknown, exception.message ?: "Unknown")
    is MessageError.EmptyBodyError -> context.getString(R.string.msg_error_empty_body)
}

/**
 * Presentation-layer extension to get localized suggested action from [MessageError].
 */
fun MessageError.getLocalizedSuggestion(context: Context): String? = when (this) {
    is MessageError.NetworkError -> context.getString(R.string.msg_action_check_network)
    is MessageError.PermissionError -> context.getString(R.string.msg_action_grant_permission)
    is MessageError.InsufficientBalanceError -> context.getString(R.string.msg_action_recharge)
    is MessageError.InvalidNumberError -> context.getString(R.string.msg_action_verify_number)
    is MessageError.StorageFullError -> context.getString(R.string.msg_action_free_space)
    is MessageError.RateLimitError -> context.getString(R.string.msg_action_wait)
    is MessageError.MmsError -> context.getString(R.string.msg_action_send_as_sms)
    is MessageError.MessageTooLargeError -> context.getString(R.string.msg_action_compress)
    is MessageError.SimCardError -> context.getString(R.string.msg_action_check_sim)
    is MessageError.EmptyBodyError -> context.getString(R.string.msg_action_type_message)
    else -> null
}
