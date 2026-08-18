package com.rasmi.purevon.data.local.entity

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageTypeTest {

    @Test
    fun `fromInt 1 returns RECEIVED`() {
        assertThat(MessageType.fromInt(1)).isEqualTo(MessageType.RECEIVED)
    }

    @Test
    fun `fromInt 2 returns SENT`() {
        assertThat(MessageType.fromInt(2)).isEqualTo(MessageType.SENT)
    }

    @Test
    fun `fromInt 3 returns DRAFT`() {
        assertThat(MessageType.fromInt(3)).isEqualTo(MessageType.DRAFT)
    }

    @Test
    fun `fromInt 4 returns OUTBOX`() {
        assertThat(MessageType.fromInt(4)).isEqualTo(MessageType.OUTBOX)
    }

    @Test
    fun `fromInt 5 returns FAILED`() {
        assertThat(MessageType.fromInt(5)).isEqualTo(MessageType.FAILED)
    }

    @Test
    fun `fromInt 6 returns QUEUED`() {
        assertThat(MessageType.fromInt(6)).isEqualTo(MessageType.QUEUED)
    }

    @Test
    fun `fromInt 0 returns RECEIVED as default`() {
        assertThat(MessageType.fromInt(0)).isEqualTo(MessageType.RECEIVED)
    }

    @Test
    fun `fromInt 99 returns RECEIVED as default`() {
        assertThat(MessageType.fromInt(99)).isEqualTo(MessageType.RECEIVED)
    }

    @Test
    fun `fromInt -1 returns RECEIVED as default`() {
        assertThat(MessageType.fromInt(-1)).isEqualTo(MessageType.RECEIVED)
    }

    @Test
    fun `value property matches constructor`() {
        assertThat(MessageType.SENT.value).isEqualTo(2)
        assertThat(MessageType.RECEIVED.value).isEqualTo(1)
        assertThat(MessageType.DRAFT.value).isEqualTo(3)
        assertThat(MessageType.OUTBOX.value).isEqualTo(4)
        assertThat(MessageType.FAILED.value).isEqualTo(5)
        assertThat(MessageType.QUEUED.value).isEqualTo(6)
    }

    @Test
    fun `all enum values are present`() {
        assertThat(MessageType.values()).hasLength(6)
    }
}
