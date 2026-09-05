package com.rasmi.purevon.util.mms

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class SmilBuilderTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
    }

    // ── SmilPart type classification ────────────────────────────

    @Test
    fun `text part classified correctly`() {
        val part = SmilBuilder.SmilPart("body.txt", "text/plain")
        assertThat(part.isText).isTrue()
        assertThat(part.isImage).isFalse()
        assertThat(part.isVideo).isFalse()
        assertThat(part.isAudio).isFalse()
        assertThat(part.isVCard).isFalse()
        assertThat(part.isMedia).isFalse()
        assertThat(part.needsRegion).isTrue()
        assertThat(part.smilTag).isEqualTo("text")
    }

    @Test
    fun `image part classified correctly`() {
        val part = SmilBuilder.SmilPart("photo.jpg", "image/jpeg")
        assertThat(part.isImage).isTrue()
        assertThat(part.isMedia).isTrue()
        assertThat(part.smilTag).isEqualTo("img")
    }

    @Test
    fun `video part classified correctly`() {
        val part = SmilBuilder.SmilPart("clip.mp4", "video/mp4")
        assertThat(part.isVideo).isTrue()
        assertThat(part.isMedia).isTrue()
        assertThat(part.smilTag).isEqualTo("video")
    }

    @Test
    fun `audio part classified correctly and needs no region`() {
        val part = SmilBuilder.SmilPart("sound.mp3", "audio/mpeg")
        assertThat(part.isAudio).isTrue()
        assertThat(part.isMedia).isTrue()
        assertThat(part.needsRegion).isFalse()
        assertThat(part.smilTag).isEqualTo("audio")
    }

    @Test
    fun `vcard part classified correctly`() {
        val part = SmilBuilder.SmilPart("card.vcf", "text/x-vcard")
        assertThat(part.isText).isFalse()
        assertThat(part.isVCard).isTrue()
        assertThat(part.isMedia).isTrue()
        assertThat(part.smilTag).isEqualTo("vcard")
    }

    @Test
    fun `unknown mime falls back to ref`() {
        val part = SmilBuilder.SmilPart("file.bin", "application/octet-stream")
        assertThat(part.isText).isFalse()
        assertThat(part.isMedia).isFalse()
        assertThat(part.smilTag).isEqualTo("ref")
        assertThat(part.needsRegion).isTrue()
    }

    @Test
    fun `vcard detection is case insensitive`() {
        val part = SmilBuilder.SmilPart("c.vcf", "text/VCARD")
        assertThat(part.isVCard).isTrue()
    }

    // ── escapeXml ───────────────────────────────────────────────

    @Test
    fun `escapeXml escapes all special characters`() {
        assertThat(SmilBuilder.escapeXml("a&b<c>d\"e'f"))
            .isEqualTo("a&amp;b&lt;c&gt;d&quot;e&apos;f")
    }

    @Test
    fun `escapeXml leaves plain text unchanged`() {
        assertThat(SmilBuilder.escapeXml("photo.jpg")).isEqualTo("photo.jpg")
    }

    @Test
    fun `escapeXml handles empty and null-safe strings`() {
        assertThat(SmilBuilder.escapeXml("")).isEmpty()
    }

    // ── groupPartsIntoSlides ────────────────────────────────────

    @Test
    fun `single text part yields one slide`() {
        val slides = SmilBuilder.groupPartsIntoSlides(listOf(part("t.txt", "text/plain")))
        assertThat(slides).hasSize(1)
        assertThat(slides[0]).hasSize(1)
    }

    @Test
    fun `text plus media share one slide`() {
        val slides = SmilBuilder.groupPartsIntoSlides(
            listOf(part("t.txt", "text/plain"), part("i.jpg", "image/jpeg"))
        )
        assertThat(slides).hasSize(1)
        assertThat(slides[0]).hasSize(2)
    }

    @Test
    fun `two text parts share a slide`() {
        val slides = SmilBuilder.groupPartsIntoSlides(
            listOf(part("a.txt", "text/plain"), part("b.txt", "text/plain"))
        )
        assertThat(slides).hasSize(1)
        assertThat(slides[0]).hasSize(2)
    }

    @Test
    fun `multiple media without text share a slide`() {
        val slides = SmilBuilder.groupPartsIntoSlides(
            listOf(part("1.jpg", "image/jpeg"), part("2.jpg", "image/jpeg"), part("3.jpg", "image/jpeg"))
        )
        assertThat(slides).hasSize(1)
        assertThat(slides[0]).hasSize(3)
    }

    @Test
    fun `text media text splits into two slides`() {
        val slides = SmilBuilder.groupPartsIntoSlides(
            listOf(part("a.txt", "text/plain"), part("1.jpg", "image/jpeg"), part("b.txt", "text/plain"))
        )
        assertThat(slides).hasSize(2)
        assertThat(slides[0]).hasSize(2)
        assertThat(slides[1]).hasSize(1)
    }

    @Test
    fun `empty part list yields no slides`() {
        assertThat(SmilBuilder.groupPartsIntoSlides(emptyList())).isEmpty()
    }

    // ── buildSmil ───────────────────────────────────────────────

    @Test
    fun `buildSmil produces valid structure for text and image`() {
        val smil = SmilBuilder.buildSmil(
            listOf(part("hello.txt", "text/plain"), part("pic.jpg", "image/jpeg"))
        )
        assertThat(smil).startsWith("<smil>")
        assertThat(smil).contains("<head>")
        assertThat(smil).contains("<layout/>")
        assertThat(smil).contains("<body>")
        assertThat(smil).contains("<par dur=\"8000ms\">")
        assertThat(smil).contains("<text src=\"hello.txt\"/>")
        assertThat(smil).contains("<img src=\"pic.jpg\"/>")
        assertThat(smil).endsWith("</smil>")
    }

    @Test
    fun `buildSmil escapes unsafe filenames in src`() {
        val smil = SmilBuilder.buildSmil(listOf(part("a&b<c>.jpg", "image/jpeg")))
        assertThat(smil).contains("src=\"a&amp;b&lt;c&gt;.jpg\"")
        assertThat(smil).doesNotContain("<c>")
    }

    @Test
    fun `buildSmil with empty parts returns minimal smil`() {
        val smil = SmilBuilder.buildSmil(emptyList())
        assertThat(smil).startsWith("<smil>")
        assertThat(smil).contains("<par dur=\"8000ms\">")
        assertThat(smil).endsWith("</smil>")
    }

    @Test
    fun `buildSmil audio part omits region but still renders`() {
        val smil = SmilBuilder.buildSmil(listOf(part("s.mp3", "audio/mpeg")))
        assertThat(smil).contains("<audio src=\"s.mp3\"/>")
    }

    private fun part(filename: String, mimeType: String) =
        SmilBuilder.SmilPart(filename, mimeType)
}
