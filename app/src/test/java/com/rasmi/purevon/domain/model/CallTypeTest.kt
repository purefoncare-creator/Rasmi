package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CallTypeTest {

    @Test
    fun `fromSystemType maps INCOMING correctly`() {
        assertThat(CallType.fromSystemType(1)).isEqualTo(CallType.INCOMING)
    }

    @Test
    fun `fromSystemType maps OUTGOING correctly`() {
        assertThat(CallType.fromSystemType(2)).isEqualTo(CallType.OUTGOING)
    }

    @Test
    fun `fromSystemType maps MISSED correctly`() {
        assertThat(CallType.fromSystemType(3)).isEqualTo(CallType.MISSED)
    }

    @Test
    fun `fromSystemType maps REJECTED correctly`() {
        assertThat(CallType.fromSystemType(5)).isEqualTo(CallType.REJECTED)
    }

    @Test
    fun `fromSystemType maps BLOCKED correctly`() {
        assertThat(CallType.fromSystemType(6)).isEqualTo(CallType.BLOCKED)
    }

    @Test
    fun `fromSystemType maps VOICEMAIL correctly`() {
        assertThat(CallType.fromSystemType(4)).isEqualTo(CallType.VOICEMAIL)
    }

    @Test
    fun `fromSystemType defaults to INCOMING for unknown`() {
        assertThat(CallType.fromSystemType(99)).isEqualTo(CallType.INCOMING)
    }

    @Test
    fun `toSystemType returns correct values`() {
        assertThat(CallType.INCOMING.toSystemType()).isEqualTo(1)
        assertThat(CallType.OUTGOING.toSystemType()).isEqualTo(2)
        assertThat(CallType.MISSED.toSystemType()).isEqualTo(3)
        assertThat(CallType.REJECTED.toSystemType()).isEqualTo(5)
        assertThat(CallType.BLOCKED.toSystemType()).isEqualTo(6)
        assertThat(CallType.VOICEMAIL.toSystemType()).isEqualTo(4)
    }

    @Test
    fun `round-trip fromSystemType and toSystemType`() {
        for (type in CallType.entries) {
            assertThat(CallType.fromSystemType(type.toSystemType())).isEqualTo(type)
        }
    }
}
