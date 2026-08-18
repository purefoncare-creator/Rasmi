package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContactAnalysisResultTest {

    private fun createAnalysis(
        totalContacts: Int = 100,
        needsCleanup: Int = 25,
        estimatedSeconds: Int = 30
    ) = ContactAnalysisResult(
        totalContacts = totalContacts,
        needsCleanup = needsCleanup,
        byIssueType = mapOf(PhoneNumberIssue.ARABIC_DIGITS to 10),
        samples = emptyList(),
        allNeedingUpdate = emptyList(),
        estimatedSeconds = estimatedSeconds,
        countryUsed = CountryPhoneRule(
            countryCode = "SA", countryName = "Saudi Arabia", countryNameAr = "السعودية",
            dialCode = "+966", phoneLength = 9, startsWithZero = true,
            validPrefixes = listOf("5"), exampleNumber = "+966501234567", flagEmoji = "🇸🇦"
        )
    )

    @Test
    fun `getCleanupPercentage calculates correctly`() {
        val result = createAnalysis(totalContacts = 100, needsCleanup = 25)
        assertThat(result.getCleanupPercentage()).isEqualTo(25)
    }

    @Test
    fun `getCleanupPercentage returns 0 for zero totalContacts`() {
        val result = createAnalysis(totalContacts = 0, needsCleanup = 0)
        assertThat(result.getCleanupPercentage()).isEqualTo(0)
    }

    @Test
    fun `getCleanupPercentage rounds down`() {
        val result = createAnalysis(totalContacts = 3, needsCleanup = 1)
        assertThat(result.getCleanupPercentage()).isEqualTo(33)
    }

    @Test
    fun `getCleanupPercentage 100 percent when all need cleanup`() {
        val result = createAnalysis(totalContacts = 50, needsCleanup = 50)
        assertThat(result.getCleanupPercentage()).isEqualTo(100)
    }

    @Test
    fun `getEstimatedTimeString under 60 seconds`() {
        val result = createAnalysis(estimatedSeconds = 30)
        assertThat(result.getEstimatedTimeString()).contains("30")
    }

    @Test
    fun `getEstimatedTimeString between 60 and 120 seconds shows one minute`() {
        val result = createAnalysis(estimatedSeconds = 90)
        assertThat(result.getEstimatedTimeString()).contains("دقيقة")
    }

    @Test
    fun `getEstimatedTimeString over 120 seconds shows minutes`() {
        val result = createAnalysis(estimatedSeconds = 180)
        assertThat(result.getEstimatedTimeString()).contains("3")
        assertThat(result.getEstimatedTimeString()).contains("دقائق")
    }

    @Test
    fun `equality works`() {
        val a = createAnalysis()
        val b = createAnalysis()
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val original = createAnalysis(totalContacts = 100)
        val copy = original.copy(totalContacts = 200)
        assertThat(copy.totalContacts).isEqualTo(200)
        assertThat(copy.needsCleanup).isEqualTo(original.needsCleanup)
    }
}
