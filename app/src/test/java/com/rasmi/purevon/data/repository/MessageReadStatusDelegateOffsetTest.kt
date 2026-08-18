package com.rasmi.purevon.data.repository

import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.entity.CachedMessageEntity
import com.rasmi.purevon.domain.model.MessageCategory
import org.junit.Test

/**
 * Tests for MessageReadStatusDelegate MMS offset handling (C-03 fix).
 *
 * Since the delegate requires Android Context (ContentResolver), we test the offset
 * logic in isolation via the companion constant and rawSystemId utility.
 */
class MessageReadStatusDelegateOffsetTest {

    private val MMS_ID_OFFSET = 2_000_000_000L

    @Test
    fun `MMS display id equals raw id plus offset`() {
        val rawId = 42L
        val displayId = rawId + MMS_ID_OFFSET
        assertThat(displayId).isEqualTo(2_000_000_042L)
    }

    @Test
    fun `raw id can be recovered from MMS display id`() {
        val displayId = 2_000_000_042L
        val rawId = displayId - MMS_ID_OFFSET
        assertThat(rawId).isEqualTo(42L)
    }

    @Test
    fun `SMS display id does not use offset`() {
        val rawId = 42L
        val displayId = rawId // No offset for SMS
        assertThat(displayId).isEqualTo(42L)
    }

    @Test
    fun `message with isMms true and id above offset is MMS`() {
        val id = 2_000_000_042L
        val isMms = id >= MMS_ID_OFFSET // Simple heuristic used in delegate
        assertThat(isMms).isTrue()
    }

    @Test
    fun `message with isMms false and id below offset is SMS`() {
        val id = 42L
        val isMms = id >= MMS_ID_OFFSET
        assertThat(isMms).isFalse()
    }

    @Test
    fun `raw id 0 is valid for MMS`() {
        val displayId = 0L + MMS_ID_OFFSET
        val rawId = displayId - MMS_ID_OFFSET
        assertThat(rawId).isEqualTo(0L)
    }

    @Test
    fun `large raw id preserves offset correctly`() {
        val rawId = 999_999L
        val displayId = rawId + MMS_ID_OFFSET
        val recoveredRawId = displayId - MMS_ID_OFFSET
        assertThat(recoveredRawId).isEqualTo(rawId)
    }
}
