package com.rasmi.purevon.domain.usecase.phone

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class FormatPhoneNumberUseCaseTest {

    private lateinit var useCase: FormatPhoneNumberUseCase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        useCase = FormatPhoneNumberUseCase()
    }

    @Test
    fun `invoke blank string returns as is`() {
        assertThat(useCase("")).isEmpty()
        assertThat(useCase("   ")).isEqualTo("   ")
    }

    @Test
    fun `invoke international format with plus`() {
        val result = useCase("+966501234567")
        assertThat(result).startsWith("+")
    }

    @Test
    fun `invoke international format with 00`() {
        val result = useCase("00966501234567")
        assertThat(result).startsWith("+")
    }

    @Test
    fun `invoke local format 10 digits`() {
        val result = useCase("0501234567")
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `invoke short code returned as is`() {
        assertThat(useCase("911")).isEqualTo("911")
    }

    @Test
    fun `getCleanDigits removes all non-digits`() {
        assertThat(useCase.getCleanDigits("+966 50 123 4567")).isEqualTo("966501234567")
    }

    @Test
    fun `getCleanDigits handles dashes`() {
        assertThat(useCase.getCleanDigits("050-123-4567")).isEqualTo("0501234567")
    }

    @Test
    fun `toE164 preserves plus prefix`() {
        assertThat(useCase.toE164("+966501234567")).isEqualTo("+966501234567")
    }

    @Test
    fun `toE164 converts 00 prefix to plus`() {
        assertThat(useCase.toE164("00966501234567")).isEqualTo("+966501234567")
    }

    @Test
    fun `toE164 adds country code for local number starting with 0`() {
        val result = useCase.toE164("0501234567", "966")
        assertThat(result).isEqualTo("+966501234567")
    }

    @Test
    fun `toE164 without country code keeps leading zero`() {
        val result = useCase.toE164("0501234567")
        assertThat(result).isEqualTo("0501234567")
    }

    @Test
    fun `toE164 adds country code for number without prefix`() {
        val result = useCase.toE164("501234567", "966")
        assertThat(result).isEqualTo("+966501234567")
    }
}
