package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CleanupResultTest {

    @Test
    fun `getSuccessPercentage calculates correctly`() {
        val result = CleanupResult(
            totalProcessed = 100,
            successCount = 75,
            failedCount = 25,
            failures = emptyList()
        )
        assertThat(result.getSuccessPercentage()).isEqualTo(75)
    }

    @Test
    fun `getSuccessPercentage returns 0 for zero total`() {
        val result = CleanupResult(0, 0, 0, emptyList())
        assertThat(result.getSuccessPercentage()).isEqualTo(0)
    }

    @Test
    fun `getSuccessPercentage returns 100 for all success`() {
        val result = CleanupResult(10, 10, 0, emptyList())
        assertThat(result.getSuccessPercentage()).isEqualTo(100)
    }

    @Test
    fun `getSuccessPercentage returns 0 for all failed`() {
        val result = CleanupResult(10, 0, 10, emptyList())
        assertThat(result.getSuccessPercentage()).isEqualTo(0)
    }

    @Test
    fun `isFullySuccessful when no failures`() {
        val result = CleanupResult(5, 5, 0, emptyList())
        assertThat(result.isFullySuccessful()).isTrue()
    }

    @Test
    fun `isFullySuccessful false when there are failures`() {
        val result = CleanupResult(5, 4, 1, listOf(
            CleanupFailure(1L, "Test", "+1234", "Error")
        ))
        assertThat(result.isFullySuccessful()).isFalse()
    }

    @Test
    fun `CleanupFailure holds correct data`() {
        val failure = CleanupFailure(
            contactId = 42L,
            displayName = "Ali",
            phoneNumber = "+966501234567",
            errorMessage = "Permission denied"
        )
        assertThat(failure.contactId).isEqualTo(42L)
        assertThat(failure.displayName).isEqualTo("Ali")
        assertThat(failure.phoneNumber).isEqualTo("+966501234567")
        assertThat(failure.errorMessage).isEqualTo("Permission denied")
    }
}
