package com.rasmi.purevon.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import java.io.IOException
import java.net.UnknownHostException

/**
 * Utility class for handling and classifying errors
 */
object ErrorHandler {
    
    /**
     * Convert exception to MessageError
     */
    fun fromException(exception: Throwable): MessageError {
        return when (exception) {
            is SecurityException -> MessageError.PermissionError(
                permission = "SMS_PERMISSION",
                message = exception.message ?: "Security exception"
            )
            
            is IllegalArgumentException -> MessageError.InvalidNumberError(
                number = "unknown",
                message = exception.message ?: "Invalid argument"
            )
            
            is IOException, is UnknownHostException -> MessageError.NetworkError(
                message = "Network error: ${exception.message}"
            )
            
            else -> MessageError.UnknownError(exception)
        }
    }
    
    /**
     * Check if has required permission
     */
    fun checkPermission(context: Context, permission: String): MessageResult<Unit> {
        return if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            MessageResult.Success(Unit)
        } else {
            MessageResult.Failure(MessageError.PermissionError(permission))
        }
    }
    
    /**
     * Check if network is available
     */
    fun checkNetwork(context: Context, requiresWifi: Boolean = false): MessageResult<Unit> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return MessageResult.Failure(MessageError.NetworkError("Cannot access connectivity service"))
        
        val network = connectivityManager.activeNetwork
            ?: return MessageResult.Failure(MessageError.NetworkError("No active network"))
        
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return MessageResult.Failure(MessageError.NetworkError("Cannot get network capabilities"))
        
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        
        return when {
            !hasInternet -> MessageResult.Failure(MessageError.NetworkError("No internet connection"))
            requiresWifi && !hasWifi && !hasCellular -> MessageResult.Failure(
                MessageError.NetworkError("MMS requires mobile data or Wi-Fi", requiresWifi = true)
            )
            else -> MessageResult.Success(Unit)
        }
    }
    
    /**
     * Check available storage
     */
    fun checkStorage(context: Context, requiredBytes: Long = 1024 * 1024): MessageResult<Unit> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableBytes = stat.availableBytes
            
            if (availableBytes < requiredBytes) {
                MessageResult.Failure(MessageError.StorageFullError(availableBytes))
            } else {
                MessageResult.Success(Unit)
            }
        } catch (e: Exception) {
            MessageResult.Failure(MessageError.UnknownError(e))
        }
    }
    
    /**
     * Validate phone number format
     * Supports both regular phone numbers and short codes (e.g., 1100 for carrier services)
     */
    fun validatePhoneNumber(phoneNumber: String): MessageResult<String> {
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
        
        return when {
            cleaned.isEmpty() -> MessageResult.Failure(
                MessageError.InvalidNumberError(phoneNumber, "Phone number is empty")
            )
            
            // Allow short codes (3-6 digits) for carrier services like 1100, 900, etc.
            cleaned.length in 3..6 -> MessageResult.Success(cleaned)
            
            // Regular phone numbers should be 7-20 digits
            cleaned.length < 7 -> MessageResult.Failure(
                MessageError.InvalidNumberError(phoneNumber, "Phone number too short")
            )
            
            cleaned.length > 20 -> MessageResult.Failure(
                MessageError.InvalidNumberError(phoneNumber, "Phone number too long")
            )
            
            else -> MessageResult.Success(cleaned)
        }
    }
    
    /**
     * Check message size
     */
    fun checkMessageSize(
        messageText: String?,
        attachments: List<*>,
        maxSmsLength: Int = 1600,
        maxMmsSize: Long = 600 * 1024 // 600KB
    ): MessageResult<Unit> {
        // For SMS without attachments
        if (attachments.isEmpty()) {
            val textLength = messageText?.length ?: 0
            return if (textLength <= maxSmsLength) {
                MessageResult.Success(Unit)
            } else {
                MessageResult.Failure(
                    MessageError.MessageTooLargeError(
                        actualSize = textLength.toLong(),
                        maxSize = maxSmsLength.toLong()
                    )
                )
            }
        }
        
        // For MMS (simplified - would need actual file sizes)
        return MessageResult.Success(Unit)
    }
    
    /**
     * Safe execution wrapper that converts exceptions to MessageResult
     */
    inline fun <T> safe(operation: String = "operation", block: () -> T): MessageResult<T> {
        return try {
            MessageResult.Success(block())
        } catch (e: SecurityException) {
            MessageResult.Failure(MessageError.PermissionError("UNKNOWN", e.message ?: "Security error"))
        } catch (e: IllegalArgumentException) {
            MessageResult.Failure(MessageError.InvalidNumberError("unknown", e.message ?: "Invalid argument"))
        } catch (e: IOException) {
            MessageResult.Failure(MessageError.NetworkError(e.message ?: "Network error"))
        } catch (e: Exception) {
            MessageResult.Failure(MessageError.UnknownError(e))
        }
    }
}
