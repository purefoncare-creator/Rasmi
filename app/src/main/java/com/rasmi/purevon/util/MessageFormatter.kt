package com.rasmi.purevon.util

/**
 * Message Formatting Utilities
 * تحسين عرض الرسائل الطويلة والروابط
 */
object MessageFormatter {
    
    /**
     * استخراج الروابط من النص
     */
    fun extractUrls(text: String): List<String> {
        val urlPattern = Regex(
            """(https?://)?(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)""",
            RegexOption.IGNORE_CASE
        )
        return urlPattern.findAll(text).map { it.value }.toList()
    }
    
    /**
     * استخراج أرقام الهواتف من النص
     */
    fun extractPhoneNumbers(text: String): List<String> {
        val phonePattern = Regex(
            """(\+?\d{1,3}[-.\s]?)?(\(?\d{3}\)?[-.\s]?)?\d{3}[-.\s]?\d{4}"""
        )
        return phonePattern.findAll(text).map { it.value }.toList()
    }
    
    /**
     * اختصار النص الطويل مع الحفاظ على الكلمات
     */
    fun truncateText(text: String, maxLength: Int = 300): String {
        if (text.length <= maxLength) return text
        
        val truncated = text.take(maxLength)
        val lastSpace = truncated.lastIndexOf(' ')
        
        return if (lastSpace > 0) {
            truncated.take(lastSpace) + "..."
        } else {
            truncated + "..."
        }
    }
    
    /**
     * تحديد نوع الرسالة (نص، صورة، صوت، فيديو)
     */
    fun detectMessageType(text: String?, mimeType: String?): MessageContentType {
        return when {
            mimeType?.startsWith("image/") == true -> MessageContentType.IMAGE
            mimeType?.startsWith("video/") == true -> MessageContentType.VIDEO
            mimeType?.startsWith("audio/") == true -> MessageContentType.AUDIO
            text?.contains("http", ignoreCase = true) == true -> MessageContentType.TEXT_WITH_LINK
            text.isNullOrBlank() -> MessageContentType.EMPTY
            else -> MessageContentType.TEXT
        }
    }
    
    /**
     * تنسيق الوقت النسبي (منذ 5 دقائق، منذ ساعة، الخ)
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "الآن"
            diff < 3600_000 -> "منذ ${diff / 60_000} د"
            diff < 86400_000 -> "منذ ${diff / 3600_000} س"
            diff < 604800_000 -> "منذ ${diff / 86400_000} يوم"
            else -> "منذ ${diff / 604800_000} أسبوع"
        }
    }
    
    // ✅ FIX #29: Renamed from MessageType to avoid collision with data.local.entity.MessageType
    enum class MessageContentType {
        TEXT,
        TEXT_WITH_LINK,
        IMAGE,
        VIDEO,
        AUDIO,
        EMPTY
    }
}
