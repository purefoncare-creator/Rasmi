package com.rasmi.purevon.util.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network utilities and retry mechanisms
 */
@Singleton
class NetworkManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Check if network is available
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Check if connected to WiFi
     */
    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Execute with automatic retry on network failures
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        initialDelay: Long = 1000L,
        maxDelay: Long = 10000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): NetworkResult<T> {
        var currentDelay = initialDelay
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                if (!isNetworkAvailable()) {
                    throw NetworkException.NoConnection()
                }
                
                val result = block()
                return NetworkResult.Success(result)
                
            } catch (e: IOException) {
                lastException = e
                
                // Don't retry on the last attempt
                if (attempt < maxRetries - 1) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                }
            } catch (e: Exception) {
                // Non-retryable exception
                return NetworkResult.Error(NetworkException.Unknown(e.message ?: "Unknown error", e))
            }
        }
        
        return NetworkResult.Error(
            NetworkException.RequestFailed(
                "Failed after $maxRetries attempts",
                lastException
            )
        )
    }
    
    /**
     * Execute only if network is available
     */
    suspend fun <T> executeIfConnected(block: suspend () -> T): NetworkResult<T> {
        return if (isNetworkAvailable()) {
            try {
                NetworkResult.Success(block())
            } catch (e: Exception) {
                NetworkResult.Error(NetworkException.Unknown(e.message ?: "Unknown error", e))
            }
        } else {
            NetworkResult.Error(NetworkException.NoConnection())
        }
    }
}

/**
 * Network result wrapper
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val exception: NetworkException) : NetworkResult<Nothing>()
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
    }
}

/**
 * Network exceptions
 */
sealed class NetworkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    class NoConnection : NetworkException("No internet connection available")
    
    class RequestFailed(
        message: String,
        cause: Throwable? = null
    ) : NetworkException(message, cause)
    
    class Timeout : NetworkException("Request timed out")
    
    class Unknown(
        message: String,
        cause: Throwable? = null
    ) : NetworkException(message, cause)
}

/**
 * Extension to get user-friendly error message
 */
fun NetworkException.getUserMessage(): String {
    return when (this) {
        is NetworkException.NoConnection -> "لا يوجد اتصال بالإنترنت. يرجى التحقق من الاتصال."
        is NetworkException.RequestFailed -> "فشل الطلب: $message"
        is NetworkException.Timeout -> "انتهت مهلة الطلب. يرجى المحاولة مرة أخرى."
        is NetworkException.Unknown -> "حدث خطأ غير متوقع: $message"
    }
}
