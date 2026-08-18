package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [MessageError] — error classification, user messages, and retry logic.
 */
class MessageErrorTest {

    // ════════════════════════════════════════════════════════════
    // getUserMessage
    // ════════════════════════════════════════════════════════════

    @Test
    fun `NetworkError getUserMessage mentions internet`() {
        val error = MessageError.NetworkError()
        assertThat(error.getUserMessage()).contains("internet")
    }

    @Test
    fun `NetworkError with requiresWifi mentions Wi-Fi`() {
        val error = MessageError.NetworkError(requiresWifi = true)
        assertThat(error.getUserMessage()).contains("Wi-Fi")
    }

    @Test
    fun `PermissionError getUserMessage mentions permission`() {
        val error = MessageError.PermissionError("SEND_SMS")
        assertThat(error.getUserMessage()).contains("SEND_SMS")
    }

    @Test
    fun `InvalidNumberError getUserMessage mentions phone number`() {
        val error = MessageError.InvalidNumberError("+966")
        assertThat(error.getUserMessage()).contains("phone number")
    }

    @Test
    fun `StorageFullError getUserMessage mentions storage`() {
        val error = MessageError.StorageFullError(availableBytes = 1024 * 1024)
        assertThat(error.getUserMessage()).contains("Storage")
    }

    @Test
    fun `RateLimitError getUserMessage includes retry seconds`() {
        val error = MessageError.RateLimitError(retryAfterSeconds = 60)
        assertThat(error.getUserMessage()).contains("60")
    }

    @Test
    fun `MmsError getUserMessage mentions MMS`() {
        val error = MessageError.MmsError("APN not found")
        assertThat(error.getUserMessage()).contains("MMS")
    }

    @Test
    fun `MessageTooLargeError getUserMessage mentions size`() {
        val error = MessageError.MessageTooLargeError(actualSize = 2048, maxSize = 1024)
        assertThat(error.getUserMessage()).contains("too large")
    }

    @Test
    fun `AttachmentError getUserMessage mentions filename`() {
        val error = MessageError.AttachmentError("photo.jpg", "Too large")
        assertThat(error.getUserMessage()).contains("photo.jpg")
    }

    @Test
    fun `SimCardError getUserMessage mentions SIM`() {
        val error = MessageError.SimCardError(simSlot = 0)
        assertThat(error.getUserMessage()).contains("SIM")
    }

    @Test
    fun `ThreadNotFoundError getUserMessage mentions conversation`() {
        val error = MessageError.ThreadNotFoundError(threadId = 42)
        assertThat(error.getUserMessage()).contains("Conversation")
    }

    @Test
    fun `EmptyBodyError getUserMessage mentions body`() {
        val error = MessageError.EmptyBodyError()
        assertThat(error.getUserMessage()).contains("body")
    }

    // ════════════════════════════════════════════════════════════
    // getSuggestedAction
    // ════════════════════════════════════════════════════════════

    @Test
    fun `NetworkError getSuggestedAction returns non-null`() {
        val error = MessageError.NetworkError()
        assertThat(error.getSuggestedAction()).isNotNull()
    }

    @Test
    fun `PermissionError getSuggestedAction mentions Settings`() {
        val error = MessageError.PermissionError("SEND_SMS")
        assertThat(error.getSuggestedAction()).contains("Settings")
    }

    @Test
    fun `InvalidNumberError getSuggestedAction mentions format`() {
        val error = MessageError.InvalidNumberError("+966")
        assertThat(error.getSuggestedAction()).contains("format")
    }

    @Test
    fun `UnknownError getSuggestedAction returns null`() {
        val error = MessageError.UnknownError(Exception("oops"))
        assertThat(error.getSuggestedAction()).isNull()
    }

    // ════════════════════════════════════════════════════════════
    // getRetryDelay
    // ════════════════════════════════════════════════════════════

    @Test
    fun `NetworkError getRetryDelay is 15 seconds`() {
        val error = MessageError.NetworkError()
        assertThat(error.getRetryDelay()).isEqualTo(15_000L)
    }

    @Test
    fun `RateLimitError getRetryDelay is 5 minutes`() {
        val error = MessageError.RateLimitError(retryAfterSeconds = 60)
        assertThat(error.getRetryDelay()).isEqualTo(300_000L)
    }

    @Test
    fun `MmsError getRetryDelay is 5 seconds`() {
        val error = MessageError.MmsError("timeout")
        assertThat(error.getRetryDelay()).isEqualTo(5_000L)
    }

    @Test
    fun `DatabaseError getRetryDelay is 10 seconds (default)`() {
        val error = MessageError.DatabaseError("insert")
        assertThat(error.getRetryDelay()).isEqualTo(10_000L)
    }

    // ════════════════════════════════════════════════════════════
    // Error codes
    // ════════════════════════════════════════════════════════════

    @Test
    fun `NetworkError has correct code`() {
        assertThat(MessageError.NetworkError().code).isEqualTo("NETWORK_ERROR")
    }

    @Test
    fun `PermissionError has correct code`() {
        assertThat(MessageError.PermissionError("SMS").code).isEqualTo("PERMISSION_ERROR")
    }

    @Test
    fun `InvalidNumberError has correct code`() {
        assertThat(MessageError.InvalidNumberError("123").code).isEqualTo("INVALID_NUMBER")
    }

    @Test
    fun `DatabaseError has correct code`() {
        assertThat(MessageError.DatabaseError("query").code).isEqualTo("DATABASE_ERROR")
    }

    // ════════════════════════════════════════════════════════════
    // isRetryable
    // ════════════════════════════════════════════════════════════

    @Test
    fun `NetworkError is retryable`() {
        assertThat(MessageError.NetworkError().isRetryable).isTrue()
    }

    @Test
    fun `PermissionError is not retryable`() {
        assertThat(MessageError.PermissionError("SMS").isRetryable).isFalse()
    }

    @Test
    fun `InvalidNumberError is not retryable`() {
        assertThat(MessageError.InvalidNumberError("123").isRetryable).isFalse()
    }

    @Test
    fun `MmsError is retryable`() {
        assertThat(MessageError.MmsError("timeout").isRetryable).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // fromException
    // ════════════════════════════════════════════════════════════

    @Test
    fun `fromException maps network message to NetworkError`() {
        val error = MessageError.fromException(Exception("no network available"))
        assertThat(error).isInstanceOf(MessageError.NetworkError::class.java)
    }

    @Test
    fun `fromException maps permission message to PermissionError`() {
        val error = MessageError.fromException(Exception("permission denied"))
        assertThat(error).isInstanceOf(MessageError.PermissionError::class.java)
    }

    @Test
    fun `fromException maps unknown to UnknownError`() {
        val error = MessageError.fromException(Exception("something weird"))
        assertThat(error).isInstanceOf(MessageError.UnknownError::class.java)
    }

    // ════════════════════════════════════════════════════════════
    // MessageResult
    // ════════════════════════════════════════════════════════════

    @Test
    fun `Success result has isSuccess true`() {
        val result = MessageResult.Success(42L)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.isFailure).isFalse()
    }

    @Test
    fun `Success result returns data`() {
        val result = MessageResult.Success(42L, threadId = 10L)
        assertThat(result.getOrNull()).isEqualTo(42L)
    }

    @Test
    fun `Success result has threadId`() {
        val result = MessageResult.Success(42L, threadId = 10L)
        assertThat(result).isInstanceOf(MessageResult.Success::class.java)
        assertThat((result as MessageResult.Success).threadId).isEqualTo(10L)
    }

    @Test
    fun `Failure result has isFailure true`() {
        val result = MessageResult.Failure(MessageError.NetworkError())
        assertThat(result.isFailure).isTrue()
        assertThat(result.isSuccess).isFalse()
    }

    @Test
    fun `Failure result returns error`() {
        val error = MessageError.NetworkError()
        val result = MessageResult.Failure(error)
        assertThat(result.errorOrNull()).isEqualTo(error)
    }

    @Test
    fun `Failure result getOrNull returns null`() {
        val result: MessageResult<Long> = MessageResult.Failure(MessageError.NetworkError())
        assertThat(result.getOrNull()).isEqualTo(null)
    }

    @Test
    fun `Success result errorOrNull returns null`() {
        val result = MessageResult.Success(42L)
        assertThat(result.errorOrNull()).isEqualTo(null)
    }

    @Test
    fun `onSuccess executes action on Success`() {
        var called = false
        MessageResult.Success(42L).onSuccess { called = true }
        assertThat(called).isTrue()
    }

    @Test
    fun `onSuccess does not execute action on Failure`() {
        var called = false
        MessageResult.Failure(MessageError.NetworkError()).onSuccess { called = true }
        assertThat(called).isFalse()
    }

    @Test
    fun `onFailure executes action on Failure`() {
        var called = false
        MessageResult.Failure(MessageError.NetworkError()).onFailure { called = true }
        assertThat(called).isTrue()
    }

    @Test
    fun `fromResult maps Success`() {
        val result: MessageResult<Long> = MessageResult.fromResult(Result.success(42L))
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(42L)
    }

    @Test
    fun `fromResult maps Failure`() {
        val result: MessageResult<Long> = MessageResult.fromResult(Result.failure(Exception("fail")))
        assertThat(result.isFailure).isTrue()
    }
}
