package com.rasmi.purevon.util.message

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MessageRetryManagerTest {

    private lateinit var manager: MessageRetryManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        manager = MessageRetryManager()
    }

    @Test
    fun `getRetryDelay first attempt returns base delay`() {
        val delay = manager.getRetryDelay(1L)
        assertThat(delay).isEqualTo(1000L)
    }

    @Test
    fun `getRetryDelay increases with exponential backoff`() {
        val d1 = manager.getRetryDelay(2L)
        val d2 = manager.getRetryDelay(2L)
        val d3 = manager.getRetryDelay(2L)
        assertThat(d2).isGreaterThan(d1)
        assertThat(d3).isGreaterThan(d2)
    }

    @Test
    fun `getRetryDelay maxes out at 32 seconds`() {
        val msgId = 3L
        var lastDelay = 0L
        for (i in 0..10) {
            val d = manager.getRetryDelay(msgId)
            if (d > 0) lastDelay = d
        }
        assertThat(lastDelay).isAtMost(32_000L)
    }

    @Test
    fun `getRetryDelay returns -1 after max retries`() {
        val msgId = 4L
        var lastResult = 0L
        for (i in 0..5) {
            lastResult = manager.getRetryDelay(msgId)
        }
        assertThat(lastResult).isEqualTo(-1)
    }

    @Test
    fun `canRetry true before max retries`() {
        assertThat(manager.canRetry(10L)).isTrue()
    }

    @Test
    fun `canRetry false after max retries`() {
        val msgId = 11L
        for (i in 0..4) {
            manager.getRetryDelay(msgId)
        }
        assertThat(manager.canRetry(msgId)).isFalse()
    }

    @Test
    fun `resetRetryCount resets attempt counter`() {
        manager.getRetryDelay(20L)
        manager.getRetryDelay(20L)
        assertThat(manager.getRetryAttempt(20L)).isGreaterThan(0)

        manager.resetRetryCount(20L)
        assertThat(manager.getRetryAttempt(20L)).isEqualTo(0)
    }

    @Test
    fun `clearAll removes all entries`() {
        manager.getRetryDelay(1L)
        manager.getRetryDelay(2L)
        manager.clearAll()
        assertThat(manager.getRetryAttempt(1L)).isEqualTo(0)
        assertThat(manager.getRetryAttempt(2L)).isEqualTo(0)
    }

    @Test
    fun `getRetryStatusMessage for no attempts`() {
        val msg = manager.getRetryStatusMessage(50L)
        assertThat(msg).contains("فشل")
    }

    @Test
    fun `getRetryStatusMessage for max retries exceeded`() {
        val msgId = 51L
        for (i in 0..4) manager.getRetryDelay(msgId)
        val msg = manager.getRetryStatusMessage(msgId)
        assertThat(msg).contains("5")
    }

    @Test
    fun `retryWithBackoff resets counter on success`() = runTest {
        val result = manager.retryWithBackoff(60L) {
            Result.success(Unit)
        }
        assertThat(result.isSuccess).isTrue()
        assertThat(manager.getRetryAttempt(60L)).isEqualTo(0)
    }

    @Test
    fun `retryWithBackoff fails after max retries`() = runTest {
        val msgId = 61L
        for (i in 0..4) manager.getRetryDelay(msgId)
        val result = manager.retryWithBackoff<Unit>(msgId) {
            Result.failure(Exception("fail"))
        }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(MaxRetriesExceededException::class.java)
    }
}
