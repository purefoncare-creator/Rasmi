package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneNumberAnalysisTest {

    private fun createAnalysis(
        confidence: Double = 1.0,
        issues: List<PhoneNumberIssue> = emptyList()
    ) = PhoneNumberAnalysis(
        original = "0501234567",
        normalized = "+966501234567",
        countryCode = "SA",
        countryName = "Saudi Arabia",
        needsUpdate = true,
        confidence = confidence,
        issues = issues,
        flagEmoji = "🇸🇦"
    )

    @Test
    fun `isConfident true for confidence 1_0`() {
        assertThat(createAnalysis(confidence = 1.0).isConfident()).isTrue()
    }

    @Test
    fun `isConfident true for confidence 0_8`() {
        assertThat(createAnalysis(confidence = 0.8).isConfident()).isTrue()
    }

    @Test
    fun `isConfident false for confidence below 0_8`() {
        assertThat(createAnalysis(confidence = 0.5).isConfident()).isFalse()
    }

    @Test
    fun `needsCountryConfirmation true for mid confidence`() {
        assertThat(createAnalysis(confidence = 0.5).needsCountryConfirmation()).isTrue()
    }

    @Test
    fun `needsCountryConfirmation false for high confidence`() {
        assertThat(createAnalysis(confidence = 1.0).needsCountryConfirmation()).isFalse()
    }

    @Test
    fun `needsCountryConfirmation false for zero confidence`() {
        assertThat(createAnalysis(confidence = 0.0).needsCountryConfirmation()).isFalse()
    }

    @Test
    fun `PhoneNumberIssue enum has all expected values`() {
        val values = PhoneNumberIssue.entries
        assertThat(values).hasSize(8)
        assertThat(values).contains(PhoneNumberIssue.ARABIC_DIGITS)
        assertThat(values).contains(PhoneNumberIssue.MISSING_COUNTRY_CODE)
        assertThat(values).contains(PhoneNumberIssue.HAS_00_PREFIX)
        assertThat(values).contains(PhoneNumberIssue.HAS_FORMATTING)
        assertThat(values).contains(PhoneNumberIssue.LEADING_ZERO_WITH_COUNTRY)
        assertThat(values).contains(PhoneNumberIssue.INVALID_LENGTH)
        assertThat(values).contains(PhoneNumberIssue.INVALID_PREFIX)
        assertThat(values).contains(PhoneNumberIssue.UNCERTAIN_COUNTRY)
    }
}
