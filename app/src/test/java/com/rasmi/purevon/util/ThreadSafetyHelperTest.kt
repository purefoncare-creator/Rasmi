package com.rasmi.purevon.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThreadSafetyHelperTest {

    @Test
    fun `LockStats starts with zero values`() {
        val stats = ThreadSafetyHelper.LockStats()
        val output = stats.getStats()
        assertThat(output).contains("Locks: 0")
        assertThat(output).contains("Contentions: 0")
    }

    @Test
    fun `LockStats records locks`() {
        val stats = ThreadSafetyHelper.LockStats()
        stats.recordLockAcquired(0)
        stats.recordLockAcquired(10)
        val output = stats.getStats()
        assertThat(output).contains("Locks: 2")
    }

    @Test
    fun `LockStats records contentions`() {
        val stats = ThreadSafetyHelper.LockStats()
        stats.recordLockAcquired(0)
        stats.recordLockAcquired(10)
        stats.recordLockAcquired(20)
        val output = stats.getStats()
        assertThat(output).contains("Contentions: 2")
    }

    @Test
    fun `LockStats calculates average wait time`() {
        val stats = ThreadSafetyHelper.LockStats()
        stats.recordLockAcquired(10)
        stats.recordLockAcquired(30)
        val output = stats.getStats()
        assertThat(output).contains("Avg Wait: 20ms")
    }
}
