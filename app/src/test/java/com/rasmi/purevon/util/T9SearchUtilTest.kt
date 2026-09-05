package com.rasmi.purevon.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class T9SearchUtilTest {

    // ── calculateMatchScore ─────────────────────────────────────

    @Test
    fun `empty query returns NO_MATCH`() {
        assertThat(T9SearchUtil.calculateMatchScore("John", "123", "")).isEqualTo(0)
    }

    @Test
    fun `numeric query matching phone start returns highest`() {
        assertThat(T9SearchUtil.calculateMatchScore("John", "+966501234567", "966"))
            .isEqualTo(T9SearchUtil.MatchScore.NUMBER_STARTS_WITH)
    }

    @Test
    fun `numeric query containing phone mid-string returns 900`() {
        assertThat(T9SearchUtil.calculateMatchScore("John", "+966501234567", "0123"))
            .isEqualTo(T9SearchUtil.MatchScore.NUMBER_CONTAINS)
    }

    @Test
    fun `numeric query with no phone match returns NO_MATCH`() {
        assertThat(T9SearchUtil.calculateMatchScore("John", "123", "999")).isEqualTo(0)
    }

    @Test
    fun `numeric query normalizes punctuation in phone`() {
        assertThat(T9SearchUtil.calculateMatchScore("John", "(050) 123-4567", "050"))
            .isEqualTo(T9SearchUtil.MatchScore.NUMBER_STARTS_WITH)
    }

    @Test
    fun `name starts with query returns 800`() {
        assertThat(T9SearchUtil.calculateMatchScore("ahmed", "123", "ah"))
            .isEqualTo(T9SearchUtil.MatchScore.NAME_STARTS_WITH)
    }

    @Test
    fun `word starts with query returns 700`() {
        assertThat(T9SearchUtil.calculateMatchScore("Ahmed Hassan", "123", "has"))
            .isEqualTo(T9SearchUtil.MatchScore.NAME_WORD_STARTS_WITH)
    }

    @Test
    fun `initials match query returns 500`() {
        assertThat(T9SearchUtil.calculateMatchScore("John Smith", "123", "js"))
            .isEqualTo(T9SearchUtil.MatchScore.NAME_INITIALS)
    }

    @Test
    fun `name contains query returns 600`() {
        assertThat(T9SearchUtil.calculateMatchScore("Mohammed", "123", "ham"))
            .isEqualTo(T9SearchUtil.MatchScore.NAME_CONTAINS)
    }

    @Test
    fun `text query with no match returns NO_MATCH`() {
        assertThat(T9SearchUtil.calculateMatchScore("Mohammed", "123", "zzz")).isEqualTo(0)
    }

    @Test
    fun `matching is case insensitive`() {
        assertThat(T9SearchUtil.calculateMatchScore("AHMED", "123", "ah"))
            .isEqualTo(T9SearchUtil.MatchScore.NAME_STARTS_WITH)
    }

    // ── matchesT9 ───────────────────────────────────────────────

    @Test
    fun `matchesT9 empty query matches everything`() {
        assertThat(T9SearchUtil.matchesT9("John", "123", "")).isTrue()
    }

    @Test
    fun `matchesT9 empty name and phone returns false`() {
        assertThat(T9SearchUtil.matchesT9("", "", "abc")).isFalse()
    }

    @Test
    fun `matchesT9 numeric query matches phone`() {
        assertThat(T9SearchUtil.matchesT9("John", "+966501234567", "966")).isTrue()
        assertThat(T9SearchUtil.matchesT9("John", "+966501234567", "777")).isFalse()
    }

    @Test
    fun `matchesT9 text query matches name case insensitive`() {
        assertThat(T9SearchUtil.matchesT9("Ahmed", "+9665", "ahmed")).isTrue()
        assertThat(T9SearchUtil.matchesT9("Ahmed", "", "AHM")).isTrue()
    }

    @Test
    fun `matchesT9 numeric query with empty phone falls to name`() {
        assertThat(T9SearchUtil.matchesT9("Ahmed", "", "ahmed")).isTrue()
    }

    // ── textToT9 ────────────────────────────────────────────────

    @Test
    fun `textToT9 empty returns empty`() {
        assertThat(T9SearchUtil.textToT9("")).isEmpty()
    }

    @Test
    fun `textToT9 converts letters`() {
        assertThat(T9SearchUtil.textToT9("hello")).isEqualTo("43556")
        assertThat(T9SearchUtil.textToT9("world")).isEqualTo("96753")
        assertThat(T9SearchUtil.textToT9("john")).isEqualTo("5646")
    }

    @Test
    fun `textToT9 is case insensitive`() {
        assertThat(T9SearchUtil.textToT9("HELLO")).isEqualTo("43556")
    }

    @Test
    fun `textToT9 ignores non letter characters`() {
        assertThat(T9SearchUtil.textToT9("a-b 1")).isEqualTo("22")
    }

    // ── getInitialsT9 ───────────────────────────────────────────

    @Test
    fun `getInitialsT9 empty returns empty`() {
        assertThat(T9SearchUtil.getInitialsT9("")).isEmpty()
    }

    @Test
    fun `getInitialsT9 extracts first letters`() {
        assertThat(T9SearchUtil.getInitialsT9("John Smith")).isEqualTo("57")
    }

    @Test
    fun `getInitialsT9 single word returns only first letter`() {
        assertThat(T9SearchUtil.getInitialsT9("Ahmed")).isEqualTo("2")
    }

    @Test
    fun `getInitialsT9 ignores non alpha first chars`() {
        assertThat(T9SearchUtil.getInitialsT9("123 Ab")).isEqualTo("2")
    }

    // ── highlightMatches ────────────────────────────────────────

    @Test
    fun `highlightMatches empty query returns text unchanged`() {
        assertThat(T9SearchUtil.highlightMatches("John", "")).isEqualTo("John")
    }

    @Test
    fun `highlightMatches empty text returns empty`() {
        assertThat(T9SearchUtil.highlightMatches("", "abc")).isEmpty()
    }

    @Test
    fun `highlightMatches phone brackets matched digits`() {
        assertThat(T9SearchUtil.highlightMatches("+966501234567", "966", isPhoneNumber = true))
            .isEqualTo("+[966]501234567")
    }

    @Test
    fun `highlightMatches name brackets matched word`() {
        assertThat(T9SearchUtil.highlightMatches("Ahmed", "ah", isPhoneNumber = false))
            .isEqualTo("[Ah]med")
    }

    @Test
    fun `getHighlightedText detects phone by digits`() {
        assertThat(T9SearchUtil.getHighlightedText("+966501234567", "966"))
            .isEqualTo("+[966]501234567")
        assertThat(T9SearchUtil.getHighlightedText("Ahmed", "Ahm"))
            .isEqualTo("[Ahm]ed")
    }

    // ── getT9Combinations ───────────────────────────────────────

    @Test
    fun `getT9Combinations empty returns empty`() {
        assertThat(T9SearchUtil.getT9Combinations("")).isEmpty()
    }

    @Test
    fun `getT9Combinations two digits produces combos`() {
        val combos = T9SearchUtil.getT9Combinations("23")
        assertThat(combos).containsExactly(
            "ad", "ae", "af", "bd", "be", "bf", "cd", "ce", "cf"
        ).inOrder()
    }

    @Test
    fun `getT9Combinations skips non-letter digits`() {
        assertThat(T9SearchUtil.getT9Combinations("2a")).containsExactly("a", "b", "c")
    }

    // ── cache ───────────────────────────────────────────────────

    @Test
    fun `clearCache and getCacheStats report zero after clear`() {
        T9SearchUtil.textToT9("hello")
        T9SearchUtil.getInitialsT9("John Smith")
        T9SearchUtil.clearCache()
        val stats = T9SearchUtil.getCacheStats()
        assertThat(stats.t9CacheSize).isEqualTo(0)
        assertThat(stats.initialsCacheSize).isEqualTo(0)
    }
}
