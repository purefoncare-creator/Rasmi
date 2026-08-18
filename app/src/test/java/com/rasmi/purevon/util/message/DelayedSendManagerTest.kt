package com.rasmi.purevon.util.message

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DelayedSendManagerTest {

    @Test
    fun `DelayPresets constants are correct`() {
        assertThat(DelayedSendManager.DelayPresets.ONE_MINUTE).isEqualTo(60_000L)
        assertThat(DelayedSendManager.DelayPresets.FIVE_MINUTES).isEqualTo(300_000L)
        assertThat(DelayedSendManager.DelayPresets.TEN_MINUTES).isEqualTo(600_000L)
        assertThat(DelayedSendManager.DelayPresets.THIRTY_MINUTES).isEqualTo(1_800_000L)
        assertThat(DelayedSendManager.DelayPresets.ONE_HOUR).isEqualTo(3_600_000L)
        assertThat(DelayedSendManager.DelayPresets.TWO_HOURS).isEqualTo(7_200_000L)
    }

    @Test
    fun `getPresetLabel returns correct labels`() {
        assertThat(DelayedSendManager.DelayPresets.getPresetLabel(60_000L)).isEqualTo("بعد دقيقة")
        assertThat(DelayedSendManager.DelayPresets.getPresetLabel(300_000L)).isEqualTo("بعد 5 دقائق")
        assertThat(DelayedSendManager.DelayPresets.getPresetLabel(600_000L)).isEqualTo("بعد 10 دقائق")
        assertThat(DelayedSendManager.DelayPresets.getPresetLabel(1_800_000L)).isEqualTo("بعد 30 دقيقة")
        assertThat(DelayedSendManager.DelayPresets.getPresetLabel(3_600_000L)).isEqualTo("بعد ساعة")
        assertThat(DelayedSendManager.DelayPresets.getPresetLabel(7_200_000L)).isEqualTo("بعد ساعتين")
    }

    @Test
    fun `getPresetLabel returns custom for unknown delay`() {
        assertThat(DelayedSendManager.DelayPresets.getPresetLabel(999_999L)).isEqualTo("مخصص")
    }

    @Test
    fun `DelayedMessageInfo holds data correctly`() {
        val info = DelayedSendManager.DelayedMessageInfo(
            workId = "abc-123",
            phoneNumber = "+966501234567",
            state = "ENQUEUED",
            scheduledTime = System.currentTimeMillis()
        )
        assertThat(info.workId).isEqualTo("abc-123")
        assertThat(info.phoneNumber).isEqualTo("+966501234567")
        assertThat(info.state).isEqualTo("ENQUEUED")
    }
}
