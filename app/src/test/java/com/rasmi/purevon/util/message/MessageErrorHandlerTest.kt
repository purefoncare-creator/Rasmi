package com.rasmi.purevon.util.message

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageErrorHandlerTest {

    @Test
    fun `RetryStrategy default values`() {
        val strategy = RetryStrategy()
        assertThat(strategy.attempt).isEqualTo(0)
        assertThat(strategy.maxAttempts).isEqualTo(3)
        assertThat(strategy.canRetry).isTrue()
    }

    @Test
    fun `RetryStrategy canRetry false when at max`() {
        val strategy = RetryStrategy(attempt = 3, maxAttempts = 3)
        assertThat(strategy.canRetry).isFalse()
    }

    @Test
    fun `RetryStrategy getNextDelay with exponential backoff`() {
        val s0 = RetryStrategy(attempt = 0, baseDelay = 10_000L)
        assertThat(s0.getNextDelay()).isEqualTo(10_000L)

        val s1 = RetryStrategy(attempt = 1, baseDelay = 10_000L)
        assertThat(s1.getNextDelay()).isEqualTo(20_000L)

        val s2 = RetryStrategy(attempt = 2, baseDelay = 10_000L)
        assertThat(s2.getNextDelay()).isEqualTo(40_000L)
    }

    @Test
    fun `RetryStrategy getNextDelay without exponential returns baseDelay`() {
        val strategy = RetryStrategy(
            attempt = 5,
            baseDelay = 10_000L,
            useExponentialBackoff = false
        )
        assertThat(strategy.getNextDelay()).isEqualTo(10_000L)
    }

    @Test
    fun `RetryStrategy next increments attempt`() {
        val strategy = RetryStrategy(attempt = 0)
        val next = strategy.next()
        assertThat(next.attempt).isEqualTo(1)
    }

    @Test
    fun `RetryStrategy next preserves other fields`() {
        val strategy = RetryStrategy(
            attempt = 0,
            maxAttempts = 5,
            baseDelay = 20_000L,
            useExponentialBackoff = false
        )
        val next = strategy.next()
        assertThat(next.maxAttempts).isEqualTo(5)
        assertThat(next.baseDelay).isEqualTo(20_000L)
        assertThat(next.useExponentialBackoff).isFalse()
    }

    @Test
    fun `MessageSendResult Success holds id and time`() {
        val success = MessageSendResult.Success(messageId = 42L, sentTime = 1000L)
        assertThat(success.messageId).isEqualTo(42L)
        assertThat(success.sentTime).isEqualTo(1000L)
    }

    @Test
    fun `MessageSendResult Failure holds error and retry info`() {
        val failure = MessageSendResult.Failure(
            error = com.rasmi.purevon.domain.model.MessageError.NetworkError(),
            canRetry = true,
            retryDelay = 5000L
        )
        assertThat(failure.canRetry).isTrue()
        assertThat(failure.retryDelay).isEqualTo(5000L)
    }

    @Test
    fun `MessageSendResult Pending holds message id`() {
        val pending = MessageSendResult.Pending(messageId = 10L)
        assertThat(pending.messageId).isEqualTo(10L)
        assertThat(pending.estimatedTime).isNull()
    }
}
