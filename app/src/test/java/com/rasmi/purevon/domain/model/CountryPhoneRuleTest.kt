package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CountryPhoneRuleTest {

    private val saRule = CountryPhoneRule(
        countryCode = "SA",
        countryName = "Saudi Arabia",
        countryNameAr = "السعودية",
        dialCode = "+966",
        phoneLength = 9,
        startsWithZero = true,
        validPrefixes = listOf("5"),
        exampleNumber = "+966501234567",
        flagEmoji = "🇸🇦"
    )

    private val kwRule = CountryPhoneRule(
        countryCode = "KW",
        countryName = "Kuwait",
        countryNameAr = "الكويت",
        dialCode = "+965",
        phoneLength = 8,
        startsWithZero = false,
        validPrefixes = listOf("5", "6", "9"),
        exampleNumber = "+96551234567",
        flagEmoji = "🇰🇼"
    )

    @Test
    fun `getNumericDialCode removes plus prefix`() {
        assertThat(saRule.getNumericDialCode()).isEqualTo("966")
        assertThat(kwRule.getNumericDialCode()).isEqualTo("965")
    }

    @Test
    fun `matchesFormat true for international number with dial code`() {
        assertThat(saRule.matchesFormat("+966501234567")).isTrue()
    }

    @Test
    fun `matchesFormat true for local number with zero`() {
        assertThat(saRule.matchesFormat("0501234567")).isTrue()
    }

    @Test
    fun `matchesFormat false for wrong number length`() {
        assertThat(saRule.matchesFormat("05012345")).isFalse()
    }

    @Test
    fun `matchesFormat true for Kuwait without leading zero`() {
        assertThat(kwRule.matchesFormat("+96551234567")).isTrue()
    }

    @Test
    fun `matchesFormat true for Kuwait local number`() {
        assertThat(kwRule.matchesFormat("51234567")).isTrue()
    }

    @Test
    fun `matchesFormat false for random string`() {
        assertThat(saRule.matchesFormat("hello")).isFalse()
    }

    @Test
    fun `matchesFormat true for number with spaces and dashes`() {
        assertThat(saRule.matchesFormat("+966 50 123 4567")).isTrue()
    }
}
