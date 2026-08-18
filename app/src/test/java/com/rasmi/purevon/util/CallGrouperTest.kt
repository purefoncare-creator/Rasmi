package com.rasmi.purevon.util

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.model.CallType
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class CallGrouperTest {

    private lateinit var grouper: CallGrouper

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        grouper = CallGrouper()
    }

    private fun call(
        number: String = "+966501234567",
        name: String? = null,
        type: CallType = CallType.INCOMING,
        date: Long = System.currentTimeMillis(),
        duration: Long = 60
    ) = CallLog(
        id = 1L,
        phoneNumber = number,
        contactName = name,
        contactPhotoUri = null,
        callType = type,
        timestamp = date,
        duration = duration,
        simSlot = null,
        isSpam = false,
        spamScore = 0f,
        notes = null
    )

    // ════════════════════════════════════════════════════════════
    // groupByContact
    // ════════════════════════════════════════════════════════════

    @Test
    fun `groupByContact groups by name when available`() {
        val calls = listOf(
            call(number = "+966501111111", name = "Ali"),
            call(number = "+966502222222", name = "Omar"),
            call(number = "+966501111111", name = "Ali")
        )

        val groups = grouper.groupByContact(calls)

        assertThat(groups).hasSize(2)
        assertThat(groups["Ali"]!!).hasSize(2)
        assertThat(groups["Omar"]!!).hasSize(1)
    }

    @Test
    fun `groupByContact uses number when name is null`() {
        val calls = listOf(
            call(number = "+966501111111", name = null),
            call(number = "+966502222222", name = null)
        )

        val groups = grouper.groupByContact(calls)

        assertThat(groups).hasSize(2)
        assertThat(groups.containsKey("+966501111111")).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // groupByType
    // ════════════════════════════════════════════════════════════

    @Test
    fun `groupByType groups correctly`() {
        val calls = listOf(
            call(type = CallType.INCOMING),
            call(type = CallType.OUTGOING),
            call(type = CallType.INCOMING),
            call(type = CallType.MISSED)
        )

        val groups = grouper.groupByType(calls)

        assertThat(groups[CallType.INCOMING]!!).hasSize(2)
        assertThat(groups[CallType.OUTGOING]!!).hasSize(1)
        assertThat(groups[CallType.MISSED]!!).hasSize(1)
    }

    // ════════════════════════════════════════════════════════════
    // mergeConsecutiveCalls
    // ════════════════════════════════════════════════════════════

    @Test
    fun `mergeConsecutiveCalls merges same number within 1 hour`() {
        val now = System.currentTimeMillis()
        val calls = listOf(
            call(number = "+966501111111", type = CallType.INCOMING, date = now, duration = 60),
            call(number = "+966501111111", type = CallType.INCOMING, date = now + 1800_000, duration = 120)
        )

        val groups = grouper.mergeConsecutiveCalls(calls)

        assertThat(groups).hasSize(1)
        assertThat(groups[0].count).isEqualTo(2)
        assertThat(groups[0].totalDuration).isEqualTo(180)
    }

    @Test
    fun `mergeConsecutiveCalls separates different numbers`() {
        val now = System.currentTimeMillis()
        val calls = listOf(
            call(number = "+966501111111", date = now),
            call(number = "+966502222222", date = now + 1000)
        )

        val groups = grouper.mergeConsecutiveCalls(calls)

        assertThat(groups).hasSize(2)
    }

    @Test
    fun `mergeConsecutiveCalls separates different types`() {
        val now = System.currentTimeMillis()
        val calls = listOf(
            call(number = "+966501111111", type = CallType.INCOMING, date = now),
            call(number = "+966501111111", type = CallType.OUTGOING, date = now + 1000)
        )

        val groups = grouper.mergeConsecutiveCalls(calls)

        assertThat(groups).hasSize(2)
    }

    @Test
    fun `mergeConsecutiveCalls separates calls more than 1 hour apart`() {
        val now = System.currentTimeMillis()
        val calls = listOf(
            call(number = "+966501111111", type = CallType.INCOMING, date = now),
            call(number = "+966501111111", type = CallType.INCOMING, date = now + 3601_000)
        )

        val groups = grouper.mergeConsecutiveCalls(calls)

        assertThat(groups).hasSize(2)
    }

    @Test
    fun `mergeConsecutiveCalls empty list returns empty`() {
        val groups = grouper.mergeConsecutiveCalls(emptyList())
        assertThat(groups).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // getMostFrequentContacts
    // ════════════════════════════════════════════════════════════

    @Test
    fun `getMostFrequentContacts returns top contacts`() {
        val calls = listOf(
            call(number = "+966501111111", name = "Ali"),
            call(number = "+966501111111", name = "Ali"),
            call(number = "+966501111111", name = "Ali"),
            call(number = "+966502222222", name = "Omar")
        )

        val result = grouper.getMostFrequentContacts(calls, limit = 2)

        assertThat(result).hasSize(2)
        assertThat(result[0].number).isEqualTo("+966501111111")
        assertThat(result[0].callCount).isEqualTo(3)
        assertThat(result[1].callCount).isEqualTo(1)
    }

    @Test
    fun `getMostFrequentContacts respects limit`() {
        val calls = (1..5).map { call(number = "+96650000000$it") }

        val result = grouper.getMostFrequentContacts(calls, limit = 3)

        assertThat(result).hasSize(3)
    }

    @Test
    fun `getMostFrequentContacts calculates total duration`() {
        val calls = listOf(
            call(number = "+966501111111", duration = 60),
            call(number = "+966501111111", duration = 120)
        )

        val result = grouper.getMostFrequentContacts(calls)

        assertThat(result[0].totalDuration).isEqualTo(180)
    }

    // ════════════════════════════════════════════════════════════
    // getCallStatistics
    // ════════════════════════════════════════════════════════════

    @Test
    fun `getCallStatistics counts all types`() {
        val calls = listOf(
            call(type = CallType.INCOMING, duration = 100),
            call(type = CallType.INCOMING, duration = 200),
            call(type = CallType.OUTGOING, duration = 150),
            call(type = CallType.MISSED, duration = 0),
            call(type = CallType.BLOCKED, duration = 0)
        )

        val stats = grouper.getCallStatistics(calls)

        assertThat(stats.totalCalls).isEqualTo(5)
        assertThat(stats.incomingCalls).isEqualTo(2)
        assertThat(stats.outgoingCalls).isEqualTo(1)
        assertThat(stats.missedCalls).isEqualTo(1)
        assertThat(stats.blockedCalls).isEqualTo(1)
        assertThat(stats.totalDuration).isEqualTo(450)
        assertThat(stats.averageDuration).isEqualTo(90)
    }

    @Test
    fun `getCallStatistics empty list returns zeros`() {
        val stats = grouper.getCallStatistics(emptyList())

        assertThat(stats.totalCalls).isEqualTo(0)
        assertThat(stats.averageDuration).isEqualTo(0)
    }

    @Test
    fun `getCallStatistics average is total divided by count`() {
        val calls = listOf(
            call(duration = 100),
            call(duration = 200),
            call(duration = 300)
        )

        val stats = grouper.getCallStatistics(calls)

        assertThat(stats.averageDuration).isEqualTo(200)
    }
}
