package com.rasmi.purevon.domain.usecase.phone

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidatePhoneNumberUseCaseTest {
    private val validate = ValidatePhoneNumberUseCase()

    @Test
    fun `accepts three digit service numbers`() {
        val result = validate("937")

        assertTrue(result is ValidatePhoneNumberUseCase.ValidationResult.Success)
        assertEquals("937", (result as ValidatePhoneNumberUseCase.ValidationResult.Success).cleanedNumber)
    }

    @Test
    fun `rejects numbers shorter than three digits`() {
        assertTrue(validate("12") is ValidatePhoneNumberUseCase.ValidationResult.Error)
    }

    @Test
    fun `accepts regular international numbers`() {
        assertTrue(validate("+966501234567") is ValidatePhoneNumberUseCase.ValidationResult.Success)
    }
}
