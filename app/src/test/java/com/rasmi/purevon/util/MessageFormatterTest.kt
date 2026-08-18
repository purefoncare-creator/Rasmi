package com.rasmi.purevon.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageFormatterTest {

    // ════════════════════════════════════════════════════════════
    // extractUrls
    // ════════════════════════════════════════════════════════════

    @Test
    fun `extractUrls finds http URL`() {
        val urls = MessageFormatter.extractUrls("Visit http://example.com today")
        assertThat(urls).isNotEmpty()
        assertThat(urls.first()).contains("example.com")
    }

    @Test
    fun `extractUrls finds https URL`() {
        val urls = MessageFormatter.extractUrls("Check https://google.com/search")
        assertThat(urls).isNotEmpty()
    }

    @Test
    fun `extractUrls finds www URL`() {
        val urls = MessageFormatter.extractUrls("Go to www.test.com")
        assertThat(urls).isNotEmpty()
    }

    @Test
    fun `extractUrls returns empty for plain text`() {
        val urls = MessageFormatter.extractUrls("Hello, how are you?")
        assertThat(urls).isEmpty()
    }

    @Test
    fun `extractUrls finds multiple URLs`() {
        val urls = MessageFormatter.extractUrls("Visit http://a.com and https://b.org")
        assertThat(urls.size).isAtLeast(2)
    }

    // ════════════════════════════════════════════════════════════
    // extractPhoneNumbers
    // ════════════════════════════════════════════════════════════

    @Test
    fun `extractPhoneNumbers finds US format`() {
        val phones = MessageFormatter.extractPhoneNumbers("Call 555-123-4567")
        assertThat(phones).isNotEmpty()
    }

    @Test
    fun `extractPhoneNumbers finds international format`() {
        val phones = MessageFormatter.extractPhoneNumbers("+1 555 123 4567")
        assertThat(phones).isNotEmpty()
    }

    @Test
    fun `extractPhoneNumbers returns empty for text only`() {
        val phones = MessageFormatter.extractPhoneNumbers("Hello world")
        assertThat(phones).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // truncateText
    // ════════════════════════════════════════════════════════════

    @Test
    fun `truncateText returns original if short enough`() {
        val text = "Hello"
        assertThat(MessageFormatter.truncateText(text, 10)).isEqualTo("Hello")
    }

    @Test
    fun `truncateText truncates long text with ellipsis`() {
        val text = "This is a very long message that should be truncated at some point"
        val result = MessageFormatter.truncateText(text, 30)
        assertThat(result.length).isAtMost(33) // 30 + "..."
        assertThat(result).endsWith("...")
    }

    @Test
    fun `truncateText truncates at word boundary`() {
        val text = "Hello beautiful world today"
        val result = MessageFormatter.truncateText(text, 12)
        assertThat(result).endsWith("...")
        assertThat(result).doesNotContain("beau...")
    }

    @Test
    fun `truncateText handles no spaces`() {
        val text = "abcdefghijklmnop"
        val result = MessageFormatter.truncateText(text, 10)
        assertThat(result).isEqualTo("abcdefghij...")
    }

    @Test
    fun `truncateText default max is 300`() {
        val shortText = "a".repeat(300)
        assertThat(MessageFormatter.truncateText(shortText)).isEqualTo(shortText)
    }

    // ════════════════════════════════════════════════════════════
    // detectMessageType
    // ════════════════════════════════════════════════════════════

    @Test
    fun `detectMediaType IMAGE for image mime`() {
        val result = MessageFormatter.detectMessageType(null, "image/jpeg")
        assertThat(result).isEqualTo(MessageFormatter.MessageContentType.IMAGE)
    }

    @Test
    fun `detectMediaType VIDEO for video mime`() {
        val result = MessageFormatter.detectMessageType(null, "video/mp4")
        assertThat(result).isEqualTo(MessageFormatter.MessageContentType.VIDEO)
    }

    @Test
    fun `detectMediaType AUDIO for audio mime`() {
        val result = MessageFormatter.detectMessageType(null, "audio/ogg")
        assertThat(result).isEqualTo(MessageFormatter.MessageContentType.AUDIO)
    }

    @Test
    fun `detectMediaType TEXT_WITH_LINK for URL text`() {
        val result = MessageFormatter.detectMessageType("Visit http://example.com", null)
        assertThat(result).isEqualTo(MessageFormatter.MessageContentType.TEXT_WITH_LINK)
    }

    @Test
    fun `detectMediaType EMPTY for null text`() {
        val result = MessageFormatter.detectMessageType(null, null)
        assertThat(result).isEqualTo(MessageFormatter.MessageContentType.EMPTY)
    }

    @Test
    fun `detectMediaType EMPTY for blank text`() {
        val result = MessageFormatter.detectMessageType("   ", null)
        assertThat(result).isEqualTo(MessageFormatter.MessageContentType.EMPTY)
    }

    @Test
    fun `detectMediaType TEXT for plain text`() {
        val result = MessageFormatter.detectMessageType("Hello world", null)
        assertThat(result).isEqualTo(MessageFormatter.MessageContentType.TEXT)
    }

    @Test
    fun `detectMediaType prefers mime over text`() {
        val result = MessageFormatter.detectMessageType("Has a link http://x.com", "image/png")
        assertThat(result).isEqualTo(MessageFormatter.MessageContentType.IMAGE)
    }

    // ════════════════════════════════════════════════════════════
    // formatRelativeTime
    // ════════════════════════════════════════════════════════════

    @Test
    fun `formatRelativeTime shows now for recent`() {
        val now = System.currentTimeMillis()
        val result = MessageFormatter.formatRelativeTime(now - 30_000) // 30 seconds ago
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `formatRelativeTime shows minutes for older`() {
        val now = System.currentTimeMillis()
        val result = MessageFormatter.formatRelativeTime(now - 3_600_000) // 1 hour
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `formatRelativeTime shows hours for older`() {
        val now = System.currentTimeMillis()
        val result = MessageFormatter.formatRelativeTime(now - 7_200_000) // 2 hours
        assertThat(result).isNotEmpty()
    }
}
