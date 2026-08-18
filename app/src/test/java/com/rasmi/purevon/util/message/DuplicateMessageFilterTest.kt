package com.rasmi.purevon.util.message

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

/**
 * Tests for [DuplicateMessageFilter] — prevents duplicate SMS from appearing in UI.
 */
class DuplicateMessageFilterTest {

    private lateinit var filter: DuplicateMessageFilter

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
        filter = DuplicateMessageFilter()
    }

    // ════════════════════════════════════════════════════════════
    // Basic duplicate detection
    // ════════════════════════════════════════════════════════════

    @Test
    fun `first message is not a duplicate`() {
        val result = filter.isDuplicate("+966500000000", "Hello", 1000L)
        assertThat(result).isFalse()
    }

    @Test
    fun `same message within 5 seconds is a duplicate`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000000", "Hello", 3000L)
        assertThat(result).isTrue()
    }

    @Test
    fun `same message after 5 seconds is not a duplicate`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000000", "Hello", 7000L)
        assertThat(result).isFalse()
    }

    // ════════════════════════════════════════════════════════════
    // Different messages
    // ════════════════════════════════════════════════════════════

    @Test
    fun `different body is not a duplicate`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000000", "World", 1000L)
        assertThat(result).isFalse()
    }

    @Test
    fun `different address is not a duplicate`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000001", "Hello", 1000L)
        assertThat(result).isFalse()
    }

    @Test
    fun `same body and address but different content is not a duplicate`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000000", "Hello world", 1000L)
        assertThat(result).isFalse()
    }

    // ════════════════════════════════════════════════════════════
    // Address normalization
    // ════════════════════════════════════════════════════════════

    @Test
    fun `address with dashes is treated same as without`() {
        filter.isDuplicate("+966-500-000000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000000", "Hello", 2000L)
        assertThat(result).isTrue()
    }

    @Test
    fun `address with spaces is treated same as without`() {
        filter.isDuplicate("+966 500 000 000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000000", "Hello", 2000L)
        assertThat(result).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // Duplicate count tracking
    // ════════════════════════════════════════════════════════════

    @Test
    fun `triple duplicate is detected`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        filter.isDuplicate("+966500000000", "Hello", 2000L)
        val result = filter.isDuplicate("+966500000000", "Hello", 3000L)
        assertThat(result).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // markAsProcessed
    // ════════════════════════════════════════════════════════════

    @Test
    fun `markAsProcessed prevents duplicate detection`() {
        filter.markAsProcessed("+966500000000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000000", "Hello", 2000L)
        assertThat(result).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // Hash-based duplicate detection
    // ════════════════════════════════════════════════════════════

    @Test
    fun `isDuplicateByHash detects same hash within window`() {
        val hash = "Hello".hashCode()
        filter.isDuplicateByHash("+966500000000", hash, 1000L)
        val result = filter.isDuplicateByHash("+966500000000", hash, 2000L)
        assertThat(result).isTrue()
    }

    @Test
    fun `isDuplicateByHash allows same hash after window`() {
        val hash = "Hello".hashCode()
        filter.isDuplicateByHash("+966500000000", hash, 1000L)
        val result = filter.isDuplicateByHash("+966500000000", hash, 7000L)
        assertThat(result).isFalse()
    }

    @Test
    fun `isDuplicateByHash allows different hash`() {
        filter.isDuplicateByHash("+966500000000", "Hello".hashCode(), 1000L)
        val result = filter.isDuplicateByHash("+966500000000", "World".hashCode(), 1000L)
        assertThat(result).isFalse()
    }

    // ════════════════════════════════════════════════════════════
    // Clear and stats
    // ════════════════════════════════════════════════════════════

    @Test
    fun `clearCache resets filter`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        filter.clearCache()
        val result = filter.isDuplicate("+966500000000", "Hello", 2000L)
        assertThat(result).isFalse()
    }

    @Test
    fun `getStats returns correct counts`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        filter.isDuplicate("+966500000000", "Hello", 2000L)
        filter.isDuplicate("+966500000001", "World", 1000L)

        val stats = filter.getStats()
        assertThat(stats.totalTracked).isEqualTo(2)
        assertThat(stats.duplicatesDetected).isEqualTo(1)
    }

    // ════════════════════════════════════════════════════════════
    // Boundary conditions
    // ════════════════════════════════════════════════════════════

    @Test
    fun `exactly at 5 second boundary is not duplicate`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000000", "Hello", 6000L)
        assertThat(result).isFalse()
    }

    @Test
    fun `one millisecond before boundary is duplicate`() {
        filter.isDuplicate("+966500000000", "Hello", 1000L)
        val result = filter.isDuplicate("+966500000000", "Hello", 5999L)
        assertThat(result).isTrue()
    }
}
