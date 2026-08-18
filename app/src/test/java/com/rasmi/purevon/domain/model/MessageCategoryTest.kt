package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageCategoryTest {

    @Test
    fun `fromString PERSONAL returns PERSONAL`() {
        assertThat(MessageCategory.fromString("PERSONAL")).isEqualTo(MessageCategory.PERSONAL)
    }

    @Test
    fun `fromString TRANSACTIONS returns TRANSACTIONS`() {
        assertThat(MessageCategory.fromString("TRANSACTIONS")).isEqualTo(MessageCategory.TRANSACTIONS)
    }

    @Test
    fun `fromString PROMOTIONS returns PROMOTIONS`() {
        assertThat(MessageCategory.fromString("PROMOTIONS")).isEqualTo(MessageCategory.PROMOTIONS)
    }

    @Test
    fun `fromString PROMOTIONAL returns PROMOTIONS`() {
        assertThat(MessageCategory.fromString("PROMOTIONAL")).isEqualTo(MessageCategory.PROMOTIONS)
    }

    @Test
    fun `fromString OTP returns OTP`() {
        assertThat(MessageCategory.fromString("OTP")).isEqualTo(MessageCategory.OTP)
    }

    @Test
    fun `fromString SPAM returns SPAM`() {
        assertThat(MessageCategory.fromString("SPAM")).isEqualTo(MessageCategory.SPAM)
    }

    @Test
    fun `fromString null returns UNKNOWN`() {
        assertThat(MessageCategory.fromString(null)).isEqualTo(MessageCategory.UNKNOWN)
    }

    @Test
    fun `fromString empty string returns UNKNOWN`() {
        assertThat(MessageCategory.fromString("")).isEqualTo(MessageCategory.UNKNOWN)
    }

    @Test
    fun `fromString unknown value returns UNKNOWN`() {
        assertThat(MessageCategory.fromString("FOOBAR")).isEqualTo(MessageCategory.UNKNOWN)
    }

    @Test
    fun `fromString is case-insensitive`() {
        assertThat(MessageCategory.fromString("personal")).isEqualTo(MessageCategory.PERSONAL)
        assertThat(MessageCategory.fromString("transactions")).isEqualTo(MessageCategory.TRANSACTIONS)
        assertThat(MessageCategory.fromString("promotions")).isEqualTo(MessageCategory.PROMOTIONS)
        assertThat(MessageCategory.fromString("otp")).isEqualTo(MessageCategory.OTP)
        assertThat(MessageCategory.fromString("spam")).isEqualTo(MessageCategory.SPAM)
    }

    @Test
    fun `fromString trims whitespace`() {
        assertThat(MessageCategory.fromString("  PERSONAL  ")).isEqualTo(MessageCategory.PERSONAL)
        assertThat(MessageCategory.fromString("  OTP  ")).isEqualTo(MessageCategory.OTP)
    }

    @Test
    fun `fromString is case-insensitive and trims`() {
        assertThat(MessageCategory.fromString("  personal  ")).isEqualTo(MessageCategory.PERSONAL)
    }

    @Test
    fun `all enum values are present`() {
        val values = MessageCategory.values()
        assertThat(values).hasLength(6)
        assertThat(values.toList()).containsExactly(
            MessageCategory.PERSONAL,
            MessageCategory.TRANSACTIONS,
            MessageCategory.PROMOTIONS,
            MessageCategory.OTP,
            MessageCategory.SPAM,
            MessageCategory.UNKNOWN
        )
    }

    @Test
    fun `valueOf works for all enum values`() {
        assertThat(MessageCategory.valueOf("PERSONAL")).isEqualTo(MessageCategory.PERSONAL)
        assertThat(MessageCategory.valueOf("TRANSACTIONS")).isEqualTo(MessageCategory.TRANSACTIONS)
        assertThat(MessageCategory.valueOf("PROMOTIONS")).isEqualTo(MessageCategory.PROMOTIONS)
        assertThat(MessageCategory.valueOf("OTP")).isEqualTo(MessageCategory.OTP)
        assertThat(MessageCategory.valueOf("SPAM")).isEqualTo(MessageCategory.SPAM)
        assertThat(MessageCategory.valueOf("UNKNOWN")).isEqualTo(MessageCategory.UNKNOWN)
    }

    @Test
    fun `fromString handles whitespace-only input`() {
        assertThat(MessageCategory.fromString("   ")).isEqualTo(MessageCategory.UNKNOWN)
    }

    @Test
    fun `fromString partial match does not match`() {
        assertThat(MessageCategory.fromString("PERS")).isEqualTo(MessageCategory.UNKNOWN)
        assertThat(MessageCategory.fromString("OT")).isEqualTo(MessageCategory.UNKNOWN)
    }
}
