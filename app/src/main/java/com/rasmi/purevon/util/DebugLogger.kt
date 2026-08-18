package com.rasmi.purevon.util

import android.util.Log
import com.rasmi.purevon.BuildConfig

/**
 * Debug Logger - Only logs in DEBUG builds
 * Prevents sensitive information from being logged in production
 */
object DebugLogger {
    
    private const val DEFAULT_TAG = "Purevon"
    
    /**
     * Log debug message (only in DEBUG builds)
     */
    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /** Diagnostic logging that is always suppressed in release builds. */
    fun diagnostic(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
        }
    }
    
    /**
     * Log info message (only in DEBUG builds)
     */
    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }
    
    /**
     * Log warning message (always logs)
     */
    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }
    
    /**
     * Log error message (always logs)
     */
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    /**
     * Log sensitive data (phone numbers, messages, etc.) - ONLY in DEBUG
     * This adds a [SENSITIVE] prefix to make it clear
     */
    fun sensitive(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, "[SENSITIVE] $message")
        }
    }
    
    /**
     * Mask phone number for logging
     * Example: +1234567890 -> +123***7890
     */
    fun maskPhoneNumber(phoneNumber: String): String {
        return if (phoneNumber.length > 6) {
            "${phoneNumber.take(4)}***${phoneNumber.takeLast(4)}"
        } else {
            "***"
        }
    }
    
    /**
     * Mask message content for logging
     * Shows only first and last 10 characters
     */
    fun maskMessage(message: String, maxLength: Int = 20): String {
        return if (message.length > maxLength) {
            "${message.take(10)}...${message.takeLast(10)} [${message.length} chars]"
        } else {
            "***[${message.length} chars]"
        }
    }

    /**
     * Mask contact name for logging
     * Example: "Ahmed Ali" -> "Ah***li"
     */
    fun maskName(name: String?): String {
        if (name.isNullOrBlank()) return "Unknown"
        return if (name.length > 4) {
            "${name.take(2)}***${name.takeLast(2)}"
        } else {
            "***"
        }
    }

    /**
     * Mask address/location for logging
     */
    fun maskAddress(address: String?): String {
        if (address.isNullOrBlank()) return "***"
        return "${address.take(5)}***"
    }
    
    /**
     * Log with masked sensitive data
     */
    fun dMasked(tag: String = DEFAULT_TAG, message: String, sensitiveData: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, "$message: ***[MASKED]***")
        }
    }
}

