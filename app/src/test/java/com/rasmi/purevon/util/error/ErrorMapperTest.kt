package com.rasmi.purevon.util.error

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMapperTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    // mapToMessage
    @Test
    fun `mapToMessage for UnknownHostException`() {
        val msg = ErrorMapper.mapToMessage(UnknownHostException())
        assertThat(msg).contains("internet")
    }

    @Test
    fun `mapToMessage for SocketTimeoutException`() {
        val msg = ErrorMapper.mapToMessage(SocketTimeoutException())
        assertThat(msg).contains("timeout")
    }

    @Test
    fun `mapToMessage for IOException`() {
        val msg = ErrorMapper.mapToMessage(IOException("test"))
        assertThat(msg).contains("Network")
    }

    @Test
    fun `mapToMessage for SecurityException`() {
        val msg = ErrorMapper.mapToMessage(SecurityException())
        assertThat(msg).contains("Permission")
    }

    @Test
    fun `mapToMessage for IllegalStateException`() {
        val msg = ErrorMapper.mapToMessage(IllegalStateException())
        assertThat(msg).contains("wrong")
    }

    @Test
    fun `mapToMessage for IllegalArgumentException`() {
        val msg = ErrorMapper.mapToMessage(IllegalArgumentException())
        assertThat(msg).contains("Invalid")
    }

    @Test
    fun `mapToMessage for AppException returns userMessage`() {
        val ex = AppException(userMessage = "Custom error message")
        val msg = ErrorMapper.mapToMessage(ex)
        assertThat(msg).isEqualTo("Custom error message")
    }

    @Test
    fun `mapToMessage for unknown exception returns message or default`() {
        val msg = ErrorMapper.mapToMessage(RuntimeException("oops"))
        assertThat(msg).isEqualTo("oops")
    }

    // mapToErrorType
    @Test
    fun `mapToErrorType for network errors`() {
        assertThat(ErrorMapper.mapToErrorType(UnknownHostException())).isEqualTo(ErrorType.NETWORK)
        assertThat(ErrorMapper.mapToErrorType(SocketTimeoutException())).isEqualTo(ErrorType.NETWORK)
        assertThat(ErrorMapper.mapToErrorType(IOException())).isEqualTo(ErrorType.NETWORK)
    }

    @Test
    fun `mapToErrorType for SecurityException`() {
        assertThat(ErrorMapper.mapToErrorType(SecurityException())).isEqualTo(ErrorType.PERMISSION)
    }

    @Test
    fun `mapToErrorType for AppException`() {
        val ex = AppException(userMessage = "test")
        assertThat(ErrorMapper.mapToErrorType(ex)).isEqualTo(ErrorType.BUSINESS_LOGIC)
    }

    @Test
    fun `mapToErrorType for unknown`() {
        assertThat(ErrorMapper.mapToErrorType(RuntimeException())).isEqualTo(ErrorType.UNKNOWN)
    }

    // isRecoverable
    @Test
    fun `isRecoverable true for network errors`() {
        assertThat(ErrorMapper.isRecoverable(UnknownHostException())).isTrue()
        assertThat(ErrorMapper.isRecoverable(SocketTimeoutException())).isTrue()
        assertThat(ErrorMapper.isRecoverable(IOException())).isTrue()
    }

    @Test
    fun `isRecoverable false for SecurityException`() {
        assertThat(ErrorMapper.isRecoverable(SecurityException())).isFalse()
    }

    @Test
    fun `isRecoverable true for other exceptions`() {
        assertThat(ErrorMapper.isRecoverable(RuntimeException())).isTrue()
    }

    // getSuggestedAction
    @Test
    fun `getSuggestedAction RETRY for network errors`() {
        assertThat(ErrorMapper.getSuggestedAction(UnknownHostException())).isEqualTo(ErrorAction.RETRY)
        assertThat(ErrorMapper.getSuggestedAction(SocketTimeoutException())).isEqualTo(ErrorAction.RETRY)
        assertThat(ErrorMapper.getSuggestedAction(IOException())).isEqualTo(ErrorAction.RETRY)
    }

    @Test
    fun `getSuggestedAction GO_TO_SETTINGS for SecurityException`() {
        assertThat(ErrorMapper.getSuggestedAction(SecurityException())).isEqualTo(ErrorAction.GO_TO_SETTINGS)
    }

    @Test
    fun `getSuggestedAction DISMISS for other`() {
        assertThat(ErrorMapper.getSuggestedAction(RuntimeException())).isEqualTo(ErrorAction.DISMISS)
    }
}

class AppExceptionTest {

    @Test
    fun `AppException holds userMessage`() {
        val ex = AppException(userMessage = "User sees this", technicalMessage = "Technical detail")
        assertThat(ex.userMessage).isEqualTo("User sees this")
        assertThat(ex.technicalMessage).isEqualTo("Technical detail")
    }

    @Test
    fun `toAppException wraps non-AppException`() {
        val original = RuntimeException("original")
        val wrapped = original.toAppException()
        assertThat(wrapped).isInstanceOf(AppException::class.java)
        assertThat(wrapped.cause).isEqualTo(original)
    }

    @Test
    fun `toAppException returns same AppException`() {
        val original = AppException(userMessage = "test")
        assertThat(original.toAppException()).isSameInstanceAs(original)
    }
}
