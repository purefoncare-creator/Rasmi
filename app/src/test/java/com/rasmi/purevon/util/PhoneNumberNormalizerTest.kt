package com.rasmi.purevon.util

import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.model.CountryPhoneRules
import com.rasmi.purevon.domain.model.CountryPhoneRule
import com.rasmi.purevon.domain.model.PhoneNumberIssue
import org.junit.Test

class PhoneNumberNormalizerTest {

    private val saRule = CountryPhoneRules.getByCountryCode("SA")!!
    private val usRule = CountryPhoneRules.getByCountryCode("US")!!
    private val egRule = CountryPhoneRules.getByCountryCode("EG")!!
    private val genericRule = CountryPhoneRules.GENERIC_RULE

    // ════════════════════════════════════════════════════════════
    // normalizePhoneNumber — Arabic digits
    // ════════════════════════════════════════════════════════════

    @Test
    fun `arabic digits are converted to latin`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("٩٦٦٥٠١٢٣٤٥٦٧", saRule)
        assertThat(result.normalized).contains("966")
        assertThat(result.issues).contains(PhoneNumberIssue.ARABIC_DIGITS)
    }

    @Test
    fun `pure latin digits have no ARABIC_DIGITS issue`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("+966501234567", saRule)
        assertThat(result.issues).doesNotContain(PhoneNumberIssue.ARABIC_DIGITS)
    }

    // ════════════════════════════════════════════════════════════
    // normalizePhoneNumber — formatting removal
    // ════════════════════════════════════════════════════════════

    @Test
    fun `spaces and dashes are removed`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("+1 212-555-1234", usRule)
        assertThat(result.normalized).doesNotContain(" ")
        assertThat(result.normalized).doesNotContain("-")
        assertThat(result.issues).contains(PhoneNumberIssue.HAS_FORMATTING)
    }

    @Test
    fun `parentheses are removed`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("+1 (212) 555-1234", usRule)
        assertThat(result.normalized).doesNotContain("(")
        assertThat(result.normalized).doesNotContain(")")
    }

    @Test
    fun `clean number has no HAS_FORMATTING issue`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("+966501234567", saRule)
        assertThat(result.issues).doesNotContain(PhoneNumberIssue.HAS_FORMATTING)
    }

    // ════════════════════════════════════════════════════════════
    // normalizePhoneNumber — 00 prefix
    // ════════════════════════════════════════════════════════════

    @Test
    fun `00 prefix is converted to plus`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("00966501234567", saRule)
        assertThat(result.normalized).startsWith("+966")
        assertThat(result.issues).contains(PhoneNumberIssue.HAS_00_PREFIX)
    }

    // ════════════════════════════════════════════════════════════
    // normalizePhoneNumber — country detection
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SA number is correctly normalized`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("+966501234567", saRule)
        assertThat(result.countryCode).isEqualTo("SA")
        assertThat(result.normalized).startsWith("+966")
        assertThat(result.needsUpdate).isFalse()
    }

    @Test
    fun `EG number with leading zero is normalized`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("+201012345678", egRule)
        assertThat(result.countryCode).isEqualTo("EG")
        assertThat(result.normalized).startsWith("+20")
    }

    // ════════════════════════════════════════════════════════════
    // normalizePhoneNumber — needsUpdate
    // ════════════════════════════════════════════════════════════

    @Test
    fun `already normalized number needs no update`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("+966501234567", saRule)
        assertThat(result.needsUpdate).isFalse()
    }

    @Test
    fun `unformatted number needs update`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("0501234567", saRule)
        assertThat(result.needsUpdate).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // normalizePhoneNumber — confidence
    // ════════════════════════════════════════════════════════════

    @Test
    fun `fully qualified number has high confidence`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("+966501234567", saRule)
        assertThat(result.confidence).isAtLeast(0.8)
    }

    @Test
    fun `confidence is between 0 and 1`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("0501234567", saRule)
        assertThat(result.confidence).isAtLeast(0.0)
        assertThat(result.confidence).isAtMost(1.0)
    }

    // ════════════════════════════════════════════════════════════
    // normalizePhoneNumber — flag emoji
    // ════════════════════════════════════════════════════════════

    @Test
    fun `flag emoji is present in result`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("+966501234567", saRule)
        assertThat(result.flagEmoji).isNotEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // normalizePhoneNumbers — batch
    // ════════════════════════════════════════════════════════════

    @Test
    fun `batch normalize returns same count`() {
        val numbers = listOf("+966501234567", "+966551234567", "0501234567")
        val results = PhoneNumberNormalizer.normalizePhoneNumbers(numbers, saRule)
        assertThat(results).hasSize(3)
    }

    // ════════════════════════════════════════════════════════════
    // needsNormalization
    // ════════════════════════════════════════════════════════════

    @Test
    fun `needsNormalization true for Arabic digits`() {
        assertThat(PhoneNumberNormalizer.needsNormalization("٩٦٦٥٠١٢٣٤")).isTrue()
    }

    @Test
    fun `needsNormalization true for 00 prefix`() {
        assertThat(PhoneNumberNormalizer.needsNormalization("00966501234")).isTrue()
    }

    @Test
    fun `needsNormalization true for spaces`() {
        assertThat(PhoneNumberNormalizer.needsNormalization("+1 212 555 1234")).isTrue()
    }

    @Test
    fun `needsNormalization false for clean international number`() {
        assertThat(PhoneNumberNormalizer.needsNormalization("+966501234567")).isFalse()
    }

    @Test
    fun `needsNormalization true for long number without plus`() {
        assertThat(PhoneNumberNormalizer.needsNormalization("9665012345678")).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // edge cases
    // ════════════════════════════════════════════════════════════

    @Test
    fun `empty string produces result with issues`() {
        val result = PhoneNumberNormalizer.normalizePhoneNumber("", saRule)
        assertThat(result.normalized).isNotEmpty()
    }

    @Test
    fun `original is preserved in result`() {
        val original = "050-123-4567"
        val result = PhoneNumberNormalizer.normalizePhoneNumber(original, saRule)
        assertThat(result.original).isEqualTo(original)
    }
}
