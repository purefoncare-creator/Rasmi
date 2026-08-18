package com.rasmi.purevon.util

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class PhoneUtilTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
    }

    // ════════════════════════════════════════════════════════════
    // formatPhoneNumber
    // ════════════════════════════════════════════════════════════

    @Test
    fun `10 digit domestic number formatted correctly`() {
        val result = PhoneUtil.formatPhoneNumber("0551234567")
        assertThat(result).isEqualTo("0551 234 567")
    }

    @Test
    fun `11 digit domestic number formatted correctly`() {
        val result = PhoneUtil.formatPhoneNumber("05512345678")
        assertThat(result).isEqualTo("0551 234 5678")
    }

    @Test
    fun `international number with plus prefix`() {
        val result = PhoneUtil.formatPhoneNumber("+966551234567")
        assertThat(result).isEqualTo("+96 655 123 4567")
    }

    @Test
    fun `international number with 00 prefix`() {
        val result = PhoneUtil.formatPhoneNumber("00966551234567")
        assertThat(result).startsWith("+")
    }

    @Test
    fun `short number returned as-is`() {
        val result = PhoneUtil.formatPhoneNumber("123")
        assertThat(result).isEqualTo("123")
    }

    @Test
    fun `empty string returned as-is`() {
        val result = PhoneUtil.formatPhoneNumber("")
        assertThat(result).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // isUssdCode
    // ════════════════════════════════════════════════════════════

    @Test
    fun `standard USSD code detected`() {
        assertThat(PhoneUtil.isUssdCode("*1400#")).isTrue()
    }

    @Test
    fun `complex USSD code detected`() {
        assertThat(PhoneUtil.isUssdCode("*123*456#")).isTrue()
    }

    @Test
    fun `regular number is not USSD`() {
        assertThat(PhoneUtil.isUssdCode("0551234567")).isFalse()
    }

    @Test
    fun `empty string is not USSD`() {
        assertThat(PhoneUtil.isUssdCode("")).isFalse()
    }

    @Test
    fun `hash only is not USSD`() {
        assertThat(PhoneUtil.isUssdCode("#")).isFalse()
    }

    @Test
    fun `star only is not USSD`() {
        assertThat(PhoneUtil.isUssdCode("*")).isFalse()
    }

    @Test
    fun `USSD code with spaces trimmed`() {
        assertThat(PhoneUtil.isUssdCode(" *1400# ")).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // isValidPhoneNumber
    // ════════════════════════════════════════════════════════════

    @Test
    fun `valid 10 digit number`() {
        assertThat(PhoneUtil.isValidPhoneNumber("0551234567")).isTrue()
    }

    @Test
    fun `valid 15 digit number`() {
        assertThat(PhoneUtil.isValidPhoneNumber("123456789012345")).isTrue()
    }

    @Test
    fun `valid international number`() {
        assertThat(PhoneUtil.isValidPhoneNumber("+966551234567")).isTrue()
    }

    @Test
    fun `too short number invalid`() {
        assertThat(PhoneUtil.isValidPhoneNumber("12")).isFalse()
    }

    @Test
    fun `too long number invalid`() {
        assertThat(PhoneUtil.isValidPhoneNumber("1234567890123456")).isFalse()
    }

    @Test
    fun `USSD code is always valid`() {
        assertThat(PhoneUtil.isValidPhoneNumber("*1400#")).isTrue()
    }

    @Test
    fun `letters only is invalid`() {
        assertThat(PhoneUtil.isValidPhoneNumber("abc")).isFalse()
    }

    @Test
    fun `3 digits is minimum valid`() {
        assertThat(PhoneUtil.isValidPhoneNumber("123")).isTrue()
    }

    @Test
    fun `2 digits is invalid`() {
        assertThat(PhoneUtil.isValidPhoneNumber("12")).isFalse()
    }

    // ════════════════════════════════════════════════════════════
    // isDialable
    // ════════════════════════════════════════════════════════════

    @Test
    fun `blank string is not dialable`() {
        assertThat(PhoneUtil.isDialable("")).isFalse()
        assertThat(PhoneUtil.isDialable("  ")).isFalse()
    }

    @Test
    fun `regular number is dialable`() {
        assertThat(PhoneUtil.isDialable("0551234567")).isTrue()
    }

    @Test
    fun `USSD code is dialable`() {
        assertThat(PhoneUtil.isDialable("*1400#")).isTrue()
    }

    @Test
    fun `number with dashes is dialable`() {
        assertThat(PhoneUtil.isDialable("055-123-4567")).isTrue()
    }

    @Test
    fun `number with spaces is dialable`() {
        assertThat(PhoneUtil.isDialable("055 123 4567")).isTrue()
    }

    @Test
    fun `only special chars with digits is dialable`() {
        assertThat(PhoneUtil.isDialable("+966 55 123 4567")).isTrue()
    }
}
