package com.rasmi.purevon.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DateTimeUtilsTest {

    private fun todayMillis(): Long {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun daysAgoMillis(days: Int): Long {
        return LocalDate.now()
            .minusDays(days.toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun hoursAgoMillis(hours: Int): Long {
        return LocalDateTime.now()
            .minusHours(hours.toLong())
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    // ════════════════════════════════════════════════════════════
    // isToday
    // ════════════════════════════════════════════════════════════

    @Test
    fun `isToday returns true for today`() {
        assertThat(DateTimeUtils.isToday(System.currentTimeMillis())).isTrue()
    }

    @Test
    fun `isToday returns false for yesterday`() {
        assertThat(DateTimeUtils.isToday(daysAgoMillis(1))).isFalse()
    }

    @Test
    fun `isToday returns false for 2 days ago`() {
        assertThat(DateTimeUtils.isToday(daysAgoMillis(2))).isFalse()
    }

    // ════════════════════════════════════════════════════════════
    // isYesterday
    // ════════════════════════════════════════════════════════════

    @Test
    fun `isYesterday returns true for yesterday`() {
        assertThat(DateTimeUtils.isYesterday(daysAgoMillis(1))).isTrue()
    }

    @Test
    fun `isYesterday returns false for today`() {
        assertThat(DateTimeUtils.isYesterday(System.currentTimeMillis())).isFalse()
    }

    @Test
    fun `isYesterday returns false for 2 days ago`() {
        assertThat(DateTimeUtils.isYesterday(daysAgoMillis(2))).isFalse()
    }

    // ════════════════════════════════════════════════════════════
    // formatMessageTime
    // ════════════════════════════════════════════════════════════

    @Test
    fun `formatMessageTime shows time for today`() {
        val result = DateTimeUtils.formatMessageTime(System.currentTimeMillis())
        assertThat(result).matches("\\d{2}:\\d{2}")
    }

    @Test
    fun `formatMessageTime shows date format for older`() {
        val result = DateTimeUtils.formatMessageTime(daysAgoMillis(5))
        assertThat(result).contains("\\")
    }

    // ════════════════════════════════════════════════════════════
    // formatStarredMessageTime
    // ════════════════════════════════════════════════════════════

    @Test
    fun `formatStarredMessageTime shows time for today`() {
        val result = DateTimeUtils.formatStarredMessageTime(System.currentTimeMillis())
        assertThat(result).matches("\\d{2}:\\d{2}")
    }

    @Test
    fun `formatStarredMessageTime shows yesterday for yesterday`() {
        val result = DateTimeUtils.formatStarredMessageTime(daysAgoMillis(1))
        assertThat(result).isEqualTo("Yesterday")
    }

    @Test
    fun `formatStarredMessageTime shows day name for this week`() {
        val result = DateTimeUtils.formatStarredMessageTime(daysAgoMillis(3))
        // Should be a day name like "Monday", "Tuesday", etc.
        assertThat(result).isNotEmpty()
        assertThat(result).isNotEqualTo("Yesterday")
    }

    @Test
    fun `formatStarredMessageTime shows short date for older`() {
        val result = DateTimeUtils.formatStarredMessageTime(daysAgoMillis(10))
        assertThat(result).isNotEmpty()
    }
}
