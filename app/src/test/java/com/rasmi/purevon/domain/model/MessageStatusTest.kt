package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageStatusTest {

    @Test
    fun `all enum values are present`() {
        assertThat(MessageStatus.values()).hasLength(4)
    }

    @Test
    fun `valueOf SENDING`() {
        assertThat(MessageStatus.valueOf("SENDING")).isEqualTo(MessageStatus.SENDING)
    }

    @Test
    fun `valueOf SENT`() {
        assertThat(MessageStatus.valueOf("SENT")).isEqualTo(MessageStatus.SENT)
    }

    @Test
    fun `valueOf DELIVERED`() {
        assertThat(MessageStatus.valueOf("DELIVERED")).isEqualTo(MessageStatus.DELIVERED)
    }

    @Test
    fun `valueOf FAILED`() {
        assertThat(MessageStatus.valueOf("FAILED")).isEqualTo(MessageStatus.FAILED)
    }

    @Test
    fun `SENDING is not equals SENT`() {
        assertThat(MessageStatus.SENDING).isNotEqualTo(MessageStatus.SENT)
    }

    @Test
    fun `DELIVERED is not equals FAILED`() {
        assertThat(MessageStatus.DELIVERED).isNotEqualTo(MessageStatus.FAILED)
    }

    @Test
    fun `name returns correct string`() {
        assertThat(MessageStatus.SENDING.name).isEqualTo("SENDING")
        assertThat(MessageStatus.SENT.name).isEqualTo("SENT")
        assertThat(MessageStatus.DELIVERED.name).isEqualTo("DELIVERED")
        assertThat(MessageStatus.FAILED.name).isEqualTo("FAILED")
    }

    @Test
    fun `ordinal is sequential`() {
        assertThat(MessageStatus.SENDING.ordinal).isEqualTo(0)
        assertThat(MessageStatus.SENT.ordinal).isEqualTo(1)
        assertThat(MessageStatus.DELIVERED.ordinal).isEqualTo(2)
        assertThat(MessageStatus.FAILED.ordinal).isEqualTo(3)
    }
}
