package com.rasmi.purevon.util.message

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * SMS Character Counter with Encoding Detection
 * Inspired by QKSMS implementation
 * 
 * Handles:
 * - GSM 7-bit encoding (160 chars per segment)
 * - UCS-2/UTF-16 encoding (70 chars per segment for Arabic/Emoji)
 * - Multipart message splitting
 * - Character counting with proper encoding
 */
@Singleton
class SmsCharacterCounter @Inject constructor() {

    companion object {
        // GSM 7-bit basic character set
        private const val GSM_7BIT_CHARS = "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        
        // GSM 7-bit extended characters (count as 2 chars)
        private const val GSM_7BIT_EXTENDED = "^{}\\\\\\[~\\]|€"
        
        // Single segment limits
        private const val GSM_7BIT_SINGLE_LIMIT = 160
        private const val UCS2_SINGLE_LIMIT = 70
        
        // Multipart segment limits
        private const val GSM_7BIT_MULTI_LIMIT = 153
        private const val UCS2_MULTI_LIMIT = 67
    }

    /**
     * Data class for SMS segment information
     */
    data class SmsSegmentInfo(
        val encoding: Encoding,
        val length: Int,
        val segments: Int,
        val charsPerSegment: Int,
        val remainingInSegment: Int,
        val extendedCharsCount: Int = 0
    ) {
        val canSend: Boolean get() = length > 0
        val isMultipart: Boolean get() = segments > 1
    }

    enum class Encoding {
        GSM_7BIT,
        UCS2
    }

    /**
     * Calculate SMS segment information for the given text
     */
    fun calculateSegments(text: String): SmsSegmentInfo {
        if (text.isEmpty()) {
            return SmsSegmentInfo(
                encoding = Encoding.GSM_7BIT,
                length = 0,
                segments = 0,
                charsPerSegment = GSM_7BIT_SINGLE_LIMIT,
                remainingInSegment = GSM_7BIT_SINGLE_LIMIT
            )
        }

        // Determine encoding
        val encoding = detectEncoding(text)
        val extendedCharsCount = if (encoding == Encoding.GSM_7BIT) {
            countExtendedChars(text)
        } else 0
        
        // Calculate effective length (extended chars count as 2)
        val effectiveLength = text.length + extendedCharsCount
        
        // Determine segment limits
        val singleLimit = when (encoding) {
            Encoding.GSM_7BIT -> GSM_7BIT_SINGLE_LIMIT
            Encoding.UCS2 -> UCS2_SINGLE_LIMIT
        }
        
        val multiLimit = when (encoding) {
            Encoding.GSM_7BIT -> GSM_7BIT_MULTI_LIMIT
            Encoding.UCS2 -> UCS2_MULTI_LIMIT
        }
        
        // Calculate segments
        val isMultipart = effectiveLength > singleLimit
        val charsPerSegment = if (isMultipart) multiLimit else singleLimit
        val segments = if (isMultipart) {
            ceil(effectiveLength.toDouble() / multiLimit).toInt()
        } else 1
        
        val remainingInSegment = if (isMultipart) {
            multiLimit - (effectiveLength % multiLimit).let { if (it == 0) multiLimit else it }
        } else {
            singleLimit - effectiveLength
        }
        
        return SmsSegmentInfo(
            encoding = encoding,
            length = effectiveLength,
            segments = segments,
            charsPerSegment = charsPerSegment,
            remainingInSegment = remainingInSegment,
            extendedCharsCount = extendedCharsCount
        )
    }

    /**
     * Detect encoding required for the text
     */
    private fun detectEncoding(text: String): Encoding {
        return if (canUseGsm7Bit(text)) {
            Encoding.GSM_7BIT
        } else {
            Encoding.UCS2
        }
    }

    /**
     * Check if text can be encoded using GSM 7-bit
     */
    private fun canUseGsm7Bit(text: String): Boolean {
        return text.all { char ->
            char in GSM_7BIT_CHARS || char in GSM_7BIT_EXTENDED
        }
    }

    /**
     * Count extended characters (they consume 2 chars)
     */
    private fun countExtendedChars(text: String): Int {
        return text.count { it in GSM_7BIT_EXTENDED }
    }

    /**
     * Get encoding name for display
     */
    fun getEncodingName(encoding: Encoding): String {
        return when (encoding) {
            Encoding.GSM_7BIT -> "GSM 7-bit"
            Encoding.UCS2 -> "Unicode"
        }
    }

    /**
     * Format segment info for display
     * Example: "1/2 (67 chars remaining)"
     */
    fun formatSegmentInfo(info: SmsSegmentInfo): String {
        return buildString {
            if (info.segments > 1) {
                append("${info.segments} parts")
            } else {
                append("1 part")
            }
            append(" • ")
            append("${info.remainingInSegment} chars left")
            
            if (info.encoding == Encoding.UCS2) {
                append(" (Unicode)")
            }
        }
    }

    /**
     * Check if adding a character will create a new segment
     */
    fun willCreateNewSegment(currentText: String, charToAdd: Char): Boolean {
        val current = calculateSegments(currentText)
        val withChar = calculateSegments(currentText + charToAdd)
        return withChar.segments > current.segments
    }
}
