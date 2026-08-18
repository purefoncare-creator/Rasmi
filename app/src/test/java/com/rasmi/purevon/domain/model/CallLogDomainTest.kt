package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CallLogDomainTest {

    private fun createCallLog(
        id: Long = 1L,
        phoneNumber: String = "+1234567890",
        contactName: String? = "John",
        callType: CallType = CallType.INCOMING,
        timestamp: Long = 1000L,
        duration: Long = 120L,
        simSlot: Int? = 1,
        isSpam: Boolean = false,
        spamScore: Float = 0f,
        notes: String? = null,
        isBlocked: Boolean = false
    ) = CallLog(
        id = id, phoneNumber = phoneNumber, contactName = contactName,
        contactPhotoUri = null, callType = callType, timestamp = timestamp,
        duration = duration, simSlot = simSlot, isSpam = isSpam,
        spamScore = spamScore, notes = notes, isBlocked = isBlocked
    )

    @Test
    fun `number alias returns phoneNumber`() {
        val log = createCallLog(phoneNumber = "+19998887777")
        assertThat(log.number).isEqualTo("+19998887777")
    }

    @Test
    fun `name alias returns contactName`() {
        val log = createCallLog(contactName = "Alice")
        assertThat(log.name).isEqualTo("Alice")
    }

    @Test
    fun `type alias returns callType system int`() {
        val log = createCallLog(callType = CallType.INCOMING)
        assertThat(log.type).isEqualTo(android.provider.CallLog.Calls.INCOMING_TYPE)
    }

    @Test
    fun `date alias returns timestamp`() {
        val log = createCallLog(timestamp = 12345L)
        assertThat(log.date).isEqualTo(12345L)
    }

    @Test
    fun `equality works`() {
        val a = createCallLog(id = 1L)
        val b = createCallLog(id = 1L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `copy modifies only specified fields`() {
        val original = createCallLog(notes = null)
        val copy = original.copy(notes = "Important")
        assertThat(copy.notes).isEqualTo("Important")
        assertThat(copy.id).isEqualTo(original.id)
    }

    @Test
    fun `null contactName is valid`() {
        val log = createCallLog(contactName = null)
        assertThat(log.contactName).isNull()
    }

    @Test
    fun `null notes is valid`() {
        val log = createCallLog(notes = null)
        assertThat(log.notes).isNull()
    }

    @Test
    fun `isBlocked defaults false`() {
        val log = createCallLog()
        assertThat(log.isBlocked).isFalse()
    }

    @Test
    fun `blocked call`() {
        val log = createCallLog(isBlocked = true)
        assertThat(log.isBlocked).isTrue()
    }

    @Test
    fun `spam call`() {
        val log = createCallLog(isSpam = true, spamScore = 0.8f)
        assertThat(log.isSpam).isTrue()
        assertThat(log.spamScore).isWithin(0.01f).of(0.8f)
    }

    @Test
    fun `hash is consistent`() {
        val log = createCallLog()
        assertThat(log.hashCode()).isEqualTo(log.hashCode())
    }
}
