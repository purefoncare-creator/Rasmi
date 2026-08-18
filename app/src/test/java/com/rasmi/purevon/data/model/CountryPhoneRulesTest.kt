package com.rasmi.purevon.data.model

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class CountryPhoneRulesTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @Test
    fun `allCountries contains expected countries`() {
        assertThat(CountryPhoneRules.allCountries).isNotEmpty()
        assertThat(CountryPhoneRules.allCountries.size).isAtLeast(30)
    }

    @Test
    fun `getByDialCode returns SA for 966`() {
        val rule = CountryPhoneRules.getByDialCode("+966")
        assertThat(rule).isNotNull()
        assertThat(rule!!.countryCode).isEqualTo("SA")
    }

    @Test
    fun `getByDialCode returns US for 1`() {
        val rule = CountryPhoneRules.getByDialCode("+1")
        assertThat(rule).isNotNull()
        assertThat(rule!!.countryCode).isAnyOf("US", "CA")
    }

    @Test
    fun `getByDialCode returns null for unknown`() {
        assertThat(CountryPhoneRules.getByDialCode("+99999")).isNull()
    }

    @Test
    fun `getByCountryCode returns KW for KW`() {
        val rule = CountryPhoneRules.getByCountryCode("KW")
        assertThat(rule).isNotNull()
        assertThat(rule!!.dialCode).isEqualTo("+965")
    }

    @Test
    fun `getByCountryCode is case insensitive`() {
        val rule = CountryPhoneRules.getByCountryCode("sa")
        assertThat(rule).isNotNull()
        assertThat(rule!!.countryCode).isEqualTo("SA")
    }

    @Test
    fun `getByCountryCode returns null for unknown`() {
        assertThat(CountryPhoneRules.getByCountryCode("ZZ")).isNull()
    }

    @Test
    fun `detectCountry finds SA from international number`() {
        val rule = CountryPhoneRules.detectCountry("+966501234567")
        assertThat(rule).isNotNull()
        assertThat(rule!!.countryCode).isEqualTo("SA")
    }

    @Test
    fun `detectCountry finds US from international number`() {
        val rule = CountryPhoneRules.detectCountry("+12125551234")
        assertThat(rule).isNotNull()
        assertThat(rule!!.countryCode).isEqualTo("US")
    }

    @Test
    fun `detectCountry finds EG from 00 prefix`() {
        val rule = CountryPhoneRules.detectCountry("00201012345678")
        assertThat(rule).isNotNull()
        assertThat(rule!!.countryCode).isEqualTo("EG")
    }

    @Test
    fun `detectCountry returns null for plain local number`() {
        assertThat(CountryPhoneRules.detectCountry("501234567")).isNull()
    }

    @Test
    fun `getDefault returns GENERIC_RULE`() {
        val default = CountryPhoneRules.getDefault()
        assertThat(default.countryCode).isEqualTo("ZZ")
        assertThat(default.flagEmoji).isNotEmpty()
    }

    @Test
    fun `GENERIC_RULE has empty dial code`() {
        assertThat(CountryPhoneRules.GENERIC_RULE.dialCode).isEmpty()
    }

    @Test
    fun `all countries have valid dial codes`() {
        for (country in CountryPhoneRules.allCountries) {
            assertThat(country.dialCode).startsWith("+")
            assertThat(country.dialCode.length).isAtLeast(2)
        }
    }

    @Test
    fun `all countries have valid phone lengths`() {
        for (country in CountryPhoneRules.allCountries) {
            assertThat(country.phoneLength).isAtLeast(5)
            assertThat(country.phoneLength).isAtMost(12)
        }
    }

    @Test
    fun `all countries have non-empty example numbers`() {
        for (country in CountryPhoneRules.allCountries) {
            assertThat(country.exampleNumber).isNotEmpty()
        }
    }
}
