package com.rasmi.purevon.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [MessageRepositoryImpl.rawSystemId] — the critical utility that converts
 * display IDs (with MMS offset) back to raw system IDs for ContentProvider queries.
 *
 * This is the fix for C-03: MMS identity mismatch.
 */
class MessageRepositoryImplRawSystemIdTest {

    // ════════════════════════════════════════════════════════════
    // rawSystemId — SMS messages (no offset)
    // ════════════════════════════════════════════════════════════

    @Test
    fun `rawSystemId returns same id for SMS message`() {
        val id = 42L
        val result = MessageRepositoryImpl.rawSystemId(id, isMms = false)
        assertThat(result).isEqualTo(42L)
    }

    @Test
    fun `rawSystemId returns same id for SMS with zero id`() {
        val result = MessageRepositoryImpl.rawSystemId(0L, isMms = false)
        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `rawSystemId returns same id for SMS with large id`() {
        val result = MessageRepositoryImpl.rawSystemId(999_999L, isMms = false)
        assertThat(result).isEqualTo(999_999L)
    }

    // ════════════════════════════════════════════════════════════
    // rawSystemId — MMS messages (offset applied)
    // ════════════════════════════════════════════════════════════

    @Test
    fun `rawSystemId subtracts offset for MMS message`() {
        val displayId = 2_000_000_042L // MMS raw ID 42 + offset
        val result = MessageRepositoryImpl.rawSystemId(displayId, isMms = true)
        assertThat(result).isEqualTo(42L)
    }

    @Test
    fun `rawSystemId returns offset itself for MMS with id equal to offset`() {
        val displayId = 2_000_000_000L // offset exactly
        val result = MessageRepositoryImpl.rawSystemId(displayId, isMms = true)
        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `rawSystemId handles MMS with large raw id`() {
        val rawId = 50_000L
        val displayId = 2_000_000_000L + rawId
        val result = MessageRepositoryImpl.rawSystemId(displayId, isMms = true)
        assertThat(result).isEqualTo(rawId)
    }

    // ════════════════════════════════════════════════════════════
    // rawSystemId — edge cases
    // ════════════════════════════════════════════════════════════

    @Test
    fun `rawSystemId does not subtract offset for MMS with id below offset`() {
        // Shouldn't happen in practice, but test defensive behavior
        val id = 100L
        val result = MessageRepositoryImpl.rawSystemId(id, isMms = true)
        assertThat(result).isEqualTo(100L) // Below offset, returned as-is
    }

    @Test
    fun `rawSystemId treats isMms=false as SMS even if id exceeds offset`() {
        // Edge case: an SMS with id >= offset (extremely unlikely)
        val id = 2_000_000_050L
        val result = MessageRepositoryImpl.rawSystemId(id, isMms = false)
        assertThat(result).isEqualTo(2_000_000_050L) // No offset subtraction
    }
}
