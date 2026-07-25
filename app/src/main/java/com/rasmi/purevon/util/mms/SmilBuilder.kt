package com.rasmi.purevon.util.mms

import android.util.Log

/**
 * SMIL (Synchronized Multimedia Integration Language) builder for MMS messages.
 * Generates a valid SMIL document that describes how media parts should be
 * presented on the recipient's device.
 *
 * Improved over the previous manual SMIL generation:
 * - Smart grouping: text + media in the same slide (like QKSMS SmilHelper)
 * - vCard correctly represented as <vcard> element (was <ref>)
 * - Empty <layout/> to let the MMS renderer decide regions (compatible with all devices)
 * - 8000ms slide duration (MMS industry standard)
 * - Proper XML escaping for all src attributes
 *
 * Reference: QKSMS SmilHelper.java, OMA-MMS Encapsulation spec
 */
object SmilBuilder {

    private const val TAG = "SmilBuilder"

    /** Default slide duration in milliseconds (8 seconds — MMS standard) */
    private const val SLIDE_DURATION_MS = 8000

    /**
     * A single media/text part in the MMS message.
     * @param filename Content-Location or filename used as the SMIL src attribute
     * @param mimeType MIME type of the part (e.g., "image/jpeg", "text/plain")
     */
    data class SmilPart(
        val filename: String,
        val mimeType: String
    ) {
        val isText: Boolean
            get() = mimeType.startsWith("text/") &&
                    !mimeType.contains("vcard", ignoreCase = true)

        val isImage: Boolean
            get() = mimeType.startsWith("image/")

        val isVideo: Boolean
            get() = mimeType.startsWith("video/")

        val isAudio: Boolean
            get() = mimeType.startsWith("audio/")

        val isVCard: Boolean
            get() = mimeType.contains("vcard", ignoreCase = true)

        /** SMIL element tag name for this part type */
        val smilTag: String
            get() = when {
                isText -> "text"
                isImage -> "img"
                isVideo -> "video"
                isAudio -> "audio"
                isVCard -> "vcard"
                else -> "ref"
            }

        /** Whether this part needs a display region (audio does not) */
        val needsRegion: Boolean
            get() = !isAudio

        /** Whether this part counts as "media" for grouping purposes */
        val isMedia: Boolean
            get() = isImage || isVideo || isAudio || isVCard
    }

    /**
     * Build a SMIL document from the given parts.
     *
     * Grouping algorithm (inspired by QKSMS SmilHelper):
     * - Each slide (<par>) holds at most one text + one media part
     * - When both text and media are present in a slide, the next part starts a new slide
     * - Multiple media items without text share a slide
     * - Audio elements have no display region
     *
     * @param parts Ordered list of parts (text first, then attachments)
     * @return Complete SMIL XML string ready to insert as an MMS part
     */
    fun buildSmil(parts: List<SmilPart>): String {
        if (parts.isEmpty()) {
            Log.w(TAG, "No parts provided, returning minimal SMIL")
            return buildMinimalSmil()
        }

        val slides = groupPartsIntoSlides(parts)
        return buildSmilFromSlides(slides)
    }

    /**
     * Group parts into slides following the text+media pairing rule.
     *
     * Algorithm:
     * 1. Start a new slide
     * 2. Add text parts to the current slide (set hasText=true)
     * 3. Add media parts to the current slide (set hasMedia=true)
     * 4. When both hasText and hasMedia are true, start a new slide for the next part
     */
    internal fun groupPartsIntoSlides(parts: List<SmilPart>): List<List<SmilPart>> {
        val slides = mutableListOf<MutableList<SmilPart>>()
        var currentSlide = mutableListOf<SmilPart>()
        var hasText = false
        var hasMedia = false

        for (part in parts) {
            // Start a new slide if current one has both text and media
            if (currentSlide.isNotEmpty() && hasText && hasMedia) {
                slides.add(currentSlide)
                currentSlide = mutableListOf()
                hasText = false
                hasMedia = false
            }

            currentSlide.add(part)
            if (part.isText) hasText = true
            if (part.isMedia) hasMedia = true
        }

        // Add the last slide if it has content
        if (currentSlide.isNotEmpty()) {
            slides.add(currentSlide)
        }

        Log.d(TAG, "Grouped ${parts.size} parts into ${slides.size} slide(s)")
        return slides
    }

    /**
     * Build SMIL XML from pre-grouped slides.
     */
    private fun buildSmilFromSlides(slides: List<List<SmilPart>>): String {
        return buildString {
            append("<smil>")
            append("<head>")
            append("<layout/>")  // Empty layout — let MMS renderer decide regions
            append("</head>")
            append("<body>")

            for (slide in slides) {
                append("<par dur=\"${SLIDE_DURATION_MS}ms\">")
                for (part in slide) {
                    val safeSrc = escapeXml(part.filename)
                    append("<${part.smilTag} src=\"$safeSrc\"")
                    if (part.needsRegion) {
                        // Region is omitted — MMS renderer uses default layout
                    }
                    append("/>")
                }
                append("</par>")
            }

            append("</body>")
            append("</smil>")
        }
    }

    /**
     * Build a minimal valid SMIL document (no parts).
     */
    private fun buildMinimalSmil(): String {
        return "<smil><head><layout/></head><body>" +
                "<par dur=\"${SLIDE_DURATION_MS}ms\">" +
                "</par>" +
                "</body></smil>"
    }

    /**
     * Escape XML special characters in src attributes.
     * Prevents injection of malicious filenames into SMIL XML.
     */
    internal fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
