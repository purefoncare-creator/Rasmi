package com.rasmi.purevon.data.local

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.data.local.entity.MessageType
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        converters = Converters()
    }

    // CallType converters
    @Test
    fun `fromCallType and toCallType round-trip`() {
        for (type in CallType.entries) {
            val serialized = converters.fromCallType(type)
            val deserialized = converters.toCallType(serialized)
            assertThat(deserialized).isEqualTo(type)
        }
    }

    @Test
    fun `toCallType defaults to INCOMING for unknown`() {
        assertThat(converters.toCallType("UNKNOWN_TYPE")).isEqualTo(CallType.INCOMING)
    }

    // MessageType converters
    @Test
    fun `fromMessageType and toMessageType round-trip`() {
        for (type in MessageType.entries) {
            val serialized = converters.fromMessageType(type)
            val deserialized = converters.toMessageType(serialized)
            assertThat(deserialized).isEqualTo(type)
        }
    }

    @Test
    fun `toMessageType defaults to RECEIVED for unknown`() {
        assertThat(converters.toMessageType("UNKNOWN")).isEqualTo(MessageType.RECEIVED)
    }

    // MessageCategory converters
    @Test
    fun `fromMessageCategory and toMessageCategory round-trip`() {
        for (category in MessageCategory.entries) {
            val serialized = converters.fromMessageCategory(category)
            val deserialized = converters.toMessageCategory(serialized)
            assertThat(deserialized).isEqualTo(category)
        }
    }

    @Test
    fun `toMessageCategory defaults to PERSONAL for unknown`() {
        assertThat(converters.toMessageCategory("NONEXISTENT")).isEqualTo(MessageCategory.PERSONAL)
    }

    // SpamType converters
    @Test
    fun `fromSpamType and toSpamType round-trip`() {
        for (spamType in com.rasmi.purevon.data.local.entity.SpamType.entries) {
            val serialized = converters.fromSpamType(spamType)
            val deserialized = converters.toSpamType(serialized)
            assertThat(deserialized).isEqualTo(spamType)
        }
    }

    @Test
    fun `toSpamType defaults to UNKNOWN for unknown`() {
        assertThat(converters.toSpamType("NONEXISTENT")).isEqualTo(com.rasmi.purevon.data.local.entity.SpamType.UNKNOWN)
    }

    // String list converters
    @Test
    fun `fromStringList and toStringList round-trip`() {
        val list = listOf("a", "b", "c")
        val serialized = converters.fromStringList(list)
        val deserialized = converters.toStringList(serialized)
        assertThat(deserialized).isEqualTo(list)
    }

    @Test
    fun `toStringList returns empty list for null`() {
        assertThat(converters.toStringList(null)).isEmpty()
    }

    @Test
    fun `fromStringList returns null for null list`() {
        assertThat(converters.fromStringList(null)).isNull()
    }

    @Test
    fun `toStringList returns empty list for invalid JSON`() {
        assertThat(converters.toStringList("not-valid-json{")).isEmpty()
    }

    @Test
    fun `fromStringList and toStringList with empty list`() {
        val serialized = converters.fromStringList(emptyList())
        assertThat(converters.toStringList(serialized)).isEmpty()
    }
}
