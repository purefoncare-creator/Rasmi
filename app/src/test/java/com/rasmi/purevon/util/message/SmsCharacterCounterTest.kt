package com.rasmi.purevon.util.message

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class SmsCharacterCounterTest {

    private lateinit var counter: SmsCharacterCounter

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        counter = SmsCharacterCounter()
    }

    @Test
    fun `calculateSegments for empty text`() {
        val info = counter.calculateSegments("")
        assertThat(info.segments).isEqualTo(0)
        assertThat(info.canSend).isFalse()
    }

    @Test
    fun `calculateSegments for short GSM text`() {
        val info = counter.calculateSegments("Hello")
        assertThat(info.encoding).isEqualTo(SmsCharacterCounter.Encoding.GSM_7BIT)
        assertThat(info.segments).isEqualTo(1)
        assertThat(info.isMultipart).isFalse()
        assertThat(info.remainingInSegment).isEqualTo(155)
    }

    @Test
    fun `calculateSegments for exactly 160 GSM characters`() {
        val text = "A".repeat(160)
        val info = counter.calculateSegments(text)
        assertThat(info.segments).isEqualTo(1)
        assertThat(info.isMultipart).isFalse()
        assertThat(info.remainingInSegment).isEqualTo(0)
    }

    @Test
    fun `calculateSegments for 161 GSM characters becomes multipart`() {
        val text = "A".repeat(161)
        val info = counter.calculateSegments(text)
        assertThat(info.segments).isEqualTo(2)
        assertThat(info.isMultipart).isTrue()
        assertThat(info.charsPerSegment).isEqualTo(153)
    }

    @Test
    fun `calculateSegments for Arabic text uses UCS2`() {
        val text = "مرحبا"
        val info = counter.calculateSegments(text)
        assertThat(info.encoding).isEqualTo(SmsCharacterCounter.Encoding.UCS2)
        assertThat(info.segments).isEqualTo(1)
    }

    @Test
    fun `calculateSegments for 71 Arabic characters becomes multipart`() {
        val text = "ع".repeat(71)
        val info = counter.calculateSegments(text)
        assertThat(info.encoding).isEqualTo(SmsCharacterCounter.Encoding.UCS2)
        assertThat(info.segments).isEqualTo(2)
    }

    @Test
    fun `getEncodingName returns correct names`() {
        assertThat(counter.getEncodingName(SmsCharacterCounter.Encoding.GSM_7BIT)).isEqualTo("GSM 7-bit")
        assertThat(counter.getEncodingName(SmsCharacterCounter.Encoding.UCS2)).isEqualTo("Unicode")
    }

    @Test
    fun `formatSegmentInfo for single part`() {
        val info = counter.calculateSegments("Hi")
        val formatted = counter.formatSegmentInfo(info)
        assertThat(formatted).contains("1 part")
        assertThat(formatted).doesNotContain("Unicode")
    }

    @Test
    fun `formatSegmentInfo for multipart`() {
        val info = counter.calculateSegments("A".repeat(300))
        val formatted = counter.formatSegmentInfo(info)
        assertThat(formatted).contains("parts")
    }

    @Test
    fun `formatSegmentInfo shows Unicode for UCS2`() {
        val info = counter.calculateSegments("مرحبا بالعالم")
        val formatted = counter.formatSegmentInfo(info)
        assertThat(formatted).contains("Unicode")
    }

    @Test
    fun `willCreateNewSegment false when not at boundary`() {
        val current = "Hello"
        assertThat(counter.willCreateNewSegment(current, ' ')).isFalse()
    }

    @Test
    fun `canSend true for non-empty text`() {
        assertThat(counter.calculateSegments("A").canSend).isTrue()
    }
}
