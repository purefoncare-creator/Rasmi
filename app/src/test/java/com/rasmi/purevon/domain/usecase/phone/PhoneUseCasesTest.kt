package com.rasmi.purevon.domain.usecase.phone

import com.rasmi.purevon.domain.usecase.phone.ValidatePhoneNumberUseCase.ValidationResult
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for pure phone utility use cases (no Android context needed):
 *   ValidatePhoneNumber, FormatPhoneNumber
 */
class PhoneUseCasesTest {

    private lateinit var validateUseCase: ValidatePhoneNumberUseCase
    private lateinit var formatUseCase: FormatPhoneNumberUseCase

    @Before
    fun setUp() {
        validateUseCase = ValidatePhoneNumberUseCase()
        formatUseCase = FormatPhoneNumberUseCase()
    }

    // ════════════════════════════════════════════════════════════
    // ValidatePhoneNumberUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `validate valid Egyptian mobile number returns success`() {
        val result = validateUseCase("+201234567890")
        assertThat(result).isInstanceOf(ValidationResult.Success::class.java)
    }

    @Test
    fun `validate empty number returns error`() {
        val result = validateUseCase("")
        assertThat(result).isInstanceOf(ValidationResult.Error::class.java)
    }

    @Test
    fun `validate blank number returns error`() {
        val result = validateUseCase("   ")
        assertThat(result).isInstanceOf(ValidationResult.Error::class.java)
    }

    @Test
    fun `isValid returns true for valid number`() {
        assertThat(validateUseCase.isValid("+201234567890")).isTrue()
    }

    @Test
    fun `isValid returns false for empty string`() {
        assertThat(validateUseCase.isValid("")).isFalse()
    }

    @Test
    fun `isEmergencyNumber recognizes 123`() {
        assertThat(validateUseCase.isEmergencyNumber("123")).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // FormatPhoneNumberUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `format returns non-empty for valid number`() {
        val result = formatUseCase("+201234567890")
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `getCleanDigits strips non-digit characters`() {
        val result = formatUseCase.getCleanDigits("+20-123-456-7890")
        assertThat(result).doesNotContain("+")
        assertThat(result).doesNotContain("-")
        assertThat(result.all { it.isDigit() }).isTrue()
    }

    @Test
    fun `toE164 prepends country code for local number`() {
        val result = formatUseCase.toE164("01234567890", "20")
        assertThat(result).startsWith("+")
    }
}
