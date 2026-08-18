package com.rasmi.purevon.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DebugLoggerMaskingTest {

    // ════════════════════════════════════════════════════════════
    // maskPhoneNumber
    // ════════════════════════════════════════════════════════════

    @Test
    fun `maskPhoneNumber shows first 4 and last 4 for long numbers`() {
        val masked = DebugLogger.maskPhoneNumber("+1234567890")
        assertThat(masked).startsWith("+123")
        assertThat(masked).endsWith("7890")
        assertThat(masked).contains("***")
    }

    @Test
    fun `maskPhoneNumber returns stars for short numbers`() {
        val masked = DebugLogger.maskPhoneNumber("123")
        assertThat(masked).isEqualTo("***")
    }

    @Test
    fun `maskPhoneNumber exactly 6 chars returns stars`() {
        val masked = DebugLogger.maskPhoneNumber("123456")
        assertThat(masked).isEqualTo("***")
    }

    @Test
    fun `maskPhoneNumber more than 6 chars masks middle`() {
        val masked = DebugLogger.maskPhoneNumber("1234567")
        assertThat(masked).contains("***")
        assertThat(masked).startsWith("1234")
        assertThat(masked).endsWith("4567")
    }

    // ════════════════════════════════════════════════════════════
    // maskMessage
    // ════════════════════════════════════════════════════════════

    @Test
    fun `maskMessage long message shows truncated with char count`() {
        val longMsg = "This is a very long message that exceeds the limit"
        val masked = DebugLogger.maskMessage(longMsg)
        assertThat(masked).contains("...")
        assertThat(masked).contains("${longMsg.length} chars")
    }

    @Test
    fun `maskMessage short message shows stars with char count`() {
        val shortMsg = "Hi"
        val masked = DebugLogger.maskMessage(shortMsg)
        assertThat(masked).contains("***")
        assertThat(masked).contains("${shortMsg.length} chars")
    }

    @Test
    fun `maskMessage shows first 10 and last 10 for long messages`() {
        val msg = "ABCDEFGHIJKLMNOPQRST1234567890"
        val masked = DebugLogger.maskMessage(msg)
        assertThat(masked).contains("ABCDEFGHIJ")
        assertThat(masked).contains("1234567890")
    }

    // ════════════════════════════════════════════════════════════
    // maskName
    // ════════════════════════════════════════════════════════════

    @Test
    fun `maskName null returns Unknown`() {
        assertThat(DebugLogger.maskName(null)).isEqualTo("Unknown")
    }

    @Test
    fun `maskName blank returns Unknown`() {
        assertThat(DebugLogger.maskName("   ")).isEqualTo("Unknown")
    }

    @Test
    fun `maskName short name returns stars`() {
        val masked = DebugLogger.maskName("Ali")
        assertThat(masked).isEqualTo("***")
    }

    @Test
    fun `maskName exactly 4 chars returns stars`() {
        val masked = DebugLogger.maskName("John")
        assertThat(masked).isEqualTo("***")
    }

    @Test
    fun `maskName longer name shows first 2 and last 2`() {
        val masked = DebugLogger.maskName("Mohammed")
        assertThat(masked).startsWith("Mo")
        assertThat(masked).endsWith("ed")
        assertThat(masked).contains("***")
    }

    // ════════════════════════════════════════════════════════════
    // maskAddress
    // ════════════════════════════════════════════════════════════

    @Test
    fun `maskAddress null returns stars`() {
        assertThat(DebugLogger.maskAddress(null)).isEqualTo("***")
    }

    @Test
    fun `maskAddress blank returns stars`() {
        assertThat(DebugLogger.maskAddress("")).isEqualTo("***")
    }

    @Test
    fun `maskAddress shows first 5 chars then stars`() {
        val masked = DebugLogger.maskAddress("Riyadh, Saudi Arabia")
        assertThat(masked).startsWith("Riyad")
        assertThat(masked).endsWith("***")
    }

    @Test
    fun `maskAddress short address still shows 5 chars`() {
        val masked = DebugLogger.maskAddress("NYC")
        assertThat(masked).startsWith("NYC")
        assertThat(masked).endsWith("***")
    }
}
